package com.ejemplo.app.carga;

import java.util.Random;

/**
 * Contexto de ejecución compartido por todos los beans de un pod (mocks,
 * simulador de la secundaria 2, inyector): el escenario completo, el índice
 * de este pod y SU generador aleatorio.
 *
 * <p>Un único {@link Random} por pod, compartido por todos los mocks del
 * mismo pod (en vez de que cada mock cree el suyo con la misma semilla): así
 * las decisiones de latencia/fallo de puertos distintos no quedan
 * correlacionadas (dos mocks con la misma semilla pero invocados en momentos
 * distintos producirían secuencias en lockstep, no independientes). La
 * semilla del pod es {@code escenario.semilla() + indicePod} (ver README de
 * escenarios): fija la distribución estadística, no el entrelazado real de
 * hilos.
 *
 * <p>Se registra como bean singleton en el contexto Spring de cada pod desde
 * {@code LanzadorPruebaCarga} (antes de {@code refresh()}, vía
 * {@code ApplicationContextInitializer}), no vía {@code @ConfigurationProperties}:
 * ya tenemos el {@link EscenarioCarga} completo parseado en memoria, así que
 * inyectarlo tal cual es más simple que volver a aplanarlo en propiedades
 * Spring sueltas.
 */
public record ContextoPod(EscenarioCarga escenario, int indicePod, Random random) {

    public static ContextoPod de(EscenarioCarga escenario, int indicePod) {
        return new ContextoPod(escenario, indicePod, new Random(escenario.semilla() + indicePod));
    }
}
