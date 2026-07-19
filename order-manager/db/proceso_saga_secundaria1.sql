-- Tabla satélite 1:1 con orden (antes 1:1 con proceso, fusionada en
-- orden.sql): el contexto propio de la saga secundaria 1 (antes en el CLOB
-- proceso.contexto). Sigue llamándose proceso_saga_secundaria1 (no
-- orden_saga_secundaria1), ver el comentario de proceso_auditoria.sql sobre
-- por qué no se renombra. Sin ON DELETE CASCADE (prohibido, ver CLAUDE.md):
-- el borrado de huérfanos es explícito, en el adaptador de persistencia,
-- hijas antes que padre.

CREATE TABLE proceso_saga_secundaria1 (
    orden_id         VARCHAR2(36)  NOT NULL,
    ref_paso1        VARCHAR2(100) NOT NULL,
    ref_inicio       VARCHAR2(100),
    ref_confirmacion VARCHAR2(100),
    CONSTRAINT pk_proceso_saga_secundaria1 PRIMARY KEY (orden_id),
    CONSTRAINT fk_proceso_saga_secundaria1_orden FOREIGN KEY (orden_id)
        REFERENCES orden (orden_id)
);
