package com.ejemplo.app.business.sagas.aplicacion.puerto.salida;

import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ComandoPasoPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ResultadoPasoPrincipal;

public interface PuertoPaso8 {
    ResultadoPasoPrincipal.ResultadoPaso8 ejecutar(ComandoPasoPrincipal.EjecutarPaso8 cmd);
}
