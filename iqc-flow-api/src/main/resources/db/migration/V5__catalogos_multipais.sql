-- Incremento multipaís: catálogos de países, esquemas de acreditación y normas con sus ediciones.
-- Los valores son configuración inicial y podrán editarse desde la administración.

CREATE TABLE paises (
  codigo                    VARCHAR(2)   PRIMARY KEY,
  nombre                    VARCHAR(100) NOT NULL,
  documento_fiscal          VARCHAR(10)  NOT NULL,
  moneda                    VARCHAR(3)   NOT NULL,
  nombre_impuesto           VARCHAR(20)  NOT NULL,
  tasa_impuesto             NUMERIC(5,2) NOT NULL CHECK (tasa_impuesto >= 0 AND tasa_impuesto < 100),
  -- Nombres de los niveles de la dirección y del código de ubicación en cada país
  etiqueta_nivel1           VARCHAR(40)  NOT NULL,
  etiqueta_nivel2           VARCHAR(40)  NOT NULL,
  etiqueta_nivel3           VARCHAR(40)  NOT NULL,
  etiqueta_codigo_ubicacion VARCHAR(40),
  zona_horaria              VARCHAR(60)  NOT NULL,
  activo                    BOOLEAN      NOT NULL DEFAULT TRUE
);

INSERT INTO paises VALUES
  ('PE', 'Perú',    'RUC', 'PEN', 'IGV', 18.00, 'Departamento', 'Provincia', 'Distrito',  'Ubigeo',     'America/Lima',      TRUE),
  ('EC', 'Ecuador', 'RUC', 'USD', 'IVA', 15.00, 'Provincia',    'Cantón',    'Parroquia', 'Código DPA', 'America/Guayaquil', TRUE);

CREATE TABLE esquemas_acreditacion (
  codigo                VARCHAR(20)  PRIMARY KEY,
  nombre                VARCHAR(120) NOT NULL,
  organismo_acreditador VARCHAR(200) NOT NULL,
  pais_acreditador      VARCHAR(2),
  -- Parte B del código único del certificado (DDC-PRO-003 §4.2.5.1)
  prefijo_certificado   VARCHAR(5)   UNIQUE,
  anios_ciclo           INTEGER      NOT NULL DEFAULT 3 CHECK (anios_ciclo > 0),
  vigilancias_por_ciclo INTEGER      NOT NULL DEFAULT 2 CHECK (vigilancias_por_ciclo >= 0),
  -- NULL: por confirmar quién toma la decisión de certificación en este esquema
  decide_iqc_peru       BOOLEAN,
  notas                 VARCHAR(1000),
  activo                BOOLEAN      NOT NULL DEFAULT TRUE
);

INSERT INTO esquemas_acreditacion
  (codigo, nombre, organismo_acreditador, pais_acreditador, prefijo_certificado, decide_iqc_peru, notas) VALUES
  ('INACAL', 'Acreditación INACAL', 'INACAL - Instituto Nacional de Calidad', 'PE', 'INA', TRUE,
   'Decisión del Jefe Técnico según DDC-PRO-003.'),
  ('SAE', 'Acreditación SAE', 'SAE - Servicio de Acreditación Ecuatoriano', 'EC', 'SAE', TRUE,
   'Certificados de empresas ecuatorianas emitidos por IQC Perú (código con prefijo PE-SAE).'),
  ('IQC_GLOBAL', 'IQC Global (India)', 'JAS-ANZ, miembro de IAF', NULL, NULL, NULL,
   'Por confirmar: quién decide, prefijo del código y reglas del ciclo.');

CREATE TABLE normas (
  codigo             VARCHAR(20)  PRIMARY KEY,
  nombre_corto       VARCHAR(40)  NOT NULL,
  titulo             VARCHAR(200) NOT NULL,
  -- Parte E del código único del certificado (DDC-PRO-003): 01 a 05
  codigo_sistema     VARCHAR(2)   NOT NULL UNIQUE,
  -- Abreviatura usada en el cuadro de control de operaciones (9, 14, 45, 27, 37)
  codigo_operaciones VARCHAR(5)   UNIQUE,
  orden              INTEGER      NOT NULL DEFAULT 0,
  activo             BOOLEAN      NOT NULL DEFAULT TRUE
);

INSERT INTO normas (codigo, nombre_corto, titulo, codigo_sistema, codigo_operaciones, orden) VALUES
  ('ISO_37001',     'ISO 37001',     'Sistemas de Gestión Antisoborno',                              '01', '37', 50),
  ('ISO_9001',      'ISO 9001',      'Sistemas de Gestión de la Calidad',                            '02', '9',  10),
  ('ISO_14001',     'ISO 14001',     'Sistemas de Gestión Ambiental',                                '03', '14', 20),
  ('ISO_IEC_27001', 'ISO/IEC 27001', 'Sistemas de Gestión de Seguridad de la Información',           '04', '27', 40),
  ('ISO_45001',     'ISO 45001',     'Sistemas de Gestión de la Seguridad y Salud en el Trabajo',    '05', '45', 30);

CREATE TABLE ediciones_norma (
  id           UUID PRIMARY KEY,
  norma_codigo VARCHAR(20)  NOT NULL REFERENCES normas (codigo),
  edicion      VARCHAR(20)  NOT NULL,
  -- Si se puede usar en nuevas auditorías y certificados
  vigente      BOOLEAN      NOT NULL DEFAULT TRUE,
  notas        VARCHAR(500),
  UNIQUE (norma_codigo, edicion)
);

INSERT INTO ediciones_norma (id, norma_codigo, edicion, vigente, notas) VALUES
  ('01900000-0000-7000-8000-000000000101', 'ISO_37001', '2016', TRUE,
   'Confirmar si aún se audita con esta edición.'),
  ('01900000-0000-7000-8000-000000000102', 'ISO_37001', '2025', TRUE,
   'Edición indicada en DDC-PRO-003 v11.'),
  ('01900000-0000-7000-8000-000000000103', 'ISO_9001', '2015', TRUE, NULL),
  ('01900000-0000-7000-8000-000000000104', 'ISO_14001', '2015', TRUE, NULL),
  ('01900000-0000-7000-8000-000000000105', 'ISO_IEC_27001', '2013', FALSE,
   'La transición internacional a la edición 2022 venció en octubre de 2025. NTP-ISO/IEC 27001:2014 es su adopción peruana.'),
  ('01900000-0000-7000-8000-000000000106', 'ISO_IEC_27001', '2022', TRUE, NULL),
  ('01900000-0000-7000-8000-000000000107', 'ISO_45001', '2018', TRUE, NULL);
