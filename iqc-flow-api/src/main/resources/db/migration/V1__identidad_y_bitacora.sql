-- Incremento 1: organizaciones usuarias, identidad (usuarios, roles, permisos) y bitácora.

CREATE TABLE organizaciones (
  id             UUID PRIMARY KEY,
  codigo         VARCHAR(40)  NOT NULL UNIQUE,
  nombre         VARCHAR(200) NOT NULL,
  codigo_pais    VARCHAR(2)   NOT NULL DEFAULT 'PE',
  zona_horaria   VARCHAR(60)  NOT NULL DEFAULT 'America/Lima',
  estado         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVA' CHECK (estado IN ('ACTIVA', 'INACTIVA')),
  creado_en      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actualizado_en TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version        BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE usuarios (
  id                      UUID PRIMARY KEY,
  organizacion_id         UUID         NOT NULL REFERENCES organizaciones (id),
  -- El correo se guarda normalizado en minúsculas y es único en todo el sistema
  correo                  VARCHAR(320) NOT NULL UNIQUE CHECK (correo = lower(correo)),
  nombre                  VARCHAR(200) NOT NULL,
  hash_contrasena         VARCHAR(255) NOT NULL,
  estado                  VARCHAR(20)  NOT NULL CHECK (estado IN ('ACTIVO', 'DESHABILITADO')),
  debe_cambiar_contrasena BOOLEAN      NOT NULL DEFAULT TRUE,
  mfa_habilitado          BOOLEAN      NOT NULL DEFAULT FALSE,
  mfa_secreto_cifrado     VARCHAR(512),
  intentos_fallidos       INTEGER      NOT NULL DEFAULT 0,
  bloqueado_hasta         TIMESTAMPTZ,
  ultimo_acceso_en        TIMESTAMPTZ,
  contrasena_cambiada_en  TIMESTAMPTZ,
  creado_en               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actualizado_en          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version                 BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX ix_usuarios_organizacion ON usuarios (organizacion_id);

-- organizacion_id NULL = rol del sistema disponible para todas las organizaciones
CREATE TABLE roles (
  id              UUID PRIMARY KEY,
  organizacion_id UUID REFERENCES organizaciones (id),
  codigo          VARCHAR(60)  NOT NULL,
  nombre          VARCHAR(120) NOT NULL,
  descripcion     VARCHAR(500),
  es_sistema      BOOLEAN      NOT NULL DEFAULT FALSE,
  creado_en       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actualizado_en  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version         BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_roles_codigo_sistema ON roles (codigo) WHERE organizacion_id IS NULL;
CREATE UNIQUE INDEX ux_roles_codigo_organizacion ON roles (organizacion_id, codigo) WHERE organizacion_id IS NOT NULL;

-- Código con formato recurso:acción[-alcance], p. ej. 'usuarios:leer'
CREATE TABLE permisos (
  codigo      VARCHAR(80) PRIMARY KEY,
  modulo      VARCHAR(40)  NOT NULL,
  descripcion VARCHAR(255) NOT NULL
);

CREATE TABLE roles_permisos (
  rol_id         UUID        NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
  permiso_codigo VARCHAR(80) NOT NULL REFERENCES permisos (codigo),
  PRIMARY KEY (rol_id, permiso_codigo)
);

CREATE TABLE usuarios_roles (
  usuario_id  UUID        NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
  rol_id      UUID        NOT NULL REFERENCES roles (id),
  asignado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (usuario_id, rol_id)
);

-- Bitácora de actividad: solo inserción
CREATE TABLE bitacora (
  id                        UUID PRIMARY KEY,
  organizacion_id           UUID REFERENCES organizaciones (id),
  ocurrido_en               TIMESTAMPTZ  NOT NULL,
  actor_usuario_id          UUID,
  tipo_actor                VARCHAR(20)  NOT NULL CHECK (tipo_actor IN ('USUARIO', 'SISTEMA', 'AUTOMATIZACION', 'ASISTENTE_IA')),
  origen                    VARCHAR(20)  NOT NULL CHECK (origen IN ('WEB', 'API', 'PROGRAMADOR', 'IA', 'MIGRACION', 'CONSOLA')),
  accion                    VARCHAR(80)  NOT NULL,
  entidad_tipo              VARCHAR(80),
  entidad_id                VARCHAR(200),
  estado_anterior           JSONB,
  estado_nuevo              JSONB,
  resultado                 VARCHAR(20)  NOT NULL CHECK (resultado IN ('EXITO', 'DENEGADO', 'FALLIDO')),
  motivo                    VARCHAR(500),
  id_correlacion            VARCHAR(64),
  interaccion_ia_id         UUID,
  confirmado_por_usuario_id UUID,
  direccion_ip              VARCHAR(45),
  agente_usuario            VARCHAR(500)
);
CREATE INDEX ix_bitacora_entidad ON bitacora (entidad_tipo, entidad_id);
CREATE INDEX ix_bitacora_ocurrido ON bitacora (organizacion_id, ocurrido_en DESC);
CREATE INDEX ix_bitacora_actor ON bitacora (actor_usuario_id, ocurrido_en DESC);

CREATE FUNCTION bitacora_inmutable() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'La bitácora es de solo inserción';
END
$$;

CREATE TRIGGER tg_bitacora_inmutable
  BEFORE UPDATE OR DELETE ON bitacora
  FOR EACH ROW EXECUTE FUNCTION bitacora_inmutable();
