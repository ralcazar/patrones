package com.ejemplo.app.business.sagas.dominio.datosnegocio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.dominio.Prioridad;

/**
 * Traducción ORIGEN -&gt; {@link Prioridad}: única pieza de negocio que sabe qué
 * determina el peso de planificación (el motor, en cambio, lo trata como un
 * valor neutro; ver {@link Prioridad}).
 */
class DatoNegocio3Test {

    @Test
    void prioridad_ordenamientoOrigen2MayorQueOrigen1MayorQueOrigen3() {
        var origen2 = new DatoNegocio3("ORIGEN2").prioridad();
        var origen1 = new DatoNegocio3("ORIGEN1").prioridad();
        var origen3 = new DatoNegocio3("ORIGEN3").prioridad();

        assertThat(origen2.peso()).isGreaterThan(origen1.peso());
        assertThat(origen1.peso()).isGreaterThan(origen3.peso());
    }

    @Test
    void prioridad_valorDesconocidoCaeEnPrioridadNormal() {
        assertThat(new DatoNegocio3("VALOR_NO_RECONOCIDO").prioridad()).isEqualTo(Prioridad.normal());
    }

    @Test
    void prioridad_origen3EsMayorQueLaNormal() {
        assertThat(new DatoNegocio3("ORIGEN3").prioridad().peso()).isGreaterThan(Prioridad.normal().peso());
    }
}
