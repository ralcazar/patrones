package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoColaTareas;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoMensajesProcesados;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoPaso1;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoPaso2;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoPaso3;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoPaso4;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoPaso5;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoPaso6;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoPaso7;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoPaso8;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioSagaPrincipal;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioSagaSecundaria1;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioSagaSecundaria2;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioSagaSecundaria3;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.UnidadDeTrabajo;
import com.ejemplo.app.business.ordermanager.aplicacion.tarea.TareaSaga;
import com.ejemplo.app.business.ordermanager.dominio.comun.ComandoPaso;
import com.ejemplo.app.business.ordermanager.dominio.comun.ContextoArranque;
import com.ejemplo.app.business.ordermanager.dominio.comun.Decision;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExcepcionServicioExterno;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.comun.MensajeId;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.UsuarioSoporte;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.ComandoPasoPrincipal;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.DatoNegocio2;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.DatoNegocio3;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.PasoSagaPrincipal;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.SagaPrincipalRoot;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria1.SagaSecundaria1Root;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria2.SagaSecundaria2Root;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria3.SagaSecundaria3Root;

/**
 * Orquestador de la saga principal.
 *
 * Especialidades respecto a la base:
 *  - iniciarOContinuar: crea la saga si no existe (idempotente ante reentregas).
 *  - Al completar PASO8: crea las 3 sagas secundarias y encola sus tareas
 *    ArrancarSaga en la MISMA transacción (un solo commit).
 *  - Cancelación por soporte con compensación de PASO2 y PASO1.
 */
@Service
public class ServicioSagaPrincipal extends ServicioSagaBase<PasoSagaPrincipal, SagaPrincipalRoot> {

    private final RepositorioSagaPrincipal repo;
    private final RepositorioSagaSecundaria1 repoSecundaria1;
    private final RepositorioSagaSecundaria2 repoSecundaria2;
    private final RepositorioSagaSecundaria3 repoSecundaria3;
    private final PuertoPaso1 puertoPaso1;
    private final PuertoPaso2 puertoPaso2;
    private final PuertoPaso3 puertoPaso3;
    private final PuertoPaso4 puertoPaso4;
    private final PuertoPaso5 puertoPaso5;
    private final PuertoPaso6 puertoPaso6;
    private final PuertoPaso7 puertoPaso7;
    private final PuertoPaso8 puertoPaso8;

    public ServicioSagaPrincipal(RepositorioSagaPrincipal repo,
            RepositorioSagaSecundaria1 repoSecundaria1, RepositorioSagaSecundaria2 repoSecundaria2,
            RepositorioSagaSecundaria3 repoSecundaria3,
            UnidadDeTrabajo tx, PuertoMensajesProcesados dedup, PuertoColaTareas cola,
            PuertoPaso1 puertoPaso1, PuertoPaso2 puertoPaso2, PuertoPaso3 puertoPaso3,
            PuertoPaso4 puertoPaso4, PuertoPaso5 puertoPaso5, PuertoPaso6 puertoPaso6,
            PuertoPaso7 puertoPaso7, PuertoPaso8 puertoPaso8) {
        super(tx, dedup, cola);
        this.repo = repo;
        this.repoSecundaria1 = repoSecundaria1;
        this.repoSecundaria2 = repoSecundaria2;
        this.repoSecundaria3 = repoSecundaria3;
        this.puertoPaso1 = puertoPaso1;
        this.puertoPaso2 = puertoPaso2;
        this.puertoPaso3 = puertoPaso3;
        this.puertoPaso4 = puertoPaso4;
        this.puertoPaso5 = puertoPaso5;
        this.puertoPaso6 = puertoPaso6;
        this.puertoPaso7 = puertoPaso7;
        this.puertoPaso8 = puertoPaso8;
    }

    // ------------------------------------------------------------------
    // Entrada desde el ManejadorTareasSaga (tareas del GestorOrdenes)
    // ------------------------------------------------------------------

    /**
     * Maneja la tarea IniciarTramitacion. Idempotente ante reentregas del lease:
     * si la saga ya existe (el proceso murió a mitad de la cadena), continúa
     * desde el último paso confirmado en lugar de crearla de nuevo.
     */
    public void iniciarOContinuar(SagaId id, ExternalId externalId,
                                  DatoNegocio3 datos, DatoNegocio2 datoNegocio2) {
        var decisiones = ReintentoOptimista.ejecutar(() -> tx.enTransaccion(() -> {
            if (!repo.existe(id)) {
                var saga = SagaPrincipalRoot.crear(id, externalId, datos, datoNegocio2);
                var ds = saga.iniciar();
                repo.crear(saga);
                return ds;
            }
            var saga = repo.cargar(id);
            var ds = saga.continuar(); // reanuda el paso SOLICITADO que quedó colgado
            repo.guardar(saga);
            return ds;
        }));
        despachar(id, decisiones);
    }

    // --- intervenciones de soporte (las enruta ServicioSoporteSagas) ---

    public void cancelar(SagaId id, UsuarioSoporte quien, String motivo) {
        procesar(MensajeId.interno(), id, saga -> saga.cancelarPorSoporte(quien, motivo));
    }

    // ------------------------------------------------------------------
    // Especialización de la base
    // ------------------------------------------------------------------

    @Override
    protected SagaPrincipalRoot cargar(SagaId id) {
        return repo.cargar(id);
    }

    @Override
    protected void guardar(SagaPrincipalRoot saga) {
        repo.guardar(saga);
    }

    /**
     * Parte transaccional de ArrancarSaga: crea la saga secundaria y encola su
     * tarea de arranque. Sagas nuevas + tareas + COMPLETADA de la principal:
     * un solo commit.
     */
    @Override
    protected void arrancarSaga(SagaPrincipalRoot saga, Decision.ArrancarSaga<PasoSagaPrincipal> d) {
        var nueva = switch (d.contexto()) {
            case ContextoArranque.ArranqueSecundaria1 c -> {
                var s = SagaSecundaria1Root.crear(SagaId.nuevo(), c);
                repoSecundaria1.crear(s);
                yield s;
            }
            case ContextoArranque.ArranqueSecundaria2 c -> {
                var s = SagaSecundaria2Root.crear(SagaId.nuevo(), c);
                repoSecundaria2.crear(s);
                yield s;
            }
            case ContextoArranque.ArranqueSecundaria3 c -> {
                var s = SagaSecundaria3Root.crear(SagaId.nuevo(), c);
                repoSecundaria3.crear(s);
                yield s;
            }
        };
        cola.encolar(new TareaSaga.ArrancarSaga(nueva.id(), nueva.tipo()));
    }

    /**
     * Ejecuta un paso síncrono y reentra con el resultado o el fallo.
     * La cadena PASO1->...->PASO8 corre entera dentro del procesar() de UNA
     * Orden, con checkpoint en BBDD por paso. IMPORTANTE: el lease del
     * GestorOrdenes debe superar la duración del peor caso de la cadena
     * (ver application.yml).
     */
    @Override
    protected void ejecutar(SagaId id, PasoSagaPrincipal paso, ComandoPaso cmd) {
        try {
            switch ((ComandoPasoPrincipal) cmd) {
                case ComandoPasoPrincipal.EjecutarPaso1 c ->
                        pasoCompletado(id, PasoSagaPrincipal.PASO1, puertoPaso1.ejecutar(c), MensajeId.interno());
                case ComandoPasoPrincipal.EjecutarPaso2 c ->
                        pasoCompletado(id, PasoSagaPrincipal.PASO2, puertoPaso2.ejecutar(c), MensajeId.interno());
                case ComandoPasoPrincipal.EjecutarPaso3 c ->
                        pasoCompletado(id, PasoSagaPrincipal.PASO3, puertoPaso3.ejecutar(c), MensajeId.interno());
                case ComandoPasoPrincipal.EjecutarPaso4 c ->
                        pasoCompletado(id, PasoSagaPrincipal.PASO4, puertoPaso4.ejecutar(c), MensajeId.interno());
                case ComandoPasoPrincipal.EjecutarPaso5 c ->
                        pasoCompletado(id, PasoSagaPrincipal.PASO5, puertoPaso5.ejecutar(c), MensajeId.interno());
                case ComandoPasoPrincipal.EjecutarPaso6 c ->
                        pasoCompletado(id, PasoSagaPrincipal.PASO6, puertoPaso6.ejecutar(c), MensajeId.interno());
                case ComandoPasoPrincipal.EjecutarPaso7 c ->
                        pasoCompletado(id, PasoSagaPrincipal.PASO7, puertoPaso7.ejecutar(c), MensajeId.interno());
                case ComandoPasoPrincipal.EjecutarPaso8 c ->
                        pasoCompletado(id, PasoSagaPrincipal.PASO8, puertoPaso8.ejecutar(c), MensajeId.interno());
                case ComandoPasoPrincipal.CompensarPaso1 c -> throw new IllegalStateException(
                        "Las compensaciones no se ejecutan por esta vía: " + c);
                case ComandoPasoPrincipal.CompensarPaso2 c -> throw new IllegalStateException(
                        "Las compensaciones no se ejecutan por esta vía: " + c);
            }
        } catch (ExcepcionServicioExterno e) {
            pasoFallido(id, paso, e.motivo(), MensajeId.interno());
        }
    }

    @Override
    protected void compensar(SagaId id, PasoSagaPrincipal paso, ComandoPaso cmd) {
        try {
            switch (cmd) {
                case ComandoPasoPrincipal.CompensarPaso2 c -> puertoPaso2.compensar(c);
                case ComandoPasoPrincipal.CompensarPaso1 c -> puertoPaso1.compensar(c);
                default -> throw new IllegalStateException("Comando de compensación desconocido: " + cmd);
            }
            ReintentoOptimista.ejecutar(() -> tx.enTransaccion(() -> {
                var saga = repo.cargar(id);
                saga.compensacionCompletada(paso);
                repo.guardar(saga);
                return null;
            }));
        } catch (ExcepcionServicioExterno e) {
            // Inconsistencia real: el paso queda BLOQUEADO_SOPORTE y el
            // planificador de tickets lo detectará en su siguiente pasada.
            ReintentoOptimista.ejecutar(() -> tx.enTransaccion(() -> {
                var saga = repo.cargar(id);
                saga.compensacionFallida(paso, e.motivo());
                repo.guardar(saga);
                return null;
            }));
        }
    }
}
