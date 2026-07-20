package com.ejemplo.app.carga.analisis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ejemplo.app.carga.esquema.InicializadorEsquemaH2;

/**
 * Tests JUnit (fase 6) de {@link Invariantes}: el invariante 5
 * ({@code sinSolicitudesDuplicadasSecundaria2}, sobre {@code List<EventoLog>}
 * en memoria) y la gracia de cierre del invariante 1
 * ({@code ningunaEstancadaSinDueno}, sobre una H2 en memoria efímera con el
 * esquema real de {@code order-manager/db}). El resto del harness se valida
 * de punta a punta (ver {@code plan-pruebas-carga.md}). Ejecutar con
 * {@code ./gradlew pruebaCargaTest} (task aparte, fuera de
 * {@code check}/jacoco).
 */
class InvariantesTest {

    private static EventoLog respuestaSecundaria2(Instant timestamp, String orden, String mensajeId) {
        return new EventoLog(timestamp, "respuesta_secundaria2_registrada",
                Map.of("orden", orden, "tipo", "SECUNDARIA2", "mensaje_id", mensajeId, "exito",
                        "true", "pod", "1"));
    }

    @Test
    void sinRespuestas_pasaTrivialmente() {
        var resultado = Invariantes.sinSolicitudesDuplicadasSecundaria2(List.of());

        assertTrue(resultado.pasa());
        assertTrue(resultado.detalles().isEmpty());
    }

    @Test
    void unaUnicaRespuesta_pasa() {
        var eventos = List.of(
                respuestaSecundaria2(Instant.parse("2026-07-19T08:00:00Z"), "orden-1", "msg-1"));

        var resultado = Invariantes.sinSolicitudesDuplicadasSecundaria2(eventos);

        assertTrue(resultado.pasa());
        assertTrue(resultado.detalles().isEmpty());
    }

    /**
     * Reentrega de Kafka: el mismo {@code mensaje_id} llega dos veces (mismo
     * evento, no una solicitud nueva). No debe contar como una respuesta más:
     * sigue habiendo 1 sola respuesta distinta, dentro del máximo permitido.
     */
    @Test
    void reentregaConMismoMensajeId_noCuentaComoRespuestaAdicional() {
        var eventos = List.of(
                respuestaSecundaria2(Instant.parse("2026-07-19T08:00:00Z"), "orden-1", "msg-1"),
                respuestaSecundaria2(Instant.parse("2026-07-19T08:00:01Z"), "orden-1", "msg-1"));

        var resultado = Invariantes.sinSolicitudesDuplicadasSecundaria2(eventos);

        assertTrue(resultado.pasa());
    }

    /**
     * Duplicación real (el Defecto A a nivel de prueba de carga): dos
     * respuestas con {@code mensaje_id} DISTINTO para la misma orden. Sin
     * caso de error en el evento real, ninguna orden debería solicitar más
     * de una vez: distintas=2, máximo permitido=1, violación.
     */
    @Test
    void dosRespuestasDistintas_esViolacion() {
        var eventos = List.of(
                respuestaSecundaria2(Instant.parse("2026-07-19T08:00:00Z"), "orden-1", "msg-1"),
                respuestaSecundaria2(Instant.parse("2026-07-19T08:00:02Z"), "orden-1", "msg-2"));

        var resultado = Invariantes.sinSolicitudesDuplicadasSecundaria2(eventos);

        assertFalse(resultado.pasa());
        assertEquals(1, resultado.detalles().size());
        String detalle = resultado.detalles().get(0);
        assertTrue(detalle.contains("orden-1"));
        assertTrue(detalle.contains("msg-1"));
        assertTrue(detalle.contains("msg-2"));
    }

    /**
     * Varias órdenes: una violación en una orden no contamina el veredicto
     * de otra orden sana en la misma ejecución.
     */
    @Test
    void violacionEnUnaOrden_noAfectaAOtraOrdenSana() {
        var eventos = List.of(
                respuestaSecundaria2(Instant.parse("2026-07-19T08:00:00Z"), "orden-1", "msg-1"),
                respuestaSecundaria2(Instant.parse("2026-07-19T08:00:02Z"), "orden-1", "msg-2"),
                respuestaSecundaria2(Instant.parse("2026-07-19T08:00:00Z"), "orden-2", "msg-3"));

        var resultado = Invariantes.sinSolicitudesDuplicadasSecundaria2(eventos);

        assertFalse(resultado.pasa());
        assertEquals(1, resultado.detalles().size());
        assertTrue(resultado.detalles().get(0).contains("orden-1"));
    }

    /**
     * Cota superior documentada en el javadoc del invariante: con
     * {@code kafka.tasa-perdida > 0} una respuesta duplicada puede perderse
     * y nunca llegar a {@code pods.log}. Este test simula justo eso: en la
     * realidad hubo una solicitud duplicada (dos solicitudes sin fallo que
     * las justifique), pero la respuesta a la segunda se perdió y solo una
     * llegó a registrarse. El invariante NO puede verla — pasa (falso
     * negativo aceptado y documentado) — pero eso es intencional: lo que
     * NUNCA debe hacer es marcar como violación un caso legítimo, y aquí,
     * con los datos que sí llegaron al log, no hay ninguna base para
     * afirmar que hubo una duplicación.
     */
    @Test
    void respuestaDuplicadaPerdidaPorTasaPerdida_noProduceFalsoPositivo() {
        var eventos = List.of(
                respuestaSecundaria2(Instant.parse("2026-07-19T08:00:00Z"), "orden-1", "msg-1"));
        // La segunda solicitud duplicada existió en la realidad simulada,
        // pero su respuesta se perdió (tasa-perdida): nunca se logueó, así
        // que no aparece en `eventos`. El invariante, correctamente, no
        // tiene forma de detectar esa duplicación real con los datos que sí
        // llegaron al log.

        var resultado = Invariantes.sinSolicitudesDuplicadasSecundaria2(eventos);

        assertTrue(resultado.pasa());
    }

    // --- Invariante 1: ninguna orden estancada sin dueño (gracia de cierre) ---

    /**
     * Prepara una H2 en memoria efímera con el esquema real de producción
     * ({@code order-manager/db/*.sql}, vía {@link InicializadorEsquemaH2},
     * igual que el harness) y devuelve su URL JDBC. {@code DB_CLOSE_DELAY=-1}
     * la mantiene viva entre la conexión del esquema, la de los INSERT y la
     * del {@link RepositorioAnalisisBbdd} bajo prueba.
     */
    private static String bbddConEsquema(String nombre) {
        String url = "jdbc:h2:mem:" + nombre + "-" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1";
        InicializadorEsquemaH2.inicializar(url, Path.of("db"));
        return url;
    }

    private static void insertarOrden(String url, String ordenId, Instant proximoReintentoEn, Instant completadaEn) {
        String sql = """
                INSERT INTO orden (orden_id, tipo, external_id, estado, intentos, proximo_reintento_en,
                                   completada_en, version, creada_en, actualizada_en)
                VALUES ('%s', 'SECUNDARIA2', '%s-ext', 'INICIAL', 1, TIMESTAMP '%s',
                        %s, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """.formatted(ordenId, ordenId, Timestamp.from(proximoReintentoEn),
                completadaEn == null ? "NULL" : "TIMESTAMP '" + Timestamp.from(completadaEn) + "'");
        try (var conexion = DriverManager.getConnection(url, "sa", "");
                var sentencia = conexion.createStatement()) {
            sentencia.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("Fallo insertando la orden de prueba en " + url, e);
        }
    }

    /** Una orden viva con el turno vencido desde mucho antes de la gracia de cierre SÍ está estancada. */
    @Test
    void ordenVencidaMasAllaDeLaGracia_esEstancada() {
        String url = bbddConEsquema("estancada");
        insertarOrden(url, "orden-vencida", Instant.now().minusSeconds(600), null);

        try (var db = new RepositorioAnalisisBbdd(url)) {
            var resultado = Invariantes.ningunaEstancadaSinDueno(db, Instant.now(), Duration.ofSeconds(3));

            assertFalse(resultado.pasa());
            assertEquals(1, resultado.detalles().size());
            assertTrue(resultado.detalles().get(0).contains("orden-vencida"));
        }
    }

    /**
     * Frontera de apagado (caso real de rafaga-extrema): el turno venció
     * DENTRO de la gracia de cierre — los pods murieron antes del barrido
     * que la habría recogido. No es estancamiento.
     */
    @Test
    void ordenVencidaDentroDeLaGracia_noEsEstancada() {
        String url = bbddConEsquema("frontera");
        insertarOrden(url, "orden-frontera", Instant.now().minusMillis(100), null);

        try (var db = new RepositorioAnalisisBbdd(url)) {
            var resultado = Invariantes.ningunaEstancadaSinDueno(db, Instant.now(), Duration.ofSeconds(3));

            assertTrue(resultado.pasa());
        }
    }

    /** Turno en el futuro (backoff de reintento o aparcada): espera legítima, no estancada. */
    @Test
    void ordenConTurnoFuturo_noEsEstancada() {
        String url = bbddConEsquema("futura");
        insertarOrden(url, "orden-esperando", Instant.now().plusSeconds(60), null);

        try (var db = new RepositorioAnalisisBbdd(url)) {
            var resultado = Invariantes.ningunaEstancadaSinDueno(db, Instant.now(), Duration.ofSeconds(3));

            assertTrue(resultado.pasa());
        }
    }

    /** Una orden completada nunca cuenta como estancada, por muy vencido que tenga el turno. */
    @Test
    void ordenCompletadaConTurnoVencido_noEsEstancada() {
        String url = bbddConEsquema("completada");
        insertarOrden(url, "orden-completada", Instant.now().minusSeconds(600), Instant.now().minusSeconds(500));

        try (var db = new RepositorioAnalisisBbdd(url)) {
            var resultado = Invariantes.ningunaEstancadaSinDueno(db, Instant.now(), Duration.ofSeconds(3));

            assertTrue(resultado.pasa());
        }
    }
}
