-- Incremento 8a: panel de certificados, marcas por país, PDF del certificado, QR y publicación a la consulta pública.
-- Una sola organización; cada país muestra su propia marca (IQC Perú, IQC Ecuador, IQC Colombia...) y su propio QR.

-- Países de IQC LATAM que faltaban en el catálogo
INSERT INTO paises VALUES
  ('CO', 'Colombia', 'NIT', 'COP', 'IVA',   19.00, 'Departamento', 'Municipio', 'Barrio o vereda', 'Código DANE', 'America/Bogota', TRUE),
  ('PA', 'Panamá',   'RUC', 'USD', 'ITBMS',  7.00, 'Provincia',    'Distrito',  'Corregimiento',   NULL,          'America/Panama', TRUE);

-- Nuevas categorías de documentos: el PDF del certificado y el logo de una marca
ALTER TABLE documentos DROP CONSTRAINT documentos_categoria_check;
ALTER TABLE documentos ADD CONSTRAINT documentos_categoria_check CHECK (categoria IN ('ACTA_COMITE_IMPARCIALIDAD',
  'DECLARACION_CONFLICTO', 'CHECKLIST_SUSPENSION', 'TRADUCCION_CERTIFICADO', 'CERTIFICADO_PDF', 'LOGO_MARCA', 'OTRO'));

-- Marca de cada país: nombre visible, membrete y dirección de consulta que lleva el QR. Sin fila, se usa la
-- marca predeterminada del país (ver MarcasPredeterminadas)
CREATE TABLE marcas_pais (
  organizacion_id   UUID          NOT NULL REFERENCES organizaciones (id),
  codigo_pais       VARCHAR(2)    NOT NULL REFERENCES paises (codigo),
  nombre            VARCHAR(80)   NOT NULL,
  url_consulta      VARCHAR(255)  NOT NULL CHECK (url_consulta LIKE 'https://%/'),
  sitio_web         VARCHAR(255),
  correo            VARCHAR(255),
  telefono          VARCHAR(60),
  direccion         VARCHAR(500),
  logo_documento_id UUID          REFERENCES documentos (id),
  actualizado_por   UUID          REFERENCES usuarios (id),
  actualizado_en    TIMESTAMPTZ   NOT NULL,
  version           BIGINT        NOT NULL DEFAULT 0,
  PRIMARY KEY (organizacion_id, codigo_pais)
);

-- pais_marca: marca elegida a mano; nulo = la del país del cliente. documento_pdf_id: PDF vigente del certificado
ALTER TABLE certificados
  ADD COLUMN pais_marca       VARCHAR(2) REFERENCES paises (codigo),
  ADD COLUMN documento_pdf_id UUID       REFERENCES documentos (id);

-- Historial de versiones del PDF de cada certificado
CREATE TABLE certificados_pdf (
  id             UUID PRIMARY KEY,
  certificado_id UUID          NOT NULL REFERENCES certificados (id),
  documento_id   UUID          NOT NULL REFERENCES documentos (id),
  numero         INTEGER       NOT NULL CHECK (numero > 0),
  motivo         VARCHAR(500),
  subido_por     UUID          NOT NULL REFERENCES usuarios (id),
  subido_en      TIMESTAMPTZ   NOT NULL,
  UNIQUE (certificado_id, numero)
);

-- Estado de la publicación de cada certificado en la base pública de consulta. La huella (SHA-256 de lo
-- publicado) permite detectar cambios sin depender de eventos: lo que cambió se vuelve a publicar
CREATE TABLE publicaciones_certificados (
  certificado_id    UUID PRIMARY KEY REFERENCES certificados (id),
  huella            CHAR(64),
  publicado_en      TIMESTAMPTZ,
  intentos_fallidos INTEGER       NOT NULL DEFAULT 0,
  ultimo_error      VARCHAR(1000),
  ultimo_intento_en TIMESTAMPTZ
);
