package com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida;

import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria2.ComandoPasoSecundaria2;

/**
 * Servicio de la saga secundaria 2: el adaptador hace la llamada REST de
 * solicitud y retorna sin resultado. El servicio destino responde a posteriori
 * (puede tardar) publicando un evento en el topic Kafka de respuesta, que
 * consume ConsumidorRespuestaSecundaria2 e invoca CasoUsoProcesarResultadoPaso.
 * El sagaId viaja en la solicitud como clave de correlación.
 */
public interface PuertoSagaSecundaria2 {

    void solicitar(SagaId sagaId, ComandoPasoSecundaria2.Solicitar cmd);
}
