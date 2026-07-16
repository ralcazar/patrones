package com.ejemplo.app.infraestructure.ordermanager.saga;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoSagasTicketPendiente.SagaTicketPendiente;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoTicketsSoporte;

/**
 * "Abrir un ticket" es escribir un cierto texto en el log (una herramienta
 * externa lo recoge y crea el ticket real): por eso no se devuelve ningún id.
 * Un único mensaje cubre todas las sagas del barrido.
 */
@Component
public class AdaptadorTicketsLog implements PuertoTicketsSoporte {

    private static final Logger log = LoggerFactory.getLogger(AdaptadorTicketsLog.class);

    @Override
    public void abrir(List<SagaTicketPendiente> sagas) {
        String detalle = sagas.stream()
                .map(s -> "%s %s (externalId %s): sigue reintentando cada 180 min tras %d intentos".formatted(
                        s.tipo(), s.sagaId().valor(), s.externalId().valor(), s.intentos()))
                .collect(Collectors.joining("; "));
        log.error("TICKET-SOPORTE-ORDERMANAGER: {} sagas requieren atención: {}", sagas.size(), detalle);
    }
}
