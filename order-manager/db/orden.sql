-- Tabla de la OrdenRoot: raíz del agregado (ÚNICO agregado por orden), fusión
-- de lo que antes eran dos filas (orden + proceso) en una sola fila atómica.
-- Lleva TANTO el estado de NEGOCIO (tipo, external_id, estado de la FSM) COMO
-- el estado de EJECUCIÓN (reintentos, lease del token, marca de ticket e
-- instante en que se completó). Una única version protege el agregado
-- completo (negocio + ejecución), porque varios flujos mutan ambos en la
-- misma transacción, y al vivir en una sola fila se elimina la lectura mixta
-- (torn read) que existía leyendo negocio y ejecución en 2 SELECT separados
-- bajo READ_COMMITTED.

CREATE TABLE orden (
    orden_id             VARCHAR2(36)   NOT NULL,
    tipo                 VARCHAR2(20)   NOT NULL,
    external_id          VARCHAR2(36)   NOT NULL,
    estado               VARCHAR2(40)   NOT NULL,
    intentos             NUMBER(10)     NOT NULL,
    proximo_reintento_en TIMESTAMP(6)   NOT NULL,
    token_trabajador     VARCHAR2(36),
    token_expira_en      TIMESTAMP(6),
    ticket_abierto_en    TIMESTAMP(6),
    ultimo_error_tipo    VARCHAR2(200),
    ultimo_error_mensaje VARCHAR2(4000),
    version              NUMBER(19)     NOT NULL,
    creada_en            TIMESTAMP(6)   NOT NULL,
    actualizada_en       TIMESTAMP(6)   NOT NULL,
    completada_en        TIMESTAMP(6),
    CONSTRAINT pk_orden PRIMARY KEY (orden_id)
);

-- Idempotencia de POST /tramitaciones (localizar la orden principal ya creada
-- para un externalId) y búsquedas por tramitación completa. Antes vivía en
-- proceso (idx_proceso_external_id); mismo propósito, tabla fusionada.
CREATE INDEX idx_orden_external_id ON orden (external_id);

-- Candidatas del planificador: proximo_reintento_en <= :ahora AND completada_en
-- IS NULL AND (token_trabajador IS NULL OR token_expira_en <= :ahora).
-- Oracle no tiene índices parciales; el índice funcional deja NULL las filas
-- ya finalizadas (completada_en NOT NULL), que así no ocupan sitio en el índice.
CREATE INDEX idx_orden_candidatas
    ON orden (CASE WHEN completada_en IS NULL THEN proximo_reintento_en END);

-- Bandeja de trabajo / tickets pendientes: intentos >= 8.
CREATE INDEX idx_orden_intentos ON orden (intentos);
