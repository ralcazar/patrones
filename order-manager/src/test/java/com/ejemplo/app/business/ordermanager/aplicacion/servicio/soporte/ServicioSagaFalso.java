package com.ejemplo.app.business.ordermanager.aplicacion.servicio.soporte;

import java.util.function.Function;

import com.ejemplo.app.business.ordermanager.aplicacion.servicio.ServicioSaga;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.SenalPaso;
import com.ejemplo.app.business.ordermanager.dominio.comun.OrdenRoot;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;

/** Test double de ServicioSaga: delega en la función que le pase el test. */
public final class ServicioSagaFalso implements ServicioSaga {

    private final TipoSaga tipo;
    private final Function<OrdenRoot, SenalPaso> comportamiento;

    public ServicioSagaFalso(TipoSaga tipo, Function<OrdenRoot, SenalPaso> comportamiento) {
        this.tipo = tipo;
        this.comportamiento = comportamiento;
    }

    @Override
    public TipoSaga tipo() { return tipo; }

    @Override
    public SenalPaso ejecutarPaso(OrdenRoot orden) {
        return comportamiento.apply(orden);
    }
}
