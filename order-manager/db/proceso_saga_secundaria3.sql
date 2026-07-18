-- Tabla satélite 1:1 con proceso: el contexto propio de la saga secundaria 3
-- (antes en el CLOB proceso.contexto). Sin ON DELETE CASCADE (prohibido, ver
-- CLAUDE.md): el borrado de huérfanos es explícito, en el adaptador de
-- persistencia, hijas antes que padre.

CREATE TABLE proceso_saga_secundaria3 (
    orden_id      VARCHAR2(36)  NOT NULL,
    ref_paso7     VARCHAR2(100) NOT NULL,
    ref_ejecucion VARCHAR2(100),
    CONSTRAINT pk_proceso_saga_secundaria3 PRIMARY KEY (orden_id),
    CONSTRAINT fk_proceso_saga_secundaria3_proceso FOREIGN KEY (orden_id)
        REFERENCES proceso (orden_id)
);
