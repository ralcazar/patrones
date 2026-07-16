-- Tabla hija de auditoría: una fila por intervención de soporte.

CREATE TABLE saga_auditoria (
    saga_id         VARCHAR2(36)   NOT NULL,
    orden_secuencia NUMBER(10)     NOT NULL,
    cuando          TIMESTAMP(6)   NOT NULL,
    quien           VARCHAR2(100),
    accion          VARCHAR2(50)   NOT NULL,
    detalle         VARCHAR2(500),
    CONSTRAINT pk_saga_auditoria PRIMARY KEY (saga_id, orden_secuencia),
    CONSTRAINT fk_saga_auditoria_saga FOREIGN KEY (saga_id)
        REFERENCES saga (saga_id) ON DELETE CASCADE
);
