package com.ejemplo.app.infraestructure.sagas.datosnegocio.programados;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ejemplo.app.business.sagas.aplicacion.puerto.entrada.CasoUsoPurgarAdjuntos;

/**
 * Disparador periódico de la purga de adjuntos (criterio por tramitación):
 * el QUÉ se anula vive en la aplicación (ServicioPurgarAdjuntos, con
 * reintento e incidencia), aquí solo el CUÁNDO y la retención -- mismo
 * patrón que PlanificadorLimpieza -> CasoUsoLimpiarDatosAntiguos.
 */
@Component
public class PlanificadorPurgaAdjuntos {

    private static final Logger log = LoggerFactory.getLogger(PlanificadorPurgaAdjuntos.class);

    private final CasoUsoPurgarAdjuntos purga;
    private final Duration retencion;

    public PlanificadorPurgaAdjuntos(CasoUsoPurgarAdjuntos purga,
            @Value("${sagas.purga-adjuntos.retencion-dias:30}") long retencionDias) {
        this.purga = purga;
        this.retencion = Duration.ofDays(retencionDias);
    }

    @Scheduled(cron = "${sagas.purga-adjuntos.cron:0 0 23 * * *}")
    public void ejecutar() {
        Instant corte = Instant.now().minus(retencion);
        var tramitacionesPurgadas = purga.purgarAdjuntos(corte);
        log.info("Purga de adjuntos (corte {}): {} tramitaciones purgadas", corte, tramitacionesPurgadas);
    }
}
