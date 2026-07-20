package com.ejemplo.app.infraestructure.sagas.datosnegocio.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Clave primaria compuesta de {@link DocumentoNegocioEntity} ({@code @IdClass}):
 * Java puro, sin JPA en juego (equals/hashCode/getters).
 */
class DocumentoNegocioEntityIdTest {

    @Test
    void constructorYGetters_exponenAmbosCampos() {
        var datosnegocioId = UUID.randomUUID();
        var id = new DocumentoNegocioEntityId(datosnegocioId, 3);

        assertThat(id.getDatosnegocioId()).isEqualTo(datosnegocioId);
        assertThat(id.getSecuencia()).isEqualTo(3);
    }

    @Test
    void constructorSinArgumentos_dejaCamposNulos() {
        var id = new DocumentoNegocioEntityId();

        assertThat(id.getDatosnegocioId()).isNull();
        assertThat(id.getSecuencia()).isNull();
    }

    @Test
    void equals_esReflexivo() {
        var id = new DocumentoNegocioEntityId(UUID.randomUUID(), 1);

        assertThat(id).isEqualTo(id);
    }

    @Test
    void equals_conMismosCampos_sonIguales() {
        var datosnegocioId = UUID.randomUUID();
        var id1 = new DocumentoNegocioEntityId(datosnegocioId, 2);
        var id2 = new DocumentoNegocioEntityId(datosnegocioId, 2);

        assertThat(id1).isEqualTo(id2);
        assertThat(id1).hasSameHashCodeAs(id2);
    }

    @Test
    void equals_conDistintaSecuencia_noSonIguales() {
        var datosnegocioId = UUID.randomUUID();
        var id1 = new DocumentoNegocioEntityId(datosnegocioId, 1);
        var id2 = new DocumentoNegocioEntityId(datosnegocioId, 2);

        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void equals_conDistintoDatosnegocioId_noSonIguales() {
        var id1 = new DocumentoNegocioEntityId(UUID.randomUUID(), 1);
        var id2 = new DocumentoNegocioEntityId(UUID.randomUUID(), 1);

        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void equals_conNull_noEsIgual() {
        var id = new DocumentoNegocioEntityId(UUID.randomUUID(), 1);

        assertThat(id).isNotEqualTo(null);
    }

    @Test
    void equals_conOtroTipo_noEsIgual() {
        var id = new DocumentoNegocioEntityId(UUID.randomUUID(), 1);

        assertThat(id).isNotEqualTo("no soy un DocumentoNegocioEntityId");
    }
}
