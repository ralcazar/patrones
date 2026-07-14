package com.ejemplo.app.infraestructure.ordermanager.cola;

import jakarta.annotation.PreDestroy;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoDespacharTareas;
import com.ejemplo.app.business.ordermanager.aplicacion.tarea.TareaReclamada;


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
 *
 * Como adaptador de entrada, no toca la cola (adaptador de salida) ni la BBDD:
 * reclama, procesa y finaliza a través del caso de uso de despacho (capa de
 * aplicación). Aquí viven solo el CUÁNDO (hilos, bucle, lease) y la política de
 * errores; el QUÉ vive en la aplicación (regla de arquitectura del CLAUDE.md).
 */
@Component
public class TrabajadorOrdenes {

    private static final Logger log = LoggerFactory.getLogger(TrabajadorOrdenes.class);

    private final CasoUsoDespacharTareas despacho;

    /** Al apagar el contexto, dejamos de reclamar trabajo nuevo (parada limpia). */
    private volatile boolean apagando = false;

    public TrabajadorOrdenes(CasoUsoDespacharTareas despacho) {
        this.despacho = despacho;
    }

    @Async("ejecutorOrdenes")
    public void drenar() {
        int procesadas = 0;
        while (!apagando) {
            String lease = UUID.randomUUID().toString(); // un lease único por reclamo

            Optional<TareaReclamada> reclamada;
            try {
                reclamada = despacho.reclamarSiguiente(lease);
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

            TareaReclamada tarea = reclamada.get();
            boolean ok = true;
            try {
                despacho.procesar(tarea); // idempotente, fuera de transacción
            } catch (Exception e) {
                ok = false;
                log.error("Error procesando la orden {}", tarea.referencia(), e);
            }

            try {
                despacho.finalizar(tarea, ok);
            } catch (Exception e) {
                // Si falla la finalización, el lease caducará y otro trabajador la retomará.
                log.error("Error al finalizar la orden {}", tarea.referencia(), e);
            }
            procesadas++;
        }
    }

    @PreDestroy
    public void parar() {
        apagando = true; // los bucles salen tras terminar la orden en curso
    }
}
