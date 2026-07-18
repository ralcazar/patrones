package com.ejemplo.app.business.ordermanager.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DatosManualesRequeridosExceptionTest {

    @Test
    void mensaje_incluyeElPasoYLaOrden() {
        var id = OrdenId.nuevo();

        var ex = new DatosManualesRequeridosException(id, "PASO3_PENDIENTE");

        assertThat(ex.getMessage())
                .contains("PASO3_PENDIENTE")
                .contains(id.valor().toString());
    }
}
