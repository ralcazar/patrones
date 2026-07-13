package com.ejemplo.tramitacion.infraestructura.saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ejemplo.tramitacion.aplicacion.saga.servicio.ManejadorTareasSaga;
import com.ejemplo.tramitacion.dominio.orden.Orden;
import com.ejemplo.tramitacion.dominio.orden.ProcesadorOrden;

/**
 * Implementación de ProcesadorOrden para las sagas: deserializa el contenido a
 * TareaSaga y lo enruta al manejador. "Procesar una orden" = continuar una saga.
 *
 * Idempotencia (contrato de ProcesadorOrden): la dan los guards de los
 * agregados y la deduplicación de MensajeId. La reentrega por lease de
 * cualquier tarea es un no-op o una reanudación segura.
 *
 * Si escapa una excepción (nunca debería: los fallos de negocio los absorbe
 * el dominio como fallar/reintentar), la orden queda FALLIDA. Alertar sobre
 * órdenes FALLIDAS: significan un bug o infraestructura rota, no un fallo de
 * negocio.
 */
@Component
public class ProcesadorOrdenSaga implements ProcesadorOrden {

    private static final Logger log = LoggerFactory.getLogger(ProcesadorOrdenSaga.class);

    private final ManejadorTareasSaga manejador;
    private final CodecTareaSaga codec;

    public ProcesadorOrdenSaga(ManejadorTareasSaga manejador, CodecTareaSaga codec) {
        this.manejador = manejador;
        this.codec = codec;
    }

    @Override
    public void procesar(Orden orden) {
        var tarea = codec.decodificar(orden.getContenido());
        log.debug("Orden {} -> tarea {} de saga {}", orden.getId(), orden.getTipoTarea(), orden.getSagaId());
        manejador.procesar(tarea);
    }
}
