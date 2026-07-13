package com.ejemplo.tramitacion.dominio.saga.general;

import java.util.UUID;

/**
 * Identificador de mensaje para deduplicación (la mensajería entrega at-least-once).
 * Los internos (transiciones síncronas dentro del propio proceso) no se deduplican.
 */
public record MensajeId(String valor, boolean externo) {
    public static MensajeId interno() { return new MensajeId(UUID.randomUUID().toString(), false); }
    public static MensajeId externo(String valor) { return new MensajeId(valor, true); }
}
