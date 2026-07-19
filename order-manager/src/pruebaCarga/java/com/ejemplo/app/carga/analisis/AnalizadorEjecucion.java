package com.ejemplo.app.carga.analisis;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Analizador determinista de una ejecución del harness de pruebas de carga
 * (fase 3 de {@code plan-pruebas-carga.md}, ampliado en la fase 6): lee
 * {@code pods.log} y consulta la H2 de la carpeta de salida por JDBC, evalúa
 * los 5 invariantes y calcula las métricas/anomalías, y escribe
 * {@code informe.md} en esa misma carpeta.
 *
 * <p>Se invoca de dos formas:
 * <ul>
 *   <li>Automáticamente, en proceso, desde {@code LanzadorPruebaCarga} al
 *       cerrar los contextos de todos los pods (usa
 *       {@link #analizar(Path, Duration, Duration)} con el lease y el
 *       intervalo de planificador REALES del escenario: el lease para que el
 *       invariante de solapes distinga un takeover legítimo, el intervalo
 *       para la gracia de cierre del invariante 1).</li>
 *   <li>A mano, sobre una carpeta de salida ya existente, vía {@link #main}
 *       (usa {@link #analizar(Path)}, con el lease y el intervalo por
 *       defecto de {@code application.yml} salvo que se pasen como segundo y
 *       tercer argumento — ver {@code PROMPT-ANALISIS.md}).</li>
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

    /**
     * Coincide con el valor por defecto de
     * {@code ordermanager.planificador.intervalo-ms} en application.yml
     * (5000). Mismo papel que {@link #LEASE_POR_DEFECTO}: el modo manual
     * ({@link #main}) no conserva el escenario .yml, así que sin argumento se
     * asume el valor de producción.
     */
    public static final Duration INTERVALO_PLANIFICADOR_POR_DEFECTO = Duration.ofMillis(5000);

    /**
     * Margen que se añade al intervalo del planificador para formar la gracia
     * de cierre del invariante 1 (ver {@code Invariantes.ningunaEstancadaSinDueno}):
     * cubre el tiempo entre el último barrido posible de un pod y el "ahora"
     * del analizador (cierre secuencial de los contextos + arranque del
     * análisis). Una orden estancada DE VERDAD lleva vencida al menos la
     * espera mínima de la escalera (60 s), así que este margen no le da
     * cobertura.
     */
    private static final Duration MARGEN_CIERRE_CONTEXTOS = Duration.ofSeconds(2);

    private AnalizadorEjecucion() {
    }

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 3) {
            System.err.println(
                    "Uso: AnalizadorEjecucion <carpeta-de-salida> [lease ISO-8601, p.ej. PT10M] [intervalo-planificador ISO-8601, p.ej. PT0.25S]");
            System.exit(2);
            return;
        }
        Path carpetaSalida = Path.of(args[0]);
        Duration lease = args.length >= 2 ? Duration.parse(args[1]) : LEASE_POR_DEFECTO;
        Duration intervalo = args.length == 3 ? Duration.parse(args[2]) : INTERVALO_PLANIFICADOR_POR_DEFECTO;
        int codigoSalida = analizar(carpetaSalida, lease, intervalo);
        System.out.println("[AnalizadorEjecucion] Veredicto: " + (codigoSalida == 0 ? "BUENO" : "MALO")
                + " — ver " + carpetaSalida.resolve("informe.md"));
        System.exit(codigoSalida);
    }

    /** Usa el lease y el intervalo por defecto de producción; para escenarios que los sobreescriben, ver {@link #analizar(Path, Duration, Duration)}. */
    public static int analizar(Path carpetaSalida) {
        return analizar(carpetaSalida, LEASE_POR_DEFECTO, INTERVALO_PLANIFICADOR_POR_DEFECTO);
    }

    /**
     * @param lease el lease de token realmente usado por los pods de esta
     *              ejecución (necesario para que el invariante de solapes
     *              distinga un takeover legítimo de un solape real; la
     *              carpeta de salida no conserva el escenario .yml original).
     * @param intervaloPlanificador el intervalo de barrido realmente usado
     *              por los pods ({@code motor.planificador.intervalo-ms} del
     *              escenario): junto con {@link #MARGEN_CIERRE_CONTEXTOS}
     *              forma la gracia de cierre del invariante 1.
     * @return 0 si el veredicto es BUENO, 1 si es MALO.
     */
    public static int analizar(Path carpetaSalida, Duration lease, Duration intervaloPlanificador) {
        List<EventoLog> eventos = LectorLog.leer(carpetaSalida.resolve("pods.log"));
        String urlJdbc = "jdbc:h2:file:" + carpetaSalida.resolve("bbdd") + ";MODE=Oracle";

        try (var db = new RepositorioAnalisisBbdd(urlJdbc)) {
            Duration graciaCierre = intervaloPlanificador.plus(MARGEN_CIERRE_CONTEXTOS);
            // Ancla del invariante 1: el fin de la ejecución según el propio log
            // (último evento), no el reloj del analizador — así re-analizar una
            // carpeta antigua reproduce el mismo veredicto que el análisis en
            // caliente. Un log sin eventos no ocurre en una ejecución real
            // (el lanzador siempre escribe los suyos); si pasara, se cae al
            // "ahora" y el invariante se evalúa sin frontera de apagado.
            Instant finEjecucion = eventos.isEmpty() ? Instant.now()
                    : eventos.get(eventos.size() - 1).timestamp();
            List<ResultadoInvariante> invariantes = List.of(
                    Invariantes.ningunaEstancadaSinDueno(db, finEjecucion, graciaCierre),
                    Invariantes.sinSolapesDeEjecucion(eventos, lease),
                    Invariantes.reintentosRespetanPolitica(eventos),
                    Invariantes.ticketsCoherentesConReintentos(db),
                    Invariantes.sinSolicitudesDuplicadasSecundaria2(eventos));
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
