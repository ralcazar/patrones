package com.ejemplo.app.infraestructure.ordermanager.eventos;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoOrdenesTicketPendiente.OrdenTicketPendiente;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoTicketsSoporte;

/**
 * "Abrir un ticket" es escribir un cierto texto en el log (una herramienta
 * externa lo recoge y crea el ticket real): por eso no se devuelve ningún id.
 * Un único mensaje cubre todas las órdenes del barrido.
 */
@Component
public class AdaptadorTicketsLog implements PuertoTicketsSoporte {

    private static final Logger log = LoggerFactory.getLogger(AdaptadorTicketsLog.class);

    @Override
    public void abrir(List<OrdenTicketPendiente> ordenes) {
        String detalle = ordenes.stream()
                .map(o -> "%s %s (externalId %s): sigue reintentando cada 180 min tras %d intentos - último error: %s".formatted(
                        o.tipo(), o.ordenId().valor(), o.externalId().valor(), o.intentos(), errorDe(o)))
                .collect(Collectors.joining("; "));
        log.error("TICKET-SOPORTE-ORDERMANAGER: {} órdenes requieren atención: {}", ordenes.size(), detalle);
    }

    private static String errorDe(OrdenTicketPendiente o) {
        var error = o.ultimoError();
        return error == null ? "sin registrar" : "%s: %s".formatted(error.tipo(), error.mensaje());
    }
}
