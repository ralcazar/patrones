package com.ejemplo.app.carga.dedup;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoMensajesProcesados;
import com.ejemplo.app.business.ordermanager.dominio.MensajeId;

/**
 * {@link PuertoMensajesProcesados} del harness: NO tiene adaptador real en
 * {@code src/main} (gap preexistente ajeno a este plan, no se toca en esta
 * fase), así que hay que fabricar uno para el perfil de carga.
 *
 * <p>La deduplicación de la respuesta diferida de la secundaria 2 tiene que
 * funcionar ENTRE pods: {@code SimuladorRespuestaSecundaria2} programa su
 * respuesta en el pod que recibió la solicitud, pero puede entregarla en
 * cualquier pod... salvo que aquí cada pod es su propio
 * {@code ApplicationContext} (uno por JVM real en producción, N en la misma
 * JVM en el harness): un {@code Set} de instancia normal, con {@code @Bean}
 * por pod, NO serviría.
 *
 * <p>De las dos opciones que documenta el plan de pruebas de carga (fase 2):
 * <ul>
 *   <li>(a) un {@code Set}/mapa ESTÁTICO (compartido por classloader, ya que
 *       todos los pods están en la misma JVM) — la elegida aquí.</li>
 *   <li>(b) una tabla H2 propia del harness con adaptador JDBC.</li>
 * </ul>
 * Se elige (a) por ser más simple y no requerir ningún esquema adicional
 * (esta clase es {@code @Component}, así que cada pod tiene su PROPIA
 * instancia de bean, pero todas leen/escriben el mismo mapa estático).
 */
@Component
public class AdaptadorMensajesProcesadosMemoria implements PuertoMensajesProcesados {

    private static final Map<String, Instant> PROCESADOS = new ConcurrentHashMap<>();

    @Override
    public boolean yaProcesado(MensajeId msgId) {
        return PROCESADOS.containsKey(msgId.valor());
    }

    @Override
    public void registrar(MensajeId msgId) {
        PROCESADOS.put(msgId.valor(), Instant.now());
    }

    @Override
    public long purgarAnterioresA(Instant corte) {
        long antes = PROCESADOS.size();
        PROCESADOS.values().removeIf(instante -> instante.isBefore(corte));
        return antes - PROCESADOS.size();
    }
}
