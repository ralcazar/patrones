package com.ejemplo.tramitacion.infraestructura.saga;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ejemplo.tramitacion.aplicacion.saga.puerto.entrada.CasoUsoLimpiarDatosAntiguos;

/**
 * Disparador periódico de la limpieza de datos: cada cierto tiempo purga las
 * sagas que acabaron bien y ya son antiguas (y sus tareas y dedup). El QUÉ se
 * borra vive en la aplicación (ServicioLimpiezaDatos); aquí solo el CUÁNDO.
 */
@Component
public class PlanificadorLimpieza {

    private static final Logger log = LoggerFactory.getLogger(PlanificadorLimpieza.class);

    private final CasoUsoLimpiarDatosAntiguos limpieza;
    private final Duration retencion;

    public PlanificadorLimpieza(CasoUsoLimpiarDatosAntiguos limpieza,
            @Value("${tramitacion.limpieza.retencion-dias:30}") long retencionDias) {
        this.limpieza = limpieza;
        this.retencion = Duration.ofDays(retencionDias);
    }

    @Scheduled(cron = "${tramitacion.limpieza.cron:0 0 3 * * *}")
    public void ejecutar() {
        Instant corte = Instant.now().minus(retencion);
        var resultado = limpieza.purgarAnterioresA(corte);
        log.info("Limpieza de datos (corte {}): {} sagas principales, {} sucesoras, "
                        + "{} registros dedup y {} tareas terminadas borradas",
                corte, resultado.sagasPrincipales(), resultado.sagasSucesoras(),
                resultado.mensajesDedup(), resultado.tareasTerminadas());
    }
}
