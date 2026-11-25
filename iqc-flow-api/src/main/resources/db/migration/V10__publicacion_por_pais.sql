-- Una base pública de consulta por país de marca (ADR-043). Se guarda en qué país quedó publicado cada certificado,
-- para retirarlo de la base anterior si cambia de marca. Lo publicado en la base única anterior se vuelve a publicar
-- en la base de su país (la huella se borra); esa base única deja de usarse.
ALTER TABLE publicaciones_certificados ADD COLUMN pais_publicado VARCHAR(2);
UPDATE publicaciones_certificados SET huella = NULL;
