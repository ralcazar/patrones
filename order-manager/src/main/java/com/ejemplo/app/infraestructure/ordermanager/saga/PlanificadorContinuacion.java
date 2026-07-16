package com.ejemplo.app.infraestructure.ordermanager.saga;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoContinuarSaga;

/**
 * Un planificador por pod: cada pasada continúa hasta {@code limite} órdenes
 * candidatas (reintento vencido, sin token vigente). El reclamo con
 * optimistic lock lo hace el propio caso de uso: si dos pods ven la misma
 * candidata, solo uno gana.
 */
@Component
public class PlanificadorContinuacion {

    private final CasoUsoContinuarSaga casoUso;
    private final int limite;

    public PlanificadorContinuacion(CasoUsoContinuarSaga casoUso,
            @Value("${orden.planificador.limite:50}") int limite) {
        this.casoUso = casoUso;
        this.limite = limite;
    }

    @Scheduled(fixedDelayString = "${orden.planificador.intervalo-ms:500}")
    public void ejecutar() {
        casoUso.continuarCandidatas(limite);
    }
}
