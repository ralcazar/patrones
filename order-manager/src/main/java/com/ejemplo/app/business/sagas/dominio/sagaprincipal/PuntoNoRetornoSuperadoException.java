package com.ejemplo.app.business.sagas.dominio.sagaprincipal;

import com.ejemplo.app.business.ordermanager.dominio.OrdenId;

/** Se intenta cancelar una {@link SagaPrincipal} que ya alcanzó PASO7_HECHO: ya no es reversible. */
public class PuntoNoRetornoSuperadoException extends RuntimeException {
    public PuntoNoRetornoSuperadoException(OrdenId id) {
        super("La saga " + id.valor() + " ya superó el punto de no retorno (PASO7 completado): "
                + "no es cancelable. Use marcar-OK manual sobre los pasos bloqueados.");
    }
}
