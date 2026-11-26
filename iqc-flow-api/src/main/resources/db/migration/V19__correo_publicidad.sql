-- Nuevo tipo de correo: PUBLICIDAD (correo masivo y no deseado). Solo amplía lo que admite el CHECK del tipo; no toca ni reclasifica
-- nada de lo ya guardado (eso lo hace, a pedido, POST /api/v1/correos/bandeja/reclasificacion)
ALTER TABLE correos_recibidos DROP CONSTRAINT correos_recibidos_tipo_check;
ALTER TABLE correos_recibidos ADD CONSTRAINT correos_recibidos_tipo_check
  CHECK (tipo IN ('COTIZACION', 'PAGO', 'AUDITORIA', 'CERTIFICADO', 'RECLAMO', 'PUBLICIDAD', 'OTRO'));
