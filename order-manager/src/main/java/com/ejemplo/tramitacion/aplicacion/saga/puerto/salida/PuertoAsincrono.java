package com.ejemplo.tramitacion.aplicacion.saga.puerto.salida;

import com.ejemplo.tramitacion.dominio.saga.general.ComandoPaso;
import com.ejemplo.tramitacion.dominio.saga.general.SagaId;

/**
 * Servicio del paso ASINCRONO: el adaptador publica el comando en Kafka
 * (idealmente vía outbox) y retorna. La respuesta llega por el topic de
 * respuesta y el consumer invoca CasoUsoProcesarResultadoSucesora.
 * El sagaId viaja en el comando como clave de correlación.
 */
public interface PuertoAsincrono {
    void ejecutar(SagaId sagaId, ComandoPaso.EjecutarAsincrono cmd);
}
