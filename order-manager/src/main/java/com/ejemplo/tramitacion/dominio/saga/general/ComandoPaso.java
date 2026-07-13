package com.ejemplo.tramitacion.dominio.saga.general;

import com.ejemplo.tramitacion.dominio.saga.paso1.DatoNegocio3;
import com.ejemplo.tramitacion.dominio.saga.paso1.RefPaso1;
import com.ejemplo.tramitacion.dominio.saga.paso2.RefPaso2;
import com.ejemplo.tramitacion.dominio.saga.paso4.RefPaso4;
import com.ejemplo.tramitacion.dominio.saga.paso5.RefPaso5;
import com.ejemplo.tramitacion.dominio.saga.paso7.DatoNegocio2;
import com.ejemplo.tramitacion.dominio.saga.paso7.RefPaso7;
import com.ejemplo.tramitacion.dominio.saga.secuencial.RefSecuencial1;

/** Lo que el dominio ordena ejecutar. La capa de aplicación lo traduce a llamadas a puertos. */
public sealed interface ComandoPaso {
    // --- saga principal ---
    record EjecutarPaso1(DatoNegocio1Id datoNegocio1Id, DatoNegocio3 datos) implements ComandoPaso {}
    record EjecutarPaso2(RefPaso1 refPaso1) implements ComandoPaso {}
    record EjecutarPaso3(DatoNegocio1Id datoNegocio1Id, RefPaso2 refPaso2) implements ComandoPaso {}
    record EjecutarPaso4(RefPaso1 refPaso1, RefPaso2 refPaso2) implements ComandoPaso {}
    record EjecutarPaso5(RefPaso4 refPaso4) implements ComandoPaso {}
    record EjecutarPaso6(RefPaso5 refPaso5) implements ComandoPaso {}
    record EjecutarPaso7(RefPaso5 refPaso5, DatoNegocio2 datoNegocio2) implements ComandoPaso {}
    record EjecutarPaso8(DatoNegocio1Id datoNegocio1Id, RefPaso7 refPaso7) implements ComandoPaso {}
    // --- sagas sucesoras ---
    record EjecutarAsincrono(DatoNegocio1Id datoNegocio1Id, RefPaso5 refPaso5) implements ComandoPaso {}
    record EjecutarSecuencial1(DatoNegocio1Id datoNegocio1Id, RefPaso1 refPaso1) implements ComandoPaso {}
    record EjecutarSecuencial2(RefSecuencial1 refSecuencial1) implements ComandoPaso {}
    record EjecutarSimple(DatoNegocio1Id datoNegocio1Id, RefPaso7 refPaso7) implements ComandoPaso {}
    // --- compensaciones (solo saga principal, solo antes del punto de no retorno) ---
    record CompensarPaso1(RefPaso1 refPaso1) implements ComandoPaso {}
    record CompensarPaso2(RefPaso2 refPaso2) implements ComandoPaso {}
}
