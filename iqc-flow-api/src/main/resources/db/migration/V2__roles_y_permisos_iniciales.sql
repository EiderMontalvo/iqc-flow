-- Roles del sistema y permisos del Incremento 1. No se crean usuarios.
-- Cada incremento posterior agrega sus propios permisos en su migración.

INSERT INTO permisos (codigo, modulo, descripcion) VALUES
  ('usuarios:leer',      'identidad',  'Consultar usuarios y roles'),
  ('usuarios:gestionar', 'identidad',  'Crear, editar y deshabilitar usuarios'),
  ('roles:asignar',      'identidad',  'Asignar roles a usuarios'),
  ('bitacora:leer',      'compartido', 'Consultar la bitácora de actividad');

INSERT INTO roles (id, organizacion_id, codigo, nombre, descripcion, es_sistema) VALUES
  ('01900000-0000-7000-8000-000000000001', NULL, 'MASTER', 'Master',
   'Configuración general, usuarios, roles, automatizaciones y auditoría de actividad', TRUE),
  ('01900000-0000-7000-8000-000000000002', NULL, 'ADMINISTRADOR', 'Administrador',
   'Supervisión de operaciones, aprobaciones y reportes', TRUE),
  ('01900000-0000-7000-8000-000000000003', NULL, 'COMERCIAL', 'Comercial',
   'Clientes, oportunidades, cotizaciones, contratos y seguimientos comerciales', TRUE),
  ('01900000-0000-7000-8000-000000000004', NULL, 'COORDINADOR_AUDITORIAS', 'Coordinador de auditorías',
   'Programación de auditorías, auditores, vigilancias, renovaciones y reuniones', TRUE),
  ('01900000-0000-7000-8000-000000000005', NULL, 'RESPONSABLE_CERTIFICACIONES', 'Responsable de certificaciones',
   'Expedientes, registro, publicación y vigencia de certificados', TRUE);

INSERT INTO roles_permisos (rol_id, permiso_codigo) VALUES
  ('01900000-0000-7000-8000-000000000001', 'usuarios:leer'),
  ('01900000-0000-7000-8000-000000000001', 'usuarios:gestionar'),
  ('01900000-0000-7000-8000-000000000001', 'roles:asignar'),
  ('01900000-0000-7000-8000-000000000001', 'bitacora:leer'),
  ('01900000-0000-7000-8000-000000000002', 'usuarios:leer'),
  ('01900000-0000-7000-8000-000000000002', 'bitacora:leer');
