-- Certificados de personas publicados para la consulta (Perú o LATAM según el país de la persona).
-- token_pdf mantiene las URL ya impresas: consultas.iqcperu.com/personal-pdf/{token}
CREATE TABLE personales_publicos (
  id                   UUID PRIMARY KEY,
  marca_pais           VARCHAR(2)    NOT NULL,
  marca_nombre         VARCHAR(80)   NOT NULL,
  url_consulta         VARCHAR(255)  NOT NULL,
  codigo               VARCHAR(64)   NOT NULL,
  titular_nombres      VARCHAR(255)  NOT NULL,
  titular_tipo_documento   VARCHAR(20) NOT NULL,
  titular_numero_documento VARCHAR(20) NOT NULL,
  curso                VARCHAR(255)  NOT NULL,
  norma_referencia     VARCHAR(120),
  norma_anio           VARCHAR(4),
  subtitulo            VARCHAR(255),
  descripcion          TEXT,
  duracion_horas       NUMERIC(6, 2),
  modalidad            VARCHAR(50),
  fecha_inicio         DATE,
  fecha_fin            DATE,
  fecha_emision        DATE          NOT NULL,
  entrenador           VARCHAR(255),
  -- EMITIDO o ANULADO
  estado               VARCHAR(15)   NOT NULL,
  token_pdf            UUID          NOT NULL UNIQUE,
  tiene_pdf            BOOLEAN       NOT NULL DEFAULT FALSE,
  actualizado_en       TIMESTAMPTZ   NOT NULL
);
CREATE INDEX ix_personales_publicos_codigo ON personales_publicos (upper(codigo));
CREATE INDEX ix_personales_publicos_documento ON personales_publicos (titular_numero_documento);

CREATE TABLE documentos_personales_publicos (
  certificado_id UUID PRIMARY KEY REFERENCES personales_publicos (id) ON DELETE CASCADE,
  tipo_contenido VARCHAR(100) NOT NULL,
  nombre         VARCHAR(255) NOT NULL,
  sha256         CHAR(64)     NOT NULL,
  contenido      BYTEA        NOT NULL,
  actualizado_en TIMESTAMPTZ  NOT NULL
);
