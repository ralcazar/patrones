package com.ejemplo.app.carga.analisis;

import java.util.List;
import java.util.Locale;

/** Ensambla {@code informe.md} a partir de los invariantes y métricas ya calculados. */
final class InformeMarkdown {

    record ResumenGlobal(long totalOrdenes, long completadas, long ticketsAbiertos, long vivas) {}

    private InformeMarkdown() {
    }

    static String generar(String nombreCarpeta, boolean bueno, List<ResultadoInvariante> invariantes,
            List<Metricas.FilaThroughput> throughput, Metricas.EstadisticasDuracion duracion,
            List<Metricas.EstadisticasPod> porPod, long reintentosTotales,
            List<Metricas.ReintentosPorTipo> reintentosPorTipo, List<RepositorioAnalisisBbdd.FilaDistribucionEstado> distribucion,
            Metricas.ProfundidadCola profundidadCola, List<String> minutosSinRitmo, List<String> sobreP99,
            List<String> desequilibrados, ResumenGlobal resumen) {

        StringBuilder md = new StringBuilder();

        md.append("# Informe de ejecución: ").append(nombreCarpeta).append("\n\n");
        md.append("**Veredicto: ").append(bueno ? "BUENO" : "MALO").append("**");
        if (bueno) {
            md.append(" — se cumplen los ").append(invariantes.size()).append(" invariantes comprobados.\n\n");
        } else {
            long fallidos = invariantes.stream().filter(r -> !r.pasa()).count();
            md.append(" — ").append(fallidos).append(" de ").append(invariantes.size())
                    .append(" invariante(s) NO se cumplen: ");
            md.append(String.join("; ", invariantes.stream().filter(r -> !r.pasa()).map(ResultadoInvariante::nombre).toList()));
            md.append(".\n\n");
        }

        md.append("Resumen: ").append(resumen.totalOrdenes()).append(" órdenes totales, ")
                .append(resumen.completadas()).append(" completadas, ").append(resumen.vivas())
                .append(" vivas, ").append(resumen.ticketsAbiertos()).append(" con ticket abierto.\n\n");

        md.append("## Invariantes\n\n");
        int i = 1;
        for (var r : invariantes) {
            md.append("### ").append(i++).append(". ").append(r.nombre()).append(" — ")
                    .append(r.pasa() ? "PASA" : "FALLA").append("\n\n");
            md.append(r.resumen()).append("\n\n");
            if (!r.detalles().isEmpty()) {
                for (var detalle : r.detalles()) {
                    md.append("- ").append(detalle).append("\n");
                }
                md.append("\n");
            }
            if (!r.notas().isEmpty()) {
                md.append("Notas:\n\n");
                for (var nota : r.notas()) {
                    md.append("- _").append(nota).append("_\n");
                }
                md.append("\n");
            }
        }

        md.append("## Métricas\n\n");

        md.append("### Throughput por minuto (creadas vs finalizadas, tipo PRINCIPAL)\n\n");
        md.append("| Minuto | Creadas | Finalizadas |\n|---|---|---|\n");
        for (var fila : throughput) {
            md.append("| ").append(fila.minuto()).append(" | ").append(fila.creadas()).append(" | ")
                    .append(fila.finalizadas()).append(" |\n");
        }
        md.append("\n");

        md.append("### Duración de saga PRINCIPAL (creación → finalización)\n\n");
        md.append("- Muestras: ").append(duracion.muestras()).append("\n");
        md.append("- p50: ").append(duracion.p50Ms()).append(" ms\n");
        md.append("- p95: ").append(duracion.p95Ms()).append(" ms\n");
        md.append("- p99: ").append(duracion.p99Ms()).append(" ms\n");
        md.append("- máx: ").append(duracion.maxMs()).append(" ms\n\n");

        md.append("### Reclamos y colisiones por pod\n\n");
        md.append("| Pod | Ganados | Perdidos | Colisiones (reclamarToken) | % colisión |\n|---|---|---|---|---|\n");
        for (var fila : porPod) {
            md.append("| ").append(fila.pod()).append(" | ").append(fila.ganados()).append(" | ")
                    .append(fila.perdidos()).append(" | ").append(fila.colisiones()).append(" | ")
                    .append(String.format(Locale.ROOT, "%.1f", fila.pctColision())).append("% |\n");
        }
        md.append("\n");

        md.append("### Reintentos\n\n");
        md.append("Total: ").append(reintentosTotales).append("\n\n");
        md.append("Por tipo de orden (el log no discrimina el nº de PASO dentro de la saga, solo el nº de "
                + "intento acumulado; ver limitación documentada en `Metricas.reintentosPorTipo`):\n\n");
        md.append("| Tipo | Reintentos |\n|---|---|\n");
        for (var fila : reintentosPorTipo) {
            md.append("| ").append(fila.tipo()).append(" | ").append(fila.total()).append(" |\n");
        }
        md.append("\n");

        md.append("### Distribución final de estados (SQL: `proceso` + `orden`)\n\n");
        md.append("| Tipo | Estado | Total | Completadas |\n|---|---|---|---|\n");
        for (var fila : distribucion) {
            md.append("| ").append(fila.tipo()).append(" | ").append(fila.estado()).append(" | ")
                    .append(fila.total()).append(" | ").append(fila.completadas()).append(" |\n");
        }
        md.append("\n");

        md.append("### Profundidad aproximada de la cola de ejecutables\n\n");
        md.append("_Aproximación por barrido de eventos, no un contador directo — ver criterio y limitación "
                + "documentados en `Metricas.profundidadCola`._\n\n");
        md.append("- Máximo: ").append(profundidadCola.maximo()).append(" (en ")
                .append(profundidadCola.instanteMaximo()).append(")\n");
        md.append("- Media ponderada por tiempo: ")
                .append(String.format(Locale.ROOT, "%.2f", profundidadCola.promedioPonderado()))
                .append("\n\n");
        md.append("| Minuto | Profundidad aprox. |\n|---|---|\n");
        for (var punto : profundidadCola.muestrasPorMinuto()) {
            md.append("| ").append(punto.instante()).append(" | ").append(punto.profundidad()).append(" |\n");
        }
        md.append("\n");

        md.append("## Anomalías\n\n");
        md.append("### Minutos sin ritmo (cola > 0 y 0 reclamos ganados)\n\n");
        aListaOSinHallazgos(md, minutosSinRitmo);

        md.append("### Órdenes con duración > p99\n\n");
        aListaOSinHallazgos(md, sobreP99);

        md.append("### Pods desequilibrados (± 30% de la media de reclamos ganados)\n\n");
        aListaOSinHallazgos(md, desequilibrados);

        return md.toString();
    }

    private static void aListaOSinHallazgos(StringBuilder md, List<String> items) {
        if (items.isEmpty()) {
            md.append("Sin hallazgos.\n\n");
            return;
        }
        for (var item : items) {
            md.append("- ").append(item).append("\n");
        }
        md.append("\n");
    }
}
