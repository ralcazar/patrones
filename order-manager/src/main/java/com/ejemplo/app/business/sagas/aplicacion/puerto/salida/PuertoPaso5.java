package com.ejemplo.app.business.sagas.aplicacion.puerto.salida;

import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ComandoPasoPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ResultadoPasoPrincipal;

/** Servicio del PASO5 (síncrono, sin compensación: ver SagaPrincipal, solo PASO1 y PASO2 la tienen). Ante fallo, el adaptador lanza ExcepcionServicioExterno. */
public interface PuertoPaso5 {
    ResultadoPasoPrincipal.ResultadoPaso5 ejecutar(ComandoPasoPrincipal.EjecutarPaso5 cmd);
}
