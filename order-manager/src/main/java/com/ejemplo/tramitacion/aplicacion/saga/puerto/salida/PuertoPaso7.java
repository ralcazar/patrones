package com.ejemplo.tramitacion.aplicacion.saga.puerto.salida;

import com.ejemplo.tramitacion.dominio.saga.general.ComandoPaso;
import com.ejemplo.tramitacion.dominio.saga.general.ResultadoPaso;

public interface PuertoPaso7 {
    ResultadoPaso.ResultadoPaso7 ejecutar(ComandoPaso.EjecutarPaso7 cmd);
}
