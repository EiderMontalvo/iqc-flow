import { useEffect, useState, type FormEvent } from 'react';
import ReactDOM from 'react-dom/client';
import { api, type ClientDetail, type ClientStatus, type ClientSummary, type CurrentUser, type Page } from './api';
import { Tasks } from './Tasks';
import { ClientExtras } from './ClientExtras';
import { Users } from './Users';
import { Notices } from './Notices';
import './style.css';
import './theme.css';

type Screen = 'overview' | 'clients' | 'tasks' | 'users' | 'notices';
type ThemePreference = 'system' | 'dark' | 'light';
function preferredTheme(): ThemePreference {
  const saved = localStorage.getItem('iqc-flow-theme');
  return saved === 'dark' || saved === 'light' ? saved : 'system';
}
const statusNames: Record<ClientStatus, string> = { PROSPECTO: 'Prospecto', ACTIVO: 'Activo', INACTIVO: 'Inactivo' };
const date = (value: string) => new Intl.DateTimeFormat('es-PE', { day: '2-digit', month: 'short', year: 'numeric' }).format(new Date(value));
const errorText = (error: unknown) => error instanceof Error ? error.message : 'Ocurrió un error inesperado.';
function Mark() { return <span className="mark" aria-hidden="true"><i /><i /><i /></span>; }

function Login({ onLogin }: { onLogin: (user: CurrentUser) => void }) {
  const [correo, setCorreo] = useState(''), [contrasena, setContrasena] = useState('');
  const [busy, setBusy] = useState(false), [error, setError] = useState('');
  async function submit(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('');
    try { await api.login(correo, contrasena); onLogin(await api.me()); }
    catch (e) { setError(errorText(e)); } finally { setBusy(false); }
  }
  return <main className="login-layout">
    <div className="login-brand"><Mark /> IQC Flow</div>
    <div className="login-copy"><p>IQC Perú</p><h1>El trabajo en orden.</h1><p>Clientes, seguimiento y certificación en un solo lugar.</p></div>
    <form className="login-panel" onSubmit={submit}><h2>Iniciar sesión</h2><p>Ingresa con tu cuenta de IQC Flow.</p>
      <label>Correo electrónico<input type="email" autoComplete="username" value={correo} onChange={e => setCorreo(e.target.value)} required autoFocus /></label>
      <label>Contraseña<input type="password" autoComplete="current-password" value={contrasena} onChange={e => setContrasena(e.target.value)} required /></label>
      {error && <p className="form-error" role="alert">{error}</p>}
      <button className="primary wide" disabled={busy}>{busy ? 'Ingresando…' : 'Ingresar'}</button>
    </form><span className="login-foot">Acceso interno · IQC Perú</span>
  </main>;
}

function PasswordChange({ user, onDone }: { user: CurrentUser; onDone: (user: CurrentUser) => void }) {
  const [current, setCurrent] = useState(''), [next, setNext] = useState(''), [confirm, setConfirm] = useState('');
  const [error, setError] = useState(''), [busy, setBusy] = useState(false);
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (next.length < 12) return setError('La nueva contraseña debe tener al menos 12 caracteres.');
    if (next !== confirm) return setError('Las contraseñas nuevas no coinciden.');
    setBusy(true); setError('');
    try { await api.changePassword(current, next); onDone(await api.me()); }
    catch (e) { setError(errorText(e)); } finally { setBusy(false); }
  }
  return <main className="center-screen"><div className="password-panel"><div className="wordmark"><Mark /> IQC Flow</div>
    <h1>Actualiza tu contraseña</h1><p>Para continuar, cambia la contraseña temporal de {user.correo}.</p>
    <form onSubmit={submit}><label>Contraseña actual<input type="password" autoComplete="current-password" value={current} onChange={e => setCurrent(e.target.value)} required /></label>
      <label>Nueva contraseña<input type="password" autoComplete="new-password" value={next} onChange={e => setNext(e.target.value)} required minLength={12} /></label>
      <label>Confirmar nueva contraseña<input type="password" autoComplete="new-password" value={confirm} onChange={e => setConfirm(e.target.value)} required minLength={12} /></label>
      {error && <p className="form-error" role="alert">{error}</p>}<button className="primary wide" disabled={busy}>{busy ? 'Guardando…' : 'Guardar contraseña'}</button>
    </form></div></main>;
}

function Overview({ onClients, canRead, onTasks, canReadTasks }: { onClients: () => void; canRead: boolean; onTasks: () => void; canReadTasks: boolean }) {
  const [counts, setCounts] = useState<Record<ClientStatus, number> | null>(null);
  const [recent, setRecent] = useState<ClientSummary[]>([]);
  const [error, setError] = useState(''), [refresh, setRefresh] = useState(0);
  const [taskCounts, setTaskCounts] = useState<{ hoy: number; atrasadas: number; proximas: number; enEspera: number } | null>(null);
  useEffect(() => {
    if (!canReadTasks) return;
    let active = true;
    api.taskCounts().then(value => { if (active) setTaskCounts(value); }).catch(() => {});
    return () => { active = false; };
  }, [canReadTasks, refresh]);
  useEffect(() => {
    if (!canRead) return;
    let active = true;
    const get = (estado?: ClientStatus) => api.clients(new URLSearchParams({ ...(estado ? { estado } : {}), tamano: '5' }));
    Promise.all([get(), get('ACTIVO'), get('PROSPECTO'), get('INACTIVO')])
      .then(([all, activos, prospectos, inactivos]) => { if (active) { setRecent(all.contenido); setCounts({ ACTIVO: activos.totalElementos, PROSPECTO: prospectos.totalElementos, INACTIVO: inactivos.totalElementos }); setError(''); } })
      .catch(e => { if (active) setError(errorText(e)); });
    return () => { active = false; };
  }, [canRead, refresh]);
  const total = counts ? Object.values(counts).reduce((sum, value) => sum + value, 0) : 0;
  return <div className="screen-body"><div className="page-heading"><div><p className="context-line">Vista general</p><h1>Panel principal</h1><p>Estado actual de la cartera de clientes.</p></div>{canRead && <button className="text-button" onClick={() => setRefresh(n => n + 1)}>Actualizar datos ↗</button>}</div>
    {canReadTasks && <section className="overview-tasks" aria-label="Mis seguimientos"><div><h2>Mis seguimientos</h2><p>Lo que necesita atención ahora</p></div><div className="overview-task-metrics"><span><strong>{taskCounts?.hoy ?? '—'}</strong> Hoy</span><span><strong>{taskCounts?.atrasadas ?? '—'}</strong> Atrasadas</span><span><strong>{taskCounts?.proximas ?? '—'}</strong> Próximas</span></div><button className="text-button" onClick={onTasks}>Abrir tareas ↗</button></section>}
    {!canRead ? <div className="notice">Tu cuenta no tiene acceso a clientes. Solicita el permiso a un administrador.</div> : <>
      {error && <div className="notice" role="alert">{error} <button onClick={() => setRefresh(n => n + 1)}>Reintentar</button></div>}
      <section className="overview-top" aria-label="Resumen de clientes"><div className="total-panel"><span>Clientes y prospectos</span><strong>{counts ? total.toLocaleString('es-PE') : '—'}</strong><p>Registros vigentes en IQC Flow</p><button onClick={onClients}>Ver cartera <span aria-hidden="true">↗</span></button></div>
        <div className="status-panel"><div className="panel-title"><h2>Composición de cartera</h2><span>Por estado</span></div>{(['ACTIVO', 'PROSPECTO', 'INACTIVO'] as const).map(status => <div className="status-row" key={status}><div><span className={`status-dot ${status.toLowerCase()}`} />{statusNames[status]}</div><strong>{counts ? counts[status].toLocaleString('es-PE') : '—'}</strong><div className="bar-track"><span className={status.toLowerCase()} style={{ width: counts && total ? `${counts[status] / total * 100}%` : '0%' }} /></div></div>)}<p className="panel-note">Distribución calculada desde los registros actuales.</p></div></section>
      <section className="recent-panel"><div className="panel-title"><div><h2>Actividad reciente</h2><p>Últimos clientes actualizados</p></div><button className="text-button" onClick={onClients}>Ver todos ↗</button></div>{!counts && !error ? <p className="empty-state">Cargando clientes…</p> : !error && recent.length === 0 ? <p className="empty-state">Aún no hay clientes. Registra el primero desde la sección Clientes.</p> : <div className="recent-list">{recent.map(client => <div className="recent-row" key={client.id}><span className={`status-dot ${client.estado.toLowerCase()}`} /><div><strong>{client.razonSocial}</strong><span>{client.codigo} · {client.responsable?.nombre || 'Sin responsable'}</span></div><span className="recent-date">{date(client.actualizadoEn)}</span></div>)}</div>}</section>
    </>}</div>;
}

function ClientForm({ initial, onSave, onCancel, busy, error }: { initial?: ClientDetail; onSave: (data: object) => void; onCancel: () => void; busy: boolean; error: string }) {
  const [name, setName] = useState(initial?.razonSocial || ''), [trade, setTrade] = useState(initial?.nombreComercial || '');
  const [sector, setSector] = useState(initial?.sector || ''), [status, setStatus] = useState<ClientStatus>(initial?.estado || 'PROSPECTO');
  const [ruc, setRuc] = useState(initial?.numeroDocumento || ''), [notes, setNotes] = useState(initial?.notas || '');
  function submit(event: FormEvent) {
    event.preventDefault();
    onSave({ razonSocial: name.trim(), nombreComercial: trade.trim(), sector: sector.trim(), estado: status,
      ...(ruc.trim() ? { tipoDocumento: 'RUC', numeroDocumento: ruc.trim() } : {}), notas: notes.trim(), ...(initial ? { version: initial.version } : {}) });
  }
  return <form className="client-form" onSubmit={submit}><div className="form-grid"><label className="full">Razón social<input value={name} onChange={e => setName(e.target.value)} required maxLength={300} /></label>
    <label>Nombre comercial<input value={trade} onChange={e => setTrade(e.target.value)} maxLength={300} /></label><label>Sector<input value={sector} onChange={e => setSector(e.target.value)} maxLength={120} /></label>
    <label>Estado<select value={status} onChange={e => setStatus(e.target.value as ClientStatus)}><option value="PROSPECTO">Prospecto</option><option value="ACTIVO">Activo</option><option value="INACTIVO">Inactivo</option></select></label>
    <label>RUC<input value={ruc} onChange={e => setRuc(e.target.value)} inputMode="numeric" maxLength={11} pattern="[0-9]{11}" placeholder="11 dígitos" /></label>
    <label className="full">Notas<textarea value={notes} onChange={e => setNotes(e.target.value)} rows={4} maxLength={4000} /></label></div>
    {status === 'ACTIVO' && !ruc && <p className="form-hint">Un cliente activo necesita un RUC válido.</p>}{error && <p className="form-error" role="alert">{error}</p>}
    <div className="form-actions"><button type="button" className="secondary" onClick={onCancel}>Cancelar</button><button className="primary" disabled={busy || (status === 'ACTIVO' && !ruc)}>{busy ? 'Guardando…' : initial ? 'Guardar cambios' : 'Crear cliente'}</button></div></form>;
}

function Clients({ canWrite, canArchive, canReadActivities, canWriteActivities }: {
  canWrite: boolean; canArchive: boolean; canReadActivities: boolean; canWriteActivities: boolean;
}) {
  const [text, setText] = useState(''), [debounced, setDebounced] = useState(''), [filter, setFilter] = useState<ClientStatus | 'TODOS'>('TODOS');
  const [page, setPage] = useState(0), [archived, setArchived] = useState(false);
  const [data, setData] = useState<Page<ClientSummary> | null>(null), [detail, setDetail] = useState<ClientDetail | null>(null);
  const [mode, setMode] = useState<'view' | 'create' | 'edit'>('view'), [error, setError] = useState(''), [formError, setFormError] = useState('');
  const [busy, setBusy] = useState(false), [refresh, setRefresh] = useState(0);
  useEffect(() => { const timer = window.setTimeout(() => setDebounced(text.trim()), 300); return () => window.clearTimeout(timer); }, [text]);
  useEffect(() => {
    let active = true;
    const query = new URLSearchParams({ pagina: String(page), tamano: '10' });
    if (debounced) query.set('texto', debounced);
    if (filter !== 'TODOS') query.set('estado', filter);
    if (archived) query.set('incluirArchivados', 'true');
    api.clients(query).then(result => { if (active) { setData(result); setError(''); } }).catch(e => { if (active) setError(errorText(e)); });
    return () => { active = false; };
  }, [debounced, filter, page, archived, refresh]);
  const reload = () => setRefresh(n => n + 1);
  async function open(id: string) { setFormError(''); try { setDetail(await api.client(id)); setMode('view'); } catch (e) { setError(errorText(e)); } }
  async function save(payload: object) {
    setBusy(true); setFormError('');
    try { const saved = mode === 'create' ? await api.createClient(payload) : await api.updateClient(detail!.id, payload); setDetail(saved); setMode('view'); reload(); }
    catch (e) { setFormError(errorText(e)); } finally { setBusy(false); }
  }
  async function toggleArchive() {
    if (!detail) return;
    setBusy(true); setFormError('');
    try { const saved = detail.archivado ? await api.restoreClient(detail.id, detail.version) : await api.archiveClient(detail.id, detail.version); setDetail(saved); reload(); }
    catch (e) { setFormError(errorText(e)); } finally { setBusy(false); }
  }
  return <div className="screen-body"><div className="page-heading"><div><p className="context-line">Gestión comercial</p><h1>Clientes</h1><p>Consulta y mantén actualizada la cartera de IQC Perú.</p></div>{canWrite && <button className="primary" onClick={() => { setDetail(null); setFormError(''); setMode('create'); }}>Nuevo cliente <span aria-hidden="true">＋</span></button>}</div>
    <section className="list-panel"><div className="list-toolbar"><label className="search-field"><span aria-hidden="true">⌕</span><span className="sr-only">Buscar clientes</span><input value={text} onChange={e => { setText(e.target.value); setPage(0); }} placeholder="Buscar por nombre, código o RUC" /></label><select aria-label="Filtrar por estado" value={filter} onChange={e => { setFilter(e.target.value as ClientStatus | 'TODOS'); setPage(0); }}><option value="TODOS">Todos los estados</option><option value="PROSPECTO">Prospectos</option><option value="ACTIVO">Activos</option><option value="INACTIVO">Inactivos</option></select></div><label className="check-row"><input type="checkbox" checked={archived} onChange={e => { setArchived(e.target.checked); setPage(0); }} /> Incluir archivados</label>
      {error && <div className="notice" role="alert">{error} <button onClick={reload}>Reintentar</button></div>}
      <div className="table-wrap"><table><thead><tr><th>Cliente</th><th>Código</th><th>Estado</th><th>Responsable</th><th>Actualizado</th><th><span className="sr-only">Acción</span></th></tr></thead><tbody>{data?.contenido.map(client => <tr key={client.id}><td><strong>{client.razonSocial}</strong>{client.nombreComercial && <small>{client.nombreComercial}</small>}</td><td>{client.codigo}</td><td><span className={`status-label ${client.estado.toLowerCase()}`}><span className={`status-dot ${client.estado.toLowerCase()}`} />{client.archivado ? 'Archivado' : statusNames[client.estado]}</span></td><td>{client.responsable?.nombre || '—'}</td><td>{date(client.actualizadoEn)}</td><td><button className="row-link" onClick={() => open(client.id)} aria-label={`Ver ${client.razonSocial}`}>Ver ↗</button></td></tr>)}</tbody></table></div>
      <div className="mobile-client-list">{data?.contenido.map(client => <button className="mobile-client-row" key={client.id} onClick={() => open(client.id)} aria-label={`Ver ${client.razonSocial}`}><span className="mobile-client-main"><strong>{client.razonSocial}</strong><span className={`status-label ${client.estado.toLowerCase()}`}><span className={`status-dot ${client.estado.toLowerCase()}`} />{client.archivado ? 'Archivado' : statusNames[client.estado]}</span></span><span className="mobile-client-meta">{client.codigo} · {client.responsable?.nombre || 'Sin responsable'} · {date(client.actualizadoEn)}</span></button>)}</div>
      {data && data.contenido.length === 0 && <p className="empty-state">No se encontraron clientes con estos filtros.</p>}
      <div className="pagination"><span>{data ? `${data.totalElementos} registros` : 'Cargando…'}</span><div><button disabled={page === 0} onClick={() => setPage(p => p - 1)}>Anterior</button><span>Página {page + 1} de {Math.max(1, data?.totalPaginas || 1)}</span><button disabled={!data || page + 1 >= data.totalPaginas} onClick={() => setPage(p => p + 1)}>Siguiente</button></div></div>
    </section>
    {(detail || mode === 'create') && <div className="dialog-backdrop" onMouseDown={e => { if (e.target === e.currentTarget) { setDetail(null); setMode('view'); } }}><section className="dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><div className="dialog-header"><div><p className="context-line">{mode === 'create' ? 'Nuevo registro' : detail?.codigo}</p><h2 id="dialog-title">{mode === 'create' ? 'Nuevo cliente' : mode === 'edit' ? 'Editar cliente' : detail?.razonSocial}</h2></div><button className="close-button" onClick={() => { setDetail(null); setMode('view'); }} aria-label="Cerrar">×</button></div>
      {mode === 'create' || mode === 'edit' ? <ClientForm key={mode + (detail?.id || '')} initial={mode === 'edit' ? detail! : undefined} onSave={save} onCancel={() => { if (detail) setMode('view'); else setMode('view'); }} busy={busy} error={formError} /> : detail && <div className="detail-body"><div className="detail-meta"><span className={`status-label ${detail.estado.toLowerCase()}`}><span className={`status-dot ${detail.estado.toLowerCase()}`} />{detail.archivado ? 'Archivado' : statusNames[detail.estado]}</span><span>Responsable: {detail.responsable?.nombre || '—'}</span></div><div className="detail-grid"><div><span>Nombre comercial</span><strong>{detail.nombreComercial || '—'}</strong></div><div><span>RUC</span><strong>{detail.numeroDocumento || '—'}</strong></div><div><span>Sector</span><strong>{detail.sector || '—'}</strong></div><div><span>Última actualización</span><strong>{date(detail.actualizadoEn)}</strong></div></div><ClientExtras detail={detail} onChanged={setDetail} canReadActivities={canReadActivities} canWriteActivities={canWriteActivities} />{detail.notas && <><h3>Notas</h3><p className="detail-notes">{detail.notas}</p></>}{formError && <p className="form-error" role="alert">{formError}</p>}<div className="form-actions">{canArchive && <button className="secondary" disabled={busy} onClick={toggleArchive}>{detail.archivado ? 'Restaurar' : 'Archivar'}</button>}{detail.puedeEditar && !detail.archivado && <button className="primary" onClick={() => setMode('edit')}>Editar cliente</button>}</div></div>}
    </section></div>}
  </div>;
}

function App() {
  const [user, setUser] = useState<CurrentUser | null>(null), [loading, setLoading] = useState(true);
  const [screen, setScreen] = useState<Screen>('overview'), [menuOpen, setMenuOpen] = useState(false);
  const [theme, setTheme] = useState<ThemePreference>(preferredTheme);
  useEffect(() => {
    const media = window.matchMedia('(prefers-color-scheme: dark)');
    const apply = () => { document.documentElement.dataset.theme = theme === 'system' ? (media.matches ? 'dark' : 'light') : theme; };
    apply();
    media.addEventListener('change', apply);
    return () => media.removeEventListener('change', apply);
  }, [theme]);
  function changeTheme(value: ThemePreference) {
    setTheme(value);
    if (value === 'system') localStorage.removeItem('iqc-flow-theme');
    else localStorage.setItem('iqc-flow-theme', value);
  }
  useEffect(() => { api.me().then(setUser).catch(() => {}).finally(() => setLoading(false)); }, []);
  async function logout() {
    try { await api.logout(); setUser(null); setScreen('overview'); }
    catch (e) { window.alert(errorText(e)); }
  }
  if (loading) return <main className="center-screen"><div className="wordmark"><Mark /> IQC Flow</div></main>;
  if (!user) return <Login onLogin={setUser} />;
  if (user.debeCambiarContrasena) return <PasswordChange user={user} onDone={setUser} />;
  const canReadClients = user.permisos.includes('clientes:leer');
  const canReadTasks = user.permisos.includes('seguimientos:leer');
  const canReadUsers = user.permisos.includes('usuarios:leer');
  const navigate = (target: Screen) => { setScreen(target); setMenuOpen(false); };
  return <div className="app-shell"><aside className={`sidebar ${menuOpen ? 'open' : ''}`}><div className="sidebar-head"><button className="wordmark" onClick={() => navigate('overview')}><Mark /> <span>IQC <b>Flow</b></span></button><button className="mobile-close" onClick={() => setMenuOpen(false)} aria-label="Cerrar menú">×</button></div><div className="workspace"><span>Espacio de trabajo</span><strong>{user.organizacion.nombre}</strong></div><nav aria-label="Navegación principal"><span className="nav-heading">Navegación</span><button className={screen === 'overview' ? 'active' : ''} onClick={() => navigate('overview')}><span className="nav-symbol">▦</span>Panel principal</button>{canReadClients && <button className={screen === 'clients' ? 'active' : ''} onClick={() => navigate('clients')}><span className="nav-symbol">▤</span>Clientes</button>}{canReadTasks && <button className={screen === 'tasks' ? 'active' : ''} onClick={() => navigate('tasks')}><span className="nav-symbol">≡</span>Seguimientos</button>}{canReadUsers && <button className={screen === 'users' ? 'active' : ''} onClick={() => navigate('users')}><span className="nav-symbol">○</span>Usuarios</button>}<button className={screen === 'notices' ? 'active' : ''} onClick={() => navigate('notices')}><span className="nav-symbol">◦</span>Notificaciones</button></nav><div className="sidebar-bottom"><div className="account"><span className="avatar">{user.nombre.slice(0, 1).toUpperCase()}</span><div><strong>{user.nombre}</strong><span>{user.correo}</span></div></div><label className="theme-control">Apariencia<select aria-label="Apariencia" value={theme} onChange={e => changeTheme(e.target.value as ThemePreference)}><option value="system">Sistema</option><option value="dark">Oscuro</option><option value="light">Claro</option></select></label><button className="logout" onClick={logout}>Cerrar sesión <span aria-hidden="true">↗</span></button></div></aside><div className="app-main"><header className="topbar"><button className="menu-button" onClick={() => setMenuOpen(true)} aria-label="Abrir menú"><span className="menu-lines" aria-hidden="true" /></button><span>{screen === 'overview' ? 'Panel principal' : screen === 'clients' ? 'Clientes' : screen === 'tasks' ? 'Seguimientos' : screen === 'users' ? 'Usuarios' : 'Notificaciones'}</span><span className="topbar-right">{user.organizacion.nombre}</span></header>{screen === 'overview' ? <Overview onClients={() => canReadClients && navigate('clients')} canRead={canReadClients} onTasks={() => canReadTasks && navigate('tasks')} canReadTasks={canReadTasks} /> : screen === 'clients' ? <Clients canWrite={user.permisos.includes('clientes:escribir')} canArchive={user.permisos.includes('clientes:archivar')} canReadActivities={user.permisos.includes('seguimientos:leer')} canWriteActivities={user.permisos.includes('seguimientos:escribir')} /> : screen === 'tasks' ? <Tasks canWrite={user.permisos.includes('seguimientos:escribir')} canReadAll={user.permisos.includes('seguimientos:leer-todos')} /> : screen === 'users' ? <Users canManage={user.permisos.includes('usuarios:gestionar')} canAssign={user.permisos.includes('roles:asignar')} /> : <Notices />}</div>{menuOpen && <button className="menu-scrim" aria-label="Cerrar menú" onClick={() => setMenuOpen(false)} />}</div>;
}
ReactDOM.createRoot(document.getElementById('root')!).render(<App />);
