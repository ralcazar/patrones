package com.ejemplo.tramitacion.aplicacion.saga.puerto.salida;

import com.ejemplo.tramitacion.dominio.saga.general.ComandoPaso;
import com.ejemplo.tramitacion.dominio.saga.general.ResultadoPaso;

public interface PuertoPaso3 {
    ResultadoPaso.ResultadoPaso3 ejecutar(ComandoPaso.EjecutarPaso3 cmd);
}
