package com.ejemplo.app.business.ordermanager.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Peso neutro de planificación: el motor solo lo compara, no sabe qué lo
 * determina (ver javadoc de la clase).
 */
class PrioridadTest {

    @Test
    void normal_tienePesoCero() {
        assertThat(Prioridad.normal().peso()).isZero();
    }

    @Test
    void constructorYAccessor_exponenElPesoDado() {
        assertThat(new Prioridad(42).peso()).isEqualTo(42);
    }

    @Test
    void normal_esConsistenteConUnaInstanciaEquivalenteConstruidaDirectamente() {
        assertThat(Prioridad.normal()).isEqualTo(new Prioridad(0));
    }
}
