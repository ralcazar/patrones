package com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida;

import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.ComandoPasoPrincipal;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.ResultadoPasoPrincipal;

public interface PuertoPaso2 {
    ResultadoPasoPrincipal.ResultadoPaso2 ejecutar(ComandoPasoPrincipal.EjecutarPaso2 cmd);
    void compensar(ComandoPasoPrincipal.CompensarPaso2 cmd);
}
