package com.ejemplo.tramitacion.aplicacion.saga.puerto.salida;

import com.ejemplo.tramitacion.dominio.saga.general.ComandoPaso;
import com.ejemplo.tramitacion.dominio.saga.general.ResultadoPaso;

public interface PuertoPaso5 {
    ResultadoPaso.ResultadoPaso5 ejecutar(ComandoPaso.EjecutarPaso5 cmd);
}
