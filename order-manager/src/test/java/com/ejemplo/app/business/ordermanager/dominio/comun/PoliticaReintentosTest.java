package com.ejemplo.app.business.ordermanager.dominio.comun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/** Escalera de reintentos 1,3,5,10,20,45,90,180 min, indefinida a partir del 8º fallo. */
class PoliticaReintentosTest {

    private final PoliticaReintentos politica = new PoliticaReintentos();

    @Test
    void esperaTras_siguelaEscaleraCompleta() {
        assertThat(politica.esperaTras(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(politica.esperaTras(2)).isEqualTo(Duration.ofMinutes(3));
        assertThat(politica.esperaTras(3)).isEqualTo(Duration.ofMinutes(5));
        assertThat(politica.esperaTras(4)).isEqualTo(Duration.ofMinutes(10));
        assertThat(politica.esperaTras(5)).isEqualTo(Duration.ofMinutes(20));
        assertThat(politica.esperaTras(6)).isEqualTo(Duration.ofMinutes(45));
        assertThat(politica.esperaTras(7)).isEqualTo(Duration.ofMinutes(90));
        assertThat(politica.esperaTras(8)).isEqualTo(Duration.ofMinutes(180));
    }

    @Test
    void esperaTras_consumidaLaEscaleraSigueA180Indefinidamente() {
        assertThat(politica.esperaTras(9)).isEqualTo(Duration.ofMinutes(180));
        assertThat(politica.esperaTras(50)).isEqualTo(Duration.ofMinutes(180));
        assertThat(politica.esperaTras(1000)).isEqualTo(Duration.ofMinutes(180));
    }

    @Test
    void esperaTras_rechazaIntentosMenoresQueUno() {
        assertThatThrownBy(() -> politica.esperaTras(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void debeAbrirTicket_falsoMientrasNoSeConsumeLaEscalera() {
        for (int i = 0; i < 8; i++) {
            assertThat(politica.debeAbrirTicket(i)).isFalse();
        }
    }

    @Test
    void debeAbrirTicket_verdaderoDesdeElOctavoIntentoEnAdelante() {
        assertThat(politica.debeAbrirTicket(8)).isTrue();
        assertThat(politica.debeAbrirTicket(9)).isTrue();
        assertThat(politica.debeAbrirTicket(1000)).isTrue();
    }
}
