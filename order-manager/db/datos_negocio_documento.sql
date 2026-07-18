-- Tabla hija de documentos (blobs) de datos_negocio. Sin ON DELETE CASCADE
-- (prohibido, ver CLAUDE.md): el borrado de huérfanos es explícito, en el
-- adaptador de persistencia, hijas antes que padre.

CREATE TABLE datos_negocio_documento (
    datosnegocio_id VARCHAR2(36)  NOT NULL,
    secuencia       NUMBER(10)    NOT NULL,
    nombre          VARCHAR2(200) NOT NULL,
    mime_type       VARCHAR2(100) NOT NULL,
    contenido       BLOB          NOT NULL,
    CONSTRAINT pk_datos_negocio_documento PRIMARY KEY (datosnegocio_id, secuencia),
    CONSTRAINT fk_datos_negocio_documento_datos_negocio FOREIGN KEY (datosnegocio_id)
        REFERENCES datos_negocio (datosnegocio_id)
);
