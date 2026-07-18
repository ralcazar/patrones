package com.ejemplo.app.infraestructure.ordermanager.eventos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoObservadorEjecucion;
import com.ejemplo.app.business.ordermanager.dominio.DetalleError;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.TipoOrden;

/**
 * Log estructurado de una línea por evento, formato {@code clave=valor}:
 * {@code evento=<nombre> orden=<id> tipo=<tipo> ... pod=<valor>}. Pensado
 * para que un analizador determinista (fase 3 del plan de pruebas de carga)
 * lo agregue sin ambigüedad; el catálogo exacto de eventos vive en
 * {@code src/pruebaCarga/resources/escenarios/README.md}.
 */
@Component
public class AdaptadorObservadorLog implements PuertoObservadorEjecucion {

    private static final Logger log = LoggerFactory.getLogger(AdaptadorObservadorLog.class);

    private final String pod;

    public AdaptadorObservadorLog(@Value("${ordermanager.pod:local}") String pod) {
        this.pod = pod;
    }

    @Override
    public void reclamoGanado(OrdenId id, TipoOrden tipo) {
        log.info("evento=reclamo_ganado orden={} tipo={} pod={}", id.valor(), tipo.valor(), pod);
    }

    @Override
    public void reclamoPerdido(OrdenId id, TipoOrden tipo, MotivoReclamoPerdido motivo) {
        log.info("evento=reclamo_perdido orden={} tipo={} motivo={} pod={}",
                id.valor(), tipo.valor(), motivo, pod);
    }

    @Override
    public void colisionOptimista(OrdenId id, TipoOrden tipo, String operacion) {
        log.info("evento=colision_optimista orden={} tipo={} operacion={} pod={}",
                id.valor(), tipo.valor(), operacion, pod);
    }

    @Override
    public void pasoCompletado(OrdenId id, TipoOrden tipo, long duracionMs) {
        log.info("evento=paso_completado orden={} tipo={} duracion_ms={} pod={}",
                id.valor(), tipo.valor(), duracionMs, pod);
    }

    @Override
    public void pasoFallido(OrdenId id, TipoOrden tipo, int intento, DetalleError error) {
        log.info("evento=paso_fallido orden={} tipo={} intento={} error_tipo={} error_mensaje={} pod={}",
                id.valor(), tipo.valor(), intento, error.tipo(), error.mensaje(), pod);
    }

    @Override
    public void reintentoProgramado(OrdenId id, TipoOrden tipo, int intento, Duration espera) {
        log.info("evento=reintento_programado orden={} tipo={} intento={} espera_ms={} pod={}",
                id.valor(), tipo.valor(), intento, espera.toMillis(), pod);
    }

    @Override
    public void ordenAparcada(OrdenId id, TipoOrden tipo, Duration ventana) {
        log.info("evento=orden_aparcada orden={} tipo={} ventana_ms={} pod={}",
                id.valor(), tipo.valor(), ventana.toMillis(), pod);
    }

    @Override
    public void ordenFinalizada(OrdenId id, TipoOrden tipo) {
        log.info("evento=orden_finalizada orden={} tipo={} resultado=ok pod={}", id.valor(), tipo.valor(), pod);
    }

    @Override
    public void datosAntiguosPurgados(long ordenesEliminadas, long mensajesEliminados) {
        log.info("evento=purga_datos_antiguos ordenes_eliminadas={} mensajes_eliminados={} pod={}",
                ordenesEliminadas, mensajesEliminados, pod);
    }
}
