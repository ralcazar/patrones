package com.ejemplo.app.infraestructure.ordermanager.eventos;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoOrdenesTicketPendiente.OrdenTicketPendiente;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoTicketsSoporte;

/**
 * "Abrir un ticket" es escribir un cierto texto en el log (una herramienta
 * externa lo recoge y crea el ticket real): por eso no se devuelve ningún id.
 * Este evento nace ya en infraestructura (no pasa por
 * {@code PuertoObservadorEjecucion}: la decisión de abrir el ticket es de
 * aplicación, pero el propio texto del ticket ya vivía aquí antes de este
 * catálogo), así que se loguea con el mismo formato
 * {@code evento=<nombre> orden=<id> tipo=<tipo> ... pod=<valor>}: una línea
 * por orden del barrido, para que el analizador pueda contarlas una a una.
 */
@Component
public class AdaptadorTicketsLog implements PuertoTicketsSoporte {

    private static final Logger log = LoggerFactory.getLogger(AdaptadorTicketsLog.class);

    private final String pod;

    public AdaptadorTicketsLog(@Value("${ordermanager.pod:local}") String pod) {
        this.pod = pod;
    }

    @Override
    public void abrir(List<OrdenTicketPendiente> ordenes) {
        for (var orden : ordenes) {
            log.error("evento=ticket_abierto orden={} tipo={} external_id={} intentos={} "
                            + "error_tipo={} error_mensaje={} pod={}",
                    orden.ordenId().valor(), orden.tipo().valor(), orden.externalId().valor(), orden.intentos(),
                    errorTipo(orden), errorMensaje(orden), pod);
        }
    }

    private static String errorTipo(OrdenTicketPendiente o) {
        var error = o.ultimoError();
        return error == null ? "sin-registrar" : error.tipo();
    }

    private static String errorMensaje(OrdenTicketPendiente o) {
        var error = o.ultimoError();
        return error == null ? "sin-registrar" : error.mensaje();
    }
}
