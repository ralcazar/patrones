package com.ejemplo.app.carga.analisis;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Anomalías: no invalidan el veredicto (no son invariantes), pero merecen
 * atención en el informe. Umbrales documentados en cada método: son
 * heurísticas razonables, no un contrato como los invariantes de
 * {@link Invariantes}.
 */
final class Anomalias {

    private Anomalias() {
    }

    /**
     * Minuto sin ningún {@code reclamo_ganado} pese a que la cola aproximada
     * ({@link Metricas#profundidadCola}) muestra profundidad > 0 en ese
     * minuto: indica falta de ritmo (planificador parado, pods caídos,
     * lote/intervalo mal dimensionado...).
     */
    static List<String> minutosSinRitmo(List<EventoLog> eventos, Metricas.ProfundidadCola profundidadCola) {
        TreeMap<Instant, Long> reclamosPorMinuto = new TreeMap<>();
        for (var evento : eventos) {
            if (evento.evento().equals("reclamo_ganado")) {
                reclamosPorMinuto.merge(evento.timestamp().truncatedTo(ChronoUnit.MINUTES), 1L, Long::sum);
            }
        }
        List<String> minutosSinRitmo = new ArrayList<>();
        for (var punto : profundidadCola.muestrasPorMinuto()) {
            if (punto.profundidad() <= 0) {
                continue;
            }
            long reclamos = reclamosPorMinuto.getOrDefault(punto.instante(), 0L);
            if (reclamos == 0) {
                minutosSinRitmo.add("minuto=%s profundidad_cola≈%d reclamos_ganados=0"
                        .formatted(punto.instante(), punto.profundidad()));
            }
        }
        return minutosSinRitmo;
    }

    /** Órdenes PRINCIPAL cuya saga completa duró más que el p99 de la ejecución. */
    static List<String> ordenesSobreP99(Map<String, Long> duracionesMs, long p99Ms) {
        List<String> resultado = new ArrayList<>();
        for (var entrada : duracionesMs.entrySet()) {
            if (entrada.getValue() > p99Ms) {
                resultado.add("orden=%s duracion_ms=%d (p99=%d)".formatted(entrada.getKey(), entrada.getValue(), p99Ms));
            }
        }
        resultado.sort((a, b) -> b.compareTo(a)); // los más largos primero (orden lexicográfico basta: mismo formato)
        return resultado;
    }

    /**
     * Pods cuyo nº de {@code reclamo_ganado} se desvía más de un 30% de la
     * media entre pods (umbral arbitrario mencionado en el plan como
     * "desequilibrados"; 30% separa un ruido estadístico normal de un
     * desequilibrio real en ejecuciones con volumen suficiente).
     */
    static List<String> podsDesequilibrados(List<Metricas.EstadisticasPod> porPod) {
        if (porPod.size() < 2) {
            return List.of();
        }
        double media = porPod.stream().mapToLong(Metricas.EstadisticasPod::ganados).average().orElse(0);
        if (media == 0) {
            return List.of();
        }
        List<String> resultado = new ArrayList<>();
        for (var fila : porPod) {
            double desviacion = (fila.ganados() - media) / media;
            if (Math.abs(desviacion) > 0.30) {
                resultado.add(String.format(Locale.ROOT, "pod=%s reclamos_ganados=%d media=%.1f desviación=%.0f%%",
                        fila.pod(), fila.ganados(), media, desviacion * 100));
            }
        }
        return resultado;
    }
}
