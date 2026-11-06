-- Incremento 6: documentos adjuntos, parámetros de la organización, declaraciones de conflicto de interés,
-- decisiones de certificación (DDC-PRO-003 / DDC-FMR-008), certificados por norma y condiciones de entrega.

-- Archivos adjuntos (actas, declaraciones firmadas, checklists, traducciones). El contenido vive en el almacén
-- configurado (hoy, disco local); aquí solo los metadatos y la huella para verificar su integridad
CREATE TABLE documentos (
  id              UUID PRIMARY KEY,
  organizacion_id UUID          NOT NULL REFERENCES organizaciones (id),
  categoria       VARCHAR(40)   NOT NULL CHECK (categoria IN ('ACTA_COMITE_IMPARCIALIDAD', 'DECLARACION_CONFLICTO',
                                                            'CHECKLIST_SUSPENSION', 'TRADUCCION_CERTIFICADO', 'OTRO')),
  nombre_original VARCHAR(255)  NOT NULL,
  tipo_contenido  VARCHAR(100)  NOT NULL,
  tamano_bytes    BIGINT        NOT NULL CHECK (tamano_bytes > 0),
  sha256          CHAR(64)      NOT NULL,
  ruta_almacen    VARCHAR(500)  NOT NULL UNIQUE,
  subido_por      UUID          NOT NULL REFERENCES usuarios (id),
  creado_en       TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_documentos_organizacion ON documentos (organizacion_id, categoria);

-- Parámetros configurables por organización (política de firma, y más adelante la visibilidad del portal, ADR-032)
CREATE TABLE parametros_organizacion (
  organizacion_id UUID          NOT NULL REFERENCES organizaciones (id),
  clave           VARCHAR(60)   NOT NULL,
  valor           VARCHAR(200)  NOT NULL,
  actualizado_por UUID          REFERENCES usuarios (id),
  actualizado_en  TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (organizacion_id, clave)
);

-- Declaración de conflicto de interés de cada miembro del equipo, por auditoría. La firma el auditor desde su
-- usuario o la registra el coordinador en su nombre con la declaración firmada adjunta
CREATE TABLE declaraciones_conflicto (
  id                UUID PRIMARY KEY,
  auditoria_id      UUID          NOT NULL REFERENCES auditorias (id) ON DELETE CASCADE,
  auditor_id        UUID          NOT NULL REFERENCES auditores (id),
  tiene_conflicto   BOOLEAN       NOT NULL,
  detalle           VARCHAR(2000),
  modo              VARCHAR(25)   NOT NULL CHECK (modo IN ('FIRMA_PROPIA', 'EN_NOMBRE_DEL_AUDITOR')),
  documento_id      UUID          REFERENCES documentos (id),
  registrado_por    UUID          NOT NULL REFERENCES usuarios (id),
  declarado_en      TIMESTAMPTZ   NOT NULL,
  creado_en         TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actualizado_en    TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version           BIGINT        NOT NULL DEFAULT 0,
  UNIQUE (auditoria_id, auditor_id),
  CONSTRAINT ck_declaracion_documento CHECK (modo = 'FIRMA_PROPIA' OR documento_id IS NOT NULL),
  CONSTRAINT ck_declaracion_detalle CHECK (NOT tiene_conflicto OR detalle IS NOT NULL)
);

-- Registro de decisiones (DDC-FMR-008) y control general de decisiones (DDC-FRM-002)
CREATE TABLE decisiones_certificacion (
  id                     UUID PRIMARY KEY,
  expediente_id          UUID          NOT NULL REFERENCES expedientes (id),
  tipo                   VARCHAR(25)   NOT NULL CHECK (tipo IN ('OTORGAMIENTO', 'RECHAZO', 'MANTENIMIENTO', 'RENOVACION',
                                                              'AMPLIACION', 'REDUCCION', 'SUSPENSION', 'RESTAURACION',
                                                              'RETIRO', 'TRANSFERENCIA', 'CANCELACION_VOLUNTARIA')),
  clasificacion          VARCHAR(30)   NOT NULL CHECK (clasificacion IN ('CONFORME', 'OBSERVADO', 'ACEPTABLE_CON_OBSERVACIONES')),
  via                    VARCHAR(25)   NOT NULL CHECK (via IN ('DECISOR', 'COMITE_IMPARCIALIDAD')),
  -- Quien decide (vía DECISOR); en la vía del comité queda el acta como documento
  decisor_usuario_id     UUID          REFERENCES usuarios (id),
  acta_documento_id      UUID          REFERENCES documentos (id),
  experto_imparcialidad  VARCHAR(200),
  recomendacion_experto  VARCHAR(4000),
  -- Anexo 1 de DDC-PRO-003, obligatorio antes de suspender o retirar
  checklist_documento_id UUID          REFERENCES documentos (id),
  fecha_decision         DATE          NOT NULL,
  fundamento             VARCHAR(4000) NOT NULL,
  nuevo_alcance          VARCHAR(4000),
  registrado_por         UUID          NOT NULL REFERENCES usuarios (id),
  creado_en              TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actualizado_en         TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version                BIGINT        NOT NULL DEFAULT 0,
  CONSTRAINT ck_decision_via CHECK (
    (via = 'DECISOR' AND decisor_usuario_id IS NOT NULL)
    OR (via = 'COMITE_IMPARCIALIDAD' AND acta_documento_id IS NOT NULL)),
  CONSTRAINT ck_decision_checklist CHECK (tipo NOT IN ('SUSPENSION', 'RETIRO') OR checklist_documento_id IS NOT NULL)
);
CREATE INDEX ix_decisiones_expediente ON decisiones_certificacion (expediente_id, fecha_decision);

-- Normas a las que se aplica la decisión (todas las del expediente si no se indica otra cosa)
CREATE TABLE decisiones_normas (
  decision_id  UUID        NOT NULL REFERENCES decisiones_certificacion (id) ON DELETE CASCADE,
  norma_codigo VARCHAR(20) NOT NULL REFERENCES normas (codigo),
  PRIMARY KEY (decision_id, norma_codigo)
);

-- Un certificado por norma. El estado administrativo (suspendido, retirado…) es independiente de la vigencia
CREATE TABLE certificados (
  id                    UUID PRIMARY KEY,
  organizacion_id       UUID          NOT NULL REFERENCES organizaciones (id),
  expediente_id         UUID          NOT NULL REFERENCES expedientes (id),
  cliente_id            UUID          NOT NULL REFERENCES clientes (id),
  esquema_codigo        VARCHAR(20)   NOT NULL REFERENCES esquemas_acreditacion (codigo),
  norma_codigo          VARCHAR(20)   NOT NULL,
  edicion               VARCHAR(20)   NOT NULL,
  -- Código único: generado (INACAL/SAE) o el enviado por India (IQC Global)
  codigo                VARCHAR(60)   NOT NULL,
  -- Año de la decisión de otorgamiento (parte D); una ampliación o reducción conserva el del ciclo
  anio_decision         INTEGER,
  emitido_por_india     BOOLEAN       NOT NULL DEFAULT FALSE,
  revision_alcance      INTEGER       NOT NULL CHECK (revision_alcance BETWEEN 1 AND 9),
  ciclo                 INTEGER       NOT NULL CHECK (ciclo BETWEEN 1 AND 9),
  alcance               VARCHAR(4000),
  estado                VARCHAR(15)   NOT NULL CHECK (estado IN ('BORRADOR', 'REVISADO', 'EMITIDO', 'SUSPENDIDO',
                                                             'RETIRADO', 'CANCELADO', 'REEMPLAZADO')),
  decision_id           UUID          REFERENCES decisiones_certificacion (id),
  reemplaza_id          UUID          REFERENCES certificados (id),
  revisado_por          UUID          REFERENCES usuarios (id),
  revisado_en           TIMESTAMPTZ,
  firma_gerencia_por    UUID          REFERENCES usuarios (id),
  firma_gerencia_en     TIMESTAMPTZ,
  firma_jefe_tecnico_por UUID         REFERENCES usuarios (id),
  firma_jefe_tecnico_en TIMESTAMPTZ,
  fecha_otorgamiento    DATE,
  fecha_emision         DATE,
  fecha_expiracion      DATE,
  version_documento     VARCHAR(30),
  numero_version        INTEGER       NOT NULL DEFAULT 1,
  -- Identificador aleatorio para la consulta pública por QR (no revela el id interno)
  token_verificacion    UUID          NOT NULL UNIQUE,
  traduccion_documento_id UUID        REFERENCES documentos (id),
  entregado_en          TIMESTAMPTZ,
  medio_entrega         VARCHAR(10)   CHECK (medio_entrega IN ('DIGITAL', 'FISICA')),
  creado_en             TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actualizado_en        TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version               BIGINT        NOT NULL DEFAULT 0,
  FOREIGN KEY (norma_codigo, edicion) REFERENCES ediciones_norma (norma_codigo, edicion),
  CONSTRAINT ck_certificado_emision CHECK (estado IN ('BORRADOR', 'REVISADO', 'CANCELADO') OR fecha_emision IS NOT NULL)
);
-- El código es único entre los certificados vigentes o en preparación; una reemisión (V.02) conserva el código
CREATE UNIQUE INDEX ux_certificados_codigo ON certificados (organizacion_id, codigo)
  WHERE estado NOT IN ('REEMPLAZADO', 'CANCELADO');
CREATE INDEX ix_certificados_expediente ON certificados (expediente_id);
CREATE INDEX ix_certificados_cliente ON certificados (cliente_id, norma_codigo);

-- Condiciones de entrega (DDC-PRO-003): pagos y documentos administrativos conformes, encuesta aplicada
ALTER TABLE expedientes
  ADD COLUMN pagos_conformes        BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN encuesta_aplicada      BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN condiciones_por        UUID REFERENCES usuarios (id),
  ADD COLUMN condiciones_en         TIMESTAMPTZ;

-- Tareas automáticas de los plazos de suspensión (restauración en 6 meses, retiro al año)
INSERT INTO tipos_tarea (codigo, nombre, prioridad_por_defecto, orden) VALUES
  ('SEGUIMIENTO_SUSPENSION', 'Seguimiento de suspensión', 'ALTA', 75);

INSERT INTO roles (id, organizacion_id, codigo, nombre, descripcion, es_sistema) VALUES
  ('01900000-0000-7000-8000-000000000006', NULL, 'ADMINISTRACION_LOGISTICA', 'Administración y logística',
   'Conformidad de pagos, encuesta de satisfacción y entrega de certificados', TRUE);

INSERT INTO permisos (codigo, modulo, descripcion) VALUES
  ('decisiones:registrar',          'auditorias',     'Registrar decisiones de certificación (sujeto al control de imparcialidad)'),
  ('certificados:gestionar',        'auditorias',     'Preparar, revisar y registrar certificados'),
  ('certificados:firmar-gerencia',  'auditorias',     'Firmar certificados como Gerencia General'),
  ('certificados:firmar-jefe-tecnico','auditorias',   'Firmar certificados como Jefe Técnico'),
  ('entregas:gestionar',            'auditorias',     'Registrar la conformidad de pagos, la encuesta y la entrega de certificados'),
  ('documentos:subir',              'documentos',     'Adjuntar documentos (actas, declaraciones, checklists, traducciones)'),
  ('organizacion:configurar',       'organizaciones', 'Cambiar los parámetros de la organización');

-- MASTER: todo. ADMINISTRADOR: gestión de certificados, firma de Gerencia y entrega.
-- RESPONSABLE_CERTIFICACIONES (Jefe Técnico): decide, gestiona y firma como Jefe Técnico.
-- COORDINADOR_AUDITORIAS: adjunta documentos (declaraciones firmadas). ADMINISTRACION_LOGISTICA: entrega
INSERT INTO roles_permisos (rol_id, permiso_codigo)
SELECT r.id, p.codigo FROM roles r CROSS JOIN permisos p
WHERE r.organizacion_id IS NULL AND (
     (r.codigo = 'MASTER' AND p.modulo IN ('auditorias', 'documentos', 'organizaciones')
        AND p.codigo NOT IN ('expedientes:leer', 'expedientes:gestionar'))
  OR (r.codigo = 'ADMINISTRADOR' AND p.codigo IN ('certificados:gestionar', 'certificados:firmar-gerencia',
        'entregas:gestionar', 'documentos:subir'))
  OR (r.codigo = 'RESPONSABLE_CERTIFICACIONES' AND p.codigo IN ('decisiones:registrar', 'certificados:gestionar',
        'certificados:firmar-jefe-tecnico', 'documentos:subir'))
  OR (r.codigo = 'COORDINADOR_AUDITORIAS' AND p.codigo = 'documentos:subir')
  OR (r.codigo = 'ADMINISTRACION_LOGISTICA' AND p.codigo IN ('entregas:gestionar', 'expedientes:leer')));
