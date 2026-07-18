-- Tabla satélite 1:1 con proceso: el contexto propio de la saga secundaria 1
-- (antes en el CLOB proceso.contexto). Sin ON DELETE CASCADE (prohibido, ver
-- CLAUDE.md): el borrado de huérfanos es explícito, en el adaptador de
-- persistencia, hijas antes que padre.

CREATE TABLE proceso_saga_secundaria1 (
    orden_id         VARCHAR2(36)  NOT NULL,
    ref_paso1        VARCHAR2(100) NOT NULL,
    ref_inicio       VARCHAR2(100),
    ref_confirmacion VARCHAR2(100),
    CONSTRAINT pk_proceso_saga_secundaria1 PRIMARY KEY (orden_id),
    CONSTRAINT fk_proceso_saga_secundaria1_proceso FOREIGN KEY (orden_id)
        REFERENCES proceso (orden_id)
);
