package com.ejemplo.app.business.sagas.aplicacion.servicio.sagasecundaria1;

import java.time.Duration;
import java.time.Instant;

import jakarta.transaction.Transactional;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoSagaSecundaria1;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.SenalPaso;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.ProcesadorOrden;
import com.ejemplo.app.business.ordermanager.dominio.ComandoPaso;
import com.ejemplo.app.business.ordermanager.dominio.OrdenRoot;
import com.ejemplo.app.business.ordermanager.dominio.TipoOrden;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.ComandoPasoSecundaria1;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.ResultadoPasoSecundaria1;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.SagaSecundaria1;

/**
 * Servicio de la saga secundaria 1: dos llamadas REST síncronas encadenadas
 * (INICIO -> CONFIRMACION). Nunca se cancela ni compensa.
 *
 * El REST ocurre fuera de transacción; aplicar el resultado y guardar van en
 * {@code @Transactional}. Como este servicio es un POJO creado por
 * {@code @Bean}, se invoca a través de {@code self} (el propio proxy,
 * inyectado por ConfiguracionSagas) para que la anotación no se ignore
 * por auto-invocación.
 */
@Service
public class ServicioSagaSecundaria1 implements ProcesadorOrden {

    private final RepositorioOrden repo;
    private final Duration lease;
    private final PuertoSagaSecundaria1 puerto;
    private ServicioSagaSecundaria1 self;

    public ServicioSagaSecundaria1(RepositorioOrden repo, Duration lease, PuertoSagaSecundaria1 puerto) {
        this.repo = repo;
        this.lease = lease;
        this.puerto = puerto;
        this.self = this; // valor por defecto (tests unitarios); ConfiguracionSagas lo sustituye por el proxy
    }

    /** Referencia al proxy transaccional de Spring de este mismo bean (ver ConfiguracionSagas). */
    public void establecerSelf(ServicioSagaSecundaria1 self) {
        this.self = self;
    }

    @Override public TipoOrden tipo() { return SagaSecundaria1.TIPO; }

    /**
     * Recibe el agregado ya cargado por el llamante (una única carga por paso,
     * antes del REST): la tx guarda esa misma instancia (con su version) para
     * que un takeover intermedio haga fallar el guardar.
     */
    @Override
    public SenalPaso ejecutarPaso(OrdenRoot orden) {
        var saga = (SagaSecundaria1) orden.proceso();
        var resultado = ejecutarComando(saga.comandoActual()); // REST fuera de tx
        return self.aplicar(orden, saga, resultado); // via proxy -> @Transactional
    }

    @Transactional
    public SenalPaso aplicar(OrdenRoot orden, SagaSecundaria1 saga, ResultadoPasoSecundaria1 resultado) {
        saga.aplicarYAvanzar(resultado);
        if (saga.terminada()) {
            orden.finalizar(Instant.now());
            repo.guardar(orden);
            return new SenalPaso.Finalizada();
        }
        orden.resetearIntentos();
        orden.renovarLease(lease, Instant.now());
        var ordenGuardada = repo.guardar(orden);
        return new SenalPaso.HayMasTrabajo(ordenGuardada);
    }

    private ResultadoPasoSecundaria1 ejecutarComando(ComandoPaso cmd) {
        return switch ((ComandoPasoSecundaria1) cmd) {
            case ComandoPasoSecundaria1.Iniciar c -> puerto.iniciar(c);
            case ComandoPasoSecundaria1.Confirmar c -> puerto.confirmar(c);
        };
    }
}
