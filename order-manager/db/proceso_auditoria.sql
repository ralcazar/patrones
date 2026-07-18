-- Tabla hija de auditoría: una fila por intervención de soporte. Sin
-- ON DELETE CASCADE (prohibido, ver CLAUDE.md): el borrado de huérfanos es
-- explícito, en el adaptador de persistencia, hijas antes que padre.

CREATE TABLE proceso_auditoria (
    orden_id  VARCHAR2(36)   NOT NULL,
    secuencia NUMBER(10)     NOT NULL,
    cuando    TIMESTAMP(6)   NOT NULL,
    quien     VARCHAR2(100),
    accion    VARCHAR2(50)   NOT NULL,
    detalle   VARCHAR2(500),
    CONSTRAINT pk_proceso_auditoria PRIMARY KEY (orden_id, secuencia),
    CONSTRAINT fk_proceso_auditoria_proceso FOREIGN KEY (orden_id)
        REFERENCES proceso (orden_id)
);
