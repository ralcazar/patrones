package com.ejemplo.app.infraestructure.ordermanager.programados;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Dos pools del módulo:
 *
 * <ul>
 *   <li>El scheduler de los {@code @Scheduled} (planificador de continuación,
 *       de tickets y de limpieza): un pool pequeño evita que una pasada lenta
 *       de uno bloquee a los demás.</li>
 *   <li>El pool de workers de continuación ("ejecutorContinuacion"): impone el
 *       tope estructural de N workers pull por pod. Con
 *       {@code corePoolSize = maxPoolSize = N}, {@code queueCapacity = 0} y
 *       {@code DiscardPolicy}, los envíos del tick que no caben en el pool se
 *       descartan en memoria (nanosegundos, cero queries): el cuerpo del
 *       {@code @Async} descartado jamás se ejecuta, y una cola solo acumularía
 *       envíos duplicados apuntando al mismo trabajo.</li>
 * </ul>
 */
@Configuration
@EnableScheduling
@EnableAsync
public class ConfiguracionEjecucionAsincrona implements SchedulingConfigurer {

    private final int tamanoPool;

    public ConfiguracionEjecucionAsincrona(@Value("${ordermanager.planificador.tamano-pool:4}") int tamanoPool) {
        this.tamanoPool = tamanoPool;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(tamanoPool);
        scheduler.setThreadNamePrefix("planificador-ordermanager-");
        scheduler.initialize();
        registrar.setTaskScheduler(scheduler);
    }

    @Bean("ejecutorContinuacion")
    ThreadPoolTaskExecutor ejecutorContinuacion(
            @Value("${ordermanager.planificador.trabajadores:2}") int trabajadores) {
        var ejecutor = new ThreadPoolTaskExecutor();
        ejecutor.setCorePoolSize(trabajadores);
        ejecutor.setMaxPoolSize(trabajadores);
        ejecutor.setQueueCapacity(0);   // sin cola: lo que no cabe se descarta
        ejecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        ejecutor.setThreadNamePrefix("trabajador-continuacion-");
        return ejecutor;
    }
}
