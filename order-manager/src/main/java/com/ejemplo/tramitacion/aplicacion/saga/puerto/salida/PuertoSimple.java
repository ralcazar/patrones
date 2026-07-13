package com.ejemplo.tramitacion.aplicacion.saga.puerto.salida;

import com.ejemplo.tramitacion.dominio.saga.general.ComandoPaso;
import com.ejemplo.tramitacion.dominio.saga.general.ResultadoPaso;

public interface PuertoSimple {
    ResultadoPaso.ResultadoSimple ejecutar(ComandoPaso.EjecutarSimple cmd);
}
