package com.ejemplo.app.infraestructure.sagas.programados;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ejemplo.app.business.sagas.aplicacion.puerto.entrada.CasoUsoPurgarDatosNegocioHuerfanos;

/**
 * Disparador periódico de la purga de datos_negocio huérfanos: el QUÉ se
 * borra vive en la aplicación (ServicioPurgarDatosNegocioHuerfanos), aquí
 * solo el CUÁNDO. Se ejecuta después de la limpieza del motor (03:00, ver
 * ordermanager.limpieza.cron), que es la que deja huérfanos al purgar
 * tramitaciones completas.
 */
@Component
public class PlanificadorPurgaDatosNegocio {

    private static final Logger log = LoggerFactory.getLogger(PlanificadorPurgaDatosNegocio.class);

    private final CasoUsoPurgarDatosNegocioHuerfanos purga;

    public PlanificadorPurgaDatosNegocio(CasoUsoPurgarDatosNegocioHuerfanos purga) {
        this.purga = purga;
    }

    @Scheduled(cron = "${sagas.purga-datos-negocio.cron:0 30 3 * * *}")
    public void ejecutar() {
        var borrados = purga.purgarHuerfanos();
        log.info("Purga de datos_negocio huérfanos: {} registros borrados", borrados);
    }
}
