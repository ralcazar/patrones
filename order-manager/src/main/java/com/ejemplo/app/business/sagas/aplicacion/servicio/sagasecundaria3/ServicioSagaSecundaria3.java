package com.ejemplo.app.business.sagas.aplicacion.servicio.sagasecundaria3;

import jakarta.transaction.Transactional;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoSagaSecundaria3;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.comun.SenalPaso;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.comun.ServicioSaga;
import com.ejemplo.app.business.ordermanager.dominio.comun.OrdenRoot;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoOrden;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria3.ComandoPasoSecundaria3;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria3.ResultadoPasoSecundaria3;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria3.SagaSecundaria3;

/**
 * Servicio de la saga secundaria 3: una única llamada REST síncrona. Nunca se
 * cancela ni compensa.
 *
 * El REST ocurre fuera de transacción; aplicar el resultado y guardar van en
 * {@code @Transactional}. Como este servicio es un POJO creado por
 * {@code @Bean}, se invoca a través de {@code self} (el propio proxy,
 * inyectado por ConfiguracionSagas) para que la anotación no se ignore
 * por auto-invocación.
 */
@Service
public class ServicioSagaSecundaria3 implements ServicioSaga {

    private final RepositorioOrden repo;
    private final PuertoSagaSecundaria3 puerto;
    private ServicioSagaSecundaria3 self;

    public ServicioSagaSecundaria3(RepositorioOrden repo, PuertoSagaSecundaria3 puerto) {
        this.repo = repo;
        this.puerto = puerto;
        this.self = this; // valor por defecto (tests unitarios); ConfiguracionSagas lo sustituye por el proxy
    }

    /** Referencia al proxy transaccional de Spring de este mismo bean (ver ConfiguracionSagas). */
    public void establecerSelf(ServicioSagaSecundaria3 self) {
        this.self = self;
    }

    @Override public TipoOrden tipo() { return SagaSecundaria3.TIPO; }

    /**
     * Recibe el agregado ya cargado por el llamante (una única carga por paso,
     * antes del REST): la tx guarda esa misma instancia (con su version) para
     * que un takeover intermedio haga fallar el guardar.
     */
    @Override
    public SenalPaso ejecutarPaso(OrdenRoot orden) {
        var saga = (SagaSecundaria3) orden.saga();
        var resultado = puerto.ejecutar((ComandoPasoSecundaria3.Ejecutar) saga.comandoActual()); // REST fuera de tx
        return self.aplicar(orden, saga, resultado); // via proxy -> @Transactional
    }

    @Transactional
    public SenalPaso aplicar(OrdenRoot orden, SagaSecundaria3 saga, ResultadoPasoSecundaria3 resultado) {
        saga.aplicarYAvanzar(resultado);
        orden.finalizar(saga.resultadoFinal());
        repo.guardar(orden);
        return new SenalPaso.Finalizada(saga.resultadoFinal());
    }
}
