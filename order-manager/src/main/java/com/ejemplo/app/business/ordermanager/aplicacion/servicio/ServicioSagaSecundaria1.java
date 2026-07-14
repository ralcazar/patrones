package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoColaTareas;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoMensajesProcesados;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoSagaSecundaria1;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioSagaSecundaria1;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.UnidadDeTrabajo;
import com.ejemplo.app.business.ordermanager.dominio.comun.ComandoPaso;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExcepcionServicioExterno;
import com.ejemplo.app.business.ordermanager.dominio.comun.MensajeId;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria1.ComandoPasoSecundaria1;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria1.PasoSagaSecundaria1;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria1.SagaSecundaria1Root;

/**
 * Orquestador de la saga secundaria 1: dos llamadas REST síncronas encadenadas
 * (INICIO -> CONFIRMACION). Cada llamada reentra con su resultado; la cadena
 * completa corre dentro del procesar() de una Orden, con checkpoint por paso.
 */
@Service
public class ServicioSagaSecundaria1 extends ServicioSagaBase<PasoSagaSecundaria1, SagaSecundaria1Root> {

    private final RepositorioSagaSecundaria1 repo;
    private final PuertoSagaSecundaria1 puerto;

    public ServicioSagaSecundaria1(RepositorioSagaSecundaria1 repo, UnidadDeTrabajo tx,
            PuertoMensajesProcesados dedup, PuertoColaTareas cola,
            PuertoSagaSecundaria1 puerto) {
        super(tx, dedup, cola);
        this.repo = repo;
        this.puerto = puerto;
    }

    @Override
    protected SagaSecundaria1Root cargar(SagaId id) {
        return repo.cargar(id);
    }

    @Override
    protected void guardar(SagaSecundaria1Root saga) {
        repo.guardar(saga);
    }

    @Override
    protected void ejecutar(SagaId id, PasoSagaSecundaria1 paso, ComandoPaso cmd) {
        try {
            switch ((ComandoPasoSecundaria1) cmd) {
                case ComandoPasoSecundaria1.Iniciar c ->
                        pasoCompletado(id, PasoSagaSecundaria1.INICIO, puerto.iniciar(c), MensajeId.interno());
                case ComandoPasoSecundaria1.Confirmar c ->
                        pasoCompletado(id, PasoSagaSecundaria1.CONFIRMACION, puerto.confirmar(c), MensajeId.interno());
            }
        } catch (ExcepcionServicioExterno e) {
            pasoFallido(id, paso, e.motivo(), MensajeId.interno());
        }
    }
}
