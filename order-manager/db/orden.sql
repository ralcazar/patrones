-- Tabla de la OrdenRoot: raíz del agregado (ÚNICO agregado por orden). Añade
-- al negocio del proceso el estado de EJECUCIÓN: reintentos, lease del token,
-- marca de ticket y el instante en que se completó. Una única version protege
-- el agregado completo (negocio + ejecución), porque varios flujos mutan
-- ambos en la misma transacción.

CREATE TABLE orden (
    orden_id             VARCHAR2(36)   NOT NULL,
    intentos             NUMBER(10)     NOT NULL,
    proximo_reintento_en TIMESTAMP(6)   NOT NULL,
    token_trabajador     VARCHAR2(36),
    token_expira_en      TIMESTAMP(6),
    ticket_abierto_en    TIMESTAMP(6),
    completada_en        TIMESTAMP(6),
    ultimo_error_tipo    VARCHAR2(200),
    ultimo_error_mensaje VARCHAR2(1000),
    version              NUMBER(19)     NOT NULL,
    creada_en            TIMESTAMP(6)   NOT NULL,
    actualizada_en       TIMESTAMP(6)   NOT NULL,
    CONSTRAINT pk_orden PRIMARY KEY (orden_id),
    CONSTRAINT fk_orden_proceso FOREIGN KEY (orden_id) REFERENCES proceso (orden_id)
);

-- Candidatas del planificador: proximo_reintento_en <= :ahora AND completada_en
-- IS NULL AND (token_trabajador IS NULL OR token_expira_en <= :ahora).
-- Oracle no tiene índices parciales; el índice funcional deja NULL las filas
-- ya finalizadas (completada_en NOT NULL), que así no ocupan sitio en el índice.
CREATE INDEX idx_orden_candidatas
    ON orden (CASE WHEN completada_en IS NULL THEN proximo_reintento_en END);

-- Bandeja de trabajo / tickets pendientes: intentos >= 8.
CREATE INDEX idx_orden_intentos ON orden (intentos);
