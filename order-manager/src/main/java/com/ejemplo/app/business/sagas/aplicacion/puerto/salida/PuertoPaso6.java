package com.ejemplo.app.business.sagas.aplicacion.puerto.salida;

import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ComandoPasoPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ResultadoPasoPrincipal;

public interface PuertoPaso6 {
    ResultadoPasoPrincipal.ResultadoPaso6 ejecutar(ComandoPasoPrincipal.EjecutarPaso6 cmd);
}
