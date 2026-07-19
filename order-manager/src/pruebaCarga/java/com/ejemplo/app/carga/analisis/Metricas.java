package com.ejemplo.app.carga.analisis;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Métricas agregadas de una ejecución a partir de {@code pods.log} ya
 * parseado (throughput, duración de saga, reclamos/colisiones por pod,
 * reintentos, profundidad de cola) más la distribución final de estados
 * (SQL, ver {@link RepositorioAnalisisBbdd}).
 */
final class Metricas {

    record FilaThroughput(Instant minuto, long creadas, long finalizadas) {}

    record EstadisticasDuracion(long p50Ms, long p95Ms, long p99Ms, long maxMs, int muestras) {
        static final EstadisticasDuracion VACIA = new EstadisticasDuracion(0, 0, 0, 0, 0);
    }

    record EstadisticasPod(String pod, long ganados, long perdidos, long colisiones) {
        double pctColision() {
            long intentos = ganados + perdidos;
            return intentos == 0 ? 0.0 : (100.0 * colisiones / intentos);
        }
    }

    record ReintentosPorTipo(String tipo, long total) {}

    record PuntoCola(Instant instante, long profundidad) {}

    record ProfundidadCola(List<PuntoCola> muestrasPorMinuto, long maximo, Instant instanteMaximo,
            double promedioPonderado) {}

    private Metricas() {
    }

    static List<FilaThroughput> throughputPorMinuto(List<EventoLog> eventos) {
        TreeMap<Instant, long[]> porMinuto = new TreeMap<>(); // [creadas, finalizadas]
        for (var evento : eventos) {
            Instant minuto = evento.timestamp().truncatedTo(ChronoUnit.MINUTES);
            if (evento.evento().equals("tramitacion_creada")) {
                porMinuto.computeIfAbsent(minuto, k -> new long[2])[0]++;
            } else if (evento.evento().equals("orden_finalizada") && "PRINCIPAL".equals(evento.tipo())) {
                porMinuto.computeIfAbsent(minuto, k -> new long[2])[1]++;
            }
        }
        List<FilaThroughput> filas = new ArrayList<>();
        for (var entrada : porMinuto.entrySet()) {
            filas.add(new FilaThroughput(entrada.getKey(), entrada.getValue()[0], entrada.getValue()[1]));
        }
        return filas;
    }

    /** Duración de la saga PRINCIPAL: de {@code tramitacion_creada} a su {@code orden_finalizada}, por orden. */
    static Map<String, Long> duracionesSagaPrincipalMs(List<EventoLog> eventos) {
        Map<String, Instant> creadas = new LinkedHashMap<>();
        Map<String, Long> duraciones = new LinkedHashMap<>();
        for (var evento : eventos) {
            if (evento.evento().equals("tramitacion_creada")) {
                creadas.put(evento.orden(), evento.timestamp());
            } else if (evento.evento().equals("orden_finalizada") && "PRINCIPAL".equals(evento.tipo())) {
                var inicio = creadas.get(evento.orden());
                if (inicio != null) {
                    duraciones.put(evento.orden(), Duration.between(inicio, evento.timestamp()).toMillis());
                }
            }
        }
        return duraciones;
    }

    static EstadisticasDuracion estadisticasDuracion(Map<String, Long> duracionesMs) {
        if (duracionesMs.isEmpty()) {
            return EstadisticasDuracion.VACIA;
        }
        List<Long> ordenadas = new ArrayList<>(duracionesMs.values());
        Collections.sort(ordenadas);
        return new EstadisticasDuracion(percentil(ordenadas, 0.50), percentil(ordenadas, 0.95),
                percentil(ordenadas, 0.99), ordenadas.get(ordenadas.size() - 1), ordenadas.size());
    }

    private static long percentil(List<Long> ordenadas, double p) {
        int indice = Math.max(0, Math.min(ordenadas.size() - 1, (int) Math.ceil(p * ordenadas.size()) - 1));
        return ordenadas.get(indice);
    }

    static List<EstadisticasPod> reclamosPorPod(List<EventoLog> eventos) {
        Map<String, long[]> porPod = new TreeMap<>(); // [ganados, perdidos, colisiones]
        for (var evento : eventos) {
            switch (evento.evento()) {
                case "reclamo_ganado" -> porPod.computeIfAbsent(evento.pod(), k -> new long[3])[0]++;
                case "reclamo_perdido" -> porPod.computeIfAbsent(evento.pod(), k -> new long[3])[1]++;
                case "colision_optimista" -> {
                    if ("reclamarToken".equals(evento.campo("operacion"))) {
                        porPod.computeIfAbsent(evento.pod(), k -> new long[3])[2]++;
                    }
                }
                default -> { /* resto de eventos no participan en esta métrica */ }
            }
        }
        List<EstadisticasPod> filas = new ArrayList<>();
        for (var entrada : porPod.entrySet()) {
            var v = entrada.getValue();
            filas.add(new EstadisticasPod(entrada.getKey(), v[0], v[1], v[2]));
        }
        return filas;
    }

    static long reintentosTotales(List<EventoLog> eventos) {
        return eventos.stream().filter(e -> e.evento().equals("reintento_programado")).count();
    }

    /**
     * Reintentos por tipo de orden: el log no lleva un identificador de PASO
     * discreto (solo {@code intento} = nº de fallo acumulado), así que se
     * aproxima "por paso" como "por tipo de orden" (PRINCIPAL/SECUNDARIA1/2/3),
     * el desglose más fino que el catálogo de eventos permite reconstruir sin
     * inventar un campo que el código no loguea. Desviación documentada
     * también en el informe final de la fase.
     */
    static List<ReintentosPorTipo> reintentosPorTipo(List<EventoLog> eventos) {
        Map<String, Long> porTipo = new TreeMap<>();
        for (var evento : eventos) {
            if (evento.evento().equals("reintento_programado")) {
                porTipo.merge(evento.tipo(), 1L, Long::sum);
            }
        }
        List<ReintentosPorTipo> filas = new ArrayList<>();
        porTipo.forEach((tipo, total) -> filas.add(new ReintentosPorTipo(tipo, total)));
        return filas;
    }

    /**
     * Profundidad aproximada de la cola de ejecutables a lo largo del tiempo.
     * No existe un evento directo "entró en cola"/"salió de cola" en el
     * catálogo, así que se reconstruye por barrido (sweep-line) a partir de
     * los eventos que sí sabemos que cambian la disponibilidad de una orden:
     *
     * <ul>
     *   <li>+1 en {@code tramitacion_creada} (nueva PRINCIPAL, candidata
     *       inmediata: ver {@code OrdenRoot.nueva}, {@code proximoReintentoEn = ahora}).</li>
     *   <li>+1 en {@code reintento_programado.timestamp + espera_ms} (vuelve a
     *       ser candidata cuando vence su espera).</li>
     *   <li>+1 en {@code orden_aparcada.timestamp + ventana_ms} (idem, para la
     *       espera de conciliación de Secundaria2).</li>
     *   <li>-1 en cada {@code reclamo_ganado} (deja la cola: un pod la toma).</li>
     * </ul>
     *
     * <p><b>Limitación conocida y asumida</b>: las órdenes Secundaria1/2/3 se
     * crean dentro de la MISMA transacción que finaliza su Principal
     * ({@code ServicioSagaPrincipal.crearHijas}) y no generan un evento propio
     * de creación (el catálogo no tiene "orden_creada" para hijas, solo
     * {@code tramitacion_creada} para la Principal inicial vía REST/inyector).
     * Su primera aparición en el log es su propio {@code reclamo_ganado}: el
     * +1 y el -1 de esa entrada coinciden en el mismo instante (contribución
     * neta 0), así que esta aproximación SUBESTIMA la profundidad real de la
     * cola en la parte que corresponde a la espera de las órdenes hijas antes
     * de su primer reclamo. Es una cifra comparativa entre ejecuciones del
     * mismo harness (coherente con el resto del plan: "no da cifras
     * extrapolables a producción"), no un conteo exacto instante a instante.
     *
     * <p>Segunda corrección necesaria: una orden Secundaria2 aparcada puede
     * desatascarse ANTES de que venza su ventana de conciliación de 3h si
     * llega antes la respuesta Kafka real ({@code ConsumidorRespuestaSecundaria2}
     * → {@code despertar()}, candidata inmediata). En ese caso el {@code +1}
     * de "vuelve a la cola tras la ventana" que programamos al ver
     * {@code orden_aparcada} nunca debe llegar a contar: se cancela (se resta)
     * si el siguiente {@code reclamo_ganado} de esa misma orden llega ANTES
     * del instante que habíamos programado. Lo mismo aplica, por simetría, a
     * {@code reintento_programado}. Sin esta cancelación, escenarios con
     * Secundaria2 (todos) mostrarían una entrada fantasma en la cola horas
     * después de que la orden ya hubiera finalizado.
     *
     * <p>La cancelación aplica también cuando lo que llega antes es OTRA
     * programación de regreso: una Secundaria2 aparcada cuya respuesta Kafka
     * trae {@code exito=false} emite un {@code reintento_programado} sin
     * pasar por {@code reclamo_ganado} (ver el catálogo de eventos), y ese
     * reintento SUSTITUYE a la ventana de conciliación de 3h — el {@code +1}
     * de la ventana debe cancelarse igual, o quedaría huérfano y pintaría
     * minutos fantasma horas después del fin de la ejecución.
     *
     * <p>Y la tercera vía que adelanta la ventana: la respuesta Kafka con
     * {@code exito=true} despierta la orden aparcada como candidata INMEDIATA
     * ({@code ServicioRegistrarRespuestaSecundaria2.respuestaOk} →
     * {@code OrdenRoot.despertar}, {@code proximo_reintento_en = ahora}). El
     * evento {@code respuesta_secundaria2_registrada} con {@code exito=true}
     * cancela por tanto el regreso programado de la ventana y suma {@code +1}
     * en su propio instante (la orden vuelve a la cola YA): sin esto, el
     * {@code -1} del reclamo posterior restaba sin {@code +1} previo
     * (infracontando), y si el apagado llegaba antes del reclamo la ventana
     * de 3h quedaba huérfana pintando un minuto fantasma.
     */
    static ProfundidadCola profundidadCola(List<EventoLog> eventos) {
        List<EventoLog> ordenados = new ArrayList<>(eventos);
        ordenados.sort((a, b) -> a.timestamp().compareTo(b.timestamp()));

        TreeMap<Instant, Long> deltas = new TreeMap<>();
        Map<String, Instant> proximoRegresoProgramado = new HashMap<>();
        for (var evento : ordenados) {
            switch (evento.evento()) {
                case "tramitacion_creada" -> deltas.merge(evento.timestamp(), 1L, Long::sum);
                case "reclamo_ganado" -> {
                    deltas.merge(evento.timestamp(), -1L, Long::sum);
                    cancelarRegresoSiSeAdelanto(deltas, proximoRegresoProgramado, evento);
                }
                case "orden_finalizada" -> cancelarRegresoSiSeAdelanto(deltas, proximoRegresoProgramado, evento);
                case "reintento_programado" -> {
                    cancelarRegresoSiSeAdelanto(deltas, proximoRegresoProgramado, evento);
                    Instant regresa = evento.timestamp().plusMillis(evento.campoLong("espera_ms"));
                    deltas.merge(regresa, 1L, Long::sum);
                    proximoRegresoProgramado.put(evento.orden(), regresa);
                }
                case "orden_aparcada" -> {
                    cancelarRegresoSiSeAdelanto(deltas, proximoRegresoProgramado, evento);
                    Instant regresa = evento.timestamp().plusMillis(evento.campoLong("ventana_ms"));
                    deltas.merge(regresa, 1L, Long::sum);
                    proximoRegresoProgramado.put(evento.orden(), regresa);
                }
                case "respuesta_secundaria2_registrada" -> {
                    // Solo exito=true despierta la orden (candidata inmediata); con
                    // exito=false el regreso ya lo reprogramó su reintento_programado.
                    if ("true".equals(evento.campo("exito"))) {
                        cancelarRegresoSiSeAdelanto(deltas, proximoRegresoProgramado, evento);
                        deltas.merge(evento.timestamp(), 1L, Long::sum);
                        proximoRegresoProgramado.put(evento.orden(), evento.timestamp());
                    }
                }
                default -> { /* resto de eventos no afectan a la cola de ejecutables */ }
            }
        }
        // Una cancelación (ver cancelarRegresoSiSeAdelanto) puede dejar una clave con
        // delta neto 0: no es un cambio de nivel real, se descarta para no ensuciar
        // la serie con instantes que no aportan información (p. ej. la ventana de
        // conciliación de 3h de una Secundaria2 que en realidad se resolvió antes).
        deltas.entrySet().removeIf(entrada -> entrada.getValue() == 0L);
        if (deltas.isEmpty()) {
            return new ProfundidadCola(List.of(), 0, null, 0.0);
        }

        List<PuntoCola> serie = new ArrayList<>();
        long acumulado = 0;
        long maximo = 0;
        Instant instanteMaximo = null;
        for (var entrada : deltas.entrySet()) {
            acumulado += entrada.getValue();
            serie.add(new PuntoCola(entrada.getKey(), acumulado));
            if (acumulado > maximo) {
                maximo = acumulado;
                instanteMaximo = entrada.getKey();
            }
        }

        // Media ponderada por el tiempo que cada nivel estuvo vigente.
        double areaTotal = 0.0;
        for (int i = 0; i < serie.size() - 1; i++) {
            long nivel = Math.max(0, serie.get(i).profundidad());
            Duration duracion = Duration.between(serie.get(i).instante(), serie.get(i + 1).instante());
            areaTotal += nivel * duracion.toMillis();
        }
        Instant inicio = serie.get(0).instante();
        Instant fin = serie.get(serie.size() - 1).instante();
        long spanMs = Duration.between(inicio, fin).toMillis();
        double promedio = spanMs == 0 ? Math.max(0, serie.get(0).profundidad()) : areaTotal / spanMs;

        // Muestreo por minuto (última profundidad observada en cada minuto) para una tabla compacta.
        TreeMap<Instant, Long> ultimaPorMinuto = new TreeMap<>();
        for (var punto : serie) {
            ultimaPorMinuto.put(punto.instante().truncatedTo(ChronoUnit.MINUTES), Math.max(0, punto.profundidad()));
        }
        List<PuntoCola> muestras = new ArrayList<>();
        ultimaPorMinuto.forEach((minuto, profundidad) -> muestras.add(new PuntoCola(minuto, profundidad)));

        return new ProfundidadCola(muestras, maximo, instanteMaximo, promedio);
    }

    /**
     * Si la orden tenía un "regreso a la cola" programado (por
     * {@code reintento_programado} o {@code orden_aparcada}) para un instante
     * futuro TODAVÍA no alcanzado, y ahora se resuelve antes por otra vía
     * ({@code reclamo_ganado}, {@code orden_finalizada} o una NUEVA
     * programación de regreso que lo sustituye, llegando antes de esa hora),
     * el {@code +1} que habíamos programado para ese instante futuro nunca
     * debe contar: se cancela restándolo del mismo instante.
     */
    private static void cancelarRegresoSiSeAdelanto(TreeMap<Instant, Long> deltas,
            Map<String, Instant> proximoRegresoProgramado, EventoLog evento) {
        Instant regresoProgramado = proximoRegresoProgramado.remove(evento.orden());
        if (regresoProgramado != null && regresoProgramado.isAfter(evento.timestamp())) {
            deltas.merge(regresoProgramado, -1L, Long::sum);
        }
    }
}
