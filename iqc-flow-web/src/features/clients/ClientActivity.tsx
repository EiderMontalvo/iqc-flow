import { Plus } from 'lucide-react';
import { useEffect, useState, type FormEvent } from 'react';
import { api, type Activity, type Page } from '../../lib/api';
import { Boton } from '../../components/ui/Boton';
import { errorText } from '../../lib/format';

const types: Array<[Activity['tipo'], string]> = [
  ['LLAMADA', 'Llamada'], ['CORREO', 'Correo'], ['REUNION', 'Reunión'],
  ['WHATSAPP', 'WhatsApp'], ['VISITA', 'Visita'], ['NOTA', 'Nota'],
];
const label = (value: string) => types.find(item => item[0] === value)?.[1] || value;

export function ClientActivity({ clientId, canWrite }: { clientId: string; canWrite: boolean }) {
  const [page, setPage] = useState(0), [refresh, setRefresh] = useState(0);
  const [data, setData] = useState<Page<Activity> | null>(null);
  const [type, setType] = useState<Activity['tipo']>('LLAMADA');
  const [direction, setDirection] = useState<'ENTRANTE' | 'SALIENTE'>('SALIENTE');
  const [summary, setSummary] = useState('');
  const [adding, setAdding] = useState(false), [busy, setBusy] = useState(false), [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    api.activities(clientId, page).then(value => { if (active) { setData(value); setError(''); } })
      .catch(e => { if (active) setError(errorText(e)); });
    return () => { active = false; };
  }, [clientId, page, refresh]);
  async function submit(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('');
    try {
      await api.addActivity({ clienteId: clientId, tipo: type,
        direccion: type === 'NOTA' ? 'INTERNA' : direction, resumen: summary.trim() });
      setSummary(''); setAdding(false); setPage(0); setRefresh(value => value + 1);
    } catch (e) { setError(errorText(e)); }
    finally { setBusy(false); }
  }
  return <section className="client-activity">
    <div className="extras-section-head"><h3>Actividad</h3>{canWrite && <Boton variante="texto" onClick={() => setAdding(value => !value)}>{adding ? 'Cerrar' : <><Plus size={14} aria-hidden /> Registrar actividad</>}</Boton>}</div>
    {adding && <form className="extras-form" onSubmit={submit}><div className="form-grid">
      <label>Tipo<select value={type} onChange={event => setType(event.target.value as Activity['tipo'])}>{types.map(([key, name]) => <option key={key} value={key}>{name}</option>)}</select></label>
      {type !== 'NOTA' && <label>Dirección<select value={direction} onChange={event => setDirection(event.target.value as 'ENTRANTE' | 'SALIENTE')}>
        <option value="SALIENTE">Saliente</option><option value="ENTRANTE">Entrante</option></select></label>}
      <label className="full">Resumen<textarea value={summary} onChange={event => setSummary(event.target.value)} rows={3} required maxLength={2000} /></label>
    </div><div className="form-actions"><Boton type="submit" variante="primario" disabled={busy}>{busy ? 'Guardando…' : 'Guardar actividad'}</Boton></div></form>}
    {error && <p className="form-error" role="alert">{error}</p>}
    {data?.contenido.map(item => <div className="detail-item" key={item.id}><strong>{label(item.tipo)}</strong>
      <span>{new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(item.ocurridaEn))} · {item.registradaPor?.nombre || 'Sistema'}</span>
      <span className="activity-summary">{item.resumen}</span></div>)}
    {data && !data.contenido.length && <p className="detail-empty">Aún no hay actividades registradas.</p>}
    {data && data.totalPaginas > 1 && <div className="pagination"><button disabled={page === 0} onClick={() => setPage(value => value - 1)}>Anterior</button>
      <span>Página {page + 1} de {data.totalPaginas}</span>
      <button disabled={page + 1 >= data.totalPaginas} onClick={() => setPage(value => value + 1)}>Siguiente</button></div>}
  </section>;
}
