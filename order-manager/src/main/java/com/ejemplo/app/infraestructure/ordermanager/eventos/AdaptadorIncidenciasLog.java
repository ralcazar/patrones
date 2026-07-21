package com.ejemplo.app.infraestructure.ordermanager.eventos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoIncidencias;

/**
 * "Abrir una incidencia" es escribir un cierto texto en el log (una
 * herramienta externa lo recoge y crea la incidencia real): mismo estilo que
 * {@link AdaptadorTicketsLog}, con el formato
 * {@code evento=incidencia_abierta tarea=<tarea> causa=<causa> intentos=<n> pod=<valor>}.
 */
@Component
public class AdaptadorIncidenciasLog implements PuertoIncidencias {

    private static final Logger log = LoggerFactory.getLogger(AdaptadorIncidenciasLog.class);

    private final String pod;

    public AdaptadorIncidenciasLog(@Value("${ordermanager.pod:local}") String pod) {
        this.pod = pod;
    }

    @Override
    public void abrir(String tarea, String causa, int intentos) {
        log.error("evento=incidencia_abierta tarea={} causa={} intentos={} pod={}", tarea, causa, intentos, pod);
    }
}
