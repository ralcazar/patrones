package com.ejemplo.app.infraestructure.sagas.programados;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.sagas.aplicacion.puerto.entrada.CasoUsoPurgarDatosNegocioHuerfanos;

/** Solo dispara el caso de uso; el QUÉ se borra vive en la aplicación (ver PlanificadorLimpiezaTest). */
class PlanificadorPurgaDatosNegocioTest {

    @Test
    void ejecutar_invocaLaPurgaDeHuerfanos() {
        var purga = mock(CasoUsoPurgarDatosNegocioHuerfanos.class);
        when(purga.purgarHuerfanos()).thenReturn(2L);
        var planificador = new PlanificadorPurgaDatosNegocio(purga);

        planificador.ejecutar();

        verify(purga).purgarHuerfanos();
    }
}
