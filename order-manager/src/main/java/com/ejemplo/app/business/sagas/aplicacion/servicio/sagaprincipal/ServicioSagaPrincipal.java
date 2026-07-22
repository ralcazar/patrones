package com.ejemplo.app.business.sagas.aplicacion.servicio.sagaprincipal;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import jakarta.transaction.Transactional;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoPaso1;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoPaso2;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoPaso3;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoPaso4;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoPaso5;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoPaso6;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoPaso7;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoPaso8;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.RepositorioDatosNegocio;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.SenalPaso;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.ProcesadorOrden;
import com.ejemplo.app.business.ordermanager.dominio.ComandoPaso;
import com.ejemplo.app.business.sagas.dominio.comun.ContextoArranque;
import com.ejemplo.app.business.ordermanager.dominio.OrdenRoot;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.Prioridad;
import com.ejemplo.app.business.ordermanager.dominio.TipoOrden;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ComandoPasoPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.EstadoSagaPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ResultadoPasoPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.SagaPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.SagaSecundaria1;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.SagaSecundaria2;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria3.SagaSecundaria3;

/**
 * Servicio de la saga principal: PASO1 -> ... -> PASO8 síncronos y, si
 * soporte cancela antes del punto de no retorno, la compensación
 * COMPENSAR_PASO2 -> COMPENSAR_PASO1 -> CANCELADA.
 *
 * Al alcanzar TERMINADA (tras PASO8), en la MISMA transacción que finaliza la
 * orden crea los 3 agregados hijos (uno por saga secundaria): es la excepción
 * aceptada a la regla de un solo agregado por transacción, porque solo
 * MODIFICA el agregado de la principal y CREA los otros tres.
 *
 * El REST de cada paso ocurre fuera de transacción; solo aplicar el
 * resultado y guardar van en {@code @Transactional}. Como este servicio es un
 * POJO creado por {@code @Bean}, una llamada interna (this.aplicarX(...)) NO
 * pasaría por el proxy transaccional de Spring, así que se invoca a través de
 * {@code self}: la referencia al propio proxy, inyectada por
 * ConfiguracionSagas (ver {@link #establecerSelf}).
 */
@Service
public class ServicioSagaPrincipal implements ProcesadorOrden {

    private final RepositorioOrden repo;
    private final Duration lease;
    private final RepositorioDatosNegocio repoDatos;
    private final PuertoPaso1 puertoPaso1;
    private final PuertoPaso2 puertoPaso2;
    private final PuertoPaso3 puertoPaso3;
    private final PuertoPaso4 puertoPaso4;
    private final PuertoPaso5 puertoPaso5;
    private final PuertoPaso6 puertoPaso6;
    private final PuertoPaso7 puertoPaso7;
    private final PuertoPaso8 puertoPaso8;
    private ServicioSagaPrincipal self;

    public ServicioSagaPrincipal(RepositorioOrden repo, Duration lease, RepositorioDatosNegocio repoDatos,
            PuertoPaso1 puertoPaso1, PuertoPaso2 puertoPaso2, PuertoPaso3 puertoPaso3,
            PuertoPaso4 puertoPaso4, PuertoPaso5 puertoPaso5, PuertoPaso6 puertoPaso6,
            PuertoPaso7 puertoPaso7, PuertoPaso8 puertoPaso8) {
        this.repo = repo;
        this.lease = lease;
        this.repoDatos = repoDatos;
        this.puertoPaso1 = puertoPaso1;
        this.puertoPaso2 = puertoPaso2;
        this.puertoPaso3 = puertoPaso3;
        this.puertoPaso4 = puertoPaso4;
        this.puertoPaso5 = puertoPaso5;
        this.puertoPaso6 = puertoPaso6;
        this.puertoPaso7 = puertoPaso7;
        this.puertoPaso8 = puertoPaso8;
        this.self = this; // valor por defecto (tests unitarios); ConfiguracionSagas lo sustituye por el proxy
    }

    /** Referencia al proxy transaccional de Spring de este mismo bean (ver ConfiguracionSagas). */
    public void establecerSelf(ServicioSagaPrincipal self) {
        this.self = self;
    }

    @Override public TipoOrden tipo() { return SagaPrincipal.TIPO; }

    /**
     * Recibe el agregado ya cargado por el llamante (una única carga por paso,
     * antes del REST): la transacción guarda esa misma instancia (con su
     * version), de modo que si otro actor escribió entre medias (takeover tras
     * lease vencido, soporte, ...) el guardar falla por version y este pod se
     * retira. Despacha por estado: CANCELADA finaliza sin REST, COMPENSAR_*
     * compensa el paso correspondiente, el resto son los pasos normales
     * PASO1..PASO8.
     */
    @Override
    public SenalPaso ejecutarPaso(OrdenRoot orden) {
        var saga = (SagaPrincipal) orden.proceso();
        return switch (saga.estado()) {
            case CANCELADA -> self.aplicarCancelacion(orden, saga); // via proxy -> @Transactional (sin REST)
            case COMPENSAR_PASO1, COMPENSAR_PASO2 -> ejecutarCompensacion(orden, saga);
            default -> ejecutarPasoNormal(orden, saga);
        };
    }

    private SenalPaso ejecutarPasoNormal(OrdenRoot orden, SagaPrincipal saga) {
        var resultado = ejecutarComando(saga.comandoActual()); // REST fuera de tx
        return self.aplicarPasoNormal(orden, saga, resultado); // via proxy -> @Transactional
    }

    @Transactional
    public SenalPaso aplicarPasoNormal(OrdenRoot orden, SagaPrincipal saga, ResultadoPasoPrincipal resultado) {
        var nuevaSaga = saga.aplicarYAvanzar(resultado);
        orden.reemplazarProceso(nuevaSaga);
        if (nuevaSaga.terminada()) {
            var ahora = Instant.now();
            crearHijas(nuevaSaga.contextosArranque(), orden.prioridad(), ahora);
            orden.finalizar(ahora);
            repo.guardar(orden);
            return new SenalPaso.Finalizada();
        }
        orden.resetearIntentos();
        orden.renovarLease(lease, Instant.now());
        var ordenGuardada = repo.guardar(orden);
        return new SenalPaso.HayMasTrabajo(ordenGuardada);
    }

    private SenalPaso ejecutarCompensacion(OrdenRoot orden, SagaPrincipal saga) {
        var ctx = saga.contexto();
        if (saga.estado() == EstadoSagaPrincipal.COMPENSAR_PASO2) {
            puertoPaso2.compensar(new ComandoPasoPrincipal.CompensarPaso2(ctx.refPaso2())); // REST fuera de tx
        } else {
            puertoPaso1.compensar(new ComandoPasoPrincipal.CompensarPaso1(ctx.refPaso1())); // REST fuera de tx
        }

        return self.aplicarCompensacion(orden, saga); // via proxy -> @Transactional
    }

    @Transactional
    public SenalPaso aplicarCancelacion(OrdenRoot orden, SagaPrincipal saga) {
        orden.finalizar(Instant.now());
        repo.guardar(orden);
        return new SenalPaso.Finalizada();
    }

    @Transactional
    public SenalPaso aplicarCompensacion(OrdenRoot orden, SagaPrincipal saga) {
        orden.reemplazarProceso(saga.compensacionCompletada());
        orden.resetearIntentos();
        orden.renovarLease(lease, Instant.now());
        var ordenGuardada = repo.guardar(orden);
        return new SenalPaso.HayMasTrabajo(ordenGuardada);
    }

    private void crearHijas(List<ContextoArranque> contextos, Prioridad prioridad, Instant ahora) {
        for (var contexto : contextos) {
            OrdenRoot hija = switch (contexto) {
                case ContextoArranque.ArranqueSecundaria1 c ->
                        OrdenRoot.nueva(SagaSecundaria1.crear(OrdenId.nuevo(), c), prioridad, ahora);
                case ContextoArranque.ArranqueSecundaria2 c ->
                        OrdenRoot.nueva(SagaSecundaria2.crear(OrdenId.nuevo(), c), prioridad, ahora);
                case ContextoArranque.ArranqueSecundaria3 c ->
                        OrdenRoot.nueva(SagaSecundaria3.crear(OrdenId.nuevo(), c), prioridad, ahora);
            };
            repo.crear(hija);
        }
    }

    // Visibilidad de paquete (no private): permite testear directamente las
    // ramas defensivas CompensarPaso1/2, inalcanzables por la API pública
    // porque comandoActual() nunca las produce en estado no-COMPENSAR.
    ResultadoPasoPrincipal ejecutarComando(ComandoPaso cmd) {
        return switch ((ComandoPasoPrincipal) cmd) {
            case ComandoPasoPrincipal.EjecutarPaso1 c -> puertoPaso1.ejecutar(c, repoDatos.cargar(c.datosNegocioId()));
            case ComandoPasoPrincipal.EjecutarPaso2 c -> puertoPaso2.ejecutar(c, repoDatos.cargar(c.datosNegocioId()),
                    repoDatos.documentosDe(c.datosNegocioId()));
            case ComandoPasoPrincipal.EjecutarPaso3 c -> puertoPaso3.ejecutar(c);
            case ComandoPasoPrincipal.EjecutarPaso4 c -> puertoPaso4.ejecutar(c);
            case ComandoPasoPrincipal.EjecutarPaso5 c -> puertoPaso5.ejecutar(c);
            case ComandoPasoPrincipal.EjecutarPaso6 c -> puertoPaso6.ejecutar(c);
            case ComandoPasoPrincipal.EjecutarPaso7 c -> puertoPaso7.ejecutar(c, repoDatos.cargar(c.datosNegocioId()));
            case ComandoPasoPrincipal.EjecutarPaso8 c -> puertoPaso8.ejecutar(c);
            case ComandoPasoPrincipal.CompensarPaso1 c -> throw new IllegalStateException(
                    "Las compensaciones no se ejecutan por esta vía: " + c);
            case ComandoPasoPrincipal.CompensarPaso2 c -> throw new IllegalStateException(
                    "Las compensaciones no se ejecutan por esta vía: " + c);
        };
    }
}
