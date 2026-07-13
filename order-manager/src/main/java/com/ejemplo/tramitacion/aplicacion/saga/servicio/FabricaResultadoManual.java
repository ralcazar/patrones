package com.ejemplo.tramitacion.aplicacion.saga.servicio;

import java.util.Map;

import com.ejemplo.tramitacion.dominio.saga.general.Paso;
import com.ejemplo.tramitacion.dominio.saga.paso1.RefPaso1;
import com.ejemplo.tramitacion.dominio.saga.paso2.RefPaso2;
import com.ejemplo.tramitacion.dominio.saga.paso4.RefPaso4;
import com.ejemplo.tramitacion.dominio.saga.paso5.RefPaso5;
import com.ejemplo.tramitacion.dominio.saga.paso7.RefPaso7;
import com.ejemplo.tramitacion.dominio.saga.secuencial.RefSecuencial1;
import com.ejemplo.tramitacion.dominio.saga.general.ResultadoPaso;

/**
 * Traduce los datos que soporte teclea en la pantalla (Map plano del DTO)
 * al ResultadoPaso tipado que el agregado aplica al contexto.
 * Solo los pasos cuyo resultado consume algún paso posterior necesitan datos.
 */
final class FabricaResultadoManual {

    private FabricaResultadoManual() {}

    static ResultadoPaso paraPaso(Paso paso, Map<String, String> datos) {
        if (datos == null || datos.isEmpty()) {
            return null;
        }
        return switch (paso) {
            case PASO1 -> new ResultadoPaso.ResultadoPaso1(new RefPaso1(requerido(datos, "refPaso1")));
            case PASO2 -> new ResultadoPaso.ResultadoPaso2(new RefPaso2(requerido(datos, "refPaso2")));
            case PASO4 -> new ResultadoPaso.ResultadoPaso4(new RefPaso4(requerido(datos, "refPaso4")));
            case PASO5 -> new ResultadoPaso.ResultadoPaso5(new RefPaso5(requerido(datos, "refPaso5")));
            case PASO7 -> new ResultadoPaso.ResultadoPaso7(new RefPaso7(requerido(datos, "refPaso7")));
            case SECUENCIAL1 -> new ResultadoPaso.ResultadoSecuencial1(
                    new RefSecuencial1(requerido(datos, "refSecuencial1")));
            // El resultado del resto de pasos no lo consume nadie: se ignora
            case PASO3, PASO6, PASO8, ASINCRONO, SECUENCIAL2, SIMPLE -> null;
        };
    }

    private static String requerido(Map<String, String> datos, String clave) {
        var valor = datos.get(clave);
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Falta el dato manual obligatorio: " + clave);
        }
        return valor;
    }
}
