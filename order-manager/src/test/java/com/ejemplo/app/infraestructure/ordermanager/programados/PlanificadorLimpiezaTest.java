package com.ejemplo.app.infraestructure.ordermanager.programados;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoLimpiarDatosAntiguos;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoLimpiarDatosAntiguos.ResultadoLimpieza;

/** Solo dispara el caso de uso con el corte calculado a partir de la retención; el QUÉ vive en la aplicación. */
class PlanificadorLimpiezaTest {

    @Test
    void ejecutar_invocaLaLimpiezaConElCorteDerivadoDeLaRetencion() {
        var limpieza = mock(CasoUsoLimpiarDatosAntiguos.class);
        when(limpieza.purgarAnterioresA(any())).thenReturn(new ResultadoLimpieza(3));
        var planificador = new PlanificadorLimpieza(limpieza, 30);

        planificador.ejecutar();

        verify(limpieza).purgarAnterioresA(any());
    }
}
