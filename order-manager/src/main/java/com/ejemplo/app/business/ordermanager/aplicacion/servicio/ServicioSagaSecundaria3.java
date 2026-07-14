package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoColaTareas;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoMensajesProcesados;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoSagaSecundaria3;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioSagaSecundaria3;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.UnidadDeTrabajo;
import com.ejemplo.app.business.ordermanager.dominio.comun.ComandoPaso;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExcepcionServicioExterno;
import com.ejemplo.app.business.ordermanager.dominio.comun.MensajeId;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria3.ComandoPasoSecundaria3;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria3.PasoSagaSecundaria3;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria3.SagaSecundaria3Root;

/** Orquestador de la saga secundaria 3: una llamada REST síncrona y COMPLETADA. */
@Service
public class ServicioSagaSecundaria3 extends ServicioSagaBase<PasoSagaSecundaria3, SagaSecundaria3Root> {

    private final RepositorioSagaSecundaria3 repo;
    private final PuertoSagaSecundaria3 puerto;

    public ServicioSagaSecundaria3(RepositorioSagaSecundaria3 repo, UnidadDeTrabajo tx,
            PuertoMensajesProcesados dedup, PuertoColaTareas cola,
            PuertoSagaSecundaria3 puerto) {
        super(tx, dedup, cola);
        this.repo = repo;
        this.puerto = puerto;
    }

    @Override
    protected SagaSecundaria3Root cargar(SagaId id) {
        return repo.cargar(id);
    }

    @Override
    protected void guardar(SagaSecundaria3Root saga) {
        repo.guardar(saga);
    }

    @Override
    protected void ejecutar(SagaId id, PasoSagaSecundaria3 paso, ComandoPaso cmd) {
        try {
            var c = (ComandoPasoSecundaria3.Ejecutar) cmd;
            pasoCompletado(id, PasoSagaSecundaria3.EJECUCION, puerto.ejecutar(c), MensajeId.interno());
        } catch (ExcepcionServicioExterno e) {
            pasoFallido(id, paso, e.motivo(), MensajeId.interno());
        }
    }
}
