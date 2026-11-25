/**
 * Origen de la API. Vacío: misma URL que el panel (desarrollo con el proxy de Vite).
 * En producción el panel vive en BanaHosting y la API en otro servidor: VITE_API_URL=https://api.iqcperu.com
 */
const API_ORIGIN = (import.meta.env.VITE_API_URL as string | undefined)?.replace(/\/+$/, '') ?? '';
const BASE = `${API_ORIGIN}/api/v1`;
/** La sesión es una cookie del servidor de la API: se envía también entre dominios */
const CREDENTIALS: RequestCredentials = API_ORIGIN ? 'include' : 'same-origin';

export interface CurrentUser {
  id: string;
  correo: string;
  nombre: string;
  organizacion: { id: string; nombre: string; codigo: string };
  roles: string[];
  permisos: string[];
  debeCambiarContrasena: boolean;
}
export type ClientStatus = 'PROSPECTO' | 'ACTIVO' | 'INACTIVO';
export interface ClientSummary {
  id: string; codigo: string; razonSocial: string; nombreComercial: string | null;
  tipoDocumento: string | null; numeroDocumento: string | null; estado: ClientStatus; codigoPais: string;
  responsable: { id: string; nombre: string }; archivado: boolean; actualizadoEn: string;
}
export interface ClientDetail extends ClientSummary {
  sector: string | null; sitioWeb: string | null; origen: string | null; notas: string | null;
  contactos: Contact[];
  direcciones: Address[];
  version: number; puedeEditar: boolean;
}
export interface Page<T> { contenido: T[]; pagina: number; tamano: number; totalElementos: number; totalPaginas: number }
export type TaskView = 'ABIERTAS' | 'HOY' | 'ATRASADAS' | 'PROXIMAS' | 'EN_ESPERA' | 'CERRADAS';
export type TaskState = 'PENDIENTE' | 'EN_CURSO' | 'EN_ESPERA' | 'COMPLETADA' | 'CANCELADA';
export type TaskPriority = 'BAJA' | 'NORMAL' | 'ALTA' | 'URGENTE';
export interface TaskSummary {
  id: string; titulo: string; tipo: string; tipoNombre: string; prioridad: TaskPriority;
  estado: TaskState; fechaLimite: string; horaLimite: string | null; vencida: boolean;
  cliente: { id: string; codigo: string; razonSocial: string } | null;
  responsable: { id: string; nombre: string }; version: number;
}
export interface TaskDetail extends TaskSummary {
  descripcion: string | null; resultado: string | null; notasResultado: string | null;
  reprogramaciones: number; creadoEn: string; puedeGestionar: boolean;
  historial: Array<{ evento: string; motivo: string | null; actor: { nombre: string }; ocurridoEn: string }>;
}
export interface TaskType { codigo: string; nombre: string; prioridadPorDefecto: TaskPriority }
export interface TaskCounts { hoy: number; atrasadas: number; proximas: number; enEspera: number }
export interface Activity {
  id: string; clienteId: string; tipo: 'LLAMADA' | 'CORREO' | 'REUNION' | 'WHATSAPP' | 'VISITA' | 'NOTA';
  direccion: 'ENTRANTE' | 'SALIENTE' | 'INTERNA' | null; ocurridaEn: string;
  resumen: string; resultado: string | null; registradaPor: { id: string; nombre: string };
}
/** Un punto de un gráfico: clave estable (mes «2026-11», código), etiqueta legible y cantidad */
export interface Punto { clave: string; etiqueta: string; cantidad: number }
/** Series para los gráficos del inicio de cada rol; cada sección llega solo si el usuario tiene permiso para su área */
export interface Indicadores {
  calculadoAl: string;
  certificados: { vencimientosPorMes: Punto[]; emisionesPorMes: Punto[]; personalesPorMes: Punto[]; vigentesPorNorma: Punto[]; vigentesPorEsquema: Punto[] } | null;
  seguimientos: { deTodos: boolean; abiertasPorTipo: Punto[]; abiertasPorPrioridad: Punto[]; completadasPorSemana: Punto[] } | null;
  clientes: { porEstado: Punto[]; altasPorMes: Punto[] } | null;
}
export interface Notice { id: string; titulo: string; cuerpo: string; tipo: string; sujetoTipo: string; sujetoId: string | null; leida: boolean; creadoEn: string }
export interface AppUser {
  id: string; correo: string; nombre: string; estado: 'ACTIVO' | 'DESHABILITADO';
  roles: string[]; debeCambiarContrasena: boolean; mfaHabilitado: boolean;
  ultimoAccesoEn: string | null; creadoEn: string; version: number;
}
export interface AppRole { codigo: string; nombre: string; descripcion: string; permisos: string[] }
export interface Contact {
  id: string; nombreCompleto: string; cargo: string | null; correo: string | null;
  telefono: string | null; roles: string[]; esPrincipal: boolean; activo: boolean;
  notas: string | null; version: number;
}
export interface Address {
  id: string; tipo: 'FISCAL' | 'SEDE' | 'CORRESPONDENCIA'; nombre: string | null;
  direccion: string; distrito: string | null; provincia: string | null;
  departamento: string | null; codigoPais: string | null; ubigeo: string | null;
  referencia: string | null; version: number;
}
export type CertificateState = 'BORRADOR' | 'REVISADO' | 'EMITIDO' | 'SUSPENDIDO' | 'RETIRADO' | 'CANCELADO' | 'REEMPLAZADO';
export type Validity = 'VIGENTE' | 'POR_VENCER' | 'VENCIDO' | 'SIN_FECHA';
export interface Certificate {
  id: string; expedienteId: string; codigo: string; norma: string; designacion: string; esquema: string;
  emitidoPorIndia: boolean; estado: CertificateState; vigencia: Validity;
  cliente: { id: string; razonSocial: string; tipoDocumento: string | null; numeroDocumento: string | null; codigoPais: string };
  paisMarca: string; marcaManual: boolean; alcance: string | null;
  fechaOtorgamiento: string | null; fechaEmision: string | null; fechaExpiracion: string | null;
  tienePdf: boolean; documentoPdfId: string | null; urlDocumentoLegado: string | null; version: number;
}
export interface PdfVersion { numero: number; documentoId: string; nombre: string; tamanoBytes: number; motivo: string | null; subidoPor: { id: string; nombre: string } | null; subidoEn: string; vigente: boolean }
export interface ConsultLink { certificadoId: string; codigo: string; token: string; url: string; paisMarca: string; marca: string }
export interface DirectRegistration { expedienteId: string; expedienteCodigo: string; certificados: Certificate[]; vigilancias: number; tareas: number }
export interface Scheme { codigo: string; nombre: string; organismoAcreditador: string; prefijoCertificado: string }
export interface Standard { codigo: string; nombreCorto: string; titulo: string; ediciones: Array<{ edicion: string; designacion: string; vigente: boolean }> }
export interface Country { codigo: string; nombre: string; moneda: string; nombreImpuesto: string; tasaImpuesto: number }
export interface Brand {
  codigoPais: string; nombrePais: string; nombre: string; urlConsulta: string; sitioWeb: string | null; correo: string | null;
  telefono: string | null; direccion: string | null; logoDocumentoId: string | null; predeterminada: boolean; actualizadoEn: string | null; version: number | null;
}
export interface PublicationRun { habilitada: boolean; ejecutada: boolean; revisados: number; publicados: number; sinCambios: number; fallidos: number; ejecutadaEn: string }
export interface PublicationStatus {
  habilitada: boolean; paises: string[]; publicados: number;
  errores: Array<{ certificadoId: string; intentos: number; error: string; ultimoIntentoEn: string }>; ultimaPasada: PublicationRun | null;
}
export interface StoredDocument { id: string; nombreOriginal: string; tipoContenido: string; tamanoBytes: number }
export interface BrandSummary { codigoPais: string; vigentes: number; porVencer: number; vencidos: number; suspendidos: number; retirados: number }
export interface PersonalSummary { codigoPais: string; emitidos: number; anulados: number }
export type PersonalState = 'EMITIDO' | 'ANULADO';
export interface Person { id: string; codigoPais: string; tipoDocumento: string; numeroDocumento: string; nombres: string; correo: string | null; telefono: string | null }
export interface PersonalCertificate {
  id: string; codigo: string; estado: PersonalState; persona: Person | null; curso: string; normaReferencia: string | null;
  normaAnio: string | null; subtitulo: string | null; descripcion: string | null; duracionHoras: number | null; modalidad: string | null;
  fechaInicio: string | null; fechaFin: string | null; fechaEmision: string; entrenador: string | null; motivoAnulacion: string | null;
  documentoPdfId: string | null; urlDocumentoLegado: string | null; tienePdf: boolean; urlConsulta: string; actualizadoEn: string; version: number;
}
export interface DocumentType { codigo: string; etiqueta: string; ayuda: string }
export type RegistrationMode = 'MANUAL' | 'AUTOMATIZADO' | 'PLANTILLA';
export interface Tools { modosEmpresarial: RegistrationMode[]; modosPersonal: RegistrationMode[]; autocompletado: boolean; generacionPdf: boolean; ia: boolean; automatizaciones: boolean }
export interface BusinessReading { codigo: string | null; esquema: string | null; titular: string | null; direccion: string | null; normaTexto: string | null; normaNombre: string | null; edicion: string | null; alcance: string | null; ciclo: number | null; fechaOtorgamiento: string | null; fechaEmision: string | null; limiteVigilancia1: string | null; limiteVigilancia2: string | null; fechaExpiracion: string | null; subdivision: string | null; clase: string | null }
export interface PersonalReading { codigo: string | null; nombres: string | null; tipoDocumento: string | null; numeroDocumento: string | null; curso: string | null; normaReferencia: string | null; normaAnio: string | null; subtitulo: string | null; descripcion: string | null; duracionHoras: number | null; modalidad: string | null; fechaInicio: string | null; fechaFin: string | null; fechaEmision: string | null; entrenador: string | null }
export interface PdfReading { formato: 'INACAL_SAE' | 'IQC_GLOBAL' | 'PERSONAL' | 'DESCONOCIDO'; origen: 'LOCAL' | 'IA'; empresarial: BusinessReading | null; normaCodigo: string | null; edicionValida: boolean; personal: PersonalReading | null; advertencias: string[] }
export interface OrgParameter {
  clave: string; valor: string; porDefecto: boolean; admitidos: string[]; actualizadoEn: string | null;
  /** Si MASTER puede apagarla a una persona concreta, y a quiénes se la apagó */
  porPersona: boolean; desactivadoPara: string[];
}
export interface DatosRuc { ruc: string; razonSocial: string; direccion: string; distrito: string; provincia: string; departamento: string }
export interface DatosDni { dni: string; nombres: string; apellidos: string; nombreCompleto: string }
export type QuoteState = 'BORRADOR' | 'ENVIADA' | 'ACEPTADA' | 'RECHAZADA' | 'ANULADA';
export type QuoteService = 'CERTIFICACION_INICIAL' | 'RECERTIFICACION' | 'AMPLIACION' | 'VIGILANCIA' | 'OTRO';
export interface QuoteClient { id: string; razonSocial: string; tipoDocumento: string | null; numeroDocumento: string | null; codigoPais: string | null }
export interface QuoteItem { id: string; normaCodigo: string | null; normaNombre: string | null; edicion: string | null; descripcion: string; diasAuditor: number; monto: number }
export interface QuoteSummary {
  id: string; numero: string; cliente: QuoteClient; tipoServicio: QuoteService; normas: string[]; moneda: string; total: number;
  estado: QuoteState; fechaEmision: string; enviadaEn: string | null; respondidaEn: string | null; diasSinRespuesta: number | null;
  responsable: { id: string; nombre: string }; version: number;
}
export interface Quote {
  id: string; numero: string; cliente: QuoteClient; contactoNombre: string | null; contactoCorreo: string | null; esquemaCodigo: string | null;
  tipoServicio: QuoteService; alcance: string | null; numeroSedes: number; sedesDetalle: string | null; moneda: string; nombreImpuesto: string;
  tasaImpuesto: number; tarifaDia: number | null; subtotal: number; impuesto: number; total: number; diasAuditorTotal: number;
  validezDias: number; validaHasta: string; condicionesPago: string | null; notas: string | null; estado: QuoteState; fechaEmision: string;
  enviadaEn: string | null; destinatarios: string | null; respondidaEn: string | null; motivoRespuesta: string | null;
  diasSinRespuesta: number | null; responsable: { id: string; nombre: string }; creadoPor: { id: string; nombre: string } | null;
  items: QuoteItem[]; version: number;
}
export interface QuoteCounts { borradores: number; enviadas: number; enviadasSinRespuesta: number; aceptadasMes: number; emitidasMes: number; conversion12Meses: number | null; diasSinRespuesta: number }
export interface RequestAnalysis {
  origen: 'LOCAL' | 'IA'; resumen: string | null; puntosClave: string[]; faltantes: string[]; advertencias: string[];
  empresa: { ruc: string | null; razonSocial: string | null; direccion: string | null; distrito: string | null; provincia: string | null; departamento: string | null; clienteId: string | null; clienteRazonSocial: string | null };
  contacto: { nombre: string | null; cargo: string | null; correo: string | null; telefono: string | null };
  tipoServicio: QuoteService; normas: Array<{ codigo: string; nombre: string; edicion: string | null }>;
  alcance: string | null; numeroSedes: number | null; sedesDetalle: string | null; numeroTrabajadores: number | null; plazo: string | null;
  /** Lo que la API leyó del archivo y del texto pegado (con remitente, fecha y asunto si era un correo) */
  texto: string;
}
export class ApiError extends Error { constructor(message: string, public status: number) { super(message); } }

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.body && !(options.body instanceof URLSearchParams) && !(options.body instanceof FormData)) headers.set('Content-Type', 'application/json');
  if (options.method && options.method !== 'GET') {
    let csrf: Response;
    try { csrf = await fetch(`${BASE}/autenticacion/csrf`, { credentials: CREDENTIALS }); }
    catch { throw new ApiError('No se pudo conectar con la API. Verifica que el servidor esté iniciado.', 0); }
    if (!csrf.ok) throw new ApiError('No se pudo preparar la solicitud. Intenta de nuevo.', csrf.status);
    const { token } = await csrf.json() as { token: string };
    headers.set('X-XSRF-TOKEN', token);
  }
  let response: Response;
  try { response = await fetch(`${BASE}${path}`, { ...options, headers, credentials: CREDENTIALS }); }
  catch { throw new ApiError('No se pudo conectar con la API. Verifica que el servidor esté iniciado.', 0); }
  if (!response.ok) {
    const problem = await response.json().catch(() => ({})) as { detail?: string; title?: string };
    throw new ApiError(problem.detail || problem.title || `La solicitud falló (${response.status}).`, response.status);
  }
  // Sin contenido (204, o 202 sin cuerpo): no hay JSON que leer
  if (response.status === 204 || response.headers.get('content-length') === '0' || !response.headers.get('content-type')?.includes('json')) return undefined as T;
  return response.json() as Promise<T>;
}
export const api = {
  me: () => request<CurrentUser>('/autenticacion/yo'),
  login: (correo: string, contrasena: string) => request<void>('/autenticacion/inicio-sesion', { method: 'POST', body: new URLSearchParams({ correo, contrasena }) }),
  logout: () => request<void>('/autenticacion/cierre-sesion', { method: 'POST' }),
  forgotPassword: (correo: string) => request<void>('/autenticacion/olvido-contrasena', { method: 'POST', body: JSON.stringify({ correo }) }),
  setTemporaryPassword: (id: string, contrasenaTemporal: string, version: number) =>
    request<AppUser>(`/usuarios/${id}/contrasena-temporal`, { method: 'POST', body: JSON.stringify({ contrasenaTemporal, version }) }),
  changePassword: (contrasenaActual: string, contrasenaNueva: string) => request<void>('/autenticacion/cambio-contrasena', { method: 'POST', body: JSON.stringify({ contrasenaActual, contrasenaNueva }) }),
  clients: (query: URLSearchParams) => request<Page<ClientSummary>>(`/clientes?${query}`),
  client: (id: string) => request<ClientDetail>(`/clientes/${id}`),
  createClient: (data: object) => request<ClientDetail>('/clientes', { method: 'POST', body: JSON.stringify(data) }),
  updateClient: (id: string, data: object) => request<ClientDetail>(`/clientes/${id}`, { method: 'PATCH', body: JSON.stringify(data) }),
  archiveClient: (id: string, version: number) => request<ClientDetail>(`/clientes/${id}/archivo`, { method: 'POST', body: JSON.stringify({ version }) }),
  restoreClient: (id: string, version: number) => request<ClientDetail>(`/clientes/${id}/restauracion`, { method: 'POST', body: JSON.stringify({ version }) }),
  addContact: (clientId: string, data: object) => request<Contact>(`/clientes/${clientId}/contactos`, { method: 'POST', body: JSON.stringify(data) }),
  updateContact: (clientId: string, contactId: string, data: object) => request<Contact>(`/clientes/${clientId}/contactos/${contactId}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteContact: (clientId: string, contactId: string) => request<void>(`/clientes/${clientId}/contactos/${contactId}`, { method: 'DELETE' }),
  addAddress: (clientId: string, data: object) => request<Address>(`/clientes/${clientId}/direcciones`, { method: 'POST', body: JSON.stringify(data) }),
  updateAddress: (clientId: string, addressId: string, data: object) => request<Address>(`/clientes/${clientId}/direcciones/${addressId}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteAddress: (clientId: string, addressId: string) => request<void>(`/clientes/${clientId}/direcciones/${addressId}`, { method: 'DELETE' }),
  taskCounts: () => request<TaskCounts>('/seguimientos/resumen'),
  indicators: () => request<Indicadores>('/indicadores'),
  activities: (clientId: string, page = 0) => request<Page<Activity>>(`/seguimientos/actividades?${new URLSearchParams({ clienteId: clientId, pagina: String(page), tamano: '10' })}`),
  addActivity: (data: object) => request<Activity>('/seguimientos/actividades', { method: 'POST', body: JSON.stringify(data) }),
  tasks: (query: URLSearchParams) => request<Page<TaskSummary>>(`/seguimientos/tareas?${query}`),
  task: (id: string) => request<TaskDetail>(`/seguimientos/tareas/${id}`),
  taskTypes: () => request<TaskType[]>('/seguimientos/tipos-tarea'),
  createTask: (data: object) => request<TaskDetail>('/seguimientos/tareas', { method: 'POST', body: JSON.stringify(data) }),
  updateTask: (id: string, data: object) => request<TaskDetail>(`/seguimientos/tareas/${id}`, { method: 'PATCH', body: JSON.stringify(data) }),
  completeTask: (id: string, data: object) => request<TaskDetail>(`/seguimientos/tareas/${id}/completado`, { method: 'POST', body: JSON.stringify(data) }),
  rescheduleTask: (id: string, data: object) => request<TaskDetail>(`/seguimientos/tareas/${id}/reprogramacion`, { method: 'POST', body: JSON.stringify(data) }),
  cancelTask: (id: string, data: object) => request<TaskDetail>(`/seguimientos/tareas/${id}/cancelacion`, { method: 'POST', body: JSON.stringify(data) }),
  notices: (query: URLSearchParams) => request<Page<Notice>>(`/notificaciones?${query}`),
  unreadNotices: () => request<{ noLeidas: number }>('/notificaciones/contador'),
  readNotice: (id: string) => request<Notice>(`/notificaciones/${id}/lectura`, { method: 'POST' }),
  readAllNotices: () => request<{ marcadas: number }>('/notificaciones/lectura', { method: 'POST' }),
  users: (query: URLSearchParams) => request<Page<AppUser>>(`/usuarios?${query}`),
  roles: () => request<AppRole[]>('/roles'),
  createUser: (data: object) => request<AppUser>('/usuarios', { method: 'POST', body: JSON.stringify(data) }),
  updateUser: (id: string, data: object) => request<AppUser>(`/usuarios/${id}`, { method: 'PATCH', body: JSON.stringify(data) }),
  certificates: (query: URLSearchParams) => request<Page<Certificate>>(`/certificados?${query}`),
  certificate: (id: string) => request<Certificate>(`/certificados/${id}`),
  certificateExportUrl: (query: URLSearchParams) => `${BASE}/certificados/exportacion?${query}`,
  registerCertificates: (data: object) => request<DirectRegistration>('/certificados/registro-directo', { method: 'POST', body: JSON.stringify(data) }),
  setCertificatePdf: (id: string, data: object) => request<Certificate>(`/certificados/${id}/pdf`, { method: 'PUT', body: JSON.stringify(data) }),
  pdfVersions: (id: string) => request<PdfVersion[]>(`/certificados/${id}/pdf/versiones`),
  setCertificateBrand: (id: string, data: object) => request<Certificate>(`/certificados/${id}/marca`, { method: 'PUT', body: JSON.stringify(data) }),
  consultLink: (id: string) => request<ConsultLink>(`/certificados/${id}/consulta`),
  qrUrl: (id: string, logo = true, tamano = 600) => `${BASE}/certificados/${id}/qr?${new URLSearchParams({ tamano: String(tamano), logo: String(logo) })}`,
  /** Padrón de SUNAT (vía la API): para completar formularios */
  padronRuc: (ruc: string) => request<DatosRuc>(`/padron/ruc/${encodeURIComponent(ruc)}`),
  padronDni: (dni: string) => request<DatosDni>(`/padron/dni/${encodeURIComponent(dni)}`),
  analyzeRequest: (data: FormData) => request<RequestAnalysis>('/cotizaciones/analisis-solicitud', { method: 'POST', body: data }),
  quotes: (query: URLSearchParams) => request<Page<QuoteSummary>>(`/cotizaciones?${query}`),
  quoteCounts: () => request<QuoteCounts>('/cotizaciones/resumen'),
  quote: (id: string) => request<Quote>(`/cotizaciones/${id}`),
  createQuote: (data: object) => request<Quote>('/cotizaciones', { method: 'POST', body: JSON.stringify(data) }),
  updateQuote: (id: string, data: object) => request<Quote>(`/cotizaciones/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  sendQuote: (id: string, data: object) => request<Quote>(`/cotizaciones/${id}/envio`, { method: 'POST', body: JSON.stringify(data) }),
  answerQuote: (id: string, data: object) => request<Quote>(`/cotizaciones/${id}/respuesta`, { method: 'POST', body: JSON.stringify(data) }),
  voidQuote: (id: string, data: object) => request<Quote>(`/cotizaciones/${id}/anulacion`, { method: 'POST', body: JSON.stringify(data) }),
  duplicateQuote: (id: string) => request<Quote>(`/cotizaciones/${id}/duplicado`, { method: 'POST' }),
  documentUrl: (id: string) => `${BASE}/documentos/${id}/contenido`,
  /** El documento como Blob, con la sesión: la API no se deja mostrar en marcos (X-Frame-Options), su copia local sí */
  documentBlob: async (id: string) => {
    const r = await fetch(`${BASE}/documentos/${id}/contenido`, { credentials: CREDENTIALS });
    if (!r.ok) throw new ApiError(r.status === 404 ? 'El documento ya no existe.' : `No se pudo abrir el documento (${r.status}).`, r.status);
    return r.blob();
  },
  uploadDocument: (categoria: string, file: File) => { const body = new FormData(); body.append('archivo', file); return request<StoredDocument>(`/documentos?${new URLSearchParams({ categoria })}`, { method: 'POST', body }); },
  schemes: () => request<Scheme[]>('/catalogos/esquemas-acreditacion'),
  standards: () => request<Standard[]>('/catalogos/normas'),
  countries: () => request<Country[]>('/catalogos/paises'),
  brands: () => request<Brand[]>('/marcas'),
  saveBrand: (pais: string, data: object) => request<Brand>(`/marcas/${pais}`, { method: 'PUT', body: JSON.stringify(data) }),
  publication: () => request<PublicationStatus>('/publicacion'),
  publish: (forzar: boolean) => request<PublicationRun>(`/publicacion?forzar=${forzar}`, { method: 'POST' }),
  certificateSummary: () => request<BrandSummary[]>('/certificados/resumen'),
  personalCertificates: (query: URLSearchParams) => request<Page<PersonalCertificate>>(`/certificados-personales?${query}`),
  personalCertificate: (id: string) => request<PersonalCertificate>(`/certificados-personales/${id}`),
  personalSummary: () => request<PersonalSummary[]>('/certificados-personales/resumen'),
  documentTypes: () => request<Record<string, DocumentType[]>>('/certificados-personales/tipos-documento'),
  personByDocument: (pais: string, tipo: string, numero: string) => request<Person>(`/certificados-personales/personas?${new URLSearchParams({ pais, tipo, numero })}`),
  registerPersonal: (data: object) => request<PersonalCertificate>('/certificados-personales', { method: 'POST', body: JSON.stringify(data) }),
  correctPersonal: (id: string, data: object) => request<PersonalCertificate>(`/certificados-personales/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  voidPersonal: (id: string, data: object) => request<PersonalCertificate>(`/certificados-personales/${id}/anulacion`, { method: 'POST', body: JSON.stringify(data) }),
  setPersonalPdf: (id: string, data: object) => request<PersonalCertificate>(`/certificados-personales/${id}/pdf`, { method: 'PUT', body: JSON.stringify(data) }),
  tools: () => request<Tools>('/certificados/herramientas'),
  readPdf: (tipo: 'EMPRESARIAL' | 'PERSONAL', documentoId: string) => request<PdfReading>('/certificados/lectura', { method: 'POST', body: JSON.stringify({ tipo, documentoId }) }),
  parameters: () => request<OrgParameter[]>('/organizacion/parametros'),
  setParameter: (clave: string, valor: string) => request<OrgParameter>(`/organizacion/parametros/${clave}`, { method: 'PUT', body: JSON.stringify({ valor }) }),
  setParameterForPerson: (clave: string, usuarioId: string, habilitado: boolean) =>
    request<OrgParameter>(`/organizacion/parametros/${clave}/personas/${usuarioId}`, { method: 'PUT', body: JSON.stringify({ habilitado }) }),
  assignRoles: (id: string, data: object) => request<AppUser>(`/usuarios/${id}/roles`, { method: 'PUT', body: JSON.stringify(data) }),
};
