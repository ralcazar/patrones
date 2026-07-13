package com.ejemplo.tramitacion.aplicacion.saga.puerto.salida;

import com.ejemplo.tramitacion.dominio.saga.general.ComandoPaso;
import com.ejemplo.tramitacion.dominio.saga.general.ResultadoPaso;

public interface PuertoPaso8 {
    ResultadoPaso.ResultadoPaso8 ejecutar(ComandoPaso.EjecutarPaso8 cmd);
}
