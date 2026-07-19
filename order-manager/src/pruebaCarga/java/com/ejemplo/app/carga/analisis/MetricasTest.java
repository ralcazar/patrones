package com.ejemplo.app.carga.analisis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Test unitario de {@link Metricas#profundidadCola(List)}: fija la
 * cancelación de regresos programados (la parte con historia de bugs — ver
 * el javadoc del método), no el barrido entero, que se valida de punta a
 * punta con el harness. Ejecutar con {@code ./gradlew pruebaCargaTest}
 * (task aparte, fuera de {@code check}/jacoco), igual que
 * {@link InvariantesTest}.
 */
class MetricasTest {

    private static final Instant BASE = Instant.parse("2026-07-19T10:00:00Z");

    private static EventoLog evento(String nombre, Instant timestamp, String orden, Map<String, String> extras) {
        var campos = new java.util.HashMap<String, String>();
        campos.put("orden", orden);
        campos.put("tipo", "SECUNDARIA2");
        campos.put("pod", "1");
        campos.putAll(extras);
        return new EventoLog(timestamp, nombre, campos);
    }

    private static EventoLog aparcada(Instant timestamp, String orden, long ventanaMs) {
        return evento("orden_aparcada", timestamp, orden, Map.of("ventana_ms", Long.toString(ventanaMs)));
    }

    private static EventoLog reintento(Instant timestamp, String orden, long esperaMs) {
        return evento("reintento_programado", timestamp, orden, Map.of("espera_ms", Long.toString(esperaMs)));
    }

    private static EventoLog reclamo(Instant timestamp, String orden) {
        return evento("reclamo_ganado", timestamp, orden, Map.of());
    }

    private static EventoLog respuestaOk(Instant timestamp, String orden) {
        return evento("respuesta_secundaria2_registrada", timestamp, orden,
                Map.of("exito", "true", "mensaje_id", "msg-" + timestamp.toEpochMilli()));
    }

    private static EventoLog finalizada(Instant timestamp, String orden) {
        return evento("orden_finalizada", timestamp, orden, Map.of("resultado", "ok"));
    }

    /**
     * Caso real de rafaga-extrema: una Secundaria2 aparcada (ventana de 3h)
     * recibe la respuesta Kafka con {@code exito=false}, que programa un
     * reintento SIN pasar por {@code reclamo_ganado}. El {@code +1} de la
     * ventana de 3h queda sustituido por el del reintento: no debe aparecer
     * ningún punto de la serie horas después de la actividad real.
     */
    @Test
    void aparcadaReprogramadaPorRespuestaErronea_noDejaRegresoFantasma() {
        var eventos = List.of(
                aparcada(BASE, "orden-1", 10_800_000),
                reintento(BASE.plusMillis(500), "orden-1", 60_000),
                reclamo(BASE.plusSeconds(61), "orden-1"),
                finalizada(BASE.plusSeconds(62), "orden-1"));

        var resultado = Metricas.profundidadCola(eventos);

        Instant ultimoInstante = resultado.muestrasPorMinuto()
                .get(resultado.muestrasPorMinuto().size() - 1).instante();
        assertTrue(ultimoInstante.isBefore(BASE.plusSeconds(120)),
                "la serie no debe extenderse a la ventana de 3h cancelada, último instante: " + ultimoInstante);
        assertEquals(1, resultado.maximo());
    }

    /** La cancelación por {@code reclamo_ganado} anterior a la ventana (comportamiento previo) se conserva. */
    @Test
    void aparcadaResueltaPorReclamoAntesDeLaVentana_cancelaSuRegreso() {
        var eventos = List.of(
                aparcada(BASE, "orden-1", 10_800_000),
                reclamo(BASE.plusSeconds(30), "orden-1"),
                finalizada(BASE.plusSeconds(31), "orden-1"));

        var resultado = Metricas.profundidadCola(eventos);

        Instant ultimoInstante = resultado.muestrasPorMinuto()
                .get(resultado.muestrasPorMinuto().size() - 1).instante();
        assertTrue(ultimoInstante.isBefore(BASE.plusSeconds(120)),
                "la ventana de 3h cancelada no debe aparecer en la serie, último instante: " + ultimoInstante);
    }

    /**
     * Tercera vía de adelanto (ver javadoc de {@code profundidadCola}): la
     * respuesta Kafka con {@code exito=true} despierta la orden aparcada como
     * candidata inmediata. Si el apagado llega antes del reclamo posterior,
     * la ventana de 3h cancelada no debe quedar pintada horas después; el
     * {@code +1} real es el del instante de la respuesta.
     */
    @Test
    void aparcadaDespertadaPorRespuestaOk_sinReclamoPosterior_noDejaRegresoFantasma() {
        var eventos = List.of(
                aparcada(BASE, "orden-1", 10_800_000),
                respuestaOk(BASE.plusMillis(300), "orden-1"));

        var resultado = Metricas.profundidadCola(eventos);

        Instant ultimoInstante = resultado.muestrasPorMinuto()
                .get(resultado.muestrasPorMinuto().size() - 1).instante();
        assertTrue(ultimoInstante.isBefore(BASE.plusSeconds(60)),
                "la ventana de 3h despertada no debe aparecer en la serie, último instante: " + ultimoInstante);
        assertEquals(1, resultado.maximo());
        assertEquals(BASE.plusMillis(300), resultado.instanteMaximo());
    }

    /**
     * Flujo normal completo de Secundaria2 (aparcada → respuesta ok →
     * reclamo → finalizada): el {@code +1} del despertar y el {@code -1} del
     * reclamo se emparejan, y la ventana de 3h no aparece.
     */
    @Test
    void flujoNormalDeSecundaria2_emparejaDespertarYReclamo() {
        var eventos = List.of(
                aparcada(BASE, "orden-1", 10_800_000),
                respuestaOk(BASE.plusMillis(300), "orden-1"),
                reclamo(BASE.plusMillis(500), "orden-1"),
                finalizada(BASE.plusMillis(600), "orden-1"));

        var resultado = Metricas.profundidadCola(eventos);

        Instant ultimoInstante = resultado.muestrasPorMinuto()
                .get(resultado.muestrasPorMinuto().size() - 1).instante();
        assertTrue(ultimoInstante.isBefore(BASE.plusSeconds(60)));
        assertEquals(1, resultado.maximo());
    }

    /**
     * Un regreso programado que sigue pendiente al acabar la ejecución (una
     * orden viva esperando su backoff) SÍ cuenta: la cancelación solo aplica
     * cuando algo lo sustituye o resuelve antes.
     */
    @Test
    void regresoPendienteSinResolver_siCuentaEnLaSerie() {
        var eventos = List.of(reintento(BASE, "orden-1", 60_000));

        var resultado = Metricas.profundidadCola(eventos);

        assertEquals(1, resultado.maximo());
        assertEquals(BASE.plusSeconds(60), resultado.instanteMaximo());
    }
}
