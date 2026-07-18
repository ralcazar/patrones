package com.ejemplo.app.carga.analisis;

import java.util.List;

/**
 * Resultado de evaluar un invariante sobre una ejecución: si pasa, un
 * resumen de una línea, el detalle de las violaciones encontradas (vacío si
 * pasa) y notas informativas que no cuentan como violación (p. ej. un
 * takeover legítimo por lease vencido).
 */
record ResultadoInvariante(String nombre, boolean pasa, String resumen, List<String> detalles, List<String> notas) {

    static ResultadoInvariante ok(String nombre, String resumen) {
        return new ResultadoInvariante(nombre, true, resumen, List.of(), List.of());
    }

    static ResultadoInvariante ok(String nombre, String resumen, List<String> notas) {
        return new ResultadoInvariante(nombre, true, resumen, List.of(), notas);
    }

    static ResultadoInvariante fallo(String nombre, String resumen, List<String> detalles) {
        return new ResultadoInvariante(nombre, false, resumen, detalles, List.of());
    }

    static ResultadoInvariante fallo(String nombre, String resumen, List<String> detalles, List<String> notas) {
        return new ResultadoInvariante(nombre, false, resumen, detalles, notas);
    }
}
