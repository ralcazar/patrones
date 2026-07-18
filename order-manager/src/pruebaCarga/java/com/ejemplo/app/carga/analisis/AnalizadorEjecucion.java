package com.ejemplo.app.carga.analisis;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Analizador determinista de una ejecución del harness de pruebas de carga
 * (fase 3 de {@code plan-pruebas-carga.md}): lee {@code pods.log} y consulta
 * la H2 de la carpeta de salida por JDBC, evalúa los 4 invariantes y calcula
 * las métricas/anomalías, y escribe {@code informe.md} en esa misma carpeta.
 *
 * <p>Se invoca de dos formas:
 * <ul>
 *   <li>Automáticamente, en proceso, desde {@code LanzadorPruebaCarga} al
 *       cerrar los contextos de todos los pods (usa {@link #analizar(Path, Duration)}
 *       con el lease REAL del escenario, para que el invariante de solapes
 *       distinga con precisión un takeover legítimo).</li>
 *   <li>A mano, sobre una carpeta de salida ya existente, vía {@link #main}
 *       (usa {@link #analizar(Path)}, con el lease por defecto de
 *       {@code application.yml} salvo que se pase como segundo argumento —
 *       ver {@code PROMPT-ANALISIS.md}).</li>
 * </ul>
 *
 * <p>El código de salida es el veredicto: 0 BUENO (todos los invariantes se
 * cumplen), 1 MALO (al menos uno falla).
 */
public final class AnalizadorEjecucion {

    /**
     * Coincide con el valor por defecto de {@code ordermanager.lease} en
     * application.yml. Público para que {@code LanzadorPruebaCarga} pueda
     * usarlo cuando el escenario no sobreescribe {@code motor.lease} (así el
     * valor por defecto vive en un solo sitio).
     */
    public static final Duration LEASE_POR_DEFECTO = Duration.parse("PT10M");

    private AnalizadorEjecucion() {
    }

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Uso: AnalizadorEjecucion <carpeta-de-salida> [lease ISO-8601, p.ej. PT10M]");
            System.exit(2);
            return;
        }
        Path carpetaSalida = Path.of(args[0]);
        Duration lease = args.length == 2 ? Duration.parse(args[1]) : LEASE_POR_DEFECTO;
        int codigoSalida = analizar(carpetaSalida, lease);
        System.out.println("[AnalizadorEjecucion] Veredicto: " + (codigoSalida == 0 ? "BUENO" : "MALO")
                + " — ver " + carpetaSalida.resolve("informe.md"));
        System.exit(codigoSalida);
    }

    /** Usa el lease por defecto de producción; para escenarios que sobreescriben {@code motor.lease}, ver {@link #analizar(Path, Duration)}. */
    public static int analizar(Path carpetaSalida) {
        return analizar(carpetaSalida, LEASE_POR_DEFECTO);
    }

    /**
     * @param lease el lease de token realmente usado por los pods de esta
     *              ejecución (necesario para que el invariante de solapes
     *              distinga un takeover legítimo de un solape real; la
     *              carpeta de salida no conserva el escenario .yml original).
     * @return 0 si el veredicto es BUENO, 1 si es MALO.
     */
    public static int analizar(Path carpetaSalida, Duration lease) {
        List<EventoLog> eventos = LectorLog.leer(carpetaSalida.resolve("pods.log"));
        String urlJdbc = "jdbc:h2:file:" + carpetaSalida.resolve("bbdd") + ";MODE=Oracle";

        try (var db = new RepositorioAnalisisBbdd(urlJdbc)) {
            List<ResultadoInvariante> invariantes = List.of(
                    Invariantes.ningunaEstancadaSinDueno(db),
                    Invariantes.sinSolapesDeEjecucion(eventos, lease),
                    Invariantes.reintentosRespetanPolitica(eventos),
                    Invariantes.ticketsCoherentesConReintentos(db));
            boolean bueno = invariantes.stream().allMatch(ResultadoInvariante::pasa);

            var throughput = Metricas.throughputPorMinuto(eventos);
            var duracionesMs = Metricas.duracionesSagaPrincipalMs(eventos);
            var estadisticasDuracion = Metricas.estadisticasDuracion(duracionesMs);
            var porPod = Metricas.reclamosPorPod(eventos);
            long reintentosTotales = Metricas.reintentosTotales(eventos);
            var reintentosPorTipo = Metricas.reintentosPorTipo(eventos);
            var distribucionEstados = db.distribucionEstados();
            var profundidadCola = Metricas.profundidadCola(eventos);

            var minutosSinRitmo = Anomalias.minutosSinRitmo(eventos, profundidadCola);
            var sobreP99 = Anomalias.ordenesSobreP99(duracionesMs, estadisticasDuracion.p99Ms());
            var desequilibrados = Anomalias.podsDesequilibrados(porPod);

            var resumen = new InformeMarkdown.ResumenGlobal(db.contarTotalOrdenes(), db.contarCompletadas(),
                    db.contarTicketsAbiertos(), db.contarVivas());

            String informe = InformeMarkdown.generar(nombreLegible(carpetaSalida), bueno, invariantes, throughput,
                    estadisticasDuracion, porPod, reintentosTotales, reintentosPorTipo, distribucionEstados,
                    profundidadCola, minutosSinRitmo, sobreP99, desequilibrados, resumen);

            escribirInforme(carpetaSalida, informe);
            return bueno ? 0 : 1;
        }
    }

    private static String nombreLegible(Path carpetaSalida) {
        Path nombre = carpetaSalida.getFileName();
        return nombre != null ? nombre.toString() : carpetaSalida.toString();
    }

    private static void escribirInforme(Path carpetaSalida, String contenido) {
        try {
            Files.writeString(carpetaSalida.resolve("informe.md"), contenido);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo escribir informe.md en " + carpetaSalida, e);
        }
    }
}
