package com.patrones.sagamanager.infrastructure.adapter.in.legacypoller;

/** Fila de la tabla legacy (legacy_request). Vive entera en este adaptador; el dominio no la conoce. */
public record LegacyRequestRow(long id, String idBorrador, String payload) {
}
