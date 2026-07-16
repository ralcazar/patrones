package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoPaso1;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoPaso2;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoPaso3;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoPaso4;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoPaso5;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoPaso6;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoPaso7;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoPaso8;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.UnidadDeTrabajo;
import com.ejemplo.app.business.ordermanager.dominio.comun.ComandoPaso;
import com.ejemplo.app.business.ordermanager.dominio.comun.ContextoArranque;
import com.ejemplo.app.business.ordermanager.dominio.comun.OrdenRoot;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.ComandoPasoPrincipal;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.EstadoSagaPrincipal;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.ResultadoPasoPrincipal;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.SagaPrincipalRoot;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria1.SagaSecundaria1Root;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria2.SagaSecundaria2Root;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria3.SagaSecundaria3Root;

/**
 * Orquestador de la saga principal: PASO1 -> ... -> PASO8 síncronos y, si
 * soporte cancela antes del punto de no retorno, la compensación
 * COMPENSAR_PASO2 -> COMPENSAR_PASO1 -> CANCELADA.
 *
 * Al alcanzar TERMINADA (tras PASO8), en la MISMA transacción que finaliza la
 * orden crea los 3 agregados hijos (uno por saga secundaria): es la excepción
 * aceptada a la regla de un solo agregado por transacción, porque solo
 * MODIFICA el agregado de la principal y CREA los otros tres.
 */
@Service
public class ServicioSagaPrincipal implements OrquestadorSaga {

    private final RepositorioOrden repo;
    private final UnidadDeTrabajo tx;
    private final Duration lease;
    private final PuertoPaso1 puertoPaso1;
    private final PuertoPaso2 puertoPaso2;
    private final PuertoPaso3 puertoPaso3;
    private final PuertoPaso4 puertoPaso4;
    private final PuertoPaso5 puertoPaso5;
    private final PuertoPaso6 puertoPaso6;
    private final PuertoPaso7 puertoPaso7;
    private final PuertoPaso8 puertoPaso8;

    public ServicioSagaPrincipal(RepositorioOrden repo, UnidadDeTrabajo tx, Duration lease,
            PuertoPaso1 puertoPaso1, PuertoPaso2 puertoPaso2, PuertoPaso3 puertoPaso3,
            PuertoPaso4 puertoPaso4, PuertoPaso5 puertoPaso5, PuertoPaso6 puertoPaso6,
            PuertoPaso7 puertoPaso7, PuertoPaso8 puertoPaso8) {
        this.repo = repo;
        this.tx = tx;
        this.lease = lease;
        this.puertoPaso1 = puertoPaso1;
        this.puertoPaso2 = puertoPaso2;
        this.puertoPaso3 = puertoPaso3;
        this.puertoPaso4 = puertoPaso4;
        this.puertoPaso5 = puertoPaso5;
        this.puertoPaso6 = puertoPaso6;
        this.puertoPaso7 = puertoPaso7;
        this.puertoPaso8 = puertoPaso8;
    }

    @Override public TipoSaga tipo() { return TipoSaga.PRINCIPAL; }

    /**
     * Una ÚNICA carga por paso, antes del REST: la transacción guarda esa misma
     * instancia (con su version), de modo que si otro actor escribió entre
     * medias (takeover tras lease vencido, soporte, ...) el guardar falla por
     * version y este pod se retira. No recargar dentro de la tx: anularía esa
     * protección.
     */
    @Override
    public SenalPaso ejecutarPaso(SagaId id) {
        var orden = repo.cargar(id);
        var saga = (SagaPrincipalRoot) orden.saga();
        return esCompensacion(saga.estado()) ? ejecutarCompensacion(orden, saga) : ejecutarPasoNormal(orden, saga);
    }

    private SenalPaso ejecutarPasoNormal(OrdenRoot orden, SagaPrincipalRoot saga) {
        var resultado = ejecutarComando(saga.comandoActual()); // REST fuera de tx

        return tx.enTransaccion(() -> {
            saga.aplicarYAvanzar(resultado);
            if (saga.terminada()) {
                crearHijas(saga.contextosArranque(), Instant.now());
                orden.finalizar(saga.resultadoFinal());
                repo.guardar(orden);
                return new SenalPaso.Finalizada(saga.resultadoFinal());
            }
            orden.resetearIntentos();
            orden.renovarLease(lease, Instant.now());
            repo.guardar(orden);
            return new SenalPaso.HayMasTrabajo();
        });
    }

    private SenalPaso ejecutarCompensacion(OrdenRoot orden, SagaPrincipalRoot saga) {
        if (saga.estado() == EstadoSagaPrincipal.CANCELADA) {
            return tx.enTransaccion(() -> {
                orden.finalizar(saga.resultadoFinal());
                repo.guardar(orden);
                return new SenalPaso.Finalizada(saga.resultadoFinal());
            });
        }

        var ctx = saga.contexto();
        if (saga.estado() == EstadoSagaPrincipal.COMPENSAR_PASO2) {
            puertoPaso2.compensar(new ComandoPasoPrincipal.CompensarPaso2(ctx.refPaso2())); // REST fuera de tx
        } else {
            puertoPaso1.compensar(new ComandoPasoPrincipal.CompensarPaso1(ctx.refPaso1())); // REST fuera de tx
        }

        return tx.enTransaccion(() -> {
            saga.compensacionCompletada();
            orden.resetearIntentos();
            orden.renovarLease(lease, Instant.now());
            repo.guardar(orden);
            return new SenalPaso.HayMasTrabajo();
        });
    }

    private void crearHijas(List<ContextoArranque> contextos, Instant ahora) {
        for (var contexto : contextos) {
            OrdenRoot hija = switch (contexto) {
                case ContextoArranque.ArranqueSecundaria1 c ->
                        OrdenRoot.nueva(SagaSecundaria1Root.crear(SagaId.nuevo(), c), ahora);
                case ContextoArranque.ArranqueSecundaria2 c ->
                        OrdenRoot.nueva(SagaSecundaria2Root.crear(SagaId.nuevo(), c), ahora);
                case ContextoArranque.ArranqueSecundaria3 c ->
                        OrdenRoot.nueva(SagaSecundaria3Root.crear(SagaId.nuevo(), c), ahora);
            };
            repo.crear(hija);
        }
    }

    private ResultadoPasoPrincipal ejecutarComando(ComandoPaso cmd) {
        return switch ((ComandoPasoPrincipal) cmd) {
            case ComandoPasoPrincipal.EjecutarPaso1 c -> puertoPaso1.ejecutar(c);
            case ComandoPasoPrincipal.EjecutarPaso2 c -> puertoPaso2.ejecutar(c);
            case ComandoPasoPrincipal.EjecutarPaso3 c -> puertoPaso3.ejecutar(c);
            case ComandoPasoPrincipal.EjecutarPaso4 c -> puertoPaso4.ejecutar(c);
            case ComandoPasoPrincipal.EjecutarPaso5 c -> puertoPaso5.ejecutar(c);
            case ComandoPasoPrincipal.EjecutarPaso6 c -> puertoPaso6.ejecutar(c);
            case ComandoPasoPrincipal.EjecutarPaso7 c -> puertoPaso7.ejecutar(c);
            case ComandoPasoPrincipal.EjecutarPaso8 c -> puertoPaso8.ejecutar(c);
            case ComandoPasoPrincipal.CompensarPaso1 c -> throw new IllegalStateException(
                    "Las compensaciones no se ejecutan por esta vía: " + c);
            case ComandoPasoPrincipal.CompensarPaso2 c -> throw new IllegalStateException(
                    "Las compensaciones no se ejecutan por esta vía: " + c);
        };
    }

    private static boolean esCompensacion(EstadoSagaPrincipal estado) {
        return estado == EstadoSagaPrincipal.COMPENSAR_PASO2
                || estado == EstadoSagaPrincipal.COMPENSAR_PASO1
                || estado == EstadoSagaPrincipal.CANCELADA;
    }
}
