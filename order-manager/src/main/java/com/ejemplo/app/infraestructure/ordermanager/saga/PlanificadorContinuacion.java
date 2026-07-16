package com.ejemplo.app.infraestructure.ordermanager.saga;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoContinuarSaga;

/**
 * Un planificador por pod: cada pasada comprueba si hay trabajo (un EXISTS
 * barato, vía el caso de uso) y, si lo hay, despierta hasta N workers pull.
 * El reclamo con optimistic lock lo hace el propio caso de uso: arbitra igual
 * entre workers del mismo pod que entre pods.
 */
@Component
public class PlanificadorContinuacion {

    private final CasoUsoContinuarSaga casoUso;
    private final TrabajadorContinuacion trabajador;
    private final int trabajadores;

    public PlanificadorContinuacion(CasoUsoContinuarSaga casoUso, TrabajadorContinuacion trabajador,
            @Value("${orden.planificador.trabajadores:4}") int trabajadores) {
        this.casoUso = casoUso;
        this.trabajador = trabajador;
        this.trabajadores = trabajadores;
    }

    @Scheduled(fixedDelayString = "${orden.planificador.intervalo-ms:500}")
    public void ejecutar() {
        if (!casoUso.hayTrabajoPendiente()) {
            return;
        }
        for (int i = 0; i < trabajadores; i++) {
            trabajador.trabajar();   // los que no quepan en el pool se descartan
        }
    }
}
