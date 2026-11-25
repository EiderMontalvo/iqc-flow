-- Cotizaciones comerciales (24/11/2026), primer paso del proceso comercial (docs/analisis/PROCESO COMERCIAL.xlsx):
-- cotización → envío → respuesta del cliente; después vendrán contrato y pagos.

-- Numeración correlativa por organización, serie y año (COT-2026-0001). La reutilizarán contratos y cartas
CREATE TABLE correlativos (
  organizacion_id UUID        NOT NULL REFERENCES organizaciones (id),
  serie           VARCHAR(20) NOT NULL,
  anio            INTEGER     NOT NULL,
  ultimo          INTEGER     NOT NULL,
  PRIMARY KEY (organizacion_id, serie, anio)
);

CREATE TABLE cotizaciones (
  id                 UUID PRIMARY KEY,
  organizacion_id    UUID           NOT NULL REFERENCES organizaciones (id),
  numero             VARCHAR(20)    NOT NULL,
  cliente_id         UUID           NOT NULL REFERENCES clientes (id),
  contacto_nombre    VARCHAR(200),
  contacto_correo    VARCHAR(320),
  esquema_codigo     VARCHAR(20),
  tipo_servicio      VARCHAR(30)    NOT NULL
    CHECK (tipo_servicio IN ('CERTIFICACION_INICIAL', 'RECERTIFICACION', 'AMPLIACION', 'VIGILANCIA', 'OTRO')),
  alcance            VARCHAR(4000),
  numero_sedes       INTEGER        NOT NULL DEFAULT 1 CHECK (numero_sedes >= 1),
  sedes_detalle      VARCHAR(1000),
  -- Moneda e impuesto del país del cliente al momento de cotizar (catálogo de países, ADR-028)
  moneda             VARCHAR(3)     NOT NULL,
  nombre_impuesto    VARCHAR(20)    NOT NULL,
  tasa_impuesto      NUMERIC(5,2)   NOT NULL,
  -- Tarifa por día auditor: si existe, propone el monto de cada línea (días × tarifa); el monto siempre se puede editar
  tarifa_dia         NUMERIC(12,2)  CHECK (tarifa_dia >= 0),
  subtotal           NUMERIC(14,2)  NOT NULL,
  impuesto           NUMERIC(14,2)  NOT NULL,
  total              NUMERIC(14,2)  NOT NULL,
  validez_dias       INTEGER        NOT NULL DEFAULT 30 CHECK (validez_dias BETWEEN 1 AND 365),
  condiciones_pago   VARCHAR(2000),
  notas              VARCHAR(4000),
  estado             VARCHAR(20)    NOT NULL CHECK (estado IN ('BORRADOR', 'ENVIADA', 'ACEPTADA', 'RECHAZADA', 'ANULADA')),
  fecha_emision      DATE           NOT NULL,
  enviada_en         DATE,
  destinatarios      VARCHAR(1000),
  respondida_en      DATE,
  motivo_respuesta   VARCHAR(1000),
  -- Cuándo se creó la tarea de «sin respuesta» (una sola vez por envío)
  recordatorio_en    TIMESTAMPTZ,
  responsable_id     UUID           NOT NULL REFERENCES usuarios (id),
  creado_por         UUID           REFERENCES usuarios (id),
  creado_en          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actualizado_en     TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version            BIGINT         NOT NULL DEFAULT 0,
  UNIQUE (organizacion_id, numero)
);
CREATE INDEX cotizaciones_org_estado ON cotizaciones (organizacion_id, estado, fecha_emision DESC);
CREATE INDEX cotizaciones_cliente ON cotizaciones (cliente_id);

-- Líneas: una por norma o servicio, con sus días auditor y su monto
CREATE TABLE cotizaciones_items (
  id             UUID PRIMARY KEY,
  cotizacion_id  UUID          NOT NULL REFERENCES cotizaciones (id) ON DELETE CASCADE,
  orden          INTEGER       NOT NULL,
  norma_codigo   VARCHAR(20),
  edicion        VARCHAR(10),
  descripcion    VARCHAR(500)  NOT NULL,
  dias_auditor   NUMERIC(6,2)  NOT NULL DEFAULT 0 CHECK (dias_auditor >= 0),
  monto          NUMERIC(14,2) NOT NULL CHECK (monto >= 0)
);
CREATE INDEX cotizaciones_items_cotizacion ON cotizaciones_items (cotizacion_id, orden);

INSERT INTO permisos (codigo, modulo, descripcion) VALUES
  ('cotizaciones:leer',      'cotizaciones', 'Ver cotizaciones y sus indicadores'),
  ('cotizaciones:gestionar', 'cotizaciones', 'Crear, enviar y registrar la respuesta de cotizaciones');

-- MASTER recibe todos los permisos al arrancar (PermisosPorParametros). Comercial y Administrador gestionan;
-- Administración y logística consulta (los pagos parten de la cotización aceptada)
INSERT INTO roles_permisos (rol_id, permiso_codigo)
SELECT r.id, p.codigo FROM roles r JOIN permisos p ON
     (r.codigo IN ('COMERCIAL', 'ADMINISTRADOR') AND p.codigo IN ('cotizaciones:leer', 'cotizaciones:gestionar'))
  OR (r.codigo = 'ADMINISTRACION_LOGISTICA' AND p.codigo = 'cotizaciones:leer')
WHERE r.organizacion_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM roles_permisos x WHERE x.rol_id = r.id AND x.permiso_codigo = p.codigo);
