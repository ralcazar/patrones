package com.ejemplo.tramitacion.infraestructura.saga;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.ejemplo.tramitacion.aplicacion.saga.puerto.salida.PuertoColaTareas;
import com.ejemplo.tramitacion.aplicacion.saga.tarea.TareaSaga;
import com.ejemplo.tramitacion.infraestructura.orden.ServicioOrdenes;

/**
 * Adaptador del puerto PuertoColaTareas sobre la tabla ordenes.
 * ServicioOrdenes.encolar es REQUIRED: se une a la transacción abierta por
 * UnidadDeTrabajo, cumpliendo el contrato de atomicidad del puerto.
 */
@Component
public class AdaptadorColaTareas implements PuertoColaTareas {

    private final ServicioOrdenes servicioOrdenes;
    private final CodecTareaSaga codec;

    public AdaptadorColaTareas(ServicioOrdenes servicioOrdenes, CodecTareaSaga codec) {
        this.servicioOrdenes = servicioOrdenes;
        this.codec = codec;
    }

    @Override
    public void encolar(TareaSaga tarea) {
        encolar(tarea, Instant.now());
    }

    @Override
    public void encolar(TareaSaga tarea, Instant noAntesDe) {
        servicioOrdenes.encolar(codec.codificar(tarea),
                tarea.sagaId().valor().toString(),
                codec.tipoDe(tarea),
                noAntesDe);
    }

    @Override
    public long purgarTerminadasAntesDe(Instant corte) {
        return servicioOrdenes.purgarCompletadas(corte);
    }
}
