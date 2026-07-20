package com.ejemplo.app.testsoporte;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoObservadorEjecucion;
import com.ejemplo.app.business.ordermanager.dominio.DetalleError;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.TipoOrden;

/**
 * Doble en memoria de {@link PuertoObservadorEjecucion}: acumula cada evento
 * recibido en una lista, para que los tests verifiquen qué evento (y con qué
 * datos) se emitió en cada rama del bucle de {@code ServicioContinuarOrden} o
 * de {@code ServicioLimpiezaDatos}, sin acoplarse a SLF4J.
 */
public class ObservadorEjecucionEnMemoria implements PuertoObservadorEjecucion {

    /** Un evento observado, con los datos con los que se emitió. */
    public sealed interface Evento {
        record ReclamoGanado(OrdenId id, TipoOrden tipo) implements Evento {}
        record ReclamoPerdido(OrdenId id, TipoOrden tipo, MotivoReclamoPerdido motivo) implements Evento {}
        record ColisionOptimista(OrdenId id, TipoOrden tipo, String operacion) implements Evento {}
        record PasoCompletado(OrdenId id, TipoOrden tipo, long duracionMs) implements Evento {}
        record PasoFallido(OrdenId id, TipoOrden tipo, int intento, DetalleError error) implements Evento {}
        record ReintentoProgramado(OrdenId id, TipoOrden tipo, int intento, Duration espera) implements Evento {}
        record OrdenAparcada(OrdenId id, TipoOrden tipo, Duration ventana) implements Evento {}
        record OrdenFinalizada(OrdenId id, TipoOrden tipo) implements Evento {}
        record DatosAntiguosPurgados(long ordenesEliminadas) implements Evento {}
    }

    private final List<Evento> eventos = new ArrayList<>();

    public List<Evento> eventos() { return eventos; }

    @Override
    public void reclamoGanado(OrdenId id, TipoOrden tipo) {
        eventos.add(new Evento.ReclamoGanado(id, tipo));
    }

    @Override
    public void reclamoPerdido(OrdenId id, TipoOrden tipo, MotivoReclamoPerdido motivo) {
        eventos.add(new Evento.ReclamoPerdido(id, tipo, motivo));
    }

    @Override
    public void colisionOptimista(OrdenId id, TipoOrden tipo, String operacion) {
        eventos.add(new Evento.ColisionOptimista(id, tipo, operacion));
    }

    @Override
    public void pasoCompletado(OrdenId id, TipoOrden tipo, long duracionMs) {
        eventos.add(new Evento.PasoCompletado(id, tipo, duracionMs));
    }

    @Override
    public void pasoFallido(OrdenId id, TipoOrden tipo, int intento, DetalleError error) {
        eventos.add(new Evento.PasoFallido(id, tipo, intento, error));
    }

    @Override
    public void reintentoProgramado(OrdenId id, TipoOrden tipo, int intento, Duration espera) {
        eventos.add(new Evento.ReintentoProgramado(id, tipo, intento, espera));
    }

    @Override
    public void ordenAparcada(OrdenId id, TipoOrden tipo, Duration ventana) {
        eventos.add(new Evento.OrdenAparcada(id, tipo, ventana));
    }

    @Override
    public void ordenFinalizada(OrdenId id, TipoOrden tipo) {
        eventos.add(new Evento.OrdenFinalizada(id, tipo));
    }

    @Override
    public void datosAntiguosPurgados(long ordenesEliminadas) {
        eventos.add(new Evento.DatosAntiguosPurgados(ordenesEliminadas));
    }
}
