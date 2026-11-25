-- Estado de la publicación de cada certificado personal en la base pública de su país (como publicaciones_certificados)
CREATE TABLE publicaciones_personales (
  certificado_id    UUID PRIMARY KEY REFERENCES certificados_personales (id),
  huella            CHAR(64),
  pais_publicado    VARCHAR(2),
  publicado_en      TIMESTAMPTZ,
  intentos_fallidos INTEGER       NOT NULL DEFAULT 0,
  ultimo_error      VARCHAR(1000),
  ultimo_intento_en TIMESTAMPTZ
);
