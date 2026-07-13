package com.ejemplo.tramitacion.aplicacion.saga.puerto.salida;

import com.ejemplo.tramitacion.dominio.saga.general.ComandoPaso;
import com.ejemplo.tramitacion.dominio.saga.general.ResultadoPaso;

public interface PuertoPaso2 {
    ResultadoPaso.ResultadoPaso2 ejecutar(ComandoPaso.EjecutarPaso2 cmd);
    void compensar(ComandoPaso.CompensarPaso2 cmd);
}
