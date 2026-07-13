package com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida;

import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.ComandoPasoPrincipal;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.ResultadoPasoPrincipal;

public interface PuertoPaso5 {
    ResultadoPasoPrincipal.ResultadoPaso5 ejecutar(ComandoPasoPrincipal.EjecutarPaso5 cmd);
}
