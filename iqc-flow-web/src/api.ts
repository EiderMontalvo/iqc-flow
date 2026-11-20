const BASE = '/api/v1';

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
  tipoDocumento: string | null; numeroDocumento: string | null; estado: ClientStatus;
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
export class ApiError extends Error { constructor(message: string, public status: number) { super(message); } }

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.body && !(options.body instanceof URLSearchParams)) headers.set('Content-Type', 'application/json');
  if (options.method && options.method !== 'GET') {
    let csrf: Response;
    try { csrf = await fetch(`${BASE}/autenticacion/csrf`, { credentials: 'same-origin' }); }
    catch { throw new ApiError('No se pudo conectar con la API. Verifica que el servidor esté iniciado.', 0); }
    if (!csrf.ok) throw new ApiError('No se pudo preparar la solicitud. Intenta de nuevo.', csrf.status);
    const { token } = await csrf.json() as { token: string };
    headers.set('X-XSRF-TOKEN', token);
  }
  let response: Response;
  try { response = await fetch(`${BASE}${path}`, { ...options, headers, credentials: 'same-origin' }); }
  catch { throw new ApiError('No se pudo conectar con la API. Verifica que el servidor esté iniciado.', 0); }
  if (!response.ok) {
    const problem = await response.json().catch(() => ({})) as { detail?: string; title?: string };
    throw new ApiError(problem.detail || problem.title || `La solicitud falló (${response.status}).`, response.status);
  }
  return response.status === 204 ? undefined as T : response.json() as Promise<T>;
}
export const api = {
  me: () => request<CurrentUser>('/autenticacion/yo'),
  login: (correo: string, contrasena: string) => request<void>('/autenticacion/inicio-sesion', { method: 'POST', body: new URLSearchParams({ correo, contrasena }) }),
  logout: () => request<void>('/autenticacion/cierre-sesion', { method: 'POST' }),
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
  assignRoles: (id: string, data: object) => request<AppUser>(`/usuarios/${id}/roles`, { method: 'PUT', body: JSON.stringify(data) }),
};
