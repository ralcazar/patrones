package com.ejemplo.app.infraestructure.ordermanager.saga;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Pool simple para los {@code @Scheduled} del módulo (planificador de
 * continuación, de tickets y de limpieza): un pool pequeño evita que una
 * pasada lenta de uno bloquee a los demás. Ya no hay trabajador dedicado ni
 * cola: cada orden candidata se procesa en el propio hilo del planificador
 * que la reclamó, con el paralelismo acotado por cuántas puede reclamar
 * (orden.planificador.limite) cada pasada.
 */
@Configuration
@EnableScheduling
public class ConfiguracionEjecucionAsincrona implements SchedulingConfigurer {

    private final int tamanoPool;

    public ConfiguracionEjecucionAsincrona(@Value("${orden.planificador.tamano-pool:4}") int tamanoPool) {
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
}
