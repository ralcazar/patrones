package com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida;

import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.ComandoPasoPrincipal;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.ResultadoPasoPrincipal;

public interface PuertoPaso3 {
    ResultadoPasoPrincipal.ResultadoPaso3 ejecutar(ComandoPasoPrincipal.EjecutarPaso3 cmd);
}
