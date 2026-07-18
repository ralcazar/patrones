package com.ejemplo.app.business.sagas.aplicacion.puerto.salida;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoDatosNegocio.RespuestaDatosNegocio;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatoNegocio1;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatoNegocio2;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatoNegocio3;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DocumentoNegocio;

/**
 * {@link PuertoDatosNegocio} es, como el resto de puertos REST del paquete,
 * solo la interfaz (sin adaptador HTTP real todavía, ver CLAUDE.md/Fase 1):
 * nadie construye {@link RespuestaDatosNegocio} todavía, así que necesita
 * test propio para cubrir su record.
 */
class PuertoDatosNegocioTest {

    @Test
    void respuestaDatosNegocio_exponeTodosSusCampos() {
        var datoNegocio1 = new DatoNegocio1(1);
        var datoNegocio2 = new DatoNegocio2(LocalDate.of(2026, 7, 18));
        var datoNegocio3 = new DatoNegocio3("dato");
        var documentos = List.of(new DocumentoNegocio("f.pdf", "application/pdf", new byte[] {1}));

        var respuesta = new RespuestaDatosNegocio(datoNegocio1, datoNegocio2, datoNegocio3, documentos);

        assertThat(respuesta.datoNegocio1()).isEqualTo(datoNegocio1);
        assertThat(respuesta.datoNegocio2()).isEqualTo(datoNegocio2);
        assertThat(respuesta.datoNegocio3()).isEqualTo(datoNegocio3);
        assertThat(respuesta.documentos()).isEqualTo(documentos);
    }
}
