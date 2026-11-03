-- Incremento 2: clientes (prospectos y clientes en una sola entidad), contactos y direcciones.

CREATE SEQUENCE clientes_codigo_seq;

CREATE TABLE clientes (
  id                     UUID PRIMARY KEY,
  organizacion_id        UUID          NOT NULL REFERENCES organizaciones (id),
  codigo                 VARCHAR(20)   NOT NULL UNIQUE
                         DEFAULT ('CLI-' || lpad(nextval('clientes_codigo_seq')::text, 6, '0')),
  razon_social           VARCHAR(300)  NOT NULL,
  nombre_comercial       VARCHAR(300),
  codigo_pais            VARCHAR(2)    NOT NULL DEFAULT 'PE',
  tipo_documento         VARCHAR(10)   CHECK (tipo_documento IN ('RUC', 'NIT', 'RFC', 'RUT', 'CUIT', 'GSTIN', 'OTRO')),
  numero_documento       VARCHAR(30),
  estado                 VARCHAR(20)   NOT NULL CHECK (estado IN ('PROSPECTO', 'ACTIVO', 'INACTIVO')),
  responsable_usuario_id UUID          NOT NULL REFERENCES usuarios (id),
  sector                 VARCHAR(120),
  sitio_web              VARCHAR(300),
  origen                 VARCHAR(30)   CHECK (origen IN ('REFERIDO', 'SITIO_WEB', 'LLAMADA', 'CORREO', 'EVENTO',
                                                       'REDES_SOCIALES', 'CLIENTE_ANTIGUO', 'OTRO')),
  notas                  VARCHAR(4000),
  -- Razón social, nombre comercial y documento en minúsculas y sin tildes, para la búsqueda
  texto_busqueda         VARCHAR(1000) NOT NULL,
  fuente_legado          VARCHAR(40),
  id_legado              VARCHAR(60),
  creado_por             UUID          REFERENCES usuarios (id),
  archivado_en           TIMESTAMPTZ,
  creado_en              TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actualizado_en         TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version                BIGINT        NOT NULL DEFAULT 0,
  CONSTRAINT ck_clientes_documento_completo CHECK ((tipo_documento IS NULL) = (numero_documento IS NULL)),
  CONSTRAINT ck_clientes_activo_con_documento CHECK (estado <> 'ACTIVO' OR numero_documento IS NOT NULL)
);
CREATE UNIQUE INDEX ux_clientes_documento ON clientes (organizacion_id, codigo_pais, tipo_documento, numero_documento)
  WHERE archivado_en IS NULL AND numero_documento IS NOT NULL;
CREATE UNIQUE INDEX ux_clientes_legado ON clientes (organizacion_id, fuente_legado, id_legado) WHERE id_legado IS NOT NULL;
CREATE INDEX ix_clientes_responsable ON clientes (organizacion_id, responsable_usuario_id) WHERE archivado_en IS NULL;

CREATE TABLE contactos_cliente (
  id              UUID PRIMARY KEY,
  cliente_id      UUID         NOT NULL REFERENCES clientes (id) ON DELETE CASCADE,
  nombre_completo VARCHAR(200) NOT NULL,
  cargo           VARCHAR(120),
  correo          VARCHAR(320),
  telefono        VARCHAR(40),
  -- Lista separada por comas: REPRESENTANTE_LEGAL, GERENCIA, CALIDAD, COMERCIAL, FACTURACION, OPERACIONES, OTRO
  roles           VARCHAR(200) NOT NULL DEFAULT '',
  es_principal    BOOLEAN      NOT NULL DEFAULT FALSE,
  activo          BOOLEAN      NOT NULL DEFAULT TRUE,
  notas           VARCHAR(1000),
  creado_en       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actualizado_en  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version         BIGINT       NOT NULL DEFAULT 0,
  CONSTRAINT ck_contactos_medio CHECK (correo IS NOT NULL OR telefono IS NOT NULL)
);
CREATE INDEX ix_contactos_cliente ON contactos_cliente (cliente_id);
CREATE UNIQUE INDEX ux_contactos_principal ON contactos_cliente (cliente_id) WHERE es_principal;

CREATE TABLE direcciones_cliente (
  id             UUID PRIMARY KEY,
  cliente_id     UUID         NOT NULL REFERENCES clientes (id) ON DELETE CASCADE,
  tipo           VARCHAR(20)  NOT NULL CHECK (tipo IN ('FISCAL', 'SEDE', 'CORRESPONDENCIA')),
  nombre         VARCHAR(120),
  direccion      VARCHAR(300) NOT NULL,
  distrito       VARCHAR(100),
  provincia      VARCHAR(100),
  departamento   VARCHAR(100),
  codigo_pais    VARCHAR(2)   NOT NULL DEFAULT 'PE',
  ubigeo         VARCHAR(6)   CHECK (ubigeo ~ '^[0-9]{6}$'),
  referencia     VARCHAR(300),
  creado_en      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actualizado_en TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version        BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX ix_direcciones_cliente ON direcciones_cliente (cliente_id);
CREATE UNIQUE INDEX ux_direcciones_fiscal ON direcciones_cliente (cliente_id) WHERE tipo = 'FISCAL';

INSERT INTO permisos (codigo, modulo, descripcion) VALUES
  ('clientes:leer',            'clientes', 'Consultar todos los clientes de la organización'),
  ('clientes:escribir',        'clientes', 'Registrar clientes y editar los clientes propios'),
  ('clientes:escribir-todos',  'clientes', 'Editar cualquier cliente aunque tenga otro responsable'),
  ('clientes:reasignar',       'clientes', 'Cambiar el responsable comercial de un cliente'),
  ('clientes:archivar',        'clientes', 'Archivar y restaurar clientes');

-- MASTER: todos; ADMINISTRADOR: todos; COMERCIAL: leer y editar los suyos;
-- COORDINADOR_AUDITORIAS y RESPONSABLE_CERTIFICACIONES: solo lectura
INSERT INTO roles_permisos (rol_id, permiso_codigo)
SELECT r.id, p.codigo
FROM roles r
JOIN permisos p ON p.modulo = 'clientes'
WHERE r.organizacion_id IS NULL
  AND (r.codigo IN ('MASTER', 'ADMINISTRADOR')
    OR (r.codigo = 'COMERCIAL' AND p.codigo IN ('clientes:leer', 'clientes:escribir'))
    OR (r.codigo IN ('COORDINADOR_AUDITORIAS', 'RESPONSABLE_CERTIFICACIONES') AND p.codigo = 'clientes:leer'));
