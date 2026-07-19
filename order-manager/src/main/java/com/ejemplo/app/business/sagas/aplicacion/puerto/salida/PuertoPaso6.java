package com.ejemplo.app.business.sagas.aplicacion.puerto.salida;

import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ComandoPasoPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ResultadoPasoPrincipal;

/** Servicio del PASO6 (síncrono, sin compensación: ver SagaPrincipal, solo PASO1 y PASO2 la tienen). Ante fallo, el adaptador lanza ExcepcionServicioExterno. */
public interface PuertoPaso6 {
    ResultadoPasoPrincipal.ResultadoPaso6 ejecutar(ComandoPasoPrincipal.EjecutarPaso6 cmd);
}
