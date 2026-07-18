package com.ejemplo.app.business.sagas.aplicacion.puerto.salida;

import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatosNegocio;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ComandoPasoPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ResultadoPasoPrincipal;

public interface PuertoPaso7 {
    ResultadoPasoPrincipal.ResultadoPaso7 ejecutar(ComandoPasoPrincipal.EjecutarPaso7 cmd, DatosNegocio datos);
}
