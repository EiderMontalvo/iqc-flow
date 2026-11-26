-- Cartas a clientes generadas desde las plantillas Word de plantillas/cartas (certificación, continuidad, suspensión
-- por falta de pago y carta libre) y firma digitalizada de cada persona para firmarlas (26/11/2026)

-- Nuevas categorías de documentos: el PDF de una carta y la imagen de la firma de una persona
ALTER TABLE documentos DROP CONSTRAINT documentos_categoria_check;
ALTER TABLE documentos ADD CONSTRAINT documentos_categoria_check CHECK (categoria IN ('ACTA_COMITE_IMPARCIALIDAD',
  'DECLARACION_CONFLICTO', 'CHECKLIST_SUSPENSION', 'TRADUCCION_CERTIFICADO', 'CERTIFICADO_PDF', 'LOGO_MARCA',
  'CARTA_PDF', 'FIRMA_USUARIO', 'OTRO'));

-- Firma de cada persona: la sube ella misma desde su perfil. nombre_mostrado: cómo firma («Fiorella A. Tenorio
-- Alfaro»); si es nulo se usa su nombre de usuario
CREATE TABLE firmas_usuario (
  usuario_id       UUID PRIMARY KEY REFERENCES usuarios (id),
  organizacion_id  UUID         NOT NULL REFERENCES organizaciones (id),
  documento_id     UUID         NOT NULL REFERENCES documentos (id),
  nombre_mostrado  VARCHAR(160),
  cargo            VARCHAR(120) NOT NULL,
  actualizado_en   TIMESTAMPTZ  NOT NULL
);

-- Carta emitida: número correlativo CAR-AAAA-0001, el PDF generado y los datos con los que se llenó la plantilla
-- (permiten volver a generar el Word para corregirlo a mano)
CREATE TABLE cartas (
  id                UUID PRIMARY KEY,
  organizacion_id   UUID         NOT NULL REFERENCES organizaciones (id),
  numero            VARCHAR(20)  NOT NULL,
  tipo              VARCHAR(30)  NOT NULL CHECK (tipo IN ('CERTIFICACION', 'CONTINUIDAD', 'SUSPENSION_PAGO', 'LIBRE')),
  cliente_id        UUID         NOT NULL REFERENCES clientes (id),
  asunto            VARCHAR(400) NOT NULL,
  fecha             DATE         NOT NULL,
  firmante_id       UUID         REFERENCES usuarios (id),
  datos             JSONB        NOT NULL,
  documento_pdf_id  UUID         NOT NULL REFERENCES documentos (id),
  creado_por        UUID         NOT NULL REFERENCES usuarios (id),
  creado_en         TIMESTAMPTZ  NOT NULL,
  UNIQUE (organizacion_id, numero)
);
CREATE INDEX cartas_organizacion_fecha ON cartas (organizacion_id, creado_en DESC);
CREATE INDEX cartas_cliente ON cartas (cliente_id);

INSERT INTO permisos (codigo, modulo, descripcion) VALUES
  ('cartas:leer',    'cartas', 'Ver las cartas emitidas a clientes y descargarlas'),
  ('cartas:generar', 'cartas', 'Redactar y emitir cartas a clientes desde las plantillas');

-- MASTER recibe todos los permisos al arrancar (PermisosPorParametros). Emiten cartas Administrador, Jefatura
-- técnica, Certificados y Administración (la suspensión por falta de pago); Comercial las consulta
INSERT INTO roles_permisos (rol_id, permiso_codigo)
SELECT r.id, p.codigo FROM roles r JOIN permisos p ON
     (r.codigo IN ('ADMINISTRADOR', 'RESPONSABLE_CERTIFICACIONES', 'CERTIFICADOS', 'ADMINISTRACION_LOGISTICA')
        AND p.codigo IN ('cartas:leer', 'cartas:generar'))
  OR (r.codigo = 'COMERCIAL' AND p.codigo = 'cartas:leer')
WHERE r.organizacion_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM roles_permisos x WHERE x.rol_id = r.id AND x.permiso_codigo = p.codigo);
