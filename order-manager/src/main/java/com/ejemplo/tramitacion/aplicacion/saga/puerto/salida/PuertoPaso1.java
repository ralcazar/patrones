package com.ejemplo.tramitacion.aplicacion.saga.puerto.salida;

import com.ejemplo.tramitacion.dominio.saga.general.ComandoPaso;
import com.ejemplo.tramitacion.dominio.saga.general.ResultadoPaso;

/** Servicio del PASO1 (síncrono). Ante fallo, el adaptador lanza ExcepcionServicioExterno. */
public interface PuertoPaso1 {
    ResultadoPaso.ResultadoPaso1 ejecutar(ComandoPaso.EjecutarPaso1 cmd);
    void compensar(ComandoPaso.CompensarPaso1 cmd);
}
