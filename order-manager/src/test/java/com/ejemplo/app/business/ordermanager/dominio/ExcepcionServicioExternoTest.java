package com.ejemplo.app.business.ordermanager.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExcepcionServicioExternoTest {

    @Test
    void motivo_devuelveElMotivoAportadoAlConstruirYPropagaLaCausa() {
        var motivo = MotivoFallo.errorTecnico("boom");
        var causa = new RuntimeException("causa real");

        var ex = new ExcepcionServicioExterno(motivo, causa);

        assertThat(ex.motivo()).isEqualTo(motivo);
        assertThat(ex.getCause()).isEqualTo(causa);
        assertThat(ex.getMessage()).isEqualTo(motivo.detalle());
    }
}
