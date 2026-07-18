package com.ejemplo.app.carga.esquema;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * Inicializa el esquema de la H2 en fichero del harness ejecutando
 * literalmente los DDL de producción ({@code order-manager/db/*.sql}), con
 * {@code ddl-auto=none} igual que en producción (ver
 * {@code plan-pruebas-carga.md}, fase 2): el harness debe parecerse lo más
 * posible a producción (constraints, índices reales), no a un esquema
 * generado por Hibernate.
 *
 * <p>Se ejecuta UNA vez desde {@code LanzadorPruebaCarga}, con JDBC plano,
 * ANTES de arrancar ningún pod (en vez de "desde el contexto Spring del pod
 * 0" como sugiere el plan): al no depender de ningún bean ni de la
 * coordinación entre pods, se elimina por completo la carrera que el plan
 * resolvía arrancando el pod 0 primero y esperando su inicialización — aquí
 * ni siquiera hace falta esperar, porque no hay ningún pod arrancado todavía
 * cuando esto se ejecuta. Se documenta como desviación en el informe de la
 * fase 2.
 *
 * <p>Usa {@link ScriptUtils} (spring-jdbc, transitivo de
 * spring-boot-starter-data-jpa) para trocear cada fichero en sentencias
 * individuales respetando comentarios {@code --} (una partición ingenua por
 * {@code ;} corta mal si un comentario contiene un punto y coma, como pasa en
 * {@code orden.sql}).
 */
public final class InicializadorEsquemaH2 {

    private static final Logger log = LoggerFactory.getLogger(InicializadorEsquemaH2.class);

    // Orden de aplicación exigido por las FKs, ver order-manager/db/README.md.
    private static final List<String> FICHEROS = List.of(
            "proceso.sql",
            "proceso_auditoria.sql",
            "orden.sql",
            "datos_negocio.sql",
            "datos_negocio_documento.sql",
            "proceso_saga_principal.sql",
            "proceso_saga_secundaria1.sql",
            "proceso_saga_secundaria2.sql",
            "proceso_saga_secundaria3.sql");

    private InicializadorEsquemaH2() {
    }

    public static void inicializar(String urlJdbc, Path directorioDb) {
        try (Connection conexion = DriverManager.getConnection(urlJdbc, "sa", "")) {
            for (String fichero : FICHEROS) {
                String contenido = adaptarParaH2(fichero, leer(directorioDb.resolve(fichero)));
                var recurso = new EncodedResource(
                        new ByteArrayResource(contenido.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
                ScriptUtils.executeSqlScript(conexion, recurso);
                log.info("evento=esquema_h2_aplicado fichero={} pod=lanzador", fichero);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo inicializar el esquema H2 del harness en " + urlJdbc, e);
        }
    }

    /**
     * ÚNICA desviación de compatibilidad H2 encontrada al adaptar
     * {@code db/*.sql} (Oracle) al harness: {@code db/orden.sql} define
     * {@code idx_orden_candidatas} como índice POR EXPRESIÓN
     * ({@code CASE WHEN completada_en IS NULL THEN proximo_reintento_en END}),
     * que Oracle soporta pero H2 2.2.224 rechaza en {@code CREATE INDEX}
     * (solo admite listas de columnas, verificado ejecutando el DDL real
     * contra H2 en modo Oracle). Se sustituye, SOLO en la copia en memoria
     * que ve H2 — nunca se toca {@code db/orden.sql} de producción — por un
     * índice compuesto equivalente en intención
     * {@code (completada_en, proximo_reintento_en)}: mismo propósito
     * (acelerar la búsqueda de candidatas del planificador), aunque el plan
     * de ejecución exacto no sea idéntico al de Oracle. Es una desviación
     * asumible porque el propio plan advierte que el harness no da cifras
     * extrapolables a producción (H2 embebida no es Oracle).
     */
    private static String adaptarParaH2(String fichero, String contenido) {
        if (!"orden.sql".equals(fichero)) {
            return contenido;
        }
        String original = "ON orden (CASE WHEN completada_en IS NULL THEN proximo_reintento_en END)";
        String sustituto = "ON orden (completada_en, proximo_reintento_en)";
        if (!contenido.contains(original)) {
            throw new IllegalStateException(
                    "db/orden.sql cambió y ya no contiene el índice funcional esperado por "
                            + "InicializadorEsquemaH2.adaptarParaH2; revisar la desviación documentada");
        }
        return contenido.replace(original, sustituto);
    }

    private static String leer(Path ruta) {
        try {
            return Files.readString(ruta, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + ruta, e);
        }
    }
}
