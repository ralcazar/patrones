package com.ejemplo.app.testsoporte;

import java.util.function.Consumer;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * {@link StatementInspector} de Hibernate con un hook estático intercambiable
 * (por defecto no-op), registrado en el contexto Spring de integración vía
 * {@code spring.jpa.properties.hibernate.session_factory.statement_inspector}.
 * Hibernate instancia esta clase por reflexión (constructor sin argumentos) y
 * la invoca para CADA sentencia SQL de CUALQUIER test que comparta ese
 * contexto: el hook por defecto no hace nada para no afectarlos, y solo el
 * test que necesita interceptar una sentencia concreta sustituye {@link #hook}
 * (y lo restaura a {@link #NINGUNO} al terminar, típicamente en un bloque
 * {@code finally}).
 *
 * Pensado para tests que reproducen una intercalación concreta entre hilos:
 * el hook recibe el SQL y el propio {@link Thread#currentThread()} bajo el
 * que se ejecuta (la sentencia JDBC corre en el hilo que la origina), así
 * puede filtrar tanto por el texto de la sentencia como por el hilo.
 */
public class InspectorSqlPausable implements StatementInspector {

    /** Hook por defecto: no hace nada. */
    public static final Consumer<String> NINGUNO = sql -> { };

    public static volatile Consumer<String> hook = NINGUNO;

    @Override
    public String inspect(String sql) {
        hook.accept(sql);
        return sql;
    }
}
