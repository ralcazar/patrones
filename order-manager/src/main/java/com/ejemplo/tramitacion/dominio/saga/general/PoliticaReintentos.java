package com.ejemplo.tramitacion.dominio.saga.general;

import java.time.Duration;

/**
 * Backoff exponencial: 30s, 1m, 2m, 4m, 8m, 16m, 32m, ~1h, ~2h, 4h (tope).
 * Al agotar MAX_INTENTOS, el paso se bloquea y se abre ticket a soporte.
 */
public final class PoliticaReintentos {

    public static final int MAX_INTENTOS = 10;
    private static final Duration BASE = Duration.ofSeconds(30);
    private static final Duration TOPE = Duration.ofHours(4);

    /** @param intentosFallidos nº de fallos ya acumulados (1..MAX_INTENTOS) */
    public Duration esperaTras(int intentosFallidos) {
        if (intentosFallidos < 1) {
            throw new IllegalArgumentException("intentosFallidos debe ser >= 1");
        }
        var espera = BASE.multipliedBy(1L << Math.min(intentosFallidos - 1L, 20L));
        return espera.compareTo(TOPE) > 0 ? TOPE : espera;
    }

    public boolean agotado(int intentosFallidos) {
        return intentosFallidos >= MAX_INTENTOS;
    }
}
