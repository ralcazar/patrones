package com.ejemplo.app.infraestructure.ordermanager.eventos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoOrdenesTicketPendiente.OrdenTicketPendiente;
import com.ejemplo.app.business.ordermanager.dominio.DetalleError;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.TipoOrden;
import com.ejemplo.app.testsoporte.CapturaLog;

/**
 * "Abrir un ticket" es solo escribir una línea de log por orden (formato
 * {@code evento=ticket_abierto orden=... tipo=... ... pod=...}, el mismo que
 * PuertoObservadorEjecucion aunque este evento no pase por ese puerto): se
 * verifica que no rompe con lista vacía ni con elementos, y el formato exacto
 * de la línea (con y sin error registrado).
 */
class AdaptadorTicketsLogTest {

    private final AdaptadorTicketsLog adaptador = new AdaptadorTicketsLog("pod-test");

    @Test
    void abrir_conListaVaciaNoRompe() {
        assertThatCode(() -> adaptador.abrir(List.of())).doesNotThrowAnyException();
    }

    @Test
    void abrir_conVariasOrdenesNoRompe() {
        var ordenes = List.of(
                new OrdenTicketPendiente(new TipoOrden("PRINCIPAL"), OrdenId.nuevo(),
                        ExternalId.de(UUID.randomUUID().toString()), 8,
                        new DetalleError("java.lang.RuntimeException", "boom")),
                new OrdenTicketPendiente(new TipoOrden("SECUNDARIA1"), OrdenId.nuevo(),
                        ExternalId.de(UUID.randomUUID().toString()), 10, null));

        assertThatCode(() -> adaptador.abrir(ordenes)).doesNotThrowAnyException();
    }

    @Test
    void abrir_logaUnaLineaPorOrdenConElFormatoDeEventoYPod() {
        var idConError = OrdenId.nuevo();
        var externalIdConError = ExternalId.de(UUID.randomUUID().toString());
        var idSinError = OrdenId.nuevo();
        var ordenes = List.of(
                new OrdenTicketPendiente(new TipoOrden("PRINCIPAL"), idConError, externalIdConError, 8,
                        new DetalleError("java.lang.RuntimeException", "boom")),
                new OrdenTicketPendiente(new TipoOrden("SECUNDARIA1"), idSinError,
                        ExternalId.de(UUID.randomUUID().toString()), 10, null));

        List<String> mensajes;
        try (var captura = new CapturaLog(AdaptadorTicketsLog.class)) {
            adaptador.abrir(ordenes);
            mensajes = captura.mensajes();
        }

        assertThat(mensajes).hasSize(2);
        assertThat(mensajes.get(0))
                .contains("evento=ticket_abierto", "orden=" + idConError.valor(), "tipo=PRINCIPAL",
                        "external_id=" + externalIdConError.valor(), "intentos=8",
                        "error_tipo=java.lang.RuntimeException", "error_mensaje=boom", "pod=pod-test");
        assertThat(mensajes.get(1))
                .contains("evento=ticket_abierto", "orden=" + idSinError.valor(), "tipo=SECUNDARIA1",
                        "intentos=10", "error_tipo=sin-registrar", "error_mensaje=sin-registrar", "pod=pod-test");
    }
}
