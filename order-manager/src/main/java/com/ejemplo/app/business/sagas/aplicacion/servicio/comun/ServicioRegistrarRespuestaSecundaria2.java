package com.ejemplo.app.business.sagas.aplicacion.servicio.comun;

import java.time.Instant;

import jakarta.transaction.Transactional;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.sagas.aplicacion.puerto.entrada.CasoUsoRegistrarRespuestaSecundaria2;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoMensajesProcesados;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.dominio.MensajeId;
import com.ejemplo.app.business.ordermanager.dominio.PoliticaReintentos;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.RefRespuesta;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.SagaSecundaria2;

/**
 * Aplica directamente la respuesta diferida de la saga secundaria 2 que trae
 * el consumer de Kafka: una única transacción, deduplicada por mensajeId (la
 * mensajería entrega at-least-once). El agregado se carga, muta y guarda
 * aquí mismo; el cierre operativo final de la orden lo deja al servicio de la saga
 * (ver ServicioSagaSecundaria2), que la recoge en su siguiente pasada.
 */
@Service
public class ServicioRegistrarRespuestaSecundaria2 implements CasoUsoRegistrarRespuestaSecundaria2 {

    private final RepositorioOrden repo;
    private final PuertoMensajesProcesados dedup;
    private final PoliticaReintentos politica;

    public ServicioRegistrarRespuestaSecundaria2(RepositorioOrden repo,
            PuertoMensajesProcesados dedup, PoliticaReintentos politica) {
        this.repo = repo;
        this.dedup = dedup;
        this.politica = politica;
    }

    @Override
    @Transactional
    public void respuestaOk(OrdenId sagaId, RefRespuesta ref, String mensajeId) {
        var msgId = MensajeId.externo(mensajeId);
        if (dedup.yaProcesado(msgId)) {
            return;
        }
        dedup.registrar(msgId);
        var orden = repo.cargar(sagaId);
        var saga = (SagaSecundaria2) orden.proceso();
        saga.respuestaRecibida(ref);
        orden.despertar(Instant.now());
        repo.guardar(orden);
    }

    @Override
    @Transactional
    public void respuestaError(OrdenId sagaId, String codigo, String detalle,
                               boolean reintentable, String mensajeId) {
        var msgId = MensajeId.externo(mensajeId);
        if (dedup.yaProcesado(msgId)) {
            return;
        }
        dedup.registrar(msgId);
        var orden = repo.cargar(sagaId);
        var saga = (SagaSecundaria2) orden.proceso();
        saga.volverASolicitar();
        orden.programarReintento(politica, Instant.now());
        repo.guardar(orden);
    }
}
