package com.ejemplo.app.infraestructure.ordermanager.saga;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoRecepcionTareas;
import com.ejemplo.app.business.ordermanager.aplicacion.tarea.TareaReclamada;
import com.ejemplo.app.infraestructure.ordermanager.cola.ServicioOrdenes;

/**
 * Adaptador del lado de RECEPCIÓN de la cola (PuertoRecepcionTareas) sobre la
 * tabla ordenes. Traduce entre el mundo de la aplicación (TareaSaga/TareaReclamada)
 * y el motor genérico de la cola (Orden con lease): reclamar decodifica el
 * contenido a TareaSaga; finalizar recupera el id y el lease de la referencia
 * opaca que llevaba la TareaReclamada.
 *
 * Gemelo de {@link AdaptadorColaTareas} (lado de ENVÍO): ambos son el adaptador
 * de la misma tabla, uno para meter tareas y otro para sacarlas.
 */
@Component
public class AdaptadorRecepcionTareas implements PuertoRecepcionTareas {

    private final ServicioOrdenes servicioOrdenes;
    private final CodecTareaSaga codec;

    public AdaptadorRecepcionTareas(ServicioOrdenes servicioOrdenes, CodecTareaSaga codec) {
        this.servicioOrdenes = servicioOrdenes;
        this.codec = codec;
    }

    @Override
    public boolean hayPendientes() {
        return servicioOrdenes.hayTrabajoPendiente();
    }

    @Override
    public Optional<TareaReclamada> reclamarSiguiente(String lease) {
        return servicioOrdenes.reclamarSiguiente(lease)
                .map(orden -> new TareaReclamada(
                        String.valueOf(orden.getId()),
                        lease,
                        codec.decodificar(orden.getContenido())));
    }

    @Override
    public void finalizar(TareaReclamada tarea, boolean exito) {
        // ServicioOrdenes.finalizar no lanza si solo se perdió el lease (loguea y
        // sigue); si falla la BBDD, la excepción sube al trabajador, que la trata.
        servicioOrdenes.finalizar(Long.valueOf(tarea.referencia()), tarea.lease(), exito);
    }
}
