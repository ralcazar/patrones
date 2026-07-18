package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.dominio.ConcurrenciaOptimistaException;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;

/**
 * Núcleo del reintento ante conflicto de versión optimista: hasta 5 intentos
 * (ver {@link ReintentoOptimista}), reintentando sobre la MISMA acción (que
 * vuelve a cargar y reaplicar) y relanzando la última excepción si los agota.
 */
class ReintentoOptimistaTest {

    private static final OrdenId ID = OrdenId.nuevo();

    @Test
    void ejecutar_devuelveElResultadoAlPrimerIntentoSinReintentar() {
        var invocaciones = new AtomicInteger();

        var resultado = ReintentoOptimista.ejecutar(() -> {
            invocaciones.incrementAndGet();
            return "ok";
        });

        assertThat(resultado).isEqualTo("ok");
        assertThat(invocaciones.get()).isEqualTo(1);
    }

    @Test
    void ejecutar_reintentaTrasConflictosTransitoriosYGanaAlTercerIntento() {
        var invocaciones = new AtomicInteger();
        Supplier<String> accion = () -> {
            var intento = invocaciones.incrementAndGet();
            if (intento < 3) {
                throw new ConcurrenciaOptimistaException(ID, intento);
            }
            return "ok";
        };

        var resultado = ReintentoOptimista.ejecutar(accion);

        assertThat(resultado).isEqualTo("ok");
        assertThat(invocaciones.get()).isEqualTo(3);
    }

    @Test
    void ejecutar_relanzaLaUltimaExcepcionTrasAgotarLosCincoReintentos() {
        var invocaciones = new AtomicInteger();
        Supplier<String> accion = () -> {
            var intento = invocaciones.incrementAndGet();
            throw new ConcurrenciaOptimistaException(ID, intento);
        };

        assertThatThrownBy(() -> ReintentoOptimista.ejecutar(accion))
                .isInstanceOf(ConcurrenciaOptimistaException.class)
                .hasMessageContaining("5"); // versión del último (5º) intento, no de los anteriores

        assertThat(invocaciones.get()).isEqualTo(5);
    }
}
