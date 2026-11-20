-- Base pública de consulta de certificados (consultas.iqcperu.com y consultas.iqc-latam.com).
-- Solo contiene lo que se puede mostrar. La escribe únicamente el panel IQC Flow; el servicio de consulta la lee
-- con un usuario de solo lectura (ver README). No tiene datos internos: ni expedientes, ni contactos, ni auditorías.

CREATE TABLE certificados_publicos (
  id                       UUID PRIMARY KEY,
  -- Marca con la que se muestra (IQC Perú, IQC Ecuador...) y su dirección de consulta
  marca_pais               VARCHAR(2)    NOT NULL,
  marca_nombre             VARCHAR(80)   NOT NULL,
  url_consulta             VARCHAR(255)  NOT NULL,
  codigo                   VARCHAR(60)   NOT NULL,
  esquema                  VARCHAR(20)   NOT NULL,
  organismo_acreditador    VARCHAR(200)  NOT NULL,
  norma                    VARCHAR(20)   NOT NULL,
  norma_designacion        VARCHAR(60)   NOT NULL,
  alcance                  VARCHAR(4000),
  -- EMITIDO, SUSPENDIDO, RETIRADO, CANCELADO o REEMPLAZADO. La vigencia se calcula con la fecha de expiración
  estado                   VARCHAR(15)   NOT NULL,
  titular_razon_social     VARCHAR(255)  NOT NULL,
  titular_tipo_documento   VARCHAR(10),
  titular_numero_documento VARCHAR(30),
  titular_pais             VARCHAR(2)    NOT NULL,
  fecha_otorgamiento       DATE,
  fecha_emision            DATE,
  fecha_expiracion         DATE,
  -- PDF: en documentos_publicos, o en el almacén anterior para los certificados importados
  url_documento_legado     VARCHAR(2048),
  tiene_pdf                BOOLEAN       NOT NULL DEFAULT FALSE,
  actualizado_en           TIMESTAMPTZ   NOT NULL
);
CREATE INDEX ix_publicos_codigo ON certificados_publicos (upper(codigo));
CREATE INDEX ix_publicos_documento ON certificados_publicos (titular_numero_documento);

-- Identificadores de los QR: los impresos de la plataforma anterior (encriptacionID) y los nuevos
CREATE TABLE qr_publicos (
  token          VARCHAR(80) PRIMARY KEY,
  certificado_id UUID        NOT NULL REFERENCES certificados_publicos (id) ON DELETE CASCADE,
  origen         VARCHAR(10) NOT NULL CHECK (origen IN ('LEGADO', 'SISTEMA'))
);
CREATE INDEX ix_qr_publicos_certificado ON qr_publicos (certificado_id);

CREATE TABLE documentos_publicos (
  certificado_id UUID PRIMARY KEY REFERENCES certificados_publicos (id) ON DELETE CASCADE,
  tipo_contenido VARCHAR(100) NOT NULL,
  nombre         VARCHAR(255) NOT NULL,
  sha256         CHAR(64)     NOT NULL,
  contenido      BYTEA        NOT NULL,
  actualizado_en TIMESTAMPTZ  NOT NULL
);
