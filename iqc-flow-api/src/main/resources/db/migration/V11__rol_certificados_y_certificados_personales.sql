-- Espacio de Certificados (20/11/2026): rol propio para la operación diaria de certificados empresariales y
-- personales, y certificados de personas (cursos y competencias), que reemplazan a «personas» y
-- «certificados_personales» de la plataforma anterior.

-- Lectura de certificados sin acceso a expedientes, auditorías ni decisiones
INSERT INTO permisos (codigo, modulo, descripcion) VALUES
  ('certificados:leer', 'certificaciones', 'Consultar certificados empresariales y personales, sus QR y sus PDF');

-- Quien ya leía expedientes sigue viendo los certificados
INSERT INTO roles_permisos (rol_id, permiso_codigo)
SELECT DISTINCT rp.rol_id, 'certificados:leer' FROM roles_permisos rp WHERE rp.permiso_codigo = 'expedientes:leer';
INSERT INTO roles_permisos (rol_id, permiso_codigo)
SELECT r.id, 'certificados:leer' FROM roles r
WHERE r.organizacion_id IS NULL AND r.codigo IN ('MASTER', 'ADMINISTRADOR', 'RESPONSABLE_CERTIFICACIONES')
  AND NOT EXISTS (SELECT 1 FROM roles_permisos x WHERE x.rol_id = r.id AND x.permiso_codigo = 'certificados:leer');

-- Rol Certificados: registra y mantiene certificados empresariales y personales, sus PDF y su publicación.
-- Lee clientes para elegir el titular; no ve expedientes, auditorías, decisiones ni seguimientos.
INSERT INTO roles (id, organizacion_id, codigo, nombre, descripcion, es_sistema) VALUES
  ('01900000-0000-7000-8000-000000000007', NULL, 'CERTIFICADOS', 'Certificados',
   'Registro y consulta de certificados empresariales y personales, sus PDF, QR y publicación', TRUE);
INSERT INTO roles_permisos (rol_id, permiso_codigo) VALUES
  ('01900000-0000-7000-8000-000000000007', 'certificados:leer'),
  ('01900000-0000-7000-8000-000000000007', 'certificados:gestionar'),
  ('01900000-0000-7000-8000-000000000007', 'documentos:subir'),
  ('01900000-0000-7000-8000-000000000007', 'clientes:leer');

-- Personas certificadas. El documento se valida según el país (DNI, CE, cédula, pasaporte...)
CREATE TABLE personas_certificadas (
  id               UUID PRIMARY KEY,
  organizacion_id  UUID         NOT NULL REFERENCES organizaciones (id),
  codigo_pais      VARCHAR(2)   NOT NULL REFERENCES paises (codigo),
  tipo_documento   VARCHAR(20)  NOT NULL,
  numero_documento VARCHAR(20)  NOT NULL,
  nombres          VARCHAR(255) NOT NULL,
  correo           VARCHAR(255),
  telefono         VARCHAR(30),
  fuente_legado    VARCHAR(40),
  id_legado        VARCHAR(60),
  version          BIGINT       NOT NULL DEFAULT 0,
  creado_en        TIMESTAMPTZ  NOT NULL,
  actualizado_en   TIMESTAMPTZ  NOT NULL,
  CONSTRAINT uq_persona_documento UNIQUE (organizacion_id, codigo_pais, tipo_documento, numero_documento),
  CONSTRAINT uq_persona_legado UNIQUE (fuente_legado, id_legado)
);
CREATE INDEX ix_personas_documento ON personas_certificadas (numero_documento);

-- Certificados de personas. token_pdf mantiene las URL ya impresas: consultas.iqcperu.com/personal-pdf/{token}
CREATE TABLE certificados_personales (
  id                   UUID PRIMARY KEY,
  organizacion_id      UUID          NOT NULL REFERENCES organizaciones (id),
  persona_id           UUID          NOT NULL REFERENCES personas_certificadas (id),
  codigo               VARCHAR(64)   NOT NULL,
  curso                VARCHAR(255)  NOT NULL,
  norma_referencia     VARCHAR(120),
  norma_anio           VARCHAR(4),
  subtitulo            VARCHAR(255),
  descripcion          TEXT,
  duracion_horas       NUMERIC(6, 2) CHECK (duracion_horas IS NULL OR duracion_horas > 0),
  modalidad            VARCHAR(50),
  fecha_inicio         DATE,
  fecha_fin            DATE,
  fecha_emision        DATE          NOT NULL,
  entrenador           VARCHAR(255),
  estado               VARCHAR(15)   NOT NULL CHECK (estado IN ('EMITIDO', 'ANULADO')),
  motivo_anulacion     VARCHAR(500),
  documento_pdf_id     UUID REFERENCES documentos (id),
  url_documento_legado VARCHAR(2048),
  token_pdf            UUID          NOT NULL UNIQUE,
  fuente_legado        VARCHAR(40),
  id_legado            VARCHAR(60),
  version              BIGINT        NOT NULL DEFAULT 0,
  creado_en            TIMESTAMPTZ   NOT NULL,
  actualizado_en       TIMESTAMPTZ   NOT NULL,
  CONSTRAINT uq_certificado_personal_codigo UNIQUE (organizacion_id, codigo),
  CONSTRAINT uq_certificado_personal_legado UNIQUE (fuente_legado, id_legado),
  CONSTRAINT ck_certificado_personal_fechas CHECK (fecha_inicio IS NULL OR fecha_fin IS NULL OR fecha_fin >= fecha_inicio),
  CONSTRAINT ck_certificado_personal_anulacion CHECK (estado <> 'ANULADO' OR motivo_anulacion IS NOT NULL)
);
CREATE INDEX ix_cert_personales_persona ON certificados_personales (persona_id);
CREATE INDEX ix_cert_personales_emision ON certificados_personales (organizacion_id, fecha_emision DESC);
