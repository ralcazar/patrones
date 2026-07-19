package com.ejemplo.app.business.sagas.aplicacion.puerto.salida;

import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatosNegocio;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ComandoPasoPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ResultadoPasoPrincipal;

/** Servicio del PASO7, el punto de no retorno de la saga (síncrono, sin compensación). Ante fallo, el adaptador lanza ExcepcionServicioExterno. */
public interface PuertoPaso7 {
    ResultadoPasoPrincipal.ResultadoPaso7 ejecutar(ComandoPasoPrincipal.EjecutarPaso7 cmd, DatosNegocio datos);
}
