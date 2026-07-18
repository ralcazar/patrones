package com.ejemplo.app.carga.analisis;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ejemplo.app.business.ordermanager.dominio.PoliticaReintentos;

/**
 * Los 4 invariantes del plan de pruebas de carga (fase 3), evaluados a partir
 * de {@code pods.log} ya parseado + consultas a la H2 de la ejecución. Cada
 * método es independiente y deliberadamente explícito (nada de heurísticas
 * compartidas ocultas): son el contrato que verifica si la ejecución fue
 * BUENA o MALA.
 */
final class Invariantes {

    /** Eventos que cierran una sesión de ejecución abierta por un {@code reclamo_ganado} (ver invariante 2). */
    private static final Set<String> EVENTOS_CIERRE_SESION =
            Set.of("orden_aparcada", "orden_finalizada", "reintento_programado");

    private Invariantes() {
    }

    /**
     * Invariante 1: toda orden creada está en estado terminal, o esperando
     * legítimamente su turno (reintento o aparcado, {@code proximo_reintento_en}
     * en el futuro), o tiene ticket abierto. Ninguna puede quedar "estancada"
     * (viva, sin ticket, con el turno ya vencido) sin que nadie la recoja.
     *
     * <p>Nota: la tabla {@code orden} no distingue en una columna aparte una
     * espera de conciliación (Secundaria2, {@code Aparcar}) de un reintento
     * normal por fallo: ambas usan el mismo campo {@code proximo_reintento_en}
     * (ver {@code OrdenRoot.aparcar}/{@code programarReintento}). Este
     * invariante generaliza "aparcada con causa viva" a cualquier espera
     * legítima (turno en el futuro), sea cual sea su origen.
     */
    static ResultadoInvariante ningunaEstancadaSinDueno(RepositorioAnalisisBbdd db) {
        var estancadas = db.ordenesEstancadas();
        if (estancadas.isEmpty()) {
            return ResultadoInvariante.ok("Ninguna orden estancada sin dueño",
                    "0 órdenes vivas con turno vencido, sin ticket");
        }
        List<String> detalles = new ArrayList<>();
        for (var fila : estancadas) {
            detalles.add("orden=%s tipo=%s intentos=%d proximo_reintento_en=%s (vencido, sin ticket)"
                    .formatted(fila.ordenId(), fila.tipo(), fila.intentos(), fila.proximoReintentoEn()));
        }
        return ResultadoInvariante.fallo("Ninguna orden estancada sin dueño",
                estancadas.size() + " orden(es) estancada(s): turno vencido, sin ticket abierto y sin finalizar",
                detalles);
    }

    /**
     * Invariante 2: entre el {@code reclamo_ganado} de una orden y su evento
     * de cierre ({@code orden_aparcada}/{@code orden_finalizada}/
     * {@code reintento_programado}, o un {@code colision_optimista} en
     * {@code ejecutarPaso}/{@code programarReintento}) no debe haber un
     * {@code reclamo_ganado} de OTRO pod para la misma orden, salvo que el
     * lease ya hubiera vencido (takeover legítimo: se señala como nota, no
     * como violación). Se reconstruyen sesiones por orden en orden
     * cronológico de línea de log; una violación real solo es posible si el
     * log está corrompido o hay un fallo del optimistic lock, porque
     * {@code reclamarToken} exige version + token no vigente en BBDD.
     */
    static ResultadoInvariante sinSolapesDeEjecucion(List<EventoLog> eventos, Duration lease) {
        Map<String, List<EventoLog>> porOrden = new LinkedHashMap<>();
        for (var evento : eventos) {
            if (evento.orden() == null) {
                continue; // eventos agregados (purga_datos_antiguos) no aplican
            }
            boolean esApertura = evento.evento().equals("reclamo_ganado");
            boolean esCierre = EVENTOS_CIERRE_SESION.contains(evento.evento())
                    || (evento.evento().equals("colision_optimista")
                            && !"reclamarToken".equals(evento.campo("operacion")));
            if (esApertura || esCierre) {
                porOrden.computeIfAbsent(evento.orden(), k -> new ArrayList<>()).add(evento);
            }
        }

        List<String> violaciones = new ArrayList<>();
        List<String> notas = new ArrayList<>();
        for (var entrada : porOrden.entrySet()) {
            String ordenId = entrada.getKey();
            var lista = entrada.getValue();
            lista.sort((a, b) -> a.timestamp().compareTo(b.timestamp()));

            String podAbierto = null;
            Instant tsAbierto = null;
            for (var evento : lista) {
                boolean esApertura = evento.evento().equals("reclamo_ganado");
                if (esApertura) {
                    if (podAbierto != null) {
                        Duration transcurrido = Duration.between(tsAbierto, evento.timestamp());
                        if (transcurrido.compareTo(lease) >= 0) {
                            notas.add(("orden=%s: pod=%s reclama en %s tras vencer el lease de pod=%s "
                                    + "(abierto en %s, transcurrido=%s >= lease=%s) — takeover legítimo")
                                    .formatted(ordenId, evento.pod(), evento.timestamp(), podAbierto, tsAbierto,
                                            transcurrido, lease));
                        } else {
                            violaciones.add(("orden=%s: pod=%s reclama en %s mientras pod=%s seguía con el token "
                                    + "(abierto en %s, transcurrido=%s < lease=%s, sin evento de cierre) — solape real")
                                    .formatted(ordenId, evento.pod(), evento.timestamp(), podAbierto, tsAbierto,
                                            transcurrido, lease));
                        }
                    }
                    podAbierto = evento.pod();
                    tsAbierto = evento.timestamp();
                } else {
                    podAbierto = null;
                    tsAbierto = null;
                }
            }
        }

        if (violaciones.isEmpty()) {
            String resumen = notas.isEmpty()
                    ? "0 solapes de ejecución detectados"
                    : notas.size() + " takeover(s) por lease vencido (legítimos, ver notas)";
            return ResultadoInvariante.ok("Sin solapes de ejecución entre pods", resumen, notas);
        }
        return ResultadoInvariante.fallo("Sin solapes de ejecución entre pods",
                violaciones.size() + " solape(s) real(es) detectado(s) (dos pods con el token vigente a la vez)",
                violaciones, notas);
    }

    /**
     * Invariante 3: cada {@code reintento_programado} respeta la escalera de
     * {@link PoliticaReintentos} (1,3,5,10,20,45,90 min, 180 min en adelante).
     * Se reutiliza la clase real de producción (solo lectura, nunca se
     * modifica) para no duplicar la escalera y quedar acoplado a la fuente
     * de verdad.
     */
    static ResultadoInvariante reintentosRespetanPolitica(List<EventoLog> eventos) {
        var politica = new PoliticaReintentos();
        List<String> violaciones = new ArrayList<>();
        int total = 0;
        for (var evento : eventos) {
            if (!evento.evento().equals("reintento_programado")) {
                continue;
            }
            total++;
            int intento = evento.campoInt("intento");
            long esperaMs = evento.campoLong("espera_ms");
            long esperado = politica.esperaTras(intento).toMillis();
            if (esperaMs != esperado) {
                violaciones.add("orden=%s tipo=%s intento=%d espera_ms=%d (esperado %d según PoliticaReintentos)"
                        .formatted(evento.orden(), evento.tipo(), intento, esperaMs, esperado));
            }
        }
        if (violaciones.isEmpty()) {
            return ResultadoInvariante.ok("Los reintentos respetan PoliticaReintentos",
                    total + " reintento(s) programado(s), todos con la espera exacta de la escalera");
        }
        return ResultadoInvariante.fallo("Los reintentos respetan PoliticaReintentos",
                violaciones.size() + " de " + total + " reintento(s) con una espera que no coincide con la escalera",
                violaciones);
    }

    /**
     * Invariante 4: ticket abierto ⇔ escalera de reintentos agotada
     * ({@code intentos >= 8}), sobre órdenes vivas. Según el código real
     * (ver investigación de la fase 3, {@code ServicioTicketsSoporte} /
     * {@code AdaptadorOrdenesTicketPendiente}), "orden aparcada caducada" no
     * tiene una vía de apertura de ticket distinta de agotar la escalera: la
     * única query de producción que decide "pendiente de ticket" es
     * {@code intentos >= 8 AND ticket_abierto_en IS NULL AND completada_en
     * IS NULL}. El invariante se comprueba en ambas direcciones ("ni de más
     * ni de menos").
     */
    static ResultadoInvariante ticketsCoherentesConReintentos(RepositorioAnalisisBbdd db) {
        var sinMotivo = db.ordenesTicketSinMotivo();
        var sinTicket = db.ordenesReintentosAgotadosSinTicket();
        if (sinMotivo.isEmpty() && sinTicket.isEmpty()) {
            return ResultadoInvariante.ok("Tickets abiertos ⇔ reintentos agotados",
                    "ticket_abierto_en coincide exactamente con intentos >= 8 en todas las órdenes vivas");
        }
        List<String> detalles = new ArrayList<>();
        for (var fila : sinMotivo) {
            detalles.add("orden=%s tipo=%s intentos=%d: ticket abierto SIN agotar la escalera (de más)"
                    .formatted(fila.ordenId(), fila.tipo(), fila.intentos()));
        }
        for (var fila : sinTicket) {
            detalles.add("orden=%s tipo=%s intentos=%d: escalera agotada SIN ticket abierto (de menos)"
                    .formatted(fila.ordenId(), fila.tipo(), fila.intentos()));
        }
        return ResultadoInvariante.fallo("Tickets abiertos ⇔ reintentos agotados",
                sinMotivo.size() + " ticket(s) de más, " + sinTicket.size() + " ticket(s) de menos", detalles);
    }
}
