package com.ejemplo.app.business.ordermanager.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** VO abierto (no enum cerrado) para que otras aplicaciones definan sus propios tipos de orden. */
class TipoOrdenTest {

    @Test
    void constructor_aceptaUnValorNoBlanco() {
        assertThat(new TipoOrden("TIPO_EJEMPLO").valor()).isEqualTo("TIPO_EJEMPLO");
    }

    @Test
    void constructor_rechazaValorNulo() {
        assertThatThrownBy(() -> new TipoOrden(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rechazaValorEnBlanco() {
        assertThatThrownBy(() -> new TipoOrden("   ")).isInstanceOf(IllegalArgumentException.class);
    }
}
