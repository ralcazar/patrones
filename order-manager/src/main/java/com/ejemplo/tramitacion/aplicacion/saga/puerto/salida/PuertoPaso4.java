package com.ejemplo.tramitacion.aplicacion.saga.puerto.salida;

import com.ejemplo.tramitacion.dominio.saga.general.ComandoPaso;
import com.ejemplo.tramitacion.dominio.saga.general.ResultadoPaso;

public interface PuertoPaso4 {
    ResultadoPaso.ResultadoPaso4 ejecutar(ComandoPaso.EjecutarPaso4 cmd);
}
