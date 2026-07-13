package com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida;

import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.ComandoPasoPrincipal;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.ResultadoPasoPrincipal;

public interface PuertoPaso8 {
    ResultadoPasoPrincipal.ResultadoPaso8 ejecutar(ComandoPasoPrincipal.EjecutarPaso8 cmd);
}
