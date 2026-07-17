package com.ejemplo.app.business.ordermanager.aplicacion.servicio.comun.soporte;

import java.util.function.Function;

import com.ejemplo.app.business.ordermanager.aplicacion.servicio.comun.ServicioSaga;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.comun.SenalPaso;
import com.ejemplo.app.business.ordermanager.dominio.comun.OrdenRoot;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoOrden;

/** Test double de ServicioSaga: delega en la función que le pase el test. */
public final class ServicioSagaFalso implements ServicioSaga {

    private final TipoOrden tipo;
    private final Function<OrdenRoot, SenalPaso> comportamiento;

    public ServicioSagaFalso(TipoOrden tipo, Function<OrdenRoot, SenalPaso> comportamiento) {
        this.tipo = tipo;
        this.comportamiento = comportamiento;
    }

    @Override
    public TipoOrden tipo() { return tipo; }

    @Override
    public SenalPaso ejecutarPaso(OrdenRoot orden) {
        return comportamiento.apply(orden);
    }
}
