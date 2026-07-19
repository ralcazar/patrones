package com.ejemplo.app.business.sagas.aplicacion.puerto.salida;

import java.util.List;

import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatosNegocio;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DocumentoNegocio;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ComandoPasoPrincipal;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.ResultadoPasoPrincipal;

/** Servicio del PASO2 (síncrono). Ante fallo, el adaptador lanza ExcepcionServicioExterno. */
public interface PuertoPaso2 {
    ResultadoPasoPrincipal.ResultadoPaso2 ejecutar(ComandoPasoPrincipal.EjecutarPaso2 cmd, DatosNegocio datos,
            List<DocumentoNegocio> documentos);
    void compensar(ComandoPasoPrincipal.CompensarPaso2 cmd);
}
