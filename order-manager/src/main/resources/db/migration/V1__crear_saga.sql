-- Tabla de la SagaRoot: SOLO estado de NEGOCIO (FSM + contexto + auditoría).
-- Sin version: la controla la tabla orden (única version del agregado).

CREATE TABLE saga (
    saga_id     VARCHAR2(36)  NOT NULL,
    tipo        VARCHAR2(20)  NOT NULL,
    external_id VARCHAR2(36)  NOT NULL,
    estado      VARCHAR2(40)  NOT NULL,
    -- JSON plano con las refs/datos propios de cada tipo de saga: todos son,
    -- en el dominio, wrappers de un único String, así que un mapeo columna a
    -- columna de 4 formas distintas no aporta nada frente al JSON.
    contexto    CLOB          NOT NULL,
    CONSTRAINT pk_saga PRIMARY KEY (saga_id)
);

CREATE INDEX idx_saga_external_id ON saga (external_id);

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
