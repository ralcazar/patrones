-- Tabla hija de auditoría: una fila por intervención de soporte. Sin
-- ON DELETE CASCADE (prohibido, ver CLAUDE.md): el borrado de huérfanos es
-- explícito, en el adaptador de persistencia, hijas antes que padre.
--
-- Sigue llamándose proceso_auditoria (no orden_auditoria) aunque ya cuelga de
-- orden, no de proceso (fusionada en orden.sql): renombrarla no aporta nada
-- (minimiza la onda expansiva del cambio) y el nombre sigue siendo preciso —
-- audita intervenciones sobre el PROCESO de negocio (la FSM), que ahora es
-- solo un subconjunto de columnas de la fila orden, no una tabla propia.

CREATE TABLE proceso_auditoria (
    orden_id  VARCHAR2(36)   NOT NULL,
    secuencia NUMBER(10)     NOT NULL,
    cuando    TIMESTAMP(6)   NOT NULL,
    quien     VARCHAR2(100),
    accion    VARCHAR2(50)   NOT NULL,
    detalle   VARCHAR2(500),
    CONSTRAINT pk_proceso_auditoria PRIMARY KEY (orden_id, secuencia),
    CONSTRAINT fk_proceso_auditoria_orden FOREIGN KEY (orden_id)
        REFERENCES orden (orden_id)
);
