package com.ejemplo.app.carga.mocks;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.sagas.aplicacion.puerto.entrada.CasoUsoRegistrarRespuestaSecundaria2;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoSagaSecundaria2;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.ComandoPasoSecundaria2;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.RefRespuesta;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.SagaSecundaria2;
import com.ejemplo.app.carga.ContextoPod;

/**
 * Mock de {@link PuertoSagaSecundaria2}: a diferencia de los demás mocks REST
 * (que fallan/tardan de forma síncrona), este simula el patrón real del
 * puerto: la solicitud vuelve al instante y la "respuesta" del servicio
 * destino llega diferida, con SU PROPIO scheduler (no el
 * {@code @Scheduled} de Spring de producción), invocando directamente
 * {@link CasoUsoRegistrarRespuestaSecundaria2} — igual que hace
 * {@code ConsumidorRespuestaSecundaria2} en producción al recibir el evento
 * Kafka real, solo que aquí no hay broker de por medio.
 *
 * <p>Parámetros del bloque {@code kafka:} del escenario: {@code retraso-ms}
 * (min-max, uniforme) y {@code tasa-perdida} (probabilidad de que la
 * respuesta simulada nunca llegue, forzando el camino de conciliación/ticket).
 *
 * <p>Si la respuesta SÍ llega, decide éxito vs error de negocio reutilizando
 * {@code rest.tasa-fallo} (o su override {@code por-puerto.PuertoSagaSecundaria2})
 * en vez de añadir una clave nueva al esquema del escenario: un único knob de
 * "probabilidad de fallo" ya cubre este caso sin ampliar innecesariamente el
 * esquema (decisión documentada en el informe de la fase 2).
 *
 * <p>Como este evento nace ya en este adaptador de entrada simulado (no hay
 * broker real, así que {@code ConsumidorRespuestaSecundaria2} nunca se
 * ejecuta en el perfil de carga), se loguea aquí mismo
 * {@code evento=respuesta_secundaria2_registrada} con el mismo formato que
 * usa esa clase en producción, para que el analizador de la fase 3 no tenga
 * que distinguir el origen.
 */
@Component
public class SimuladorRespuestaSecundaria2 implements PuertoSagaSecundaria2 {

    private static final Logger log = LoggerFactory.getLogger(SimuladorRespuestaSecundaria2.class);
    private static final String NOMBRE_PUERTO = "PuertoSagaSecundaria2";

    private final ContextoPod contexto;
    private final CasoUsoRegistrarRespuestaSecundaria2 registro;
    private final String pod;
    private final ScheduledExecutorService scheduler;

    public SimuladorRespuestaSecundaria2(ContextoPod contexto, CasoUsoRegistrarRespuestaSecundaria2 registro,
            @Value("${ordermanager.pod:local}") String pod) {
        this.contexto = contexto;
        this.registro = registro;
        this.pod = pod;
        ThreadFactory hilosDaemon = runnable -> {
            var hilo = new Thread(runnable, "simulador-secundaria2-pod-" + contexto.indicePod());
            hilo.setDaemon(true);
            return hilo;
        };
        this.scheduler = Executors.newSingleThreadScheduledExecutor(hilosDaemon);
    }

    @Override
    public void solicitar(OrdenId sagaId, ComandoPasoSecundaria2.Solicitar cmd) {
        var kafka = contexto.escenario().kafka();
        var random = contexto.random();

        var rango = kafka.retrasoMs();
        long retraso = rango.min() >= rango.max()
                ? rango.min()
                : rango.min() + (long) (random.nextDouble() * (rango.max() - rango.min()));
        boolean sePierde = random.nextDouble() < kafka.tasaPerdida();
        boolean esError = random.nextDouble() < contexto.escenario().rest().tasaFalloPara(NOMBRE_PUERTO);
        String mensajeId = "mock-mensaje-" + Long.toHexString(random.nextLong());

        if (sePierde) {
            return; // Respuesta que nunca llega: fuerza el camino de conciliación/ticket.
        }
        scheduler.schedule(() -> emitirRespuesta(sagaId, esError, mensajeId), retraso, TimeUnit.MILLISECONDS);
    }

    private void emitirRespuesta(OrdenId sagaId, boolean esError, String mensajeId) {
        try {
            if (esError) {
                registro.respuestaError(sagaId, "ERROR_NEGOCIO_SIMULADO",
                        "Fallo de negocio simulado por " + NOMBRE_PUERTO, false, mensajeId);
            } else {
                registro.respuestaOk(sagaId, new RefRespuesta("mock-respuesta-" + mensajeId), mensajeId);
            }
            log.info("evento=respuesta_secundaria2_registrada orden={} tipo={} exito={} mensaje_id={} pod={}",
                    sagaId.valor(), SagaSecundaria2.TIPO.valor(), !esError, mensajeId, pod);
        } catch (RuntimeException e) {
            // La orden pudo ya no existir/estar viva (drenaje al final de la prueba,
            // orden cancelada, etc.): no derribar el hilo del scheduler por eso.
            log.warn("evento=respuesta_secundaria2_fallo_al_registrar orden={} mensaje_id={} pod={} error={}",
                    sagaId.valor(), mensajeId, pod, e.toString());
        }
    }

    @PreDestroy
    void detener() {
        scheduler.shutdownNow();
    }
}
