package com.ejemplo.app.business.ordermanager.aplicacion.servicio.soporte;

import java.util.function.Function;

import com.ejemplo.app.business.ordermanager.aplicacion.servicio.OrquestadorSaga;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.SenalPaso;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;

/** Test double de OrquestadorSaga: delega en la función que le pase el test. */
public final class OrquestadorFalso implements OrquestadorSaga {

    private final TipoSaga tipo;
    private final Function<SagaId, SenalPaso> comportamiento;

    public OrquestadorFalso(TipoSaga tipo, Function<SagaId, SenalPaso> comportamiento) {
        this.tipo = tipo;
        this.comportamiento = comportamiento;
    }

    @Override
    public TipoSaga tipo() { return tipo; }

    @Override
    public SenalPaso ejecutarPaso(SagaId id) {
        return comportamiento.apply(id);
    }
}
