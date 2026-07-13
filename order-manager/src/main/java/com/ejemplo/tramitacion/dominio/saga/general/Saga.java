package com.ejemplo.tramitacion.dominio.saga.general;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * EL concepto del dominio: una saga es una secuencia de pasos con estado
 * persistente, reintentos con backoff e intervención de soporte.
 *
 * Aquí no hay sagas "padre" ni "hijas": todas las sagas son iguales ante este
 * ciclo de vida. Que al completarse una arranquen otras es una DECISIÓN que
 * emite el agregado que termina (ver Decision.ArrancarSaga), no una relación
 * jerárquica: la saga arrancada nace independiente y nunca vuelve a mirar
 * a la que la originó.
 *
 * Ciclo de vida común que esta base encapsula:
 * - iniciar/continuar idempotentes: la reentrega por lease del GestorOrdenes
 *   puede reinvocarlos; si el estado ya avanzó son un no-op o una reanudación.
 * - Ante fallo: backoff exponencial hasta agotar intentos, luego ticket a soporte.
 * - Soporte puede reintentar manualmente o marcar el paso como OK.
 */
public abstract sealed class Saga permits SagaPrincipal, SagaSucesora {

    protected final SagaId id;
    protected final EnumMap<Paso, EjecucionPaso> pasos;
    protected final List<AuditoriaIntervencion> auditoria;
    protected final PoliticaReintentos reintentos = new PoliticaReintentos();
    protected EstadoSaga estado;
    protected long version;

    protected Saga(SagaId id, EnumSet<Paso> misPasos, EstadoSaga estado, long version) {
        this.id = id;
        this.pasos = new EnumMap<>(Paso.class);
        misPasos.forEach(p -> pasos.put(p, EjecucionPaso.nuevo(p)));
        this.auditoria = new ArrayList<>();
        this.estado = estado;
        this.version = version;
    }

    /** Constructor de rehidratación para el adaptador de persistencia. */
    protected Saga(SagaId id, EnumMap<Paso, EjecucionPaso> pasos,
                   List<AuditoriaIntervencion> auditoria, EstadoSaga estado, long version) {
        this.id = id;
        this.pasos = pasos;
        this.auditoria = new ArrayList<>(auditoria);
        this.estado = estado;
        this.version = version;
    }

    // --- puntos de extensión: la lógica específica de cada saga ---

    public abstract TipoSaga tipo();

    public abstract DatoNegocio1Id datoNegocio1Id();

    protected abstract Paso pasoInicial();

    protected abstract ComandoPaso comandoPara(Paso paso);

    protected abstract void aplicarResultado(Paso paso, ResultadoPaso resultado);

    /** Qué viene después de completar un paso: el siguiente Ejecutar, o finalizar(). */
    protected abstract List<Decision> transicionTras(Paso paso);

    /** Pasos cuyo marcar-OK manual exige aportar datos (los consume un paso posterior). */
    protected EnumSet<Paso> pasosConDatosManualesObligatorios() {
        return EnumSet.noneOf(Paso.class);
    }

    /** Solo la saga principal puede ser cancelable, y solo antes de su punto de no retorno. */
    public boolean esCancelable() {
        return false;
    }

    // --- ciclo de vida común ---

    public final List<Decision> iniciar() {
        if (estado != EstadoSaga.INICIADA) {
            return List.of(); // idempotente: la recuperación tras caída puede reinvocar
        }
        estado = EstadoSaga.EN_CURSO;
        return solicitar(pasoInicial());
    }

    /**
     * Reanudación tras reentrega de la tarea por el lease del GestorOrdenes
     * (el proceso murió a mitad). Cubre tanto la saga que nunca arrancó como el
     * paso SOLICITADO cuyo resultado se perdió. Reemitir el Ejecutar implica
     * entrega at-least-once hacia los servicios externos: deben ser
     * idempotentes por datoNegocio1.
     */
    public final List<Decision> continuar() {
        if (estado == EstadoSaga.INICIADA) {
            return iniciar();
        }
        if (estado != EstadoSaga.EN_CURSO) {
            return List.of(); // completada o cancelada: nada que relanzar
        }
        return pasos.values().stream()
                .filter(ep -> ep.estado() == EstadoPaso.SOLICITADO)
                .findFirst()
                .<List<Decision>>map(ep -> List.of(new Decision.Ejecutar(ep.paso(), comandoPara(ep.paso()))))
                .orElse(List.of());
    }

    public final List<Decision> completar(Paso paso, ResultadoPaso resultado) {
        var ep = pasoDe(paso);
        if (estado == EstadoSaga.CANCELADA || ep.estado() != EstadoPaso.SOLICITADO) {
            return List.of(); // duplicado, tardío o saga cancelada: se ignora
        }
        ep.completar();
        aplicarResultado(paso, resultado);
        return transicionTras(paso);
    }

    public final List<Decision> fallar(Paso paso, MotivoFallo motivo) {
        var ep = pasoDe(paso);
        if (estado == EstadoSaga.CANCELADA || ep.estado() != EstadoPaso.SOLICITADO) {
            return List.of();
        }
        ep.registrarFallo(motivo);

        if (motivo.esReintentable() && !reintentos.agotado(ep.intentos())) {
            ep.esperarReintento();
            return List.of(new Decision.ProgramarReintento(
                    paso, reintentos.esperaTras(ep.intentos()), ep.intentos() + 1));
        }
        ep.bloquear();
        return List.of(new Decision.AbrirTicketSoporte(
                id, tipo(), paso, motivo, ep.intentos(), esCancelable()));
    }

    /** Lo invoca el caso de uso cuando vence el reintento programado. */
    public final List<Decision> reintentar(Paso paso) {
        var ep = pasoDe(paso);
        if (estado == EstadoSaga.CANCELADA || ep.estado() != EstadoPaso.ESPERANDO_REINTENTO) {
            return List.of(); // cancelada o ya intervenida entre medias
        }
        return solicitar(paso);
    }

    // --- intervenciones de soporte ---

    /** Reintento manual: resetea el contador (vuelve a haber intentos) y relanza el paso. */
    public final List<Decision> reanudarPorSoporte(Paso paso, UsuarioSoporte quien) {
        var ep = pasoDe(paso);
        if (ep.estado() != EstadoPaso.BLOQUEADO_SOPORTE) {
            throw new PasoNoIntervenibleException(id, paso, ep.estado());
        }
        ep.resetearIntentos();
        auditoria.add(AuditoriaIntervencion.de(quien, "REINTENTO_MANUAL", paso.name()));
        return solicitar(paso);
    }

    /**
     * Soporte arregló el paso a mano en el sistema destino y lo marca OK.
     * La saga CONTINÚA su flujo normal desde ese paso (no se cierra en falso).
     * Si pasos posteriores consumen datos que este paso producía, hay que aportarlos.
     */
    public final List<Decision> marcarOkManual(Paso paso, UsuarioSoporte quien,
                                               String justificacion, ResultadoPaso datos) {
        var ep = pasoDe(paso);
        if (ep.estado() != EstadoPaso.BLOQUEADO_SOPORTE) {
            throw new PasoNoIntervenibleException(id, paso, ep.estado());
        }
        if (pasosConDatosManualesObligatorios().contains(paso) && datos == null) {
            throw new DatosManualesRequeridosException(id, paso);
        }
        ep.completarManual();
        if (datos != null) {
            aplicarResultado(paso, datos);
        }
        auditoria.add(AuditoriaIntervencion.de(quien, "MARCAR_OK_MANUAL", paso + ": " + justificacion));
        return transicionTras(paso); // la saga continúa (o finaliza) su curso normal
    }

    // --- helpers para las subclases ---

    protected final List<Decision> solicitar(Paso p) {
        pasoDe(p).solicitar();
        return List.of(new Decision.Ejecutar(p, comandoPara(p)));
    }

    protected final List<Decision> finalizar() {
        estado = EstadoSaga.COMPLETADA;
        return List.of(new Decision.PublicarEvento(
                new EventoTramitacion.SagaCompletada(id, tipo(), datoNegocio1Id())));
    }

    protected final EjecucionPaso pasoDe(Paso p) {
        var ep = pasos.get(p);
        if (ep == null) {
            throw new IllegalArgumentException("Paso " + p + " ajeno a la saga " + tipo());
        }
        return ep;
    }

    // --- lectura (persistencia y pantalla de soporte) ---

    public final SagaId id() { return id; }
    public final EstadoSaga estado() { return estado; }
    public final long version() { return version; }
    public final Map<Paso, EjecucionPaso> pasos() { return Collections.unmodifiableMap(pasos); }
    public final List<AuditoriaIntervencion> auditoria() { return Collections.unmodifiableList(auditoria); }
    public final boolean tienePasosBloqueados() {
        return pasos.values().stream().anyMatch(ep -> ep.estado() == EstadoPaso.BLOQUEADO_SOPORTE);
    }
    public final boolean requiereDatosManuales(Paso paso) {
        return pasosConDatosManualesObligatorios().contains(paso);
    }
}
