-- Incremento 3: tareas de seguimiento, historial, actividades con clientes y notificaciones internas.

CREATE TABLE tipos_tarea (
  codigo                VARCHAR(40)  PRIMARY KEY,
  nombre                VARCHAR(120) NOT NULL,
  prioridad_por_defecto VARCHAR(10)  NOT NULL CHECK (prioridad_por_defecto IN ('BAJA', 'NORMAL', 'ALTA', 'URGENTE')),
  orden                 INTEGER      NOT NULL DEFAULT 0,
  activo                BOOLEAN      NOT NULL DEFAULT TRUE
);

-- Catálogo inicial basado en PROCESO COMERCIAL.xlsx y el cuadro de control de operaciones
INSERT INTO tipos_tarea (codigo, nombre, prioridad_por_defecto, orden) VALUES
  ('SEGUIMIENTO_COMERCIAL',   'Seguimiento comercial',           'NORMAL', 10),
  ('RECORDATORIO_COTIZACION', 'Recordatorio de cotización',      'ALTA',   20),
  ('LLAMADA',                 'Llamada',                         'NORMAL', 30),
  ('REUNION',                 'Reunión',                         'NORMAL', 40),
  ('ENVIO_DOCUMENTO',         'Envío de documento',              'NORMAL', 50),
  ('SEGUIMIENTO_PAGO',        'Seguimiento de pago',             'ALTA',   60),
  ('PROGRAMAR_AUDITORIA',     'Programar auditoría',             'ALTA',   70),
  ('SEGUIMIENTO_PAQUETE',     'Seguimiento de paquete a India',  'NORMAL', 80),
  ('ENVIO_CARTA',             'Envío de carta',                  'NORMAL', 90),
  ('OTRO',                    'Otro',                            'NORMAL', 999);

CREATE TABLE tareas_seguimiento (
  id                     UUID PRIMARY KEY,
  organizacion_id        UUID          NOT NULL REFERENCES organizaciones (id),
  titulo                 VARCHAR(200)  NOT NULL,
  descripcion            VARCHAR(2000),
  tipo_codigo            VARCHAR(40)   NOT NULL REFERENCES tipos_tarea (codigo),
  -- Registro al que se refiere la tarea (CLIENTE hoy; COTIZACION, EXPEDIENTE, AUDITORIA más adelante)
  sujeto_tipo            VARCHAR(40),
  sujeto_id              UUID,
  cliente_id             UUID          REFERENCES clientes (id),
  responsable_usuario_id UUID          NOT NULL REFERENCES usuarios (id),
  prioridad              VARCHAR(10)   NOT NULL CHECK (prioridad IN ('BAJA', 'NORMAL', 'ALTA', 'URGENTE')),
  estado                 VARCHAR(15)   NOT NULL CHECK (estado IN ('PENDIENTE', 'EN_CURSO', 'EN_ESPERA', 'COMPLETADA', 'CANCELADA')),
  -- Fecha en hora de Lima; la hora es opcional ("para el jueves" o "el jueves a las 10:00")
  fecha_limite           DATE          NOT NULL,
  hora_limite            TIME,
  resultado              VARCHAR(30),
  notas_resultado        VARCHAR(2000),
  cerrada_en             TIMESTAMPTZ,
  cerrada_por            UUID          REFERENCES usuarios (id),
  reprogramaciones       INTEGER       NOT NULL DEFAULT 0,
  aviso_vencimiento_en   TIMESTAMPTZ,
  nivel_escalamiento     INTEGER       NOT NULL DEFAULT 0,
  origen                 VARCHAR(15)   NOT NULL CHECK (origen IN ('MANUAL', 'AUTOMATIZACION', 'IA')),
  clave_deduplicacion    VARCHAR(200),
  creado_por             UUID          REFERENCES usuarios (id),
  creado_en              TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actualizado_en         TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version                BIGINT        NOT NULL DEFAULT 0,
  CONSTRAINT ck_tareas_sujeto_completo CHECK ((sujeto_tipo IS NULL) = (sujeto_id IS NULL)),
  CONSTRAINT ck_tareas_cierre CHECK ((estado IN ('COMPLETADA', 'CANCELADA')) = (cerrada_en IS NOT NULL))
);
-- Una automatización no puede crear dos veces la misma tarea abierta
CREATE UNIQUE INDEX ux_tareas_deduplicacion ON tareas_seguimiento (organizacion_id, clave_deduplicacion)
  WHERE clave_deduplicacion IS NOT NULL AND estado IN ('PENDIENTE', 'EN_CURSO', 'EN_ESPERA');
CREATE INDEX ix_tareas_responsable ON tareas_seguimiento (organizacion_id, responsable_usuario_id, estado, fecha_limite);
CREATE INDEX ix_tareas_cliente ON tareas_seguimiento (cliente_id);
CREATE INDEX ix_tareas_sujeto ON tareas_seguimiento (sujeto_tipo, sujeto_id);
CREATE INDEX ix_tareas_por_avisar ON tareas_seguimiento (fecha_limite)
  WHERE estado IN ('PENDIENTE', 'EN_CURSO', 'EN_ESPERA') AND aviso_vencimiento_en IS NULL;

CREATE TABLE historial_tareas (
  id               UUID PRIMARY KEY,
  tarea_id         UUID          NOT NULL REFERENCES tareas_seguimiento (id) ON DELETE CASCADE,
  evento           VARCHAR(20)   NOT NULL CHECK (evento IN ('CREADA', 'EDITADA', 'ESTADO_CAMBIADO', 'REASIGNADA',
                                                           'REPROGRAMADA', 'COMPLETADA', 'CANCELADA')),
  desde            VARCHAR(300),
  hacia            VARCHAR(300),
  motivo           VARCHAR(2000),
  actor_usuario_id UUID          REFERENCES usuarios (id),
  ocurrido_en      TIMESTAMPTZ   NOT NULL
);
CREATE INDEX ix_historial_tarea ON historial_tareas (tarea_id, ocurrido_en);

CREATE TABLE actividades (
  id              UUID PRIMARY KEY,
  organizacion_id UUID          NOT NULL REFERENCES organizaciones (id),
  cliente_id      UUID          NOT NULL REFERENCES clientes (id),
  tipo            VARCHAR(15)   NOT NULL CHECK (tipo IN ('LLAMADA', 'CORREO', 'REUNION', 'WHATSAPP', 'VISITA', 'NOTA')),
  direccion       VARCHAR(10)   NOT NULL CHECK (direccion IN ('ENTRANTE', 'SALIENTE', 'INTERNA')),
  ocurrida_en     TIMESTAMPTZ   NOT NULL,
  resumen         VARCHAR(2000) NOT NULL,
  resultado       VARCHAR(200),
  contacto_id     UUID          REFERENCES contactos_cliente (id) ON DELETE SET NULL,
  tarea_id        UUID          REFERENCES tareas_seguimiento (id) ON DELETE SET NULL,
  origen          VARCHAR(15)   NOT NULL CHECK (origen IN ('MANUAL', 'AUTOMATIZACION', 'IA')),
  creado_por      UUID          REFERENCES usuarios (id),
  creado_en       TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_actividades_cliente ON actividades (cliente_id, ocurrida_en DESC);

CREATE TABLE notificaciones (
  id                      UUID PRIMARY KEY,
  organizacion_id         UUID          NOT NULL REFERENCES organizaciones (id),
  destinatario_usuario_id UUID          NOT NULL REFERENCES usuarios (id),
  tipo                    VARCHAR(40)   NOT NULL,
  titulo                  VARCHAR(200)  NOT NULL,
  cuerpo                  VARCHAR(1000),
  sujeto_tipo             VARCHAR(40),
  sujeto_id               UUID,
  -- Evita avisar dos veces por el mismo hecho, aunque el proceso se ejecute de nuevo
  clave_deduplicacion     VARCHAR(200)  UNIQUE,
  leida_en                TIMESTAMPTZ,
  creado_en               TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actualizado_en          TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version                 BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX ix_notificaciones_destinatario ON notificaciones (destinatario_usuario_id, leida_en, creado_en DESC);

INSERT INTO permisos (codigo, modulo, descripcion) VALUES
  ('seguimientos:leer',       'seguimientos', 'Consultar las tareas propias y las de los clientes a cargo'),
  ('seguimientos:leer-todos', 'seguimientos', 'Consultar todas las tareas de la organización'),
  ('seguimientos:escribir',   'seguimientos', 'Crear tareas propias y registrar actividades'),
  ('seguimientos:asignar',    'seguimientos', 'Asignar y reasignar tareas a otras personas');

-- MASTER y ADMINISTRADOR: todos; COORDINADOR_AUDITORIAS: leer, escribir y asignar;
-- COMERCIAL y RESPONSABLE_CERTIFICACIONES: leer y escribir
INSERT INTO roles_permisos (rol_id, permiso_codigo)
SELECT r.id, p.codigo
FROM roles r
JOIN permisos p ON p.modulo = 'seguimientos'
WHERE r.organizacion_id IS NULL
  AND (r.codigo IN ('MASTER', 'ADMINISTRADOR')
    OR (r.codigo = 'COORDINADOR_AUDITORIAS' AND p.codigo <> 'seguimientos:leer-todos')
    OR (r.codigo IN ('COMERCIAL', 'RESPONSABLE_CERTIFICACIONES')
        AND p.codigo IN ('seguimientos:leer', 'seguimientos:escribir')));
