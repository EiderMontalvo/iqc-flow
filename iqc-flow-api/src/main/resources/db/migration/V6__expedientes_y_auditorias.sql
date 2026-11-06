-- Incremento 5: auditores, aliados comerciales, expedientes de certificación, auditorías y equipo auditor.

-- Respuesta de IQC (06/11/2026): en IQC Global decide y emite India, que además envía el código del certificado
UPDATE esquemas_acreditacion
   SET decide_iqc_peru = FALSE,
       notas = 'India decide y emite el certificado, y envía su código. IQC Perú gestiona el flujo (application, proposal, paquetes Fase I y II) y traduce el certificado al español.'
 WHERE codigo = 'IQC_GLOBAL';

CREATE TABLE auditores (
  id              UUID PRIMARY KEY,
  organizacion_id UUID          NOT NULL REFERENCES organizaciones (id),
  nombre_completo VARCHAR(200)  NOT NULL,
  correo          VARCHAR(320),
  telefono        VARCHAR(40),
  codigo_pais     VARCHAR(2)    NOT NULL DEFAULT 'PE',
  -- Roles que puede cumplir: LIDER, AUDITOR, EXPERTO_TECNICO, OBSERVADOR, EN_ENTRENAMIENTO (separados por comas)
  roles           VARCHAR(200)  NOT NULL,
  -- Si además es usuario de IQC Flow (p. ej. el Jefe Técnico también audita): necesario para el control de imparcialidad
  usuario_id      UUID          UNIQUE REFERENCES usuarios (id),
  activo          BOOLEAN       NOT NULL DEFAULT TRUE,
  notas           VARCHAR(1000),
  creado_en       TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actualizado_en  TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version         BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX ix_auditores_organizacion ON auditores (organizacion_id, activo);

CREATE TABLE competencias_auditor (
  auditor_id   UUID        NOT NULL REFERENCES auditores (id) ON DELETE CASCADE,
  norma_codigo VARCHAR(20) NOT NULL REFERENCES normas (codigo),
  PRIMARY KEY (auditor_id, norma_codigo)
);

-- SPONSOR del cuadro de control: aliado que refiere al cliente; la comisión es variable
CREATE TABLE aliados_comerciales (
  id                  UUID PRIMARY KEY,
  organizacion_id     UUID          NOT NULL REFERENCES organizaciones (id),
  codigo              VARCHAR(10)   NOT NULL,
  nombre              VARCHAR(200)  NOT NULL,
  comision_porcentaje NUMERIC(5,2)  CHECK (comision_porcentaje >= 0 AND comision_porcentaje <= 100),
  activo              BOOLEAN       NOT NULL DEFAULT TRUE,
  notas               VARCHAR(1000),
  creado_en           TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actualizado_en      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version             BIGINT        NOT NULL DEFAULT 0,
  UNIQUE (organizacion_id, codigo)
);

CREATE SEQUENCE expedientes_codigo_seq;

CREATE TABLE expedientes (
  id                     UUID PRIMARY KEY,
  organizacion_id        UUID          NOT NULL REFERENCES organizaciones (id),
  codigo                 VARCHAR(20)   NOT NULL UNIQUE
                         DEFAULT ('EXP-' || lpad(nextval('expedientes_codigo_seq')::text, 6, '0')),
  cliente_id             UUID          NOT NULL REFERENCES clientes (id),
  esquema_codigo         VARCHAR(20)   NOT NULL REFERENCES esquemas_acreditacion (codigo),
  tipo_servicio          VARCHAR(30)   NOT NULL CHECK (tipo_servicio IN ('CERTIFICACION_INICIAL', 'RECERTIFICACION', 'AMPLIACION')),
  estado                 VARCHAR(30)   NOT NULL,
  estado_cambiado_en     TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  alcance                VARCHAR(4000),
  aliado_id              UUID          REFERENCES aliados_comerciales (id),
  comision_porcentaje    NUMERIC(5,2)  CHECK (comision_porcentaje >= 0 AND comision_porcentaje <= 100),
  -- Contrato de certificación (parte C del código del certificado) o proposal de India
  numero_contrato        VARCHAR(40),
  -- Código del certificado enviado por India (esquema IQC Global)
  codigo_externo         VARCHAR(60),
  responsable_usuario_id UUID          NOT NULL REFERENCES usuarios (id),
  -- Fecha efectiva de otorgamiento: base del ciclo (vigilancias a 12 y 24 meses, expiración a 36)
  fecha_certificacion    DATE,
  fecha_expiracion       DATE,
  notas                  VARCHAR(4000),
  creado_por             UUID          REFERENCES usuarios (id),
  creado_en              TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actualizado_en         TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version                BIGINT        NOT NULL DEFAULT 0,
  CONSTRAINT ck_expedientes_ciclo CHECK ((fecha_certificacion IS NULL) = (fecha_expiracion IS NULL))
);
CREATE INDEX ix_expedientes_cliente ON expedientes (cliente_id);
CREATE INDEX ix_expedientes_estado ON expedientes (organizacion_id, estado);

-- Normas auditadas juntas en el expediente, con su edición; se emitirá un certificado por norma
CREATE TABLE expedientes_normas (
  expediente_id UUID        NOT NULL REFERENCES expedientes (id) ON DELETE CASCADE,
  norma_codigo  VARCHAR(20) NOT NULL,
  edicion       VARCHAR(20) NOT NULL,
  PRIMARY KEY (expediente_id, norma_codigo),
  FOREIGN KEY (norma_codigo, edicion) REFERENCES ediciones_norma (norma_codigo, edicion)
);

-- Sedes del cliente incluidas en el alcance (certificación multisitio)
CREATE TABLE expedientes_sedes (
  expediente_id UUID NOT NULL REFERENCES expedientes (id) ON DELETE CASCADE,
  direccion_id  UUID NOT NULL REFERENCES direcciones_cliente (id),
  PRIMARY KEY (expediente_id, direccion_id)
);

CREATE TABLE historial_expedientes (
  id               UUID PRIMARY KEY,
  expediente_id    UUID          NOT NULL REFERENCES expedientes (id) ON DELETE CASCADE,
  estado_desde     VARCHAR(30),
  estado_hacia     VARCHAR(30)   NOT NULL,
  motivo           VARCHAR(2000),
  actor_usuario_id UUID          REFERENCES usuarios (id),
  ocurrido_en      TIMESTAMPTZ   NOT NULL
);
CREATE INDEX ix_historial_expediente ON historial_expedientes (expediente_id, ocurrido_en);

CREATE TABLE auditorias (
  id                 UUID PRIMARY KEY,
  expediente_id      UUID          NOT NULL REFERENCES expedientes (id) ON DELETE CASCADE,
  tipo               VARCHAR(20)   NOT NULL CHECK (tipo IN ('ETAPA_1', 'ETAPA_2', 'VIGILANCIA_1', 'VIGILANCIA_2',
                                                          'RECERTIFICACION', 'EXTRAORDINARIA')),
  estado             VARCHAR(15)   NOT NULL CHECK (estado IN ('POR_PROGRAMAR', 'PROGRAMADA', 'EJECUTADA', 'CANCELADA')),
  -- Fecha antes de la cual debe realizarse (vigilancias del ciclo)
  fecha_limite       DATE,
  fecha_inicio_plan  DATE,
  fecha_fin_plan     DATE,
  fecha_real_inicio  DATE,
  fecha_real_fin     DATE,
  dias_auditor       NUMERIC(4,1)  CHECK (dias_auditor > 0),
  modalidad          VARCHAR(10)   CHECK (modalidad IN ('PRESENCIAL', 'REMOTA', 'MIXTA')),
  -- Seguimiento del paquete con India (esquema IQC Global), sobre todo en vigilancias
  estado_paquete     VARCHAR(25)   CHECK (estado_paquete IN ('PAQUETE_RECIBIDO', 'PAQUETE_TERMINADO', 'ENVIADO_A_INDIA',
                                                           'CONTINUA_CERTIFICACION', 'CARTA_SOLICITADA', 'CARTA_ENVIADA')),
  notas              VARCHAR(2000),
  creado_en          TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actualizado_en     TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version            BIGINT        NOT NULL DEFAULT 0,
  CONSTRAINT ck_auditorias_plan CHECK (fecha_fin_plan IS NULL OR fecha_inicio_plan IS NULL OR fecha_fin_plan >= fecha_inicio_plan),
  CONSTRAINT ck_auditorias_real CHECK (fecha_real_fin IS NULL OR fecha_real_inicio IS NULL OR fecha_real_fin >= fecha_real_inicio)
);
-- Cada etapa o vigilancia ocurre una sola vez por expediente (salvo las extraordinarias)
CREATE UNIQUE INDEX ux_auditorias_tipo ON auditorias (expediente_id, tipo)
  WHERE tipo <> 'EXTRAORDINARIA' AND estado <> 'CANCELADA';
CREATE INDEX ix_auditorias_limite ON auditorias (fecha_limite) WHERE estado IN ('POR_PROGRAMAR', 'PROGRAMADA');

CREATE TABLE equipo_auditoria (
  auditoria_id UUID        NOT NULL REFERENCES auditorias (id) ON DELETE CASCADE,
  auditor_id   UUID        NOT NULL REFERENCES auditores (id),
  rol          VARCHAR(20) NOT NULL CHECK (rol IN ('LIDER', 'AUDITOR', 'EXPERTO_TECNICO', 'OBSERVADOR', 'EN_ENTRENAMIENTO')),
  PRIMARY KEY (auditoria_id, auditor_id)
);
CREATE INDEX ix_equipo_auditor ON equipo_auditoria (auditor_id);

INSERT INTO permisos (codigo, modulo, descripcion) VALUES
  ('expedientes:leer',     'auditorias', 'Consultar expedientes, auditorías y vigilancias'),
  ('expedientes:gestionar','auditorias', 'Crear y gestionar expedientes, auditorías, equipos y el ciclo de certificación'),
  ('auditores:leer',       'auditores',  'Consultar auditores, aliados comerciales y competencias'),
  ('auditores:gestionar',  'auditores',  'Registrar y editar auditores y aliados comerciales');

-- MASTER, ADMINISTRADOR y COORDINADOR_AUDITORIAS (Jefe de Operaciones): todos;
-- RESPONSABLE_CERTIFICACIONES (Jefe Técnico) y COMERCIAL: solo lectura
INSERT INTO roles_permisos (rol_id, permiso_codigo)
SELECT r.id, p.codigo
FROM roles r
JOIN permisos p ON p.modulo IN ('auditorias', 'auditores')
WHERE r.organizacion_id IS NULL
  AND (r.codigo IN ('MASTER', 'ADMINISTRADOR', 'COORDINADOR_AUDITORIAS')
    OR (r.codigo IN ('RESPONSABLE_CERTIFICACIONES', 'COMERCIAL') AND p.codigo IN ('expedientes:leer', 'auditores:leer')));
