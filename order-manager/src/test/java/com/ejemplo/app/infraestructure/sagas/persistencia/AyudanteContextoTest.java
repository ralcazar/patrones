package com.ejemplo.app.infraestructure.sagas.persistencia;

import static com.ejemplo.app.infraestructure.sagas.persistencia.AyudanteContexto.ponerSiNoNulo;
import static com.ejemplo.app.infraestructure.sagas.persistencia.AyudanteContexto.refONull;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Helpers compartidos por los {@code SoporteSaga*} para leer/escribir el mapa de contexto. */
class AyudanteContextoTest {

    @Test
    void ponerSiNoNulo_conValorNoNuloLoInsertaEnElMapa() {
        Map<String, String> m = new LinkedHashMap<>();

        ponerSiNoNulo(m, "clave", "valor");

        assertThat(m).containsEntry("clave", "valor");
    }

    @Test
    void ponerSiNoNulo_conValorNuloNoInsertaNada() {
        Map<String, String> m = new LinkedHashMap<>();

        ponerSiNoNulo(m, "clave", null);

        assertThat(m).isEmpty();
    }

    @Test
    void refONull_conClaveAusenteDevuelveNull() {
        Map<String, String> ctx = Map.of();

        var resultado = refONull(ctx, "clave", String::toUpperCase);

        assertThat(resultado).isNull();
    }

    @Test
    void refONull_conClavePresenteAplicaLaFabrica() {
        Map<String, String> ctx = Map.of("clave", "valor");

        var resultado = refONull(ctx, "clave", String::toUpperCase);

        assertThat(resultado).isEqualTo("VALOR");
    }
}
