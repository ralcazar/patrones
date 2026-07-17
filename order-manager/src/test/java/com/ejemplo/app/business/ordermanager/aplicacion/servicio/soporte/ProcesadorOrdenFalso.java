package com.ejemplo.app.business.ordermanager.aplicacion.servicio.soporte;

import java.util.function.Function;

import com.ejemplo.app.business.ordermanager.aplicacion.servicio.ProcesadorOrden;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.SenalPaso;
import com.ejemplo.app.business.ordermanager.dominio.OrdenRoot;
import com.ejemplo.app.business.ordermanager.dominio.TipoOrden;

/** Test double de ProcesadorOrden: delega en la función que le pase el test. */
public final class ProcesadorOrdenFalso implements ProcesadorOrden {

    private final TipoOrden tipo;
    private final Function<OrdenRoot, SenalPaso> comportamiento;

    public ProcesadorOrdenFalso(TipoOrden tipo, Function<OrdenRoot, SenalPaso> comportamiento) {
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
