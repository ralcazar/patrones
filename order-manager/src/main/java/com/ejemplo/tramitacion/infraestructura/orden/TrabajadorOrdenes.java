package com.ejemplo.tramitacion.infraestructura.orden;

import jakarta.annotation.PreDestroy;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.ejemplo.tramitacion.dominio.orden.Orden;
import com.ejemplo.tramitacion.dominio.orden.ProcesadorOrden;

/**
 * Un trabajador del pool. {@link #drenar()} corre en un hilo del executor
 * (@Async) y consume órdenes EN BUCLE hasta vaciar la cola; entonces retorna y
 * libera el hilo.
 *
 * Por qué es un bean SEPARADO de {@link GestorOrdenes}: @Async funciona por
 * proxy de Spring. Si el método @Async se invocara desde la misma clase
 * (this.drenar()), el proxy no se aplicaría y correría de forma síncrona. Al
 * llamarlo desde otro bean (GestorOrdenes -> trabajador.drenar()), sí pasa por
 * el proxy y va al pool.
 *
 * Concurrencia: el executor está dimensionado a N hilos (ver
 * ConfiguracionEjecucionAsincrona), así que como mucho N drenadores corren a la
 * vez = hasta N órdenes en paralelo.
 */
@Component
public class TrabajadorOrdenes {

    private static final Logger log = LoggerFactory.getLogger(TrabajadorOrdenes.class);

    private final ServicioOrdenes servicioOrdenes;
    private final ProcesadorOrden procesador;

    /** Al apagar el contexto, dejamos de reclamar trabajo nuevo (parada limpia). */
    private volatile boolean apagando = false;

    public TrabajadorOrdenes(ServicioOrdenes servicioOrdenes, ProcesadorOrden procesador) {
        this.servicioOrdenes = servicioOrdenes;
        this.procesador = procesador;
    }

    @Async("ejecutorOrdenes")
    public void drenar() {
        int procesadas = 0;
        while (!apagando) {
            String token = UUID.randomUUID().toString(); // un lease único por reclamo

            Optional<Orden> reclamada;
            try {
                reclamada = servicioOrdenes.reclamarSiguiente(token);
            } catch (Exception e) {
                // BBDD caída u otro fallo al reclamar: salimos y reintentamos en el
                // siguiente ciclo del dispatcher (backoff natural del fixedDelay).
                log.error("Fallo al reclamar; el trabajador se detiene hasta el próximo ciclo", e);
                return;
            }

            if (reclamada.isEmpty()) {
                if (procesadas > 0) {
                    log.debug("Cola vacía; drenadas {} órdenes en esta pasada", procesadas);
                }
                return; // nada más que hacer: libera el hilo del pool
            }

            Orden orden = reclamada.get();
            boolean ok = true;
            try {
                procesador.procesar(orden); // idempotente, fuera de transacción
            } catch (Exception e) {
                ok = false;
                log.error("Error procesando la orden {}", orden.getId(), e);
            }

            try {
                servicioOrdenes.finalizar(orden.getId(), token, ok);
            } catch (Exception e) {
                // Si falla la finalización, el lease caducará y otro trabajador la retomará.
                log.error("Error al finalizar la orden {}", orden.getId(), e);
            }
            procesadas++;
        }
    }

    @PreDestroy
    public void parar() {
        apagando = true; // los bucles salen tras terminar la orden en curso
    }
}
