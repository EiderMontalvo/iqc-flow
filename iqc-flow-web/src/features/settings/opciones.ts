/** Textos de las opciones de MASTER, agrupadas como aparecen en Configuración */
export interface Opcion { clave: string; titulo: string; detalle: string }
export interface GrupoOpciones { titulo: string; descripcion: string; opciones: Opcion[] }

export const GRUPOS: GrupoOpciones[] = [
  { titulo: 'General', descripcion: 'Valen para todos, salvo para quien se la apagues en «Opciones por persona».', opciones: [
    { clave: 'IA_HABILITADA', titulo: 'Inteligencia artificial', detalle: 'Lectura con IA de los PDF que la lectura local no reconoce y, más adelante, el asistente. Aún no hay proveedor configurado.' },
    { clave: 'AUTOMATIZACIONES_HABILITADAS', titulo: 'Automatizaciones', detalle: 'Tareas automáticas del ciclo de certificación, recordatorios y escalamientos.' },
  ] },
  { titulo: 'Registro de certificados empresariales', descripcion: 'Modos que aparecen al registrar un certificado de sistema de gestión.', opciones: [
    { clave: 'CERT_EMPRESARIAL_MODO_MANUAL', titulo: 'Manual', detalle: 'Se sube el PDF y los datos se completan a mano.' },
    { clave: 'CERT_EMPRESARIAL_MODO_AUTOMATIZADO', titulo: 'Automatizado', detalle: 'Se sube el PDF y el sistema completa el formulario leyendo sus campos.' },
    { clave: 'CERT_EMPRESARIAL_MODO_PLANTILLA', titulo: 'Desde plantilla', detalle: 'Se completa el formulario y el PDF se genera con una plantilla Word.' },
  ] },
  { titulo: 'Registro de certificados personales', descripcion: 'Modos que aparecen al registrar un certificado de curso o competencia.', opciones: [
    { clave: 'CERT_PERSONAL_MODO_MANUAL', titulo: 'Manual', detalle: 'Se sube el PDF y los datos se completan a mano.' },
    { clave: 'CERT_PERSONAL_MODO_AUTOMATIZADO', titulo: 'Automatizado', detalle: 'Se sube el PDF y el sistema completa el formulario leyendo sus campos.' },
    { clave: 'CERT_PERSONAL_MODO_PLANTILLA', titulo: 'Desde plantilla', detalle: 'Se completa el formulario y el PDF se genera con una plantilla Word.' },
  ] },
  { titulo: 'Administración y logística', descripcion: 'Herramientas del rol Administración. MASTER las conserva siempre. A una persona se le apagan desde sus modos de registro.', opciones: [
    { clave: 'ADMINISTRACION_AUTOCOMPLETADO', titulo: 'Autocompletado desde PDF', detalle: 'Administración puede completar formularios leyendo el PDF.' },
    { clave: 'ADMINISTRACION_GENERACION_PDF', titulo: 'Generación de PDF', detalle: 'Administración puede generar el PDF de un certificado desde una plantilla.' },
  ] },
];

export const FIRMA: Record<string, string> = { FIRMA_GERENCIA: 'Solo Gerencia General', FIRMA_GERENCIA_Y_JEFE_TECNICO: 'Gerencia General y Jefe Técnico' };
