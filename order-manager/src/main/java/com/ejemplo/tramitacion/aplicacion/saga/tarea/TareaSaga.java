package com.ejemplo.tramitacion.aplicacion.saga.tarea;

import com.ejemplo.tramitacion.dominio.saga.paso1.DatoNegocio3;
import com.ejemplo.tramitacion.dominio.saga.paso7.DatoNegocio2;
import com.ejemplo.tramitacion.dominio.saga.general.DatoNegocio1Id;
import com.ejemplo.tramitacion.dominio.saga.general.Paso;
import com.ejemplo.tramitacion.dominio.saga.asincrono.RefAsincrono;
import com.ejemplo.tramitacion.dominio.saga.general.SagaId;
import com.ejemplo.tramitacion.dominio.saga.general.TipoSaga;

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
    record IniciarTramitacion(SagaId sagaId, DatoNegocio1Id datoNegocio1Id,
                              DatoNegocio3 datos, DatoNegocio2 datoNegocio2) implements TareaSaga {}

    /** Arrancar (o retomar) una saga recién creada al completarse otra. */
    record ArrancarSaga(SagaId sagaId) implements TareaSaga {}

    /** Reintento con backoff: se encola con ejecutarDesde = ahora + espera. */
    record Reintentar(TipoSaga tipo, SagaId sagaId, Paso paso, int intentoNum) implements TareaSaga {}

    /** Vigilante de la respuesta del paso ASINCRONO: se encola al publicar el comando. */
    record TimeoutAsincrono(SagaId sagaId) implements TareaSaga {}

    /** Respuesta OK del paso ASINCRONO: el consumer de Kafka la encola y hace ack. */
    record ResultadoAsincronoOk(SagaId sagaId, RefAsincrono ref, String mensajeId) implements TareaSaga {}

    /** Respuesta de error del paso ASINCRONO. */
    record ResultadoAsincronoError(SagaId sagaId, String codigo, String detalle,
                                   boolean reintentable, String mensajeId) implements TareaSaga {}
}
