import { X, Plus, CalendarCheck, CalendarRange, Clock3, PauseCircle } from 'lucide-react';
import { FilaCifras, TarjetaCifra } from '../../components/cifras/TarjetaCifra';
import { Pagination } from '../../components/Pagination';
import { FilaSeguimiento } from './FilaSeguimiento';
import { useEffect, useState, type FormEvent } from 'react';
import { api, type ClientSummary, type TaskCounts, type TaskDetail, type TaskSummary, type TaskType, type TaskView, type Page } from '../../lib/api';

const views: Array<[TaskView, string]> = [
  ['ABIERTAS', 'Abiertas'], ['HOY', 'Hoy'], ['ATRASADAS', 'Atrasadas'],
  ['PROXIMAS', 'Próximas'], ['EN_ESPERA', 'En espera'], ['CERRADAS', 'Cerradas'],
];
const stateNames: Record<string, string> = {
  PENDIENTE: 'Pendiente', EN_CURSO: 'En curso', EN_ESPERA: 'En espera',
  COMPLETADA: 'Completada', CANCELADA: 'Cancelada',
};
const resultNames: Record<string, string> = {
  REALIZADA: 'Realizada', CONTACTADO: 'Contactado', SIN_RESPUESTA: 'Sin respuesta',
  INTERESADO: 'Interesado', ACEPTADO: 'Aceptado', RECHAZADO: 'Rechazado', NO_APLICA: 'No aplica',
};
const localDate = (offset = 0) => {
  const value = new Date(); value.setDate(value.getDate() + offset);
  return [value.getFullYear(), String(value.getMonth() + 1).padStart(2, '0'), String(value.getDate()).padStart(2, '0')].join('-');
};
const displayDate = (value: string) => new Intl.DateTimeFormat('es-PE', { day: '2-digit', month: 'short', year: 'numeric' }).format(new Date(value + 'T12:00:00'));
const errorText = (error: unknown) => error instanceof Error ? error.message : 'Ocurrió un error inesperado.';

function ClientPicker({ value, onChange }: { value: ClientSummary | null; onChange: (client: ClientSummary | null) => void }) {
  const [query, setQuery] = useState(value?.razonSocial || '');
  const [matches, setMatches] = useState<ClientSummary[]>([]);
  useEffect(() => {
    if (value || query.trim().length < 2) return;
    let active = true;
    const timer = window.setTimeout(() => {
      api.clients(new URLSearchParams({ texto: query.trim(), tamano: '6' }))
        .then(result => { if (active) setMatches(result.contenido); })
        .catch(() => { if (active) setMatches([]); });
    }, 250);
    return () => { active = false; window.clearTimeout(timer); };
  }, [query, value]);
  return <div className="client-picker">
    <label>Cliente (opcional)<input value={query} onChange={event => { setQuery(event.target.value); onChange(null); }} placeholder="Buscar cliente" /></label>
    {value && <button type="button" className="picker-clear" onClick={() => { setQuery(''); onChange(null); }}>Quitar cliente</button>}
    {!value && matches.length > 0 && <div className="picker-results">{matches.map(client =>
      <button type="button" key={client.id} onClick={() => { onChange(client); setQuery(client.razonSocial); setMatches([]); }}>
        <strong>{client.razonSocial}</strong><span>{client.codigo}</span>
      </button>)}</div>}
  </div>;
}

function NewTask({ types, onSave, onClose, busy, error }: {
  types: TaskType[]; onSave: (data: object) => void; onClose: () => void; busy: boolean; error: string;
}) {
  const [title, setTitle] = useState(''), [description, setDescription] = useState('');
  const [type, setType] = useState(types[0]?.codigo || ''), [due, setDue] = useState(localDate(1));
  const [time, setTime] = useState(''), [client, setClient] = useState<ClientSummary | null>(null);
  useEffect(() => { if (!type && types.length) setType(types[0].codigo); }, [type, types]);
  function submit(event: FormEvent) {
    event.preventDefault();
    onSave({ titulo: title.trim(), descripcion: description.trim(), tipo: type, fechaLimite: due,
      ...(time ? { horaLimite: time } : {}), ...(client ? { clienteId: client.id } : {}) });
  }
  return <div className="dialog-backdrop" onMouseDown={event => { if (event.target === event.currentTarget) onClose(); }}>
    <section className="dialog" role="dialog" aria-modal="true" aria-labelledby="new-task-title">
      <div className="dialog-header"><div><h2 id="new-task-title">Nuevo seguimiento</h2></div>
        <button type="button" className="icon-button" onClick={onClose} aria-label="Cerrar"><X size={18} aria-hidden /></button></div>
      <form className="client-form" onSubmit={submit}>
        <div className="form-grid">
          <label className="full">Título<input value={title} onChange={event => setTitle(event.target.value)} maxLength={200} required autoFocus /></label>
          <label>Tipo<select value={type} onChange={event => setType(event.target.value)} required>
            {types.map(item => <option key={item.codigo} value={item.codigo}>{item.nombre}</option>)}</select></label>
          <label>Fecha límite<input type="date" value={due} onChange={event => setDue(event.target.value)} required /></label>
          <label>Hora límite (opcional)<input type="time" value={time} onChange={event => setTime(event.target.value)} /></label>
          <div className="full"><ClientPicker value={client} onChange={setClient} /></div>
          <label className="full">Descripción (opcional)<textarea rows={4} value={description} onChange={event => setDescription(event.target.value)} maxLength={2000} /></label>
        </div>
        {!types.length && <p className="form-hint">Cargando tipos de seguimiento. Si no aparecen, cierra y vuelve a intentar.</p>}
        {error && <p className="form-error" role="alert">{error}</p>}
        <div className="form-actions"><button type="button" className="secondary" onClick={onClose}>Cancelar</button>
          <button className="primary" disabled={busy || !types.length}>{busy ? 'Guardando…' : 'Crear seguimiento'}</button></div>
      </form>
    </section>
  </div>;
}

type TaskAction = 'complete' | 'reschedule' | 'cancel';
function TaskDetailPanel({ task, onClose, onUpdate, busy, error }: {
  task: TaskDetail; onClose: () => void; onUpdate: (action: 'state' | TaskAction, data: object) => void;
  busy: boolean; error: string;
}) {
  const [action, setAction] = useState<TaskAction | null>(null);
  const [result, setResult] = useState('REALIZADA'), [notes, setNotes] = useState('');
  const [due, setDue] = useState(task.fechaLimite), [reason, setReason] = useState('');
  const open = task.estado !== 'COMPLETADA' && task.estado !== 'CANCELADA';
  function submit(event: FormEvent) {
    event.preventDefault();
    if (action === 'complete') onUpdate('complete', { resultado: result, notas: notes.trim(), version: task.version });
    if (action === 'reschedule') onUpdate('reschedule', { fechaLimite: due, motivo: reason.trim(), version: task.version });
    if (action === 'cancel') onUpdate('cancel', { motivo: reason.trim(), version: task.version });
  }
  return <div className="dialog-backdrop" onMouseDown={event => { if (event.target === event.currentTarget) onClose(); }}>
    <section className="dialog" role="dialog" aria-modal="true" aria-labelledby="task-detail-title">
      <div className="dialog-header"><div><h2 id="task-detail-title">{task.titulo}</h2></div>
        <button type="button" className="icon-button" onClick={onClose} aria-label="Cerrar"><X size={18} aria-hidden /></button></div>
      <div className="detail-body">
        <div className="detail-meta"><span className={`task-state ${task.vencida ? 'overdue' : ''}`}>{stateNames[task.estado]}</span>
          <span>{task.prioridad.toLowerCase()} · {task.responsable.nombre}</span></div>
        <div className="detail-grid">
          <div><span>Fecha límite</span><strong>{displayDate(task.fechaLimite)}{task.horaLimite ? ` · ${task.horaLimite.slice(0, 5)}` : ''}</strong></div>
          <div><span>Cliente</span><strong>{task.cliente?.razonSocial || 'Sin cliente'}</strong></div>
        </div>
        {task.descripcion && <><h3>Descripción</h3><p className="detail-notes">{task.descripcion}</p></>}
        {task.resultado && <><h3>Resultado</h3><p className="detail-notes">{resultNames[task.resultado] || task.resultado}{task.notasResultado ? ` · ${task.notasResultado}` : ''}</p></>}
        {open && task.puedeGestionar && <div className="task-actions">
          {task.estado !== 'EN_CURSO' && <button className="secondary" disabled={busy} onClick={() => onUpdate('state', { estado: 'EN_CURSO', version: task.version })}>Iniciar</button>}
          {task.estado !== 'EN_ESPERA' && <button className="secondary" disabled={busy} onClick={() => onUpdate('state', { estado: 'EN_ESPERA', version: task.version })}>En espera</button>}
          <button className="primary" onClick={() => setAction('complete')}>Completar</button>
          <button className="text-button" onClick={() => setAction('reschedule')}>Reprogramar</button>
          <button className="text-button" onClick={() => setAction('cancel')}>Cancelar seguimiento</button>
        </div>}
        {action && <form className="task-action-form" onSubmit={submit}>
          <h3>{action === 'complete' ? 'Completar seguimiento' : action === 'reschedule' ? 'Reprogramar seguimiento' : 'Cancelar seguimiento'}</h3>
          {action === 'complete' ? <><label>Resultado<select value={result} onChange={event => setResult(event.target.value)}>
            {Object.entries(resultNames).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label>
            <label>Notas<textarea rows={3} value={notes} onChange={event => setNotes(event.target.value)} maxLength={2000} /></label></> :
            <>{action === 'reschedule' && <label>Nueva fecha<input type="date" value={due} onChange={event => setDue(event.target.value)} required /></label>}
              <label>Motivo<textarea rows={3} value={reason} onChange={event => setReason(event.target.value)} maxLength={1000} required /></label></>}
          {error && <p className="form-error" role="alert">{error}</p>}
          <div className="form-actions"><button type="button" className="secondary" onClick={() => setAction(null)}>Volver</button>
            <button className="primary" disabled={busy}>{busy ? 'Guardando…' : 'Confirmar'}</button></div>
        </form>}
        {!action && error && <p className="form-error" role="alert">{error}</p>}
        <h3>Historial</h3>
        {task.historial.length ? task.historial.map((item, index) => <div className="detail-item" key={index}>
          <strong>{item.evento.replaceAll('_', ' ').toLowerCase()}</strong>
          <span>{item.actor?.nombre || 'Sistema'} · {new Intl.DateTimeFormat('es-PE', { day: '2-digit', month: 'short', year: 'numeric' }).format(new Date(item.ocurridoEn))}{item.motivo ? ` · ${item.motivo}` : ''}</span>
        </div>) : <p className="detail-empty">Sin cambios registrados.</p>}
      </div>
    </section>
  </div>;
}

export function Tasks({ canWrite, canReadAll, initialView }: { canWrite: boolean; canReadAll: boolean; initialView?: TaskView }) {
  const [view, setView] = useState<TaskView>(initialView ?? 'ABIERTAS'), [mine, setMine] = useState(true);
  const [page, setPage] = useState(0), [refresh, setRefresh] = useState(0);
  const [items, setItems] = useState<Page<TaskSummary> | null>(null), [counts, setCounts] = useState<TaskCounts | null>(null);
  const [types, setTypes] = useState<TaskType[]>([]), [selected, setSelected] = useState<TaskDetail | null>(null);
  const [creating, setCreating] = useState(false), [busy, setBusy] = useState(false);
  const [error, setError] = useState(''), [formError, setFormError] = useState('');
  useEffect(() => {
    let active = true;
    const query = new URLSearchParams({ vista: view, mias: String(mine), pagina: String(page), tamano: '10' });
    api.tasks(query).then(data => { if (active) { setItems(data); setError(''); } }).catch(e => { if (active) setError(errorText(e)); });
    return () => { active = false; };
  }, [view, mine, page, refresh]);
  useEffect(() => {
    api.taskCounts().then(setCounts).catch(() => {});
    api.taskTypes().then(setTypes).catch(() => {});
  }, [refresh]);
  const reload = () => setRefresh(value => value + 1);
  const elegir = (v: TaskView) => { setView(view === v ? 'ABIERTAS' : v); setPage(0); };
  async function openTask(id: string) { setFormError(''); try { setSelected(await api.task(id)); } catch (e) { setError(errorText(e)); } }
  async function create(data: object) {
    setBusy(true); setFormError('');
    try { const task = await api.createTask(data); setCreating(false); setSelected(task); reload(); }
    catch (e) { setFormError(errorText(e)); } finally { setBusy(false); }
  }
  async function update(action: 'state' | TaskAction, data: object) {
    if (!selected) return;
    setBusy(true); setFormError('');
    try {
      const saved = action === 'state' ? await api.updateTask(selected.id, data) :
        action === 'complete' ? await api.completeTask(selected.id, data) :
        action === 'reschedule' ? await api.rescheduleTask(selected.id, data) :
        await api.cancelTask(selected.id, data);
      setSelected(saved); reload();
    } catch (e) { setFormError(errorText(e)); }
    finally { setBusy(false); }
  }
  return <div className="screen-body">
    <div className="page-heading"><div><h1>Seguimientos</h1><p>Lo que requiere respuesta, quién lo lleva y cuándo vence. Registra aquí el avance.</p></div>
      {canWrite && <button className="primary" onClick={() => { setFormError(''); setCreating(true); }}><Plus size={16} aria-hidden /> Nuevo seguimiento</button>}</div>
    <FilaCifras etiqueta="Resumen de seguimientos">
      <TarjetaCifra icono={Clock3} etiqueta="Atrasados" valor={counts?.atrasadas} detalle="Pasaron su fecha límite" tono="mal" activa={view === 'ATRASADAS'} alPulsar={() => elegir('ATRASADAS')} />
      <TarjetaCifra icono={CalendarCheck} etiqueta="Para hoy" valor={counts?.hoy} detalle="Vencen hoy" tono="alerta" activa={view === 'HOY'} alPulsar={() => elegir('HOY')} />
      <TarjetaCifra icono={CalendarRange} etiqueta="Próximos" valor={counts?.proximas} detalle="En los próximos 7 días" activa={view === 'PROXIMAS'} alPulsar={() => elegir('PROXIMAS')} />
      <TarjetaCifra icono={PauseCircle} etiqueta="En espera" valor={counts?.enEspera} detalle="Esperan una respuesta externa" tono="neutro" activa={view === 'EN_ESPERA'} alPulsar={() => elegir('EN_ESPERA')} />
    </FilaCifras>
    <section className="list-panel"><div className="task-tabs" role="tablist" aria-label="Vista de seguimientos">{views.map(([key, label]) =>
      <button role="tab" aria-selected={view === key} className={view === key ? 'active' : ''} key={key}
        onClick={() => { setView(key); setPage(0); }}>{label}</button>)}</div>
      <div className="task-toolbar"><span>{items ? `${items.totalElementos} ${items.totalElementos === 1 ? 'seguimiento' : 'seguimientos'}` : 'Cargando seguimientos…'}</span>
        {canReadAll && <label className="check-row"><input type="checkbox" checked={!mine} onChange={event => { setMine(!event.target.checked); setPage(0); }} /> Todas las visibles</label>}</div>
      {error && <div className="notice" role="alert">{error} <button onClick={reload}>Reintentar</button></div>}
      <div className="task-list">{items?.contenido.map(task => <FilaSeguimiento key={task.id} t={task} alAbrir={() => openTask(task.id)} />)}</div>
      {items && items.contenido.length === 0 && <p className="empty-state">No hay seguimientos en esta vista.</p>}
      <Pagination page={page} totalPages={items?.totalPaginas ?? 1} total={items?.totalElementos ?? null} noun="seguimientos" onPage={setPage} />
    </section>
    {creating && <NewTask types={types} onSave={create} onClose={() => setCreating(false)} busy={busy} error={formError} />}
    {selected && <TaskDetailPanel key={selected.id + selected.version} task={selected} onClose={() => setSelected(null)} onUpdate={update} busy={busy} error={formError} />}
  </div>;
}
