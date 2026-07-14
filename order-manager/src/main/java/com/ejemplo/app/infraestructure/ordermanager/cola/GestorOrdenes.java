package com.ejemplo.app.infraestructure.ordermanager.cola;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoDespacharTareas;

/**
 * El concepto general: coordina el procesamiento de las órdenes.
 *
 * No crea hilos a mano. Cada {@code intervalo-sondeo-ms}, si hay trabajo
 * pendiente, "rellena" el pool lanzando N tareas {@link TrabajadorOrdenes#drenar()}
 * (@Async). Los hilos ocupados hacen que las llamadas sobrantes se descarten en
 * el executor (DiscardPolicy), así que el efecto neto es: mantener hasta N
 * drenadores vivos mientras haya cola.
 *
 * Como adaptador de entrada (@Scheduled), no consulta la BBDD directamente:
 * pregunta por trabajo al caso de uso de despacho (capa de aplicación), que se
 * apoya en el puerto de recepción (regla de arquitectura del CLAUDE.md).
 *
 * Flujo:
 *  - Con cola llena: los N hilos están drenando; las N llamadas de este ciclo
 *    se descartan (no-op) y no tocan la BBDD.
 *  - Cola vacía: {@link CasoUsoDespacharTareas#hayTrabajoPendiente()} devuelve
 *    false y ni siquiera despertamos al pool (1 consulta barata por ciclo).
 *  - Llega trabajo: el siguiente ciclo lo detecta y relanza los drenadores libres.
 */
@Component
public class GestorOrdenes {

    private final CasoUsoDespacharTareas despacho;
    private final TrabajadorOrdenes trabajador;
    private final int tamanoPool;

    public GestorOrdenes(CasoUsoDespacharTareas despacho,
                         TrabajadorOrdenes trabajador,
                         @Value("${gestor-ordenes.tamano-pool:10}") int tamanoPool) {
        this.despacho = despacho;
        this.trabajador = trabajador;
        this.tamanoPool = tamanoPool;
    }

    @Scheduled(fixedDelayString = "${gestor-ordenes.intervalo-sondeo-ms:500}")
    public void despachar() {
        if (!despacho.hayTrabajoPendiente()) {
            return; // nada pendiente: no despertamos al pool
        }
        // Rellena el pool. Las llamadas que no encuentren hilo libre se descartan
        // en el executor; las que sí, arrancan (o continúan) un drenador.
        for (int i = 0; i < tamanoPool; i++) {
            trabajador.drenar();
        }
    }
}
