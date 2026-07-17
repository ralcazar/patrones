package com.ejemplo.app.infraestructure.sagas.persistencia;

import java.util.Map;
import java.util.function.Function;

/** Helpers compartidos por los {@code SoporteSaga*} para leer/escribir el mapa de contexto. */
final class AyudanteContexto {

    private AyudanteContexto() {}

    static void ponerSiNoNulo(Map<String, String> m, String clave, String valor) {
        if (valor != null) {
            m.put(clave, valor);
        }
    }

    static <R> R refONull(Map<String, String> ctx, String clave, Function<String, R> fabrica) {
        var valor = ctx.get(clave);
        return valor == null ? null : fabrica.apply(valor);
    }
}
