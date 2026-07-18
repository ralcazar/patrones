package com.ejemplo.app.carga.analisis;

import java.time.Instant;
import java.util.Map;

/**
 * Una línea de {@code pods.log} ya parseada: el timestamp de línea (prefijo
 * que añade {@code ConfiguradorLogging}, NO una clave {@code clave=valor} más)
 * y el resto de pares {@code clave=valor} del mensaje estructurado, según el
 * catálogo de eventos documentado en
 * {@code src/pruebaCarga/resources/escenarios/README.md}.
 *
 * <p>{@code campos} conserva TODAS las claves del mensaje salvo {@code evento}
 * (ya extraída a {@link #evento()}), incluidas {@code orden}, {@code tipo} y
 * {@code pod} tal cual aparecen en la línea.
 */
public record EventoLog(Instant timestamp, String evento, Map<String, String> campos) {

    public String campo(String clave) {
        return campos.get(clave);
    }

    public String orden() {
        return campo("orden");
    }

    public String tipo() {
        return campo("tipo");
    }

    public String pod() {
        return campo("pod");
    }

    /** {@code campo(clave)} convertido a {@code long}, para *_ms/intentos/recuentos. */
    public long campoLong(String clave) {
        return Long.parseLong(campo(clave));
    }

    /** {@code campo(clave)} convertido a {@code int}. */
    public int campoInt(String clave) {
        return Integer.parseInt(campo(clave));
    }
}
