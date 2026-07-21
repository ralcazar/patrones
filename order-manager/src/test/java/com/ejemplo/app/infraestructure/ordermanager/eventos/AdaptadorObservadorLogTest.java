package com.ejemplo.app.infraestructure.ordermanager.eventos;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoObservadorEjecucion.MotivoReclamoPerdido;
import com.ejemplo.app.business.ordermanager.dominio.DetalleError;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.TipoOrden;
import com.ejemplo.app.testsoporte.CapturaLog;

/**
 * Formato de una línea por evento: {@code evento=<nombre> orden=<id>
 * tipo=<tipo> ... pod=<valor>}. Cada test verifica el evento correspondiente
 * del catálogo (ver {@code src/pruebaCarga/resources/escenarios/README.md}).
 */
class AdaptadorObservadorLogTest {

    private static final TipoOrden TIPO = new TipoOrden("PRINCIPAL");
    private final OrdenId id = OrdenId.nuevo();
    private final AdaptadorObservadorLog adaptador = new AdaptadorObservadorLog("pod-7");

    private String unicoMensaje(Runnable emision) {
        try (var captura = new CapturaLog(AdaptadorObservadorLog.class)) {
            emision.run();
            var mensajes = captura.mensajes();
            assertThat(mensajes).hasSize(1);
            return mensajes.get(0);
        }
    }

    @Test
    void reclamoGanado() {
        var mensaje = unicoMensaje(() -> adaptador.reclamoGanado(id, TIPO));

        assertThat(mensaje).isEqualTo("evento=reclamo_ganado orden=%s tipo=PRINCIPAL pod=pod-7".formatted(id.valor()));
    }

    @Test
    void reclamoPerdido() {
        var mensaje = unicoMensaje(
                () -> adaptador.reclamoPerdido(id, TIPO, MotivoReclamoPerdido.TOKEN_VIGENTE));

        assertThat(mensaje).isEqualTo(
                "evento=reclamo_perdido orden=%s tipo=PRINCIPAL motivo=TOKEN_VIGENTE pod=pod-7".formatted(id.valor()));
    }

    @Test
    void colisionOptimista() {
        var mensaje = unicoMensaje(() -> adaptador.colisionOptimista(id, TIPO, "ejecutarPaso"));

        assertThat(mensaje).isEqualTo(
                "evento=colision_optimista orden=%s tipo=PRINCIPAL operacion=ejecutarPaso pod=pod-7"
                        .formatted(id.valor()));
    }

    @Test
    void pasoCompletado() {
        var mensaje = unicoMensaje(() -> adaptador.pasoCompletado(id, TIPO, 123L));

        assertThat(mensaje).isEqualTo(
                "evento=paso_completado orden=%s tipo=PRINCIPAL duracion_ms=123 pod=pod-7".formatted(id.valor()));
    }

    @Test
    void pasoFallido() {
        var mensaje = unicoMensaje(() -> adaptador.pasoFallido(id, TIPO, 3,
                new DetalleError("java.lang.RuntimeException", "boom")));

        assertThat(mensaje).isEqualTo(
                ("evento=paso_fallido orden=%s tipo=PRINCIPAL intento=3 error_tipo=java.lang.RuntimeException "
                        + "error_mensaje=boom pod=pod-7").formatted(id.valor()));
    }

    @Test
    void reintentoProgramado() {
        var mensaje = unicoMensaje(
                () -> adaptador.reintentoProgramado(id, TIPO, 2, Duration.ofMinutes(3)));

        assertThat(mensaje).isEqualTo(
                "evento=reintento_programado orden=%s tipo=PRINCIPAL intento=2 espera_ms=180000 pod=pod-7"
                        .formatted(id.valor()));
    }

    @Test
    void ordenAparcada() {
        var mensaje = unicoMensaje(
                () -> adaptador.ordenAparcada(id, TIPO, Duration.ofHours(3)));

        assertThat(mensaje).isEqualTo(
                "evento=orden_aparcada orden=%s tipo=PRINCIPAL ventana_ms=10800000 pod=pod-7".formatted(id.valor()));
    }

    @Test
    void ordenFinalizada() {
        var mensaje = unicoMensaje(() -> adaptador.ordenFinalizada(id, TIPO));

        assertThat(mensaje).isEqualTo(
                "evento=orden_finalizada orden=%s tipo=PRINCIPAL resultado=ok pod=pod-7".formatted(id.valor()));
    }

}
