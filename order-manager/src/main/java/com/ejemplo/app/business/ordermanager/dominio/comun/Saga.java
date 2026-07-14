package com.ejemplo.app.business.ordermanager.dominio.comun;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.jmolecules.ddd.annotation.Identity;

/**
 * EL concepto del dominio: una saga es una secuencia de pasos con estado
 * persistente, reintentos con backoff e intervención de soporte.
 *
 * Base abstracta común de los 4 agregados (SagaPrincipalRoot y las 3
 * SagaSecundariaNRoot). NO es un agregado en sí misma: es el ciclo de vida
 * compartido. Genérica en P, el enum de pasos propio de cada saga: cada
 * agregado solo puede razonar sobre SUS pasos y el compilador lo garantiza.
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
 * - Fallo reintentable: backoff exponencial (1..180 min) y, consumida la
 *   escalera, reintento indefinido cada 180 min con el flag de ticket
 *   PENDIENTE (se borra si un reintento acaba bien).
 * - Fallo NO reintentable (p. ej. JSON imparseable): sin reintento automático;
 *   la saga queda FALLIDA (paso BLOQUEADO_SOPORTE) y con ticket PENDIENTE.
 * - Los tickets NO se abren aquí: el planificador barre los PENDIENTE, abre
 *   UN ticket para todos y los deja en ABIERTO con su fecha.
 * - Soporte puede reintentar manualmente o marcar el paso como OK: la saga
 *   FALLIDA vuelve a EN_CURSO.
 */
public abstract class Saga<P extends Enum<P> & PasoSaga> {

    @Identity
    protected final SagaId id;
    protected final ExternalId externalId;
    protected final Class<P> tipoPaso;
    protected final EnumMap<P, EjecucionPaso<P>> pasos;
    protected final List<AuditoriaIntervencion> auditoria;
    protected final PoliticaReintentos reintentos = new PoliticaReintentos();
    protected EstadoSaga estado;
    protected EstadoTicket estadoTicket;
    protected Instant ticketAbiertoEn;
    protected long version;

    protected Saga(SagaId id, ExternalId externalId, Class<P> tipoPaso,
                   EnumSet<P> misPasos, EstadoSaga estado, long version) {
        this.id = id;
        this.externalId = externalId;
        this.tipoPaso = tipoPaso;
        this.pasos = new EnumMap<>(tipoPaso);
        misPasos.forEach(p -> pasos.put(p, EjecucionPaso.nuevo(p)));
        this.auditoria = new ArrayList<>();
        this.estado = estado;
        this.estadoTicket = EstadoTicket.SIN_TICKET;
        this.ticketAbiertoEn = null;
        this.version = version;
    }

    /** Constructor de rehidratación para el adaptador de persistencia. */
    protected Saga(SagaId id, ExternalId externalId, Class<P> tipoPaso,
                   EnumMap<P, EjecucionPaso<P>> pasos, List<AuditoriaIntervencion> auditoria,
                   EstadoSaga estado, EstadoTicket estadoTicket, Instant ticketAbiertoEn,
                   long version) {
        this.id = id;
        this.externalId = externalId;
        this.tipoPaso = tipoPaso;
        this.pasos = pasos;
        this.auditoria = new ArrayList<>(auditoria);
        this.estado = estado;
        this.estadoTicket = estadoTicket;
        this.ticketAbiertoEn = ticketAbiertoEn;
        this.version = version;
    }

    // --- puntos de extensión: la lógica específica de cada saga ---

    public abstract TipoSaga tipo();

    protected abstract P pasoInicial();

    protected abstract ComandoPaso comandoPara(P paso);

    protected abstract void aplicarResultado(P paso, ResultadoPaso resultado);

    /** Qué viene después de completar un paso: el siguiente Ejecutar, o finalizar(). */
    protected abstract List<Decision<P>> transicionTras(P paso);

    /** Pasos cuyo marcar-OK manual exige aportar datos (los consume un paso posterior). */
    protected EnumSet<P> pasosConDatosManualesObligatorios() {
        return EnumSet.noneOf(tipoPaso);
    }

    /** Solo la saga principal puede ser cancelable, y solo antes de su punto de no retorno. */
    public boolean esCancelable() {
        return false;
    }

    // --- ciclo de vida común ---

    public final List<Decision<P>> iniciar() {
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
     * idempotentes por externalId.
     */
    public final List<Decision<P>> continuar() {
        if (estado == EstadoSaga.INICIADA) {
            return iniciar();
        }
        if (estado != EstadoSaga.EN_CURSO) {
            return List.of(); // completada o cancelada: nada que relanzar
        }
        return pasos.values().stream()
                .filter(ep -> ep.estado() == EstadoPaso.SOLICITADO)
                .findFirst()
                .<List<Decision<P>>>map(ep -> List.of(new Decision.Ejecutar<>(ep.paso(), comandoPara(ep.paso()))))
                .orElse(List.of());
    }

    public final List<Decision<P>> completar(P paso, ResultadoPaso resultado) {
        var ep = pasoDe(paso);
        if (estado == EstadoSaga.CANCELADA || ep.estado() != EstadoPaso.SOLICITADO) {
            return List.of(); // duplicado, tardío o saga cancelada: se ignora
        }
        ep.completar();
        limpiarTicket(); // el reintento (o el primer intento) acabó bien: nada que contar a soporte
        aplicarResultado(paso, resultado);
        return transicionTras(paso);
    }

    public final List<Decision<P>> fallar(P paso, MotivoFallo motivo) {
        var ep = pasoDe(paso);
        if (estado == EstadoSaga.CANCELADA || ep.estado() != EstadoPaso.SOLICITADO) {
            return List.of();
        }
        ep.registrarFallo(motivo);

        if (!motivo.esReintentable()) {
            // p. ej. fallo de datos al parsear un JSON: reintentar no lo arreglará.
            // Sin reintento automático: FALLIDA, y que el planificador pida el ticket.
            ep.bloquear();
            estado = EstadoSaga.FALLIDA;
            solicitarTicket();
            return List.of();
        }
        if (reintentos.escaleraConsumida(ep.intentos())) {
            // Se sigue reintentando (cada 180 min, indefinidamente), pero ya con ticket pedido.
            solicitarTicket();
        }
        ep.esperarReintento();
        return List.of(new Decision.ProgramarReintento<>(
                paso, reintentos.esperaTras(ep.intentos()), ep.intentos() + 1));
    }

    /** Lo invoca el caso de uso cuando vence el reintento programado. */
    public final List<Decision<P>> reintentar(P paso) {
        var ep = pasoDe(paso);
        if (estado == EstadoSaga.CANCELADA || ep.estado() != EstadoPaso.ESPERANDO_REINTENTO) {
            return List.of(); // cancelada o ya intervenida entre medias
        }
        return solicitar(paso);
    }

    // --- intervenciones de soporte ---

    /** Reintento manual: resetea el contador (vuelve a haber intentos) y relanza el paso. */
    public final List<Decision<P>> reanudarPorSoporte(P paso, UsuarioSoporte quien) {
        var ep = pasoDe(paso);
        if (estado == EstadoSaga.CANCELADA || ep.estado() != EstadoPaso.BLOQUEADO_SOPORTE) {
            throw new PasoNoIntervenibleException(id, paso, ep.estado());
        }
        reactivar();
        ep.resetearIntentos(); // borra también el ticket: un nuevo bloqueo será un estado de error nuevo
        auditoria.add(AuditoriaIntervencion.de(quien, "REINTENTO_MANUAL", paso.name()));
        return solicitar(paso);
    }

    /**
     * Soporte arregló el paso a mano en el sistema destino y lo marca OK.
     * La saga CONTINÚA su flujo normal desde ese paso (no se cierra en falso).
     * Si pasos posteriores consumen datos que este paso producía, hay que aportarlos.
     */
    public final List<Decision<P>> marcarOkManual(P paso, UsuarioSoporte quien,
                                                  String justificacion, ResultadoPaso datos) {
        var ep = pasoDe(paso);
        if (estado == EstadoSaga.CANCELADA || ep.estado() != EstadoPaso.BLOQUEADO_SOPORTE) {
            throw new PasoNoIntervenibleException(id, paso, ep.estado());
        }
        if (pasosConDatosManualesObligatorios().contains(paso) && datos == null) {
            throw new DatosManualesRequeridosException(id, paso);
        }
        reactivar();
        ep.completarManual();
        limpiarTicket(); // soporte lo arregló: el problema deja de estar vivo
        if (datos != null) {
            aplicarResultado(paso, datos);
        }
        auditoria.add(AuditoriaIntervencion.de(quien, "MARCAR_OK_MANUAL", paso + ": " + justificacion));
        return transicionTras(paso); // la saga continúa (o finaliza) su curso normal
    }

    // --- tickets (los abre el planificador, no el flujo de fallo) ---

    /**
     * El planificador de tickets ya avisó a soporte (el ticket se abre
     * escribiendo en el log: no hay id que guardar). Idempotente: si el flag
     * ya no está PENDIENTE (el problema se curó entre medias), es un no-op.
     */
    public final List<Decision<P>> marcarTicketAbierto(Instant cuando) {
        if (estadoTicket == EstadoTicket.PENDIENTE) {
            estadoTicket = EstadoTicket.ABIERTO;
            ticketAbiertoEn = cuando;
        }
        return List.of();
    }

    // --- helpers para las subclases ---

    /** Soporte ha intervenido: la saga FALLIDA vuelve a estar viva. */
    private void reactivar() {
        if (estado == EstadoSaga.FALLIDA) {
            estado = EstadoSaga.EN_CURSO;
        }
    }

    /** Pide ticket; si ya hay uno pedido o abierto por un problema vivo, no se duplica. */
    private void solicitarTicket() {
        if (estadoTicket == EstadoTicket.SIN_TICKET) {
            estadoTicket = EstadoTicket.PENDIENTE;
        }
    }

    private void limpiarTicket() {
        estadoTicket = EstadoTicket.SIN_TICKET;
        ticketAbiertoEn = null;
    }

    /**
     * Deja el paso bloqueado registrando el motivo, sin pasar por el ciclo de
     * reintentos (p. ej. una compensación que falla: inconsistencia real que
     * debe mirar una persona), y pide ticket para soporte.
     */
    protected final void bloquearPorFallo(P paso, MotivoFallo motivo) {
        var ep = pasoDe(paso);
        ep.registrarFallo(motivo);
        ep.bloquear();
        solicitarTicket();
    }

    protected final List<Decision<P>> solicitar(P p) {
        pasoDe(p).solicitar();
        return List.of(new Decision.Ejecutar<>(p, comandoPara(p)));
    }

    /**
     * La saga termina aquí: COMPLETADA es el final del camino. No se publica
     * ningún evento: todo acaba cuando las sagas se completan.
     */
    protected final List<Decision<P>> finalizar() {
        estado = EstadoSaga.COMPLETADA;
        return List.of();
    }

    /** Las transiciones de EjecucionPaso son package-private: los agregados pasan por aquí. */
    protected final void cancelarPasosActivos() {
        pasos.values().stream()
                .filter(ep -> ep.estado().esActivo())
                .forEach(EjecucionPaso::cancelar);
    }

    protected final void marcarCompensado(P paso) {
        pasoDe(paso).compensado();
    }

    protected final EjecucionPaso<P> pasoDe(P p) {
        var ep = pasos.get(p);
        if (ep == null) {
            throw new IllegalArgumentException("Paso " + p + " ajeno a la saga " + tipo());
        }
        return ep;
    }

    // --- lectura (persistencia y pantalla de soporte) ---

    public final SagaId id() { return id; }
    public final ExternalId externalId() { return externalId; }
    public final EstadoSaga estado() { return estado; }
    public final EstadoTicket estadoTicket() { return estadoTicket; }
    /** Fecha de apertura del ticket; null salvo que estadoTicket == ABIERTO. */
    public final Instant ticketAbiertoEn() { return ticketAbiertoEn; }
    public final long version() { return version; }
    public final Map<P, EjecucionPaso<P>> pasos() { return Collections.unmodifiableMap(pasos); }
    public final List<AuditoriaIntervencion> auditoria() { return Collections.unmodifiableList(auditoria); }
    public final boolean tienePasosBloqueados() {
        return pasos.values().stream().anyMatch(ep -> ep.estado() == EstadoPaso.BLOQUEADO_SOPORTE);
    }
    public final boolean requiereDatosManuales(P paso) {
        return pasosConDatosManualesObligatorios().contains(paso);
    }
}
