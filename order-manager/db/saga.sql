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
