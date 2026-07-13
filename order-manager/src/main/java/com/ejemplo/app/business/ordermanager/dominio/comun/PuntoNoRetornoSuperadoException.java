package com.ejemplo.app.business.ordermanager.dominio.comun;

public class PuntoNoRetornoSuperadoException extends RuntimeException {
    public PuntoNoRetornoSuperadoException(SagaId id) {
        super("La saga " + id.valor() + " ya superó el punto de no retorno (PASO7 completado): "
                + "no es cancelable. Use marcar-OK manual sobre los pasos bloqueados.");
    }
}
