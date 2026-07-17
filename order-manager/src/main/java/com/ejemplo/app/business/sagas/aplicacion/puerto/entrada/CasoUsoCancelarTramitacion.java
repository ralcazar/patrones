package com.ejemplo.app.business.sagas.aplicacion.puerto.entrada;

import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.UsuarioSoporte;

/** Lo invoca la pantalla de soporte. Específico de la saga principal: solo ella es cancelable. */
public interface CasoUsoCancelarTramitacion {

    /**
     * Cancela la saga principal (dispara la compensación de PASO2 y PASO1).
     * Solo posible antes de alcanzar PASO7_HECHO; después lanza
     * PuntoNoRetornoSuperadoException.
     */
    void cancelarPrincipal(SagaId id, UsuarioSoporte quien, String motivo);
}
