package com.ejemplo.tramitacion.aplicacion.saga.puerto.salida;

import com.ejemplo.tramitacion.dominio.saga.general.EventoTramitacion;

public interface PuertoEventos {
    void publicar(EventoTramitacion evento);
}
