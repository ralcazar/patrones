-- Tabla del Proceso (entidad interna del agregado): SOLO estado de NEGOCIO (FSM + contexto + auditoría).
-- Sin version: la controla la tabla orden (única version del agregado).

CREATE TABLE proceso (
    orden_id    VARCHAR2(36)  NOT NULL,
    tipo        VARCHAR2(20)  NOT NULL,
    external_id VARCHAR2(36)  NOT NULL,
    estado      VARCHAR2(40)  NOT NULL,
    -- JSON plano con las refs/datos propios de cada tipo de orden: todos son,
    -- en el dominio, wrappers de un único String, así que un mapeo columna a
    -- columna de 4 formas distintas no aporta nada frente al JSON.
    contexto    CLOB          NOT NULL,
    CONSTRAINT pk_proceso PRIMARY KEY (orden_id)
);

CREATE INDEX idx_proceso_external_id ON proceso (external_id);
