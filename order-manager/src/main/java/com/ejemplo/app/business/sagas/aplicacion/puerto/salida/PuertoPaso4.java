package com.ejemplo.app.business.sagas.aplicacion.puerto.salida;

import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ComandoPasoPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ResultadoPasoPrincipal;

public interface PuertoPaso4 {
    ResultadoPasoPrincipal.ResultadoPaso4 ejecutar(ComandoPasoPrincipal.EjecutarPaso4 cmd);
}
