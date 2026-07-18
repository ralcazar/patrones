package com.ejemplo.app.business.ordermanager.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Deduplicación de mensajes: los internos se autogeneran, los externos conservan la clave del broker. */
class MensajeIdTest {

    @Test
    void interno_generaUnValorAleatorioYNoEsExterno() {
        var msg1 = MensajeId.interno();
        var msg2 = MensajeId.interno();

        assertThat(msg1.externo()).isFalse();
        assertThat(msg1.valor()).isNotBlank();
        assertThat(msg1.valor()).isNotEqualTo(msg2.valor());
    }

    @Test
    void externo_conservaElValorAportadoYMarcaExterno() {
        var msg = MensajeId.externo("kafka-key-123");

        assertThat(msg.valor()).isEqualTo("kafka-key-123");
        assertThat(msg.externo()).isTrue();
    }
}
