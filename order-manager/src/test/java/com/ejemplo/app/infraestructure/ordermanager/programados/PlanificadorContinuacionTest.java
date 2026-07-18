package com.ejemplo.app.infraestructure.ordermanager.programados;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoContinuarOrden;

/**
 * Un planificador por pod: si hay trabajo pendiente despierta hasta N
 * trabajadores; si no, no hace nada. El reclamo lo arbitra el propio caso de
 * uso, así que aquí basta con Mockito, sin arrancar el scheduler.
 */
class PlanificadorContinuacionTest {

    @Test
    void ejecutar_sinTrabajoPendienteNoDespiertaNingunTrabajador() {
        var casoUso = mock(CasoUsoContinuarOrden.class);
        var trabajador = mock(TrabajadorContinuacion.class);
        when(casoUso.hayTrabajoPendiente()).thenReturn(false);
        var planificador = new PlanificadorContinuacion(casoUso, trabajador, 3);

        planificador.ejecutar();

        verify(trabajador, never()).trabajar();
    }

    @Test
    void ejecutar_conTrabajoPendienteDespiertaExactamenteNTrabajadores() {
        var casoUso = mock(CasoUsoContinuarOrden.class);
        var trabajador = mock(TrabajadorContinuacion.class);
        when(casoUso.hayTrabajoPendiente()).thenReturn(true);
        var planificador = new PlanificadorContinuacion(casoUso, trabajador, 3);

        planificador.ejecutar();

        verify(trabajador, times(3)).trabajar();
    }
}
