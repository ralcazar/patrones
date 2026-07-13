package com.ejemplo.tramitacion.infraestructura.orden;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * El concepto general: coordina el procesamiento de las órdenes.
 *
 * No crea hilos a mano. Cada {@code intervalo-sondeo-ms}, si hay trabajo
 * pendiente, "rellena" el pool lanzando N tareas {@link TrabajadorOrdenes#drenar()}
 * (@Async). Los hilos ocupados hacen que las llamadas sobrantes se descarten en
 * el executor (DiscardPolicy), así que el efecto neto es: mantener hasta N
 * drenadores vivos mientras haya cola.
 *
 * Flujo:
 *  - Con cola llena: los N hilos están drenando; las N llamadas de este ciclo
 *    se descartan (no-op) y no tocan la BBDD.
 *  - Cola vacía: {@link ServicioOrdenes#hayTrabajoPendiente()} devuelve false y
 *    ni siquiera despertamos al pool (1 consulta barata por ciclo).
 *  - Llega trabajo: el siguiente ciclo lo detecta y relanza los drenadores libres.
 */
@Component
public class GestorOrdenes {

    private final ServicioOrdenes servicioOrdenes;
    private final TrabajadorOrdenes trabajador;
    private final int tamanoPool;

    public GestorOrdenes(ServicioOrdenes servicioOrdenes,
                         TrabajadorOrdenes trabajador,
                         @Value("${gestor-ordenes.tamano-pool:10}") int tamanoPool) {
        this.servicioOrdenes = servicioOrdenes;
        this.trabajador = trabajador;
        this.tamanoPool = tamanoPool;
    }

    @Scheduled(fixedDelayString = "${gestor-ordenes.intervalo-sondeo-ms:500}")
    public void despachar() {
        if (!servicioOrdenes.hayTrabajoPendiente()) {
            return; // nada pendiente: no despertamos al pool
        }
        // Rellena el pool. Las llamadas que no encuentren hilo libre se descartan
        // en el executor; las que sí, arrancan (o continúan) un drenador.
        for (int i = 0; i < tamanoPool; i++) {
            trabajador.drenar();
        }
    }
}
