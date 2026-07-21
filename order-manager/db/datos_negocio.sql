-- Tabla del agregado DatosNegocio: SOLO escalares (datos de negocio de la
-- tramitación, correlacionados por external_id con la saga principal). Los
-- documentos (blobs) van en datos_negocio_documento.sql.

CREATE TABLE datos_negocio (
    datosnegocio_id VARCHAR2(36)  NOT NULL,
    external_id     VARCHAR2(36)  NOT NULL,
    dato_negocio1   NUMBER(10)    NOT NULL,
    dato_negocio2   DATE          NOT NULL,
    dato_negocio3   VARCHAR2(400) NOT NULL,
    purgado_en      TIMESTAMP     NULL,
    CONSTRAINT pk_datos_negocio PRIMARY KEY (datosnegocio_id)
);

CREATE UNIQUE INDEX idx_datos_negocio_external_id ON datos_negocio (external_id);
