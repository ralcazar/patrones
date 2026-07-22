package com.ejemplo.app.business.sagas.dominio.datosnegocio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.dominio.ExternalId;

/**
 * Cobertura del dominio nuevo de datos de negocio (Fase 1, autocontenida):
 * nadie más lo usa todavía, así que estos VOs y el agregado necesitan test
 * propio en vez de quedar cubiertos indirectamente por otro agregado.
 */
class DatosNegocioTest {

    @Test
    void datosNegocioId_nuevo_generaUnIdentificadorConUuidDistinto() {
        var id1 = DatosNegocioId.nuevo();
        var id2 = DatosNegocioId.nuevo();

        assertThat(id1.valor()).isInstanceOf(UUID.class);
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void datoNegocio1_exponeSuValor() {
        assertThat(new DatoNegocio1(42).valor()).isEqualTo(42);
    }

    @Test
    void datoNegocio2_exponeSuValor() {
        var fecha = LocalDate.of(2026, 7, 18);
        assertThat(new DatoNegocio2(fecha).valor()).isEqualTo(fecha);
    }

    @Test
    void datoNegocio3_exponeSuValor() {
        assertThat(new DatoNegocio3("texto").valor()).isEqualTo("texto");
    }

    @Test
    void documentoNegocio_exponeNombreMimeTypeYContenido() {
        var contenido = new byte[] {1, 2, 3};
        var documento = new DocumentoNegocio("factura.pdf", "application/pdf", contenido);

        assertThat(documento.nombre()).isEqualTo("factura.pdf");
        assertThat(documento.mimeType()).isEqualTo("application/pdf");
        assertThat(documento.contenido()).isEqualTo(contenido);
    }

    @Test
    void crear_construyeElAgregadoConTodosSusCamposYSinPurgar() {
        var id = DatosNegocioId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var datoNegocio1 = new DatoNegocio1(7);
        var datoNegocio2 = new DatoNegocio2(LocalDate.of(2026, 1, 1));
        var datoNegocio3 = new DatoNegocio3("dato");

        var datosNegocio = DatosNegocio.crear(id, externalId, datoNegocio1, datoNegocio2, datoNegocio3);

        assertThat(datosNegocio.id()).isEqualTo(id);
        assertThat(datosNegocio.externalId()).isEqualTo(externalId);
        assertThat(datosNegocio.datoNegocio1()).isEqualTo(datoNegocio1);
        assertThat(datosNegocio.datoNegocio2()).isEqualTo(datoNegocio2);
        assertThat(datosNegocio.datoNegocio3()).isEqualTo(datoNegocio3);
        assertThat(datosNegocio.purgadoEn()).isNull();
        assertThat(datosNegocio.estaPurgada()).isFalse();
    }

    @Test
    void rehidratar_conservaElPurgadoEnRecibido() {
        var id = DatosNegocioId.nuevo();
        var externalId = ExternalId.de(UUID.randomUUID().toString());
        var purgadoEn = Instant.parse("2026-06-21T10:00:00Z");

        var datosNegocio = DatosNegocio.rehidratar(id, externalId, new DatoNegocio1(1),
                new DatoNegocio2(LocalDate.of(2026, 1, 1)), new DatoNegocio3("dato"), purgadoEn);

        assertThat(datosNegocio.purgadoEn()).isEqualTo(purgadoEn);
        assertThat(datosNegocio.estaPurgada()).isTrue();
    }

    @Test
    void rehidratar_conPurgadoEnNulo_noEstaPurgada() {
        var datosNegocio = DatosNegocio.rehidratar(DatosNegocioId.nuevo(),
                ExternalId.de(UUID.randomUUID().toString()), new DatoNegocio1(1),
                new DatoNegocio2(LocalDate.of(2026, 1, 1)), new DatoNegocio3("dato"), null);

        assertThat(datosNegocio.estaPurgada()).isFalse();
    }

    @Test
    void purgar_sellaElInstanteRecibidoYQuedaMarcadaComoPurgada() {
        var datosNegocio = DatosNegocio.crear(DatosNegocioId.nuevo(),
                ExternalId.de(UUID.randomUUID().toString()), new DatoNegocio1(1),
                new DatoNegocio2(LocalDate.of(2026, 1, 1)), new DatoNegocio3("dato"));
        var ahora = Instant.parse("2026-06-21T10:00:00Z");

        datosNegocio.purgar(ahora);

        assertThat(datosNegocio.purgadoEn()).isEqualTo(ahora);
        assertThat(datosNegocio.estaPurgada()).isTrue();
    }
}
