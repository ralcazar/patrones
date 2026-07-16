package com.ejemplo.app.infraestructure.ordermanager.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoContinuarSaga;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;

/**
 * Bucle del worker pull (sin Spring: aquí no hay @Async ni pool, solo el
 * método): encadena continuarSiguiente hasta que no queda trabajo y no
 * propaga fallos de infraestructura.
 */
class TrabajadorContinuacionTest {

    /** Fake del caso de uso: cada llamada a continuarSiguiente consume la siguiente respuesta. */
    private static class CasoUsoFalso implements CasoUsoContinuarSaga {

        private final boolean[] respuestas;
        private final AtomicInteger llamadas = new AtomicInteger();

        CasoUsoFalso(boolean... respuestas) {
            this.respuestas = respuestas;
        }

        @Override
        public boolean continuarSiguiente() {
            return respuestas[llamadas.getAndIncrement()];
        }

        @Override
        public void continuar(SagaId id, TipoSaga tipo) { }

        @Override
        public boolean hayTrabajoPendiente() { return false; }
    }

    @Test
    void trabajar_encadenaSagasHastaQueNoQuedaTrabajo() {
        var casoUso = new CasoUsoFalso(true, true, false);

        new TrabajadorContinuacion(casoUso).trabajar();

        assertThat(casoUso.llamadas.get()).isEqualTo(3);
    }

    @Test
    void trabajar_noPropagaUnFalloDeInfraestructura() {
        var casoUso = new CasoUsoFalso(true, true, false) {
            @Override
            public boolean continuarSiguiente() {
                throw new IllegalStateException("BD caída");
            }
        };

        assertThatCode(() -> new TrabajadorContinuacion(casoUso).trabajar()).doesNotThrowAnyException();
    }
}
