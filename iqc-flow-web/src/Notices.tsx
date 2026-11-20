import { useEffect, useState } from 'react';
import { api, type Notice, type Page } from './api';

const errorText = (error: unknown) => error instanceof Error ? error.message : 'Ocurrió un error inesperado.';
const formatDate = (value: string) => new Intl.DateTimeFormat('es-PE', {
  day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
}).format(new Date(value));

export function Notices() {
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [page, setPage] = useState(0), [refresh, setRefresh] = useState(0);
  const [data, setData] = useState<Page<Notice> | null>(null);
  const [busy, setBusy] = useState(false), [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    const params = new URLSearchParams({ soloNoLeidas: String(unreadOnly), pagina: String(page), tamano: '10' });
    api.notices(params).then(value => { if (active) { setData(value); setError(''); } })
      .catch(e => { if (active) setError(errorText(e)); });
    return () => { active = false; };
  }, [unreadOnly, page, refresh]);
  const reload = () => setRefresh(value => value + 1);
  async function markRead(id: string) {
    setBusy(true); setError('');
    try { await api.readNotice(id); reload(); }
    catch (e) { setError(errorText(e)); } finally { setBusy(false); }
  }
  async function markAll() {
    setBusy(true); setError('');
    try { await api.readAllNotices(); reload(); }
    catch (e) { setError(errorText(e)); } finally { setBusy(false); }
  }
  return <div className="screen-body">
    <div className="page-heading"><div><p className="context-line">Actividad personal</p><h1>Notificaciones</h1><p>Avisos sobre tus tareas y cambios relevantes.</p></div>
      <button className="text-button" onClick={markAll} disabled={busy}>Marcar todas como leídas</button></div>
    <section className="list-panel">
      <div className="task-toolbar"><label className="check-row"><input type="checkbox" checked={unreadOnly}
        onChange={event => { setUnreadOnly(event.target.checked); setPage(0); }} /> Solo no leídas</label>
        <span>{data ? `${data.totalElementos} avisos` : 'Cargando…'}</span></div>
      {error && <div className="notice" role="alert">{error} <button onClick={reload}>Reintentar</button></div>}
      <div className="notice-list">{data?.contenido.map(item => <article className={`notice-item ${item.leida ? '' : 'unread'}`} key={item.id}>
        <div><div className="notice-title"><strong>{item.titulo}</strong>{!item.leida && <span>Nuevo</span>}</div>
          <p>{item.cuerpo}</p><small>{formatDate(item.creadoEn)}</small></div>
        {!item.leida && <button className="text-button" disabled={busy} onClick={() => markRead(item.id)}>Marcar leída</button>}
      </article>)}</div>
      {data && !data.contenido.length && <p className="empty-state">No hay notificaciones en esta vista.</p>}
      <div className="pagination"><span>{data ? `${data.totalElementos} avisos` : 'Cargando…'}</span><div>
        <button disabled={page === 0} onClick={() => setPage(value => value - 1)}>Anterior</button>
        <span>Página {page + 1} de {Math.max(1, data?.totalPaginas || 1)}</span>
        <button disabled={!data || page + 1 >= data.totalPaginas} onClick={() => setPage(value => value + 1)}>Siguiente</button>
      </div></div>
    </section>
  </div>;
}
