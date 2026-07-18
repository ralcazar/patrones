-- Tabla satélite 1:1 con proceso: el contexto propio de la saga secundaria 2
-- (antes en el CLOB proceso.contexto). Sin ON DELETE CASCADE (prohibido, ver
-- CLAUDE.md): el borrado de huérfanos es explícito, en el adaptador de
-- persistencia, hijas antes que padre.

CREATE TABLE proceso_saga_secundaria2 (
    orden_id      VARCHAR2(36)  NOT NULL,
    ref_paso5     VARCHAR2(100) NOT NULL,
    ref_respuesta VARCHAR2(100),
    CONSTRAINT pk_proceso_saga_secundaria2 PRIMARY KEY (orden_id),
    CONSTRAINT fk_proceso_saga_secundaria2_proceso FOREIGN KEY (orden_id)
        REFERENCES proceso (orden_id)
);
