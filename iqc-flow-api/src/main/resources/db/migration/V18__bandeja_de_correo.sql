-- Bandeja de correo: lo que llega a los buzones, su clasificación y lo que se responde desde IQC Flow (25/11/2026)

INSERT INTO permisos (codigo, modulo, descripcion) VALUES
  ('correos:leer',      'notificaciones', 'Ver la bandeja de correo: los mensajes que llegan a los buzones de IQC'),
  ('correos:responder', 'notificaciones', 'Clasificar, asignar y responder los correos de la bandeja, enviar correos nuevos y revisar los buzones');

INSERT INTO roles_permisos (rol_id, permiso_codigo)
SELECT r.id, p.codigo FROM roles r CROSS JOIN permisos p
WHERE r.organizacion_id IS NULL AND r.codigo = 'MASTER' AND p.codigo IN ('correos:leer', 'correos:responder')
  AND NOT EXISTS (SELECT 1 FROM roles_permisos x WHERE x.rol_id = r.id AND x.permiso_codigo = p.codigo);

INSERT INTO roles_permisos (rol_id, permiso_codigo)
SELECT r.id, p.codigo FROM roles r CROSS JOIN permisos p
WHERE r.organizacion_id IS NULL AND r.codigo IN ('ADMINISTRADOR', 'ADMINISTRACION_LOGISTICA', 'COMERCIAL')
  AND p.codigo IN ('correos:leer', 'correos:responder')
  AND NOT EXISTS (SELECT 1 FROM roles_permisos x WHERE x.rol_id = r.id AND x.permiso_codigo = p.codigo);

-- Un correo recibido. Se identifica por la cuenta y el UID del servidor (válido mientras no cambie uid_validity) y, para no
-- repetirlo si el servidor renumera, por su Message-ID. Solo se guarda el texto: nunca HTML, y los adjuntos van como datos
CREATE TABLE correos_recibidos (
  id               UUID PRIMARY KEY,
  organizacion_id  UUID          NOT NULL REFERENCES organizaciones (id),
  cuenta           VARCHAR(40)   NOT NULL,
  uid_validity     BIGINT        NOT NULL,
  uid              BIGINT        NOT NULL,
  message_id       VARCHAR(500),
  in_reply_to      VARCHAR(500),
  referencias      TEXT,
  remitente_correo VARCHAR(320)  NOT NULL,
  remitente_nombre VARCHAR(255),
  para             TEXT,
  cc               TEXT,
  asunto           VARCHAR(998)  NOT NULL DEFAULT '',
  fecha            TIMESTAMPTZ   NOT NULL,
  texto            TEXT          NOT NULL DEFAULT '',
  texto_truncado   BOOLEAN       NOT NULL DEFAULT FALSE,
  adjuntos         JSONB         NOT NULL DEFAULT '[]'::jsonb,
  tamano_bytes     BIGINT        NOT NULL DEFAULT 0,
  tipo             VARCHAR(20)   NOT NULL CHECK (tipo IN ('COTIZACION', 'PAGO', 'AUDITORIA', 'CERTIFICADO', 'RECLAMO', 'OTRO')),
  origen_tipo      VARCHAR(10)   NOT NULL CHECK (origen_tipo IN ('REGLA', 'MANUAL', 'IA')),
  motivo_tipo      VARCHAR(200),
  estado           VARCHAR(15)   NOT NULL DEFAULT 'NUEVO' CHECK (estado IN ('NUEVO', 'EN_CURSO', 'ATENDIDO', 'DESCARTADO')),
  responsable_id   UUID          REFERENCES usuarios (id),
  cliente_id       UUID          REFERENCES clientes (id),
  tiene_eml        BOOLEAN       NOT NULL DEFAULT FALSE,
  version          BIGINT        NOT NULL DEFAULT 0,
  creado_en        TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actualizado_en   TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_correo_servidor UNIQUE (cuenta, uid_validity, uid),
  CONSTRAINT uq_correo_message_id UNIQUE (cuenta, message_id)
);
CREATE INDEX ix_correos_bandeja ON correos_recibidos (organizacion_id, fecha DESC);
CREATE INDEX ix_correos_filtro ON correos_recibidos (organizacion_id, estado, tipo, fecha DESC);
CREATE INDEX ix_correos_cliente ON correos_recibidos (cliente_id) WHERE cliente_id IS NOT NULL;
CREATE INDEX ix_correos_hilo ON correos_recibidos (organizacion_id, message_id) WHERE message_id IS NOT NULL;
CREATE INDEX ix_correos_texto ON correos_recibidos USING GIN (to_tsvector('spanish', texto));

-- El mensaje original completo (.eml), aparte para que listar la bandeja no lo cargue. Los adjuntos se sirven desde él
CREATE TABLE correos_eml (
  correo_id UUID PRIMARY KEY REFERENCES correos_recibidos (id) ON DELETE CASCADE,
  contenido BYTEA NOT NULL
);

-- Lo que se envía desde IQC Flow: respuestas a un correo recibido (correo_recibido_id) o correos nuevos
CREATE TABLE correos_enviados (
  id                 UUID PRIMARY KEY,
  organizacion_id    UUID          NOT NULL REFERENCES organizaciones (id),
  correo_recibido_id UUID          REFERENCES correos_recibidos (id),
  cuenta             VARCHAR(40)   NOT NULL,
  message_id         VARCHAR(500)  NOT NULL,
  in_reply_to        VARCHAR(500),
  para               VARCHAR(320)  NOT NULL,
  asunto             VARCHAR(998)  NOT NULL,
  texto              TEXT          NOT NULL,
  enviado_por        UUID          NOT NULL REFERENCES usuarios (id),
  enviado_en         TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_correos_enviados_recibido ON correos_enviados (correo_recibido_id) WHERE correo_recibido_id IS NOT NULL;
CREATE INDEX ix_correos_enviados_fecha ON correos_enviados (organizacion_id, enviado_en DESC);

-- Hasta dónde se leyó cada buzón y cómo salió la última pasada (el error es un texto corto, sin datos de la cuenta)
CREATE TABLE correos_sincronizacion (
  cuenta          VARCHAR(40) PRIMARY KEY,
  uid_validity    BIGINT,
  ultimo_uid      BIGINT       NOT NULL DEFAULT 0,
  ultima_pasada   TIMESTAMPTZ,
  nuevos_ultima   INTEGER      NOT NULL DEFAULT 0,
  ultimo_error    VARCHAR(300)
);
