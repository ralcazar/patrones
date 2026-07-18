package com.ejemplo.app.business.ordermanager.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** timeout/errorTecnico son reintentables; errorNegocio no (reintentar no lo arregla). */
class MotivoFalloTest {

    @Test
    void timeout_esReintentable() {
        var motivo = MotivoFallo.timeout();

        assertThat(motivo.codigo()).isEqualTo("TIMEOUT");
        assertThat(motivo.esReintentable()).isTrue();
    }

    @Test
    void errorTecnico_esReintentableConElDetalleAportado() {
        var motivo = MotivoFallo.errorTecnico("fallo de red");

        assertThat(motivo.codigo()).isEqualTo("ERROR_TECNICO");
        assertThat(motivo.detalle()).isEqualTo("fallo de red");
        assertThat(motivo.esReintentable()).isTrue();
    }

    @Test
    void errorNegocio_noEsReintentable() {
        var motivo = MotivoFallo.errorNegocio("dato inválido");

        assertThat(motivo.codigo()).isEqualTo("ERROR_NEGOCIO");
        assertThat(motivo.detalle()).isEqualTo("dato inválido");
        assertThat(motivo.esReintentable()).isFalse();
    }
}
