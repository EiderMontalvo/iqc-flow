-- Resumen que hizo la IA de un correo: se guarda para no pagar dos veces por lo mismo (el contenido de un correo recibido no cambia)
ALTER TABLE correos_recibidos
  ADD COLUMN resumen_ia        JSONB,
  ADD COLUMN resumen_ia_modelo VARCHAR(60);
