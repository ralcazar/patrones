package com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida;

import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.ComandoPasoPrincipal;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.ResultadoPasoPrincipal;

public interface PuertoPaso7 {
    ResultadoPasoPrincipal.ResultadoPaso7 ejecutar(ComandoPasoPrincipal.EjecutarPaso7 cmd);
}
