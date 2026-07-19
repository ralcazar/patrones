-- Tabla satélite 1:1 con orden (antes 1:1 con proceso, fusionada en
-- orden.sql): el contexto propio de la saga secundaria 3 (antes en el CLOB
-- proceso.contexto). Sigue llamándose proceso_saga_secundaria3 (no
-- orden_saga_secundaria3), ver el comentario de proceso_auditoria.sql sobre
-- por qué no se renombra. Sin ON DELETE CASCADE (prohibido, ver CLAUDE.md):
-- el borrado de huérfanos es explícito, en el adaptador de persistencia,
-- hijas antes que padre.

CREATE TABLE proceso_saga_secundaria3 (
    orden_id      VARCHAR2(36)  NOT NULL,
    ref_paso7     VARCHAR2(100) NOT NULL,
    ref_ejecucion VARCHAR2(100),
    CONSTRAINT pk_proceso_saga_secundaria3 PRIMARY KEY (orden_id),
    CONSTRAINT fk_proceso_saga_secundaria3_orden FOREIGN KEY (orden_id)
        REFERENCES orden (orden_id)
);
