package com.ejemplo.app.carga.analisis;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Compacta {@code pods.log} (fase 5 de {@code plan-pruebas-carga.md}) a un
 * formato reducido para que un agente LLM analista pueda leerlo COMPLETO
 * dentro de su presupuesto de contexto: descarta las líneas que no son
 * eventos (arranque de Spring, Hikari, Hibernate...), recorta el timestamp a
 * solo la hora (la fecha y la zona son constantes en toda la ejecución, se
 * documentan una vez en la leyenda), abrevia el pod ({@code pod=3} ->
 * {@code p3}, {@code pod=lanzador} -> {@code lanzador}) y sustituye cada
 * UUID de {@code orden=} por un alias corto ({@code o1}, {@code o2}...)
 * asignado por orden de primera aparición. Es una transformación 1:1 con las
 * líneas de evento del crudo (mismo orden, sin agregación ni filtrado de
 * eventos): el entrelazado real se conserva intacto para el análisis
 * cualitativo (ver {@code PROMPT-ANALISIS.md}).
 *
 * <p>Escribe en la carpeta de salida {@code pods-compacto.log} (una línea
 * por evento del crudo) y {@code leyenda-compacto.md} (fecha/zona, formato
 * de línea, tabla alias -> UUID). Se invoca de las mismas dos formas que
 * {@link AnalizadorEjecucion}: automáticamente desde
 * {@code LanzadorPruebaCarga} al final de cada ejecución, en el mismo punto
 * donde ya se invoca el analizador, o a mano sobre una carpeta de salida ya
 * existente vía {@link #main}.
 */
public final class CompactadorLogLlm {

    private static final DateTimeFormatter FORMATO_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /** Mismo patrón que {@link LectorLog}: identifica el inicio de una nueva clave {@code clave=valor}. */
    private static final Pattern INICIO_CLAVE = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*=.*");

    private CompactadorLogLlm() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Uso: CompactadorLogLlm <carpeta-de-salida>");
            System.exit(2);
            return;
        }
        Path carpetaSalida = Path.of(args[0]);
        compactar(carpetaSalida);
        System.out.println("[CompactadorLogLlm] Escrito " + carpetaSalida.resolve("pods-compacto.log") + " y "
                + carpetaSalida.resolve("leyenda-compacto.md"));
    }

    public static void compactar(Path carpetaSalida) {
        List<String> lineas = leerLineas(carpetaSalida.resolve("pods.log"));

        Map<String, String> aliasPorOrden = new LinkedHashMap<>(); // UUID -> alias, por orden de primera aparición
        int[] contadorOrden = {0};
        StringBuilder compacto = new StringBuilder();
        String fechaZona = null;

        for (String linea : lineas) {
            if (linea.isBlank()) {
                continue;
            }
            int separador = linea.indexOf(' ');
            if (separador < 0) {
                continue;
            }
            String textoTimestamp = linea.substring(0, separador);
            String resto = linea.substring(separador + 1);
            if (!resto.startsWith("evento=")) {
                continue; // línea de continuación (stacktrace) o log ajeno al catálogo, igual que LectorLog
            }

            OffsetDateTime timestamp = OffsetDateTime.parse(textoTimestamp, FORMATO_TIMESTAMP);
            if (fechaZona == null) {
                fechaZona = timestamp.toLocalDate() + " (zona " + timestamp.getOffset() + ")";
            }

            Map<String, String> campos = parsearCampos(resto);
            String evento = campos.remove("evento");
            String pod = campos.remove("pod");
            String orden = campos.remove("orden");
            String tipo = campos.remove("tipo");
            String errorTipo = campos.get("error_tipo");
            if (errorTipo != null) {
                campos.put("error_tipo", simplificarFqcn(errorTipo));
            }

            compacto.append(FORMATO_HORA.format(timestamp.toLocalTime())).append(' ').append(aliasPod(pod))
                    .append(' ').append(evento);
            if (orden != null) {
                String alias = aliasPorOrden.computeIfAbsent(orden, k -> "o" + (++contadorOrden[0]));
                compacto.append(' ').append(alias).append(' ').append(tipo);
            }
            for (var entrada : campos.entrySet()) {
                compacto.append(' ').append(entrada.getKey()).append('=').append(entrada.getValue());
            }
            compacto.append('\n');
        }

        escribir(carpetaSalida.resolve("pods-compacto.log"), compacto.toString());
        escribir(carpetaSalida.resolve("leyenda-compacto.md"), leyenda(fechaZona, aliasPorOrden));
    }

    private static String aliasPod(String pod) {
        return pod.chars().allMatch(Character::isDigit) ? "p" + pod : pod;
    }

    private static String simplificarFqcn(String fqcn) {
        int punto = fqcn.lastIndexOf('.');
        return punto < 0 ? fqcn : fqcn.substring(punto + 1);
    }

    /** Mismo tokenizado que {@link LectorLog#leer}: un valor puede llevar espacios (p. ej. {@code error_mensaje}); se reconoce el inicio de una clave nueva por {@link #INICIO_CLAVE}. */
    private static Map<String, String> parsearCampos(String resto) {
        var campos = new LinkedHashMap<String, String>();
        String claveActual = null;
        StringBuilder valorActual = null;
        for (String token : resto.split(" ")) {
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
        return campos;
    }

    private static String leyenda(String fechaZona, Map<String, String> aliasPorOrden) {
        StringBuilder md = new StringBuilder();
        md.append("# Leyenda de `pods-compacto.log`\n\n");
        md.append("Fecha y zona horaria de la ejecución (recortadas del timestamp de cada línea): ")
                .append(fechaZona != null ? fechaZona : "sin eventos").append(".\n\n");

        md.append("## Formato de línea\n\n");
        md.append("`<hora> <pod> <evento> [<alias-orden> <tipo>] <resto>`: `<hora>` es `HH:mm:ss.SSS` "
                + "(fecha y zona arriba, constantes en toda la ejecución); `<pod>` es `pN` para el pod N o "
                + "`lanzador`; `[<alias-orden> <tipo>]` solo aparece si el evento lleva `orden`/`tipo` (ver "
                + "catálogo de eventos en `resources/escenarios/README.md`); `<resto>` son los demás campos "
                + "`clave=valor` del evento tal cual, en el mismo orden que en `pods.log`, salvo `error_tipo`, "
                + "que aquí lleva solo el nombre simple de la clase (no el FQCN). Es una transformación 1:1 "
                + "con las líneas de evento del crudo: mismo orden, sin agregación ni filtrado de eventos.\n\n");

        md.append("## Alias de orden -> UUID\n\n");
        md.append("| Alias | UUID |\n|---|---|\n");
        for (var entrada : aliasPorOrden.entrySet()) {
            md.append("| ").append(entrada.getValue()).append(" | ").append(entrada.getKey()).append(" |\n");
        }
        return md.toString();
    }

    private static List<String> leerLineas(Path podsLog) {
        try {
            return Files.readAllLines(podsLog);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + podsLog, e);
        }
    }

    private static void escribir(Path fichero, String contenido) {
        try {
            Files.writeString(fichero, contenido);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo escribir " + fichero, e);
        }
    }
}
