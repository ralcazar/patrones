package com.ejemplo.app.infraestructure.ordermanager.eventos;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoOrdenesTicketPendiente.OrdenTicketPendiente;
import com.ejemplo.app.business.ordermanager.dominio.DetalleError;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.TipoOrden;

/** "Abrir un ticket" es solo escribir un mensaje en el log: se verifica que no rompe con lista vacía ni con elementos. */
class AdaptadorTicketsLogTest {

    private final AdaptadorTicketsLog adaptador = new AdaptadorTicketsLog();

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
}
