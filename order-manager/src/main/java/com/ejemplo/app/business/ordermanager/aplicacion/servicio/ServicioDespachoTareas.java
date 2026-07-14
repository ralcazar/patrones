package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import java.util.Optional;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoDespacharTareas;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoRecepcionTareas;
import com.ejemplo.app.business.ordermanager.aplicacion.tarea.TareaReclamada;

/**
 * Servicio de aplicación del despacho: media entre el pool (adaptador de entrada)
 * y la cola (adaptador de salida) para que la regla "entrada -> aplicación ->
 * salida" también se cumpla al RECIBIR trabajo, no solo al encolarlo.
 *
 * No orquesta el bucle ni los hilos (eso es infraestructura del pool): solo
 * delega cada operación a su colaborador — consultar/reclamar/finalizar al
 * {@code PuertoRecepcionTareas} y procesar al {@code ManejadorTareasSaga}.
 */
@Service
public class ServicioDespachoTareas implements CasoUsoDespacharTareas {

    private final PuertoRecepcionTareas recepcion;
    private final ManejadorTareasSaga manejador;

    public ServicioDespachoTareas(PuertoRecepcionTareas recepcion, ManejadorTareasSaga manejador) {
        this.recepcion = recepcion;
        this.manejador = manejador;
    }

    @Override
    public boolean hayTrabajoPendiente() {
        return recepcion.hayPendientes();
    }

    @Override
    public Optional<TareaReclamada> reclamarSiguiente(String lease) {
        return recepcion.reclamarSiguiente(lease);
    }

    @Override
    public void procesar(TareaReclamada tarea) {
        manejador.procesar(tarea.tarea());
    }

    @Override
    public void finalizar(TareaReclamada tarea, boolean exito) {
        recepcion.finalizar(tarea, exito);
    }
}
