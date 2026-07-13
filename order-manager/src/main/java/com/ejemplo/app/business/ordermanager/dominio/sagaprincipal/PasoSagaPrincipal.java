package com.ejemplo.app.business.ordermanager.dominio.sagaprincipal;

import com.ejemplo.app.business.ordermanager.dominio.comun.PasoSaga;

/** Pasos de la saga principal. El orden de declaración ES el orden del flujo. */
public enum PasoSagaPrincipal implements PasoSaga {
    PASO1, PASO2, PASO3, PASO4, PASO5, PASO6, PASO7, PASO8
}
