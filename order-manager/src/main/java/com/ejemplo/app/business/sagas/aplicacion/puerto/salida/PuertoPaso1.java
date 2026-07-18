package com.ejemplo.app.business.sagas.aplicacion.puerto.salida;

import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatosNegocio;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ComandoPasoPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ResultadoPasoPrincipal;

/** Servicio del PASO1 (síncrono). Ante fallo, el adaptador lanza ExcepcionServicioExterno. */
public interface PuertoPaso1 {
    ResultadoPasoPrincipal.ResultadoPaso1 ejecutar(ComandoPasoPrincipal.EjecutarPaso1 cmd, DatosNegocio datos);
    void compensar(ComandoPasoPrincipal.CompensarPaso1 cmd);
}
