package com.ejemplo.app.business.sagas.aplicacion.puerto.salida;

import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ComandoPasoPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ResultadoPasoPrincipal;

public interface PuertoPaso3 {
    ResultadoPasoPrincipal.ResultadoPaso3 ejecutar(ComandoPasoPrincipal.EjecutarPaso3 cmd);
}
