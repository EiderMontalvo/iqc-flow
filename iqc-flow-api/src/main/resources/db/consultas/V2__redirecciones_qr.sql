-- Redirecciones de QR impresos con la dirección de otra marca (ADR-043).
-- El panel anterior imprimió consultas.iqcperu.com en todos los QR, también en los de Ecuador. Como cada país tiene
-- ahora su propia base, la base de Perú guarda a dónde reenviar esos QR (el portal LATAM), y el portal de Perú sigue
-- leyendo solo su base. Las bases de los demás países la tienen vacía.
CREATE TABLE qr_redirecciones (
  token          VARCHAR(80)   PRIMARY KEY,
  certificado_id UUID          NOT NULL,
  marca_pais     VARCHAR(2)    NOT NULL,
  url_destino    VARCHAR(2048) NOT NULL,
  actualizado_en TIMESTAMPTZ   NOT NULL
);
CREATE INDEX ix_qr_redirecciones_certificado ON qr_redirecciones (certificado_id);
