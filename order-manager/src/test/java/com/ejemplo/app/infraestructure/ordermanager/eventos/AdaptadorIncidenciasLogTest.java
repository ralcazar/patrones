package com.ejemplo.app.infraestructure.ordermanager.eventos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ejemplo.app.testsoporte.CapturaLog;

/**
 * "Abrir una incidencia" es solo escribir una línea de log (formato
 * {@code evento=incidencia_abierta tarea=... causa=... intentos=... pod=...},
 * mismo estilo que {@link AdaptadorTicketsLog}): se verifica que no rompe y
 * el formato exacto de la línea.
 */
class AdaptadorIncidenciasLogTest {

    private final AdaptadorIncidenciasLog adaptador = new AdaptadorIncidenciasLog("pod-test");

    @Test
    void abrir_noRompe() {
        assertThatCode(() -> adaptador.abrir("purga-adjuntos", "timeout", 3)).doesNotThrowAnyException();
    }

    @Test
    void abrir_logaUnaLineaConElFormatoDeEventoYPod() {
        List<String> mensajes;
        try (var captura = new CapturaLog(AdaptadorIncidenciasLog.class)) {
            adaptador.abrir("purga-completadas", "java.lang.RuntimeException: boom", 5);
            mensajes = captura.mensajes();
        }

        assertThat(mensajes).hasSize(1);
        assertThat(mensajes.get(0))
                .contains("evento=incidencia_abierta", "tarea=purga-completadas",
                        "causa=java.lang.RuntimeException: boom", "intentos=5", "pod=pod-test");
    }
}
