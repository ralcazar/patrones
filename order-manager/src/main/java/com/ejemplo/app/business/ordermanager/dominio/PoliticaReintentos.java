package com.ejemplo.app.business.ordermanager.dominio;

import java.time.Duration;
import java.util.List;

/**
 * Backoff exponencial en minutos: 1, 3, 5, 10, 20, 45, 90, 180.
 * Los reintentos NUNCA se agotan: consumida la escalera se sigue reintentando
 * indefinidamente cada 180 minutos, pero la orden queda marcada con
 * "abrir ticket pendiente" para que el planificador de tickets avise a soporte
 * (el flag se borra si un reintento por fin termina bien).
 */
public final class PoliticaReintentos {

    private static final List<Duration> ESCALERA = List.of(
            Duration.ofMinutes(1), Duration.ofMinutes(3), Duration.ofMinutes(5),
            Duration.ofMinutes(10), Duration.ofMinutes(20), Duration.ofMinutes(45),
            Duration.ofMinutes(90), Duration.ofMinutes(180));

    /** @param intentosFallidos nº de fallos ya acumulados (>= 1); del 8º en adelante, 180 min. */
    public Duration esperaTras(int intentosFallidos) {
        if (intentosFallidos < 1) {
            throw new IllegalArgumentException("intentosFallidos debe ser >= 1");
        }
        return ESCALERA.get(Math.min(intentosFallidos, ESCALERA.size()) - 1);
    }

    /**
     * La escalera entera ya se consumió (8 intentos fallidos): el planificador
     * de tickets debe avisar a soporte, aunque el reintento automático continúe
     * indefinidamente cada 180 min.
     */
    public boolean debeAbrirTicket(int intentos) {
        return intentos >= ESCALERA.size();
    }
}
