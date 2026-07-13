package com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida;

import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.ComandoPasoPrincipal;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.ResultadoPasoPrincipal;

public interface PuertoPaso4 {
    ResultadoPasoPrincipal.ResultadoPaso4 ejecutar(ComandoPasoPrincipal.EjecutarPaso4 cmd);
}
