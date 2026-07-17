package com.ejemplo.app.business.sagas.aplicacion.puerto.salida;

import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.ComandoPasoSecundaria1;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria1.ResultadoPasoSecundaria1;

/**
 * Servicio de la saga secundaria 1: dos métodos REST distintos del mismo
 * servicio, llamados en secuencia (INICIO y CONFIRMACION). Ante fallo, el
 * adaptador lanza ExcepcionServicioExterno.
 */
public interface PuertoSagaSecundaria1 {

    ResultadoPasoSecundaria1.Iniciada iniciar(ComandoPasoSecundaria1.Iniciar cmd);

    ResultadoPasoSecundaria1.Confirmada confirmar(ComandoPasoSecundaria1.Confirmar cmd);
}
