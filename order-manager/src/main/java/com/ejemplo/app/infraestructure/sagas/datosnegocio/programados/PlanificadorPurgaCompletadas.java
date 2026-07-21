package com.ejemplo.app.infraestructure.sagas.datosnegocio.programados;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ejemplo.app.business.sagas.aplicacion.puerto.entrada.CasoUsoPurgarCompletadas;

/**
 * Disparador periódico de la purga de tramitaciones completadas (criterio
 * por tramitación): el QUÉ se borra vive en la aplicación
 * (ServicioPurgarCompletadas, con reintento e incidencia), aquí solo el
 * CUÁNDO y la retención -- mismo patrón que PlanificadorLimpieza ->
 * CasoUsoLimpiarDatosAntiguos. Se ejecuta después de la purga de adjuntos
 * (23:00, ver sagas.purga-adjuntos.cron).
 */
@Component
public class PlanificadorPurgaCompletadas {

    private static final Logger log = LoggerFactory.getLogger(PlanificadorPurgaCompletadas.class);

    private final CasoUsoPurgarCompletadas purga;
    private final Duration retencion;

    public PlanificadorPurgaCompletadas(CasoUsoPurgarCompletadas purga,
            @Value("${sagas.purga-completadas.retencion-dias:180}") long retencionDias) {
        this.purga = purga;
        this.retencion = Duration.ofDays(retencionDias);
    }

    @Scheduled(cron = "${sagas.purga-completadas.cron:0 30 23 * * *}")
    public void ejecutar() {
        Instant corte = Instant.now().minus(retencion);
        var tramitacionesPurgadas = purga.purgarCompletadas(corte);
        log.info("Purga de completadas (corte {}): {} tramitaciones purgadas", corte, tramitacionesPurgadas);
    }
}
