package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import java.time.Duration;
import java.time.Instant;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoSagaSecundaria3;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.UnidadDeTrabajo;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria3.ComandoPasoSecundaria3;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria3.SagaSecundaria3Root;

/** Orquestador de la saga secundaria 3: una única llamada REST síncrona. Nunca se cancela ni compensa. */
@Service
public class ServicioSagaSecundaria3 implements OrquestadorSaga {

    private final RepositorioOrden repo;
    private final UnidadDeTrabajo tx;
    private final Duration lease;
    private final PuertoSagaSecundaria3 puerto;

    public ServicioSagaSecundaria3(RepositorioOrden repo, UnidadDeTrabajo tx, Duration lease,
            PuertoSagaSecundaria3 puerto) {
        this.repo = repo;
        this.tx = tx;
        this.lease = lease;
        this.puerto = puerto;
    }

    @Override public TipoSaga tipo() { return TipoSaga.SECUNDARIA3; }

    /**
     * Una ÚNICA carga por paso, antes del REST: la tx guarda esa misma
     * instancia (con su version) para que un takeover intermedio haga fallar
     * el guardar. No recargar dentro de la tx.
     */
    @Override
    public SenalPaso ejecutarPaso(SagaId id) {
        var orden = repo.cargar(id);
        var saga = (SagaSecundaria3Root) orden.saga();
        var resultado = puerto.ejecutar((ComandoPasoSecundaria3.Ejecutar) saga.comandoActual()); // REST fuera de tx

        return tx.enTransaccion(() -> {
            saga.aplicarYAvanzar(resultado);
            orden.finalizar(saga.resultadoFinal());
            repo.guardar(orden);
            return new SenalPaso.Finalizada(saga.resultadoFinal());
        });
    }
}
