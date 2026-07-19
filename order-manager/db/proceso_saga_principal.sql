-- Tabla satélite 1:1 con orden (antes 1:1 con proceso, fusionada en
-- orden.sql): el contexto propio de la saga principal (antes en el CLOB
-- proceso.contexto). Sigue llamándose proceso_saga_principal (no
-- orden_saga_principal), ver el comentario de proceso_auditoria.sql sobre por
-- qué no se renombra. Sin ON DELETE CASCADE (prohibido, ver CLAUDE.md): el
-- borrado de huérfanos es explícito, en el adaptador de persistencia, hijas
-- antes que padre. La FK a datos_negocio es solo de integridad (el borrado de
-- datos_negocio no lo gestiona esta tabla).

CREATE TABLE proceso_saga_principal (
    orden_id        VARCHAR2(36)  NOT NULL,
    datosnegocio_id VARCHAR2(36)  NOT NULL,
    ref_paso1       VARCHAR2(100),
    ref_paso2       VARCHAR2(100),
    ref_paso3       VARCHAR2(100),
    ref_paso4       VARCHAR2(100),
    ref_paso5       VARCHAR2(100),
    ref_paso6       VARCHAR2(100),
    ref_paso7       VARCHAR2(100),
    ref_paso8       VARCHAR2(100),
    CONSTRAINT pk_proceso_saga_principal PRIMARY KEY (orden_id),
    CONSTRAINT fk_proceso_saga_principal_orden FOREIGN KEY (orden_id)
        REFERENCES orden (orden_id),
    CONSTRAINT fk_proceso_saga_principal_datos_negocio FOREIGN KEY (datosnegocio_id)
        REFERENCES datos_negocio (datosnegocio_id)
);
