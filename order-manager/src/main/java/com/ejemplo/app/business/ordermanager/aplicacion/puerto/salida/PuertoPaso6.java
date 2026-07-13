package com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida;

import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.ComandoPasoPrincipal;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.ResultadoPasoPrincipal;

public interface PuertoPaso6 {
    ResultadoPasoPrincipal.ResultadoPaso6 ejecutar(ComandoPasoPrincipal.EjecutarPaso6 cmd);
}
