package com.ejemplo.app.business.sagas.aplicacion.servicio.sagasecundaria2;

import java.time.Instant;

import jakarta.transaction.Transactional;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.sagas.aplicacion.puerto.entrada.CasoUsoRegistrarRespuestaSecundaria2;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenRoot;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.RefRespuesta;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.SagaSecundaria2;

/**
 * Aplica directamente la respuesta diferida de la saga secundaria 2 que trae
 * el consumer de Kafka: una única transacción. El agregado se carga, muta y
 * guarda aquí mismo; el cierre operativo final de la orden lo deja al
 * servicio de la saga (ver ServicioSagaSecundaria2), que la recoge en su
 * siguiente pasada.
 *
 * <p>La idempotencia de este caso de uso descansa en que el evento solo trae
 * éxito y {@code respuestaRecibida} es una transición a estado absorbente
 * (TERMINADA): un duplicado reentregado por la mensajería (at-least-once)
 * llega a una orden ya terminada o purgada y el guard de abajo lo ignora. Si
 * el contrato añadiera un caso de error, NO volver la saga a INICIAL desde
 * el consumer (crearía un ciclo en la FSM INICIAL → ESPERANDO_RESPUESTA →
 * INICIAL y los duplicados reentregados dispararían solicitudes fantasma al
 * destino; exigiría volver al patrón idempotent consumer con deduplicación
 * por mensajeId, o correlación por intento): dejarla en ESPERANDO_RESPUESTA
 * y que lo resuelva la conciliación, que es idempotente.
 */
@Service
public class ServicioRegistrarRespuestaSecundaria2 implements CasoUsoRegistrarRespuestaSecundaria2 {

    private final RepositorioOrden repo;

    public ServicioRegistrarRespuestaSecundaria2(RepositorioOrden repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public void respuestaOk(OrdenId sagaId, RefRespuesta ref) {
        OrdenRoot orden;
        try {
            orden = repo.cargar(sagaId);
        } catch (IllegalArgumentException yaPurgada) {
            // Duplicado tan tardío que la limpieza de datos ya borró la orden:
            // sin esto, el reintento indefinido del listener la convertiría
            // en poison pill.
            return;
        }
        if (!orden.estaViva() || orden.proceso().terminada()) {
            return;
        }
        var saga = (SagaSecundaria2) orden.proceso();
        var ahora = Instant.now();
        orden.reemplazarProceso(saga.respuestaRecibida(ref), ahora);
        orden.despertar(ahora);
        repo.guardar(orden);
    }
}
