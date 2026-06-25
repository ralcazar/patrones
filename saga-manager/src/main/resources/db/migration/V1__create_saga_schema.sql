-- Registro de identidad: dedup de SAGAS por identidad externa (entrada).
-- payload se almacena como texto (JSON serializado) para no depender de tipos
-- exclusivos de Postgres (JSONB) y mantener portabilidad con el fallback H2.
CREATE TABLE saga_identity (
    id_app     VARCHAR(64)  NOT NULL,
    id_externo VARCHAR(128) NOT NULL,
    saga_id    UUID         NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    PRIMARY KEY (id_app, id_externo),
    CONSTRAINT uq_saga_identity_saga UNIQUE (saga_id)
);

-- Histórico append-only del estado: concurrencia POR PASO dentro de una saga.
CREATE TABLE saga_state (
    saga_id    UUID         NOT NULL,
    version    BIGINT       NOT NULL,
    status     VARCHAR(40)  NOT NULL,
    first_id   VARCHAR(100),
    second_id  VARCHAR(100),
    third_id   VARCHAR(100),
    payload    VARCHAR(4000),
    failure_reason VARCHAR(2000),
    created_at TIMESTAMP    NOT NULL,
    PRIMARY KEY (saga_id, version),
    CONSTRAINT fk_saga_state_identity FOREIGN KEY (saga_id) REFERENCES saga_identity (saga_id)
);

CREATE INDEX idx_saga_latest ON saga_state (saga_id, version DESC);
