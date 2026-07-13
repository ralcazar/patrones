package com.ejemplo.tramitacion.aplicacion.saga.puerto.salida;

import com.ejemplo.tramitacion.dominio.saga.general.ComandoPaso;
import com.ejemplo.tramitacion.dominio.saga.general.ResultadoPaso;

public interface PuertoPaso6 {
    ResultadoPaso.ResultadoPaso6 ejecutar(ComandoPaso.EjecutarPaso6 cmd);
}
