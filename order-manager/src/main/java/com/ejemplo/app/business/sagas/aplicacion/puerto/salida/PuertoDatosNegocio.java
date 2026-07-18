package com.ejemplo.app.business.sagas.aplicacion.puerto.salida;

import java.util.List;

import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatoNegocio1;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatoNegocio2;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DatoNegocio3;
import com.ejemplo.app.business.sagas.dominio.datosnegocio.DocumentoNegocio;

/** Servicio externo "DatosDeNegocio". Sin adaptador HTTP real todavía: solo la interfaz. */
public interface PuertoDatosNegocio {
    RespuestaDatosNegocio obtener(ExternalId externalId);

    record RespuestaDatosNegocio(DatoNegocio1 datoNegocio1, DatoNegocio2 datoNegocio2, DatoNegocio3 datoNegocio3,
            List<DocumentoNegocio> documentos) {}
}
