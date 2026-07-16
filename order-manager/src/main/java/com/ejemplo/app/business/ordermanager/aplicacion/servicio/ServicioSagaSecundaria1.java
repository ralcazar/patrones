package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import java.time.Duration;
import java.time.Instant;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoSagaSecundaria1;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.UnidadDeTrabajo;
import com.ejemplo.app.business.ordermanager.dominio.comun.ComandoPaso;
import com.ejemplo.app.business.ordermanager.dominio.comun.OrdenRoot;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria1.ComandoPasoSecundaria1;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria1.ResultadoPasoSecundaria1;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria1.SagaSecundaria1;

/**
 * Servicio de la saga secundaria 1: dos llamadas REST síncronas encadenadas
 * (INICIO -> CONFIRMACION). Nunca se cancela ni compensa.
 */
@Service
public class ServicioSagaSecundaria1 implements ServicioSaga {

    private final RepositorioOrden repo;
    private final UnidadDeTrabajo tx;
    private final Duration lease;
    private final PuertoSagaSecundaria1 puerto;

    public ServicioSagaSecundaria1(RepositorioOrden repo, UnidadDeTrabajo tx, Duration lease,
            PuertoSagaSecundaria1 puerto) {
        this.repo = repo;
        this.tx = tx;
        this.lease = lease;
        this.puerto = puerto;
    }

    @Override public TipoSaga tipo() { return TipoSaga.SECUNDARIA1; }

    /**
     * Recibe el agregado ya cargado por el llamante (una única carga por paso,
     * antes del REST): la tx guarda esa misma instancia (con su version) para
     * que un takeover intermedio haga fallar el guardar.
     */
    @Override
    public SenalPaso ejecutarPaso(OrdenRoot orden) {
        var saga = (SagaSecundaria1) orden.saga();
        var resultado = ejecutarComando(saga.comandoActual()); // REST fuera de tx

        return tx.enTransaccion(() -> {
            saga.aplicarYAvanzar(resultado);
            if (saga.terminada()) {
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

    private ResultadoPasoSecundaria1 ejecutarComando(ComandoPaso cmd) {
        return switch ((ComandoPasoSecundaria1) cmd) {
            case ComandoPasoSecundaria1.Iniciar c -> puerto.iniciar(c);
            case ComandoPasoSecundaria1.Confirmar c -> puerto.confirmar(c);
        };
    }
}
