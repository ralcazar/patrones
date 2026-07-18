package com.ejemplo.app.carga;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.sagas.aplicacion.puerto.entrada.CasoUsoIniciarTramitacion;
import com.ejemplo.app.business.sagas.aplicacion.puerto.entrada.CasoUsoIniciarTramitacion.ComandoIniciarTramitacion;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.SagaPrincipal;

/**
 * Inyector de tramitaciones del harness: desde el contexto del pod 0, invoca
 * {@link CasoUsoIniciarTramitacion#iniciar} al ritmo configurado en el
 * escenario, con payloads sintéticos ({@link ExternalId} generado con el
 * {@link java.util.Random} sembrado del pod 0), hasta agotar el total o la
 * duración de inyección (lo primero que ocurra).
 *
 * <p>Es, en sí mismo, un adaptador de entrada del harness (el equivalente a
 * {@code ControladorTramitaciones} pero sin HTTP): invoca el caso de uso
 * directamente, nunca un puerto de salida (regla de arquitectura entrada -&gt;
 * aplicación -&gt; salida del CLAUDE.md), y por eso es él quien loguea
 * {@code evento=tramitacion_creada} con el mismo formato que el controlador
 * REST real — ese evento nace en el adaptador de entrada que crea la
 * tramitación, y aquí ese adaptador es este inyector, no
 * {@code ControladorTramitaciones} (que nunca se invoca en el harness: no
 * hay servidor web, {@code spring.main.web-application-type=none}).
 */
final class InyectorTramitaciones {

    private static final Logger log = LoggerFactory.getLogger(InyectorTramitaciones.class);

    private final CasoUsoIniciarTramitacion casoUso;
    private final ContextoPod contextoPod0;
    private final ScheduledExecutorService scheduler;
    private final AtomicInteger inyectadas = new AtomicInteger();
    private final String pod0;

    InyectorTramitaciones(CasoUsoIniciarTramitacion casoUso, ContextoPod contextoPod0,
            ScheduledExecutorService scheduler) {
        this.casoUso = casoUso;
        this.contextoPod0 = contextoPod0;
        this.scheduler = scheduler;
        this.pod0 = String.valueOf(contextoPod0.indicePod());
    }

    /** Inyecta hasta agotar el total o {@code duracion}; bloquea el hilo llamante hasta entonces. */
    void ejecutarHastaAgotar() {
        var inyeccion = contextoPod0.escenario().inyeccion();
        int total = inyeccion.tramitaciones();
        long periodoMs = Math.max(1, 1000L / Math.max(1, inyeccion.ritmoPorSegundo()));
        Instant limite = Instant.now().plus(contextoPod0.escenario().duracion());

        Object cerrojo = new Object();
        ScheduledFuture<?> tarea = scheduler.scheduleAtFixedRate(() -> {
            if (inyectadas.get() >= total || Instant.now().isAfter(limite)) {
                synchronized (cerrojo) {
                    cerrojo.notifyAll();
                }
                return;
            }
            try {
                inyectarUna();
            } catch (RuntimeException e) {
                log.warn("evento=inyeccion_fallo pod={} error={}", pod0, e.toString());
            }
        }, 0, periodoMs, TimeUnit.MILLISECONDS);

        synchronized (cerrojo) {
            while (inyectadas.get() < total && !Instant.now().isAfter(limite)) {
                try {
                    cerrojo.wait(periodoMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        tarea.cancel(false);
        log.info("evento=inyeccion_finalizada pod={} inyectadas={} objetivo={}", pod0, inyectadas.get(), total);
    }

    private void inyectarUna() {
        var random = contextoPod0.random();
        var externalId = new ExternalId(new UUID(random.nextLong(), random.nextLong()));
        var ordenId = casoUso.iniciar(new ComandoIniciarTramitacion(externalId));
        inyectadas.incrementAndGet();
        log.info("evento=tramitacion_creada orden={} tipo={} external_id={} pod={}",
                ordenId.valor(), SagaPrincipal.TIPO.valor(), externalId.valor(), pod0);
    }

    int inyectadas() {
        return inyectadas.get();
    }
}
