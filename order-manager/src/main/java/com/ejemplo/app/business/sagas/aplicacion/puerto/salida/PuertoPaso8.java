package com.ejemplo.app.business.sagas.aplicacion.puerto.salida;

import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ComandoPasoPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ResultadoPasoPrincipal;

/** Servicio del PASO8, el último de la saga principal (síncrono, ya sin compensación posible: es posterior al punto de no retorno). Ante fallo, el adaptador lanza ExcepcionServicioExterno. */
public interface PuertoPaso8 {
    ResultadoPasoPrincipal.ResultadoPaso8 ejecutar(ComandoPasoPrincipal.EjecutarPaso8 cmd);
}
