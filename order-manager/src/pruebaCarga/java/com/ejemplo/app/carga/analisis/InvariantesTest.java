package com.ejemplo.app.carga.analisis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Test unitario (fase 6) del invariante 5 de {@link Invariantes}
 * ({@code sinSolicitudesDuplicadasSecundaria2}): es el ÚNICO test JUnit de
 * {@code src/pruebaCarga} (el resto del harness se valida de punta a punta,
 * ver {@code plan-pruebas-carga.md}), posible aquí porque el invariante
 * opera solo sobre {@code List<EventoLog>} ya parseado, sin BBDD ni Spring
 * de por medio. Ejecutar con {@code ./gradlew pruebaCargaTest} (task aparte,
 * fuera de {@code check}/jacoco).
 */
class InvariantesTest {

    private static EventoLog respuestaSecundaria2(Instant timestamp, String orden, String mensajeId, boolean exito) {
        return new EventoLog(timestamp, "respuesta_secundaria2_registrada",
                Map.of("orden", orden, "tipo", "SECUNDARIA2", "mensaje_id", mensajeId, "exito",
                        Boolean.toString(exito), "pod", "1"));
    }

    @Test
    void sinRespuestas_pasaTrivialmente() {
        var resultado = Invariantes.sinSolicitudesDuplicadasSecundaria2(List.of());

        assertTrue(resultado.pasa());
        assertTrue(resultado.detalles().isEmpty());
    }

    @Test
    void unaUnicaRespuestaExitosaSinFallosPrevios_pasa() {
        var eventos = List.of(
                respuestaSecundaria2(Instant.parse("2026-07-19T08:00:00Z"), "orden-1", "msg-1", true));

        var resultado = Invariantes.sinSolicitudesDuplicadasSecundaria2(eventos);

        assertTrue(resultado.pasa());
        assertTrue(resultado.detalles().isEmpty());
    }

    /**
     * Reintentos legítimos: dos fallos consecutivos habilitan, cada uno, un
     * reintento de solicitud; la tercera respuesta (éxito) es la que agotan
     * esos dos fallos. distintas=3, fallosPrevios=2, máximo permitido=3: NO
     * es una violación.
     */
    @Test
    void variasRespuestasTrasFallosPrevios_noEsViolacion() {
        var eventos = List.of(
                respuestaSecundaria2(Instant.parse("2026-07-19T08:00:00Z"), "orden-1", "msg-1", false),
                respuestaSecundaria2(Instant.parse("2026-07-19T08:05:00Z"), "orden-1", "msg-2", false),
                respuestaSecundaria2(Instant.parse("2026-07-19T08:10:00Z"), "orden-1", "msg-3", true));

        var resultado = Invariantes.sinSolicitudesDuplicadasSecundaria2(eventos);

        assertTrue(resultado.pasa());
        assertTrue(resultado.detalles().isEmpty());
    }

    /**
     * Reentrega de Kafka: el mismo {@code mensaje_id} llega dos veces (mismo
     * evento, no una solicitud nueva). No debe contar como una respuesta más:
     * sigue habiendo 1 sola respuesta distinta y 0 fallos, dentro del máximo
     * permitido (1).
     */
    @Test
    void reentregaConMismoMensajeId_noCuentaComoRespuestaAdicional() {
        var eventos = List.of(
                respuestaSecundaria2(Instant.parse("2026-07-19T08:00:00Z"), "orden-1", "msg-1", true),
                respuestaSecundaria2(Instant.parse("2026-07-19T08:00:01Z"), "orden-1", "msg-1", true));

        var resultado = Invariantes.sinSolicitudesDuplicadasSecundaria2(eventos);

        assertTrue(resultado.pasa());
    }

    /**
     * Duplicación real (el Defecto A a nivel de prueba de carga): dos
     * respuestas de éxito con {@code mensaje_id} DISTINTO para la misma
     * orden sin ningún fallo previo que habilite un segundo reintento.
     * distintas=2, fallosPrevios=0, máximo permitido=1: violación.
     */
    @Test
    void dosRespuestasDistintasSinFallosPrevios_esViolacion() {
        var eventos = List.of(
                respuestaSecundaria2(Instant.parse("2026-07-19T08:00:00Z"), "orden-1", "msg-1", true),
                respuestaSecundaria2(Instant.parse("2026-07-19T08:00:02Z"), "orden-1", "msg-2", true));

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
                respuestaSecundaria2(Instant.parse("2026-07-19T08:00:00Z"), "orden-1", "msg-1", true),
                respuestaSecundaria2(Instant.parse("2026-07-19T08:00:02Z"), "orden-1", "msg-2", true),
                respuestaSecundaria2(Instant.parse("2026-07-19T08:00:00Z"), "orden-2", "msg-3", true));

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
                respuestaSecundaria2(Instant.parse("2026-07-19T08:00:00Z"), "orden-1", "msg-1", true));
        // La segunda solicitud duplicada existió en la realidad simulada,
        // pero su respuesta se perdió (tasa-perdida): nunca se logueó, así
        // que no aparece en `eventos`. El invariante, correctamente, no
        // tiene forma de detectar esa duplicación real con los datos que sí
        // llegaron al log.

        var resultado = Invariantes.sinSolicitudesDuplicadasSecundaria2(eventos);

        assertTrue(resultado.pasa());
    }
}
