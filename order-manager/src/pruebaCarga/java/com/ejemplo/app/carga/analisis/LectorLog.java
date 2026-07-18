package com.ejemplo.app.carga.analisis;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Parsea {@code pods.log} (formato documentado en
 * {@code src/pruebaCarga/resources/escenarios/README.md}, sección "Nota sobre
 * pods.log y el timestamp" + "Catálogo de eventos del log") a
 * {@link EventoLog}.
 *
 * <p>Cada línea es {@code <timestamp-ISO8601> evento=<nombre> clave=valor...}.
 * El timestamp lo añade {@code ConfiguradorLogging}
 * ({@code yyyy-MM-dd'T'HH:mm:ss.SSSXXX}) como prefijo de línea; el resto son
 * pares {@code clave=valor} separados por espacios. Un valor puede contener
 * espacios (p. ej. {@code error_mensaje} del mock: "Fallo simulado por el
 * mock de PuertoPaso4"): se reconoce el inicio de una nueva clave por el
 * patrón {@code ^[a-zA-Z_][a-zA-Z0-9_]*=} y todo lo que no encaja con ese
 * patrón se considera continuación del valor anterior.
 */
public final class LectorLog {

    private static final DateTimeFormatter FORMATO_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private static final Pattern INICIO_CLAVE = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*=.*");

    private LectorLog() {
    }

    public static List<EventoLog> leer(Path podsLog) {
        List<String> lineas;
        try {
            lineas = Files.readAllLines(podsLog);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + podsLog, e);
        }
        List<EventoLog> eventos = new ArrayList<>(lineas.size());
        for (String linea : lineas) {
            if (linea.isBlank()) {
                continue;
            }
            EventoLog evento = parsearLinea(linea);
            if (evento != null) {
                eventos.add(evento);
            }
        }
        return eventos;
    }

    /** {@code null} si la línea no es un evento reconocible (p. ej. una traza multi-línea de una excepción). */
    private static EventoLog parsearLinea(String linea) {
        int separador = linea.indexOf(' ');
        if (separador < 0) {
            return null;
        }
        String textoTimestamp = linea.substring(0, separador);
        String resto = linea.substring(separador + 1);
        if (!resto.startsWith("evento=")) {
            return null; // línea de continuación (stacktrace) o log ajeno al catálogo
        }

        var timestamp = OffsetDateTime.parse(textoTimestamp, FORMATO_TIMESTAMP).toInstant();
        var campos = new LinkedHashMap<String, String>();

        String[] tokens = resto.split(" ");
        String claveActual = null;
        StringBuilder valorActual = null;
        for (String token : tokens) {
            if (INICIO_CLAVE.matcher(token).matches()) {
                if (claveActual != null) {
                    campos.put(claveActual, valorActual.toString());
                }
                int igual = token.indexOf('=');
                claveActual = token.substring(0, igual);
                valorActual = new StringBuilder(token.substring(igual + 1));
            } else if (claveActual != null) {
                valorActual.append(' ').append(token);
            }
        }
        if (claveActual != null) {
            campos.put(claveActual, valorActual.toString());
        }

        String nombreEvento = campos.remove("evento");
        if (nombreEvento == null) {
            return null;
        }
        return new EventoLog(timestamp, nombreEvento, campos);
    }
}
