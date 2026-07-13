package com.ejemplo.tramitacion.dominio.saga.general;

import com.ejemplo.tramitacion.dominio.saga.paso1.RefPaso1;
import com.ejemplo.tramitacion.dominio.saga.paso5.RefPaso5;
import com.ejemplo.tramitacion.dominio.saga.paso7.RefPaso7;

/**
 * Contexto recortado con el que arranca cada saga sucesora: exactamente los
 * datos que necesita y nada más. La saga arrancada nunca vuelve a mirar a la
 * que la originó; su única correlación con la tramitación es el datoNegocio1Id.
 */
public sealed interface ContextoArranque {
    DatoNegocio1Id datoNegocio1Id();

    record ContextoAsincrona(DatoNegocio1Id datoNegocio1Id, RefPaso5 refPaso5) implements ContextoArranque {}

    record ContextoSecuencial(DatoNegocio1Id datoNegocio1Id, RefPaso1 refPaso1) implements ContextoArranque {}

    record ContextoSimple(DatoNegocio1Id datoNegocio1Id, RefPaso7 refPaso7) implements ContextoArranque {}
}
