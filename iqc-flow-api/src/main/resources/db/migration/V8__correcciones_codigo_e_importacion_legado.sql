-- Incremento 7: correcciones del código de certificado (según certificados reales) e importación del histórico
-- de la plataforma anterior de certificaciones (Laravel/MariaDB), desde un volcado y nunca contra producción.

-- La edición 2025 de ISO 37001 agrega la letra "A" al final del código (el código de sistema 01 no distingue ediciones)
ALTER TABLE ediciones_norma ADD COLUMN sufijo_codigo VARCHAR(2);
UPDATE ediciones_norma SET sufijo_codigo = 'A' WHERE norma_codigo = 'ISO_37001' AND edicion = '2025';

-- En IQC Global un mismo código cubre varias normas (sistema integrado): la unicidad es código + norma
DROP INDEX ux_certificados_codigo;
CREATE UNIQUE INDEX ux_certificados_codigo ON certificados (organizacion_id, codigo, norma_codigo)
  WHERE estado NOT IN ('REEMPLAZADO', 'CANCELADO');

-- Origen de los registros importados (permite reejecutar la importación sin duplicar)
ALTER TABLE expedientes
  ADD COLUMN fuente_legado VARCHAR(40),
  ADD COLUMN id_legado     VARCHAR(120);
CREATE UNIQUE INDEX ux_expedientes_legado ON expedientes (organizacion_id, fuente_legado, id_legado)
  WHERE id_legado IS NOT NULL;

ALTER TABLE certificados
  ADD COLUMN fuente_legado        VARCHAR(40),
  ADD COLUMN id_legado            VARCHAR(60),
  -- PDF del certificado en el almacén anterior (Google Cloud Storage); se conserva allí (ADR-005)
  ADD COLUMN url_documento_legado VARCHAR(2048);
CREATE UNIQUE INDEX ux_certificados_legado ON certificados (organizacion_id, fuente_legado, id_legado)
  WHERE id_legado IS NOT NULL;

-- QR ya impresos: la consulta pública abre https://consultas.iqcperu.com/<encriptacion_id> (ADR-003)
CREATE TABLE qr_legado (
  encriptacion_id VARCHAR(60)  PRIMARY KEY,
  id_qr           VARCHAR(40)  NOT NULL,
  organizacion_id UUID         NOT NULL REFERENCES organizaciones (id),
  certificado_id  UUID         NOT NULL REFERENCES certificados (id),
  activo          BOOLEAN      NOT NULL,
  registrado_en   TIMESTAMPTZ,
  importado_en    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_qr_legado_certificado ON qr_legado (certificado_id);
