package com.ejemplo.app.infraestructure.sagas.datosnegocio.programados;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ejemplo.app.business.sagas.aplicacion.puerto.entrada.CasoUsoPurgarAdjuntos;

/** Solo dispara el caso de uso con el corte calculado a partir de la retención; el QUÉ vive en la aplicación. */
class PlanificadorPurgaAdjuntosTest {

    @Test
    void ejecutar_invocaLaPurgaConElCorteDerivadoDeLaRetencion() {
        var purga = mock(CasoUsoPurgarAdjuntos.class);
        when(purga.purgarAdjuntos(any())).thenReturn(3L);
        var planificador = new PlanificadorPurgaAdjuntos(purga, 30);

        var antes = Instant.now();
        planificador.ejecutar();
        var despues = Instant.now();

        // El corte no es un any(): debe ser ahora - 30 días (la retención configurada). Así
        // un error de signo o de unidades en el cálculo del corte haría fallar el test.
        var corte = ArgumentCaptor.forClass(Instant.class);
        verify(purga).purgarAdjuntos(corte.capture());
        assertThat(corte.getValue())
                .isBetween(antes.minus(Duration.ofDays(30)), despues.minus(Duration.ofDays(30)));
    }
}
