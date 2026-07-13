package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import java.util.Map;

import org.jmolecules.ddd.annotation.Factory;

import com.ejemplo.app.business.ordermanager.dominio.comun.PasoSaga;
import com.ejemplo.app.business.ordermanager.dominio.comun.RefPaso1;
import com.ejemplo.app.business.ordermanager.dominio.comun.RefPaso5;
import com.ejemplo.app.business.ordermanager.dominio.comun.RefPaso7;
import com.ejemplo.app.business.ordermanager.dominio.comun.ResultadoPaso;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.PasoSagaPrincipal;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.RefPaso2;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.RefPaso4;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.ResultadoPasoPrincipal;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria1.PasoSagaSecundaria1;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria1.RefInicio;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria1.ResultadoPasoSecundaria1;

/**
 * Traduce los datos que soporte teclea en la pantalla (Map plano del DTO)
 * al ResultadoPaso tipado que el agregado aplica al contexto.
 * Solo los pasos cuyo resultado consume algún paso posterior necesitan datos.
 */
@Factory
final class FabricaResultadoManual {

    private FabricaResultadoManual() {}

    static ResultadoPaso paraPaso(PasoSaga paso, Map<String, String> datos) {
        if (datos == null || datos.isEmpty()) {
            return null;
        }
        return switch (paso) {
            case PasoSagaPrincipal p -> paraPrincipal(p, datos);
            case PasoSagaSecundaria1 p -> paraSecundaria1(p, datos);
            // SECUNDARIA2 y SECUNDARIA3: su resultado no lo consume nadie
            default -> null;
        };
    }

    private static ResultadoPaso paraPrincipal(PasoSagaPrincipal paso, Map<String, String> datos) {
        return switch (paso) {
            case PASO1 -> new ResultadoPasoPrincipal.ResultadoPaso1(new RefPaso1(requerido(datos, "refPaso1")));
            case PASO2 -> new ResultadoPasoPrincipal.ResultadoPaso2(new RefPaso2(requerido(datos, "refPaso2")));
            case PASO4 -> new ResultadoPasoPrincipal.ResultadoPaso4(new RefPaso4(requerido(datos, "refPaso4")));
            case PASO5 -> new ResultadoPasoPrincipal.ResultadoPaso5(new RefPaso5(requerido(datos, "refPaso5")));
            case PASO7 -> new ResultadoPasoPrincipal.ResultadoPaso7(new RefPaso7(requerido(datos, "refPaso7")));
            // El resultado del resto de pasos no lo consume nadie: se ignora
            case PASO3, PASO6, PASO8 -> null;
        };
    }

    private static ResultadoPaso paraSecundaria1(PasoSagaSecundaria1 paso, Map<String, String> datos) {
        return switch (paso) {
            case INICIO -> new ResultadoPasoSecundaria1.Iniciada(new RefInicio(requerido(datos, "refInicio")));
            case CONFIRMACION -> null; // cierra la saga: nadie consume su resultado
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
