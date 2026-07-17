package com.ejemplo.app.business.sagas.aplicacion.puerto.salida;

import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ComandoPasoPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ResultadoPasoPrincipal;

public interface PuertoPaso5 {
    ResultadoPasoPrincipal.ResultadoPaso5 ejecutar(ComandoPasoPrincipal.EjecutarPaso5 cmd);
}
