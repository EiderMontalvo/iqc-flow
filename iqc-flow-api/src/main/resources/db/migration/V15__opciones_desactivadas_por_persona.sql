-- Opciones de MASTER desactivadas para una persona concreta (24/11/2026). Una opción vale para alguien solo si está
-- encendida para la organización y no figura aquí para esa persona: MASTER puede apagarla a cada uno por separado
CREATE TABLE parametros_desactivados_usuario (
  usuario_id      UUID          NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
  clave           VARCHAR(60)   NOT NULL,
  organizacion_id UUID          NOT NULL REFERENCES organizaciones (id),
  desactivado_por UUID          REFERENCES usuarios (id),
  desactivado_en  TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (usuario_id, clave)
);
CREATE INDEX parametros_desactivados_usuario_org ON parametros_desactivados_usuario (organizacion_id, clave);
