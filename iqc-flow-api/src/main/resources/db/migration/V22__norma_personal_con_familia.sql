-- La norma de referencia de los certificados personales se guarda con su familia («ISO 37001»), como la deja el lector de
-- PDF y la lista del formulario. Los importados de la plataforma anterior quedaron solo con el número («37001») y la
-- plantilla del PDF los imprimía sin «ISO». El dominio ya normaliza lo nuevo (CertificadoPersonal.normalizarNorma)
UPDATE certificados_personales SET norma_referencia = 'ISO ' || btrim(norma_referencia)
WHERE norma_referencia ~ '^\s*[0-9]';
