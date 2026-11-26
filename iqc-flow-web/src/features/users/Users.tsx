import { X, Plus, Search, ChevronRight } from 'lucide-react';
import { useEffect, useState, type FormEvent } from 'react';
import { api, type AppRole, type AppUser, type Page } from '../../lib/api';
import { ContrasenaTemporal } from './ContrasenaTemporal';
import { LONGITUD_MINIMA_CONTRASENA } from '../../lib/politicaContrasena';
import { Boton, BotonIcono } from '../../components/ui/Boton';
import { errorText } from '../../lib/format';

const formatDate = (value: string | null) => value
  ? new Intl.DateTimeFormat('es-PE', { day: '2-digit', month: 'short', year: 'numeric' }).format(new Date(value)) : '—';

function UserForm({ initial, roles, canManage, canAssign, onSave, onClose, onUpdated, busy, error }: {
  initial?: AppUser; roles: AppRole[]; canManage: boolean; canAssign: boolean; onUpdated: (u: AppUser) => void;
  onSave: (data: { correo: string; nombre: string; contrasenaInicial: string; estado: AppUser['estado']; roles: string[] }) => void;
  onClose: () => void; busy: boolean; error: string;
}) {
  const [name, setName] = useState(initial?.nombre || '');
  const [email, setEmail] = useState(initial?.correo || '');
  const [password, setPassword] = useState('');
  const [state, setState] = useState<AppUser['estado']>(initial?.estado || 'ACTIVO');
  const [selectedRoles, setSelectedRoles] = useState<string[]>(initial?.roles || []);
  function toggleRole(code: string) {
    setSelectedRoles(current => current.includes(code) ? current.filter(value => value !== code) : [...current, code]);
  }
  function submit(event: FormEvent) {
    event.preventDefault();
    onSave({ correo: email.trim(), nombre: name.trim(), contrasenaInicial: password, estado: state, roles: selectedRoles });
  }
  return <div className="dialog-backdrop" onMouseDown={event => { if (event.target === event.currentTarget) onClose(); }}>
    <section className="dialog" role="dialog" aria-modal="true" aria-labelledby="user-dialog-title">
      <div className="dialog-header"><div><h2 id="user-dialog-title">{initial ? 'Editar usuario' : 'Nuevo usuario'}</h2></div>
        <BotonIcono etiqueta="Cerrar" icono={X} tamano={18} type="button" onClick={onClose} /></div>
      <form className="client-form" onSubmit={submit}>
        <div className="form-grid">
          <label className="full">Nombre<input value={name} onChange={event => setName(event.target.value)} maxLength={200} required readOnly={!canManage} /></label>
          <label className="full">Correo electrónico<input type="email" value={email} onChange={event => setEmail(event.target.value)} readOnly={!!initial} required maxLength={320} /></label>
          {!initial && <label className="full">Contraseña inicial<input type="password" autoComplete="new-password" value={password} onChange={event => setPassword(event.target.value)} minLength={LONGITUD_MINIMA_CONTRASENA} maxLength={128} required /><span className="field-help">El usuario tendrá que cambiarla al ingresar.</span></label>}
          {initial && <label className="full">Estado<select value={state} onChange={event => setState(event.target.value as AppUser['estado'])} disabled={!canManage}><option value="ACTIVO">Activo</option><option value="DESHABILITADO">Deshabilitado</option></select></label>}
        </div>
        {canAssign && <fieldset className="role-fieldset"><legend>Roles</legend>{roles.map(role =>
          <label key={role.codigo}><input type="checkbox" checked={selectedRoles.includes(role.codigo)} onChange={() => toggleRole(role.codigo)} />
            <span><strong>{role.nombre}</strong><small>{role.descripcion}</small></span></label>)}</fieldset>}
        {error && <p className="form-error" role="alert">{error}</p>}
        <div className="form-actions"><Boton type="button" variante="secundario" onClick={onClose}>Cancelar</Boton>
          <Boton type="submit" variante="primario" disabled={busy}>{busy ? 'Guardando…' : initial ? 'Guardar cambios' : 'Crear usuario'}</Boton></div>
      </form>
      {initial && canManage && <ContrasenaTemporal user={initial} alGuardar={onUpdated} />}
    </section>
  </div>;
}

export function Users({ canManage, canAssign }: { canManage: boolean; canAssign: boolean }) {
  const [search, setSearch] = useState(''), [query, setQuery] = useState('');
  const [page, setPage] = useState(0), [refresh, setRefresh] = useState(0);
  const [data, setData] = useState<Page<AppUser> | null>(null), [roles, setRoles] = useState<AppRole[]>([]);
  const [selected, setSelected] = useState<AppUser | null>(null), [creating, setCreating] = useState(false);
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [formError, setFormError] = useState('');
  useEffect(() => { const timer = window.setTimeout(() => setQuery(search.trim()), 250); return () => window.clearTimeout(timer); }, [search]);
  useEffect(() => {
    let active = true;
    const params = new URLSearchParams({ pagina: String(page), tamano: '10' });
    if (query) params.set('texto', query);
    api.users(params).then(value => { if (active) { setData(value); setError(''); } }).catch(e => { if (active) setError(errorText(e)); });
    return () => { active = false; };
  }, [query, page, refresh]);
  useEffect(() => { api.roles().then(setRoles).catch(() => {}); }, []);
  const reload = () => setRefresh(value => value + 1);
  async function save(values: { correo: string; nombre: string; contrasenaInicial: string; estado: AppUser['estado']; roles: string[] }) {
    setBusy(true); setFormError('');
    try {
      if (!selected) {
        await api.createUser({ correo: values.correo, nombre: values.nombre,
          contrasenaInicial: values.contrasenaInicial, roles: canAssign ? values.roles : [] });
      } else {
        let updated = selected;
        if (values.nombre !== selected.nombre || values.estado !== selected.estado) {
          updated = await api.updateUser(selected.id, { nombre: values.nombre, estado: values.estado, version: selected.version });
        }
        const changedRoles = [...values.roles].sort().join('|') !== [...selected.roles].sort().join('|');
        if (canAssign && changedRoles) await api.assignRoles(selected.id, { roles: values.roles, version: updated.version });
      }
      setSelected(null); setCreating(false); reload();
    } catch (e) { setFormError(errorText(e)); }
    finally { setBusy(false); }
  }
  return <div className="screen-body">
    <div className="page-heading"><div><h1>Usuarios</h1><p>Accesos y roles de la organización.</p></div>
      {canManage && <Boton variante="primario" onClick={() => { setSelected(null); setFormError(''); setCreating(true); }}><Plus size={16} aria-hidden /> Nuevo usuario</Boton>}</div>
    <section className="list-panel"><div className="list-toolbar"><label className="search-field"><Search size={16} aria-hidden /><span className="sr-only">Buscar usuarios</span>
      <input value={search} onChange={event => { setSearch(event.target.value); setPage(0); }} placeholder="Buscar por nombre o correo" /></label></div>
      {error && <div className="notice" role="alert">{error} <Boton variante="texto" onClick={reload}>Reintentar</Boton></div>}
      <div className="table-wrap"><table><thead><tr><th>Usuario</th><th>Roles</th><th>Estado</th><th>Último acceso</th><th><span className="sr-only">Acción</span></th></tr></thead>
        <tbody>{data?.contenido.map(item => <tr key={item.id}><td><strong>{item.nombre}</strong><small>{item.correo}</small></td>
          <td>{item.roles.length ? item.roles.join(', ') : 'Sin rol'}</td><td>{item.estado === 'ACTIVO' ? 'Activo' : 'Deshabilitado'}</td><td>{formatDate(item.ultimoAccesoEn)}</td>
          <td>{(canManage || canAssign) && <Boton variante="texto" onClick={() => { setFormError(''); setSelected(item); }}>Editar <ChevronRight size={14} aria-hidden /></Boton>}</td></tr>)}</tbody></table></div>
      <div className="mobile-list">{data?.contenido.map(item => <button className="mobile-row" key={item.id}
        onClick={() => { if (canManage || canAssign) { setFormError(''); setSelected(item); } }}>
        <span className="mobile-row-main"><strong>{item.nombre}</strong><span>{item.estado === 'ACTIVO' ? 'Activo' : 'Deshabilitado'}</span></span>
        <span className="mobile-row-meta">{item.correo} · {item.roles.join(', ') || 'Sin rol'}</span></button>)}</div>
      {data && !data.contenido.length && <p className="empty-state">No se encontraron usuarios.</p>}
      <div className="pagination"><span>{data ? `${data.totalElementos} usuarios` : 'Cargando…'}</span><div>
        <button disabled={page === 0} onClick={() => setPage(value => value - 1)}>Anterior</button>
        <span>Página {page + 1} de {Math.max(1, data?.totalPaginas || 1)}</span>
        <button disabled={!data || page + 1 >= data.totalPaginas} onClick={() => setPage(value => value + 1)}>Siguiente</button>
      </div></div>
    </section>
    {(creating || selected) && <UserForm key={selected?.id || 'new'} initial={selected || undefined} roles={roles}
      canManage={canManage} canAssign={canAssign} onSave={save} onUpdated={u => { setSelected(u); reload(); }} onClose={() => { setSelected(null); setCreating(false); }} busy={busy} error={formError} />}
  </div>;
}
