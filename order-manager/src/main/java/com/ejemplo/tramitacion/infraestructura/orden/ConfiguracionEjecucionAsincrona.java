package com.ejemplo.tramitacion.infraestructura.orden;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Pool de N hilos que ejecutan {@link TrabajadorOrdenes#drenar()}, y
 * habilitación del scheduler de {@link GestorOrdenes}.
 *
 * Claves del executor:
 *  - core = max = N y queueCapacity = 0 (SynchronousQueue): NO se encolan tareas.
 *    Cada drenar() o se ejecuta en un hilo libre, o se rechaza. Así el paralelismo
 *    queda topado exactamente en N.
 *  - DiscardPolicy: si el dispatcher lanza más drenar() de los que caben, los
 *    sobrantes se descartan silenciosamente (sin tocar la BBDD). Es el
 *    comportamiento deseado: "pool lleno -> no hago nada más este ciclo".
 *  - waitForTasksToCompleteOnShutdown + awaitTermination: parada ordenada; junto
 *    con el flag de TrabajadorOrdenes, deja terminar la orden en curso y no coge
 *    nuevas.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class ConfiguracionEjecucionAsincrona {

    @Bean(name = "ejecutorOrdenes")
    public Executor ejecutorOrdenes(@Value("${gestor-ordenes.tamano-pool:10}") int tamanoPool) {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(tamanoPool);
        ex.setMaxPoolSize(tamanoPool);
        ex.setQueueCapacity(0);
        ex.setThreadNamePrefix("trabajador-ordenes-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(30);
        ex.initialize();
        return ex;
    }
}
