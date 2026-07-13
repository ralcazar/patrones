package com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida;

import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria3.ComandoPasoSecundaria3;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria3.ResultadoPasoSecundaria3;

/** Servicio de la saga secundaria 3: una llamada REST síncrona. */
public interface PuertoSagaSecundaria3 {

    ResultadoPasoSecundaria3.Ejecutada ejecutar(ComandoPasoSecundaria3.Ejecutar cmd);
}
