-- Tabla del Proceso (entidad interna del agregado): SOLO estado de NEGOCIO
-- (FSM + auditoría). El contexto propio de cada tipo de orden (las refs/datos
-- que acumula paso a paso) vive en una tabla satélite relacional por tipo
-- (proceso_saga_principal.sql, proceso_saga_secundaria{1,2,3}.sql), no aquí.
-- Sin version: la controla la tabla orden (única version del agregado).

CREATE TABLE proceso (
    orden_id    VARCHAR2(36)  NOT NULL,
    tipo        VARCHAR2(20)  NOT NULL,
    external_id VARCHAR2(36)  NOT NULL,
    estado      VARCHAR2(40)  NOT NULL,
    CONSTRAINT pk_proceso PRIMARY KEY (orden_id)
);

CREATE INDEX idx_proceso_external_id ON proceso (external_id);
