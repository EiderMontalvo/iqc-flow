-- Configuración global de MASTER, herramientas de registro de certificados, plantillas y correo (21/11/2026)

INSERT INTO permisos (codigo, modulo, descripcion) VALUES
  ('certificados:autocompletar', 'certificaciones', 'Completar los formularios de certificados leyendo el PDF subido'),
  ('certificados:generar-pdf',   'certificaciones', 'Generar el PDF de un certificado desde una plantilla Word'),
  ('plantillas:gestionar',       'documentos',      'Subir y mantener plantillas Word de certificados, cartas y correos'),
  ('correos:enviar',             'notificaciones',  'Enviar certificados y documentos por correo'),
  ('correos:configurar',         'notificaciones',  'Configurar las cuentas de envío de correo');

-- MASTER tiene todos los permisos (los futuros los agrega SincronizacionPermisosMaster al arrancar)
INSERT INTO roles_permisos (rol_id, permiso_codigo)
SELECT r.id, p.codigo FROM roles r CROSS JOIN permisos p
WHERE r.organizacion_id IS NULL AND r.codigo = 'MASTER'
  AND NOT EXISTS (SELECT 1 FROM roles_permisos x WHERE x.rol_id = r.id AND x.permiso_codigo = p.codigo);

-- Herramientas de registro y correo por rol. Administración y logística además registra certificados y gestiona
-- plantillas; MASTER puede quitarle el autocompletado y la generación de PDF (parámetros ADMINISTRACION_*)
INSERT INTO roles_permisos (rol_id, permiso_codigo)
SELECT r.id, p.codigo FROM roles r JOIN permisos p ON
     (r.codigo = 'ADMINISTRADOR' AND p.codigo IN ('certificados:autocompletar', 'certificados:generar-pdf',
        'plantillas:gestionar', 'correos:enviar'))
  OR (r.codigo IN ('CERTIFICADOS', 'RESPONSABLE_CERTIFICACIONES') AND p.codigo IN ('certificados:autocompletar',
        'certificados:generar-pdf', 'correos:enviar'))
  OR (r.codigo = 'ADMINISTRACION_LOGISTICA' AND p.codigo IN ('certificados:leer', 'certificados:gestionar',
        'documentos:subir', 'clientes:leer', 'plantillas:gestionar', 'correos:enviar', 'certificados:autocompletar',
        'certificados:generar-pdf'))
WHERE r.organizacion_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM roles_permisos x WHERE x.rol_id = r.id AND x.permiso_codigo = p.codigo);
