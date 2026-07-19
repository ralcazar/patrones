-- Tabla satélite 1:1 con orden (antes 1:1 con proceso, fusionada en
-- orden.sql): el contexto propio de la saga secundaria 2 (antes en el CLOB
-- proceso.contexto). Sigue llamándose proceso_saga_secundaria2 (no
-- orden_saga_secundaria2), ver el comentario de proceso_auditoria.sql sobre
-- por qué no se renombra. Sin ON DELETE CASCADE (prohibido, ver CLAUDE.md):
-- el borrado de huérfanos es explícito, en el adaptador de persistencia,
-- hijas antes que padre.

CREATE TABLE proceso_saga_secundaria2 (
    orden_id      VARCHAR2(36)  NOT NULL,
    ref_paso5     VARCHAR2(100) NOT NULL,
    ref_respuesta VARCHAR2(100),
    CONSTRAINT pk_proceso_saga_secundaria2 PRIMARY KEY (orden_id),
    CONSTRAINT fk_proceso_saga_secundaria2_orden FOREIGN KEY (orden_id)
        REFERENCES orden (orden_id)
);
