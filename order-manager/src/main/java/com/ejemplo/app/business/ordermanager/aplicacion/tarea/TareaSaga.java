package com.ejemplo.app.business.ordermanager.aplicacion.tarea;

import com.ejemplo.app.business.ordermanager.dominio.comun.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.comun.PasoSaga;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.DatoNegocio2;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.DatoNegocio3;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria2.RefRespuesta;

/**
 * Una tarea = el contenido de una Orden del GestorOrdenes.
 * "Cada orden es continuar una saga": procesar una tarea significa cargar la
 * saga y avanzarla todo lo que se pueda ahora mismo.
 *
 * Propiedad clave: TODAS las tareas se encolan DENTRO de la transacción que
 * muta la saga (PuertoColaTareas se une a ella). Saga y tarea se commitean
 * juntas: no hay hueco de "commit sin despacho" y no hace falta proceso de
 * recuperación aparte — el lease del GestorOrdenes ya reentrega lo que se caiga.
 */
public sealed interface TareaSaga {

    SagaId sagaId();

    /** Crear (o retomar tras caída) la saga principal y avanzarla. */
    record IniciarTramitacion(SagaId sagaId, ExternalId externalId,
                              DatoNegocio3 datos, DatoNegocio2 datoNegocio2) implements TareaSaga {}

    /**
     * Arrancar (o retomar) una saga secundaria recién creada al completarse la
     * principal. Lleva el tipo para que el manejador rutee al servicio (y al
     * repositorio) de la saga correcta.
     */
    record ArrancarSaga(SagaId sagaId, TipoSaga tipo) implements TareaSaga {}

    /** Reintento con backoff: se encola con ejecutarDesde = ahora + espera. */
    record Reintentar(TipoSaga tipo, SagaId sagaId, PasoSaga paso, int intentoNum) implements TareaSaga {}

    /** Vigilante de la respuesta de la saga secundaria 2 (24h): se encola al solicitar. */
    record TimeoutSagaSecundaria2(SagaId sagaId) implements TareaSaga {}

    /** Respuesta OK de la saga secundaria 2: el consumer de Kafka la encola y hace ack. */
    record ResultadoSagaSecundaria2Ok(SagaId sagaId, RefRespuesta ref, String mensajeId) implements TareaSaga {}

    /** Respuesta de error de la saga secundaria 2. */
    record ResultadoSagaSecundaria2Error(SagaId sagaId, String codigo, String detalle,
                                         boolean reintentable, String mensajeId) implements TareaSaga {}
}
