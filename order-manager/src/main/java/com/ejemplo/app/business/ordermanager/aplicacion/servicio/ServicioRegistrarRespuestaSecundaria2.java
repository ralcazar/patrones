package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoRegistrarRespuestaSecundaria2;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoColaTareas;
import com.ejemplo.app.business.ordermanager.aplicacion.tarea.TareaSaga;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria2.RefRespuesta;

/**
 * Intake de la respuesta diferida de la saga secundaria 2: traduce lo que trae
 * el consumer de Kafka a una tarea durable y la encola. El procesamiento real
 * (cargar la saga, dedup por mensajeId) lo hará el pool del GestorOrdenes.
 * Es la pieza de aplicación que evita que el adaptador de entrada (consumer)
 * hable directamente con el adaptador de salida (cola).
 */
@Service
public class ServicioRegistrarRespuestaSecundaria2 implements CasoUsoRegistrarRespuestaSecundaria2 {

    private final PuertoColaTareas cola;

    public ServicioRegistrarRespuestaSecundaria2(PuertoColaTareas cola) {
        this.cola = cola;
    }

    @Override
    public void respuestaOk(SagaId sagaId, RefRespuesta ref, String mensajeId) {
        cola.encolar(new TareaSaga.ResultadoSagaSecundaria2Ok(sagaId, ref, mensajeId));
    }

    @Override
    public void respuestaError(SagaId sagaId, String codigo, String detalle,
                               boolean reintentable, String mensajeId) {
        cola.encolar(new TareaSaga.ResultadoSagaSecundaria2Error(
                sagaId, codigo, detalle, reintentable, mensajeId));
    }
}
