package com.ejemplo.app.business.sagas.aplicacion.puerto.salida;

import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ComandoPasoPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ResultadoPasoPrincipal;

public interface PuertoPaso2 {
    ResultadoPasoPrincipal.ResultadoPaso2 ejecutar(ComandoPasoPrincipal.EjecutarPaso2 cmd);
    void compensar(ComandoPasoPrincipal.CompensarPaso2 cmd);
}
