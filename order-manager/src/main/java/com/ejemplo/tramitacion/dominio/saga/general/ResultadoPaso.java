package com.ejemplo.tramitacion.dominio.saga.general;

import com.ejemplo.tramitacion.dominio.saga.asincrono.RefAsincrono;
import com.ejemplo.tramitacion.dominio.saga.paso1.RefPaso1;
import com.ejemplo.tramitacion.dominio.saga.paso2.RefPaso2;
import com.ejemplo.tramitacion.dominio.saga.paso3.RefPaso3;
import com.ejemplo.tramitacion.dominio.saga.paso4.RefPaso4;
import com.ejemplo.tramitacion.dominio.saga.paso5.RefPaso5;
import com.ejemplo.tramitacion.dominio.saga.paso6.RefPaso6;
import com.ejemplo.tramitacion.dominio.saga.paso7.RefPaso7;
import com.ejemplo.tramitacion.dominio.saga.paso8.RefPaso8;
import com.ejemplo.tramitacion.dominio.saga.secuencial.RefSecuencial1;
import com.ejemplo.tramitacion.dominio.saga.secuencial.RefSecuencial2;
import com.ejemplo.tramitacion.dominio.saga.simple.RefSimple;

/**
 * Lo que cada paso produce al completarse. Sealed: el switch de aplicación
 * al contexto es exhaustivo y el compilador avisa si aparece un paso nuevo.
 */
public sealed interface ResultadoPaso {
    // --- saga principal ---
    record ResultadoPaso1(RefPaso1 ref) implements ResultadoPaso {}
    record ResultadoPaso2(RefPaso2 ref) implements ResultadoPaso {}
    record ResultadoPaso3(RefPaso3 ref) implements ResultadoPaso {}
    record ResultadoPaso4(RefPaso4 ref) implements ResultadoPaso {}
    record ResultadoPaso5(RefPaso5 ref) implements ResultadoPaso {}
    record ResultadoPaso6(RefPaso6 ref) implements ResultadoPaso {}
    record ResultadoPaso7(RefPaso7 ref) implements ResultadoPaso {}
    record ResultadoPaso8(RefPaso8 ref) implements ResultadoPaso {}
    // --- sagas sucesoras ---
    record ResultadoAsincrono(RefAsincrono ref) implements ResultadoPaso {}
    record ResultadoSecuencial1(RefSecuencial1 ref) implements ResultadoPaso {}
    record ResultadoSecuencial2(RefSecuencial2 ref) implements ResultadoPaso {}
    record ResultadoSimple(RefSimple ref) implements ResultadoPaso {}
}
