package com.ejemplo.app.infraestructure.ordermanager.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** (De)serialización del contexto de una orden a JSON plano, ida y vuelta. */
class ContextoCodecTest {

    @Test
    void escribirYLeer_haceIdaYVueltaConLosMismosValores() {
        Map<String, String> valores = new LinkedHashMap<>();
        valores.put("refPaso1", "ref1");
        valores.put("refPaso2", "ref2");

        var json = ContextoCodec.escribir(valores);
        var leido = ContextoCodec.leer(json);

        assertThat(leido).isEqualTo(valores);
    }

    @Test
    void leer_conJsonNuloDevuelveMapaVacio() {
        assertThat(ContextoCodec.leer(null)).isEmpty();
    }

    @Test
    void leer_conJsonMalformadoLanzaIllegalStateException() {
        assertThatThrownBy(() -> ContextoCodec.leer("esto no es json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("deserializar")
                .hasCauseInstanceOf(Exception.class);
    }

    @Test
    void escribir_conFalloDeSerializacionLanzaIllegalStateException() {
        // Un Map cuyo entrySet() falla al iterar fuerza el catch de Jackson:
        // simula un fallo real de serialización sin tocar producción.
        Map<String, String> mapaRoto = new AbstractMap<>() {
            @Override
            public java.util.Set<Entry<String, String>> entrySet() {
                throw new RuntimeException("boom");
            }
        };

        assertThatThrownBy(() -> ContextoCodec.escribir(mapaRoto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("serializar")
                .hasCauseInstanceOf(Exception.class);
    }
}
