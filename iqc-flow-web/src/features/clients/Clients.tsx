import { useEffect, useState, type FormEvent } from 'react';
import { Archive, ArchiveRestore, BadgeCheck, Building2, ChevronRight, LoaderCircle, MoonStar, Pencil, Plus, Search, Sparkles } from 'lucide-react';
import { FilaCifras, TarjetaCifra } from '../../components/cifras/TarjetaCifra';
import { api, ApiError, type ClientDetail, type ClientStatus, type ClientSummary, type Page } from '../../lib/api';
import { day, errorText } from '../../lib/format';
import { Drawer } from '../../components/Drawer';
import { Pagination } from '../../components/Pagination';
import { SearchField } from '../../components/SearchField';
import { ClientExtras } from './ClientExtras';

const statusNames: Record<ClientStatus, string> = { PROSPECTO: 'Prospecto', ACTIVO: 'Activo', INACTIVO: 'Inactivo' };

function StatusLabel({ estado, archivado }: { estado: ClientStatus; archivado: boolean }) {
  return <span className={`status-label ${estado.toLowerCase()}`}><span className={`status-dot ${archivado ? 'archivado' : estado.toLowerCase()}`} />{archivado ? 'Archivado' : statusNames[estado]}</span>;
}

type DireccionFiscal = { direccion: string; distrito: string; provincia: string; departamento: string };

/**
 * Formulario de cliente. Empieza por el RUC: la lupa consulta SUNAT y completa la razón social y la dirección fiscal
 * (que se guarda con el cliente al crearlo). Todo lo propuesto se puede corregir antes de guardar.
 */
function ClientForm({ initial, onSave, onCancel, busy, error }: { initial?: ClientDetail; onSave: (data: object, fiscal: DireccionFiscal | null) => void; onCancel: () => void; busy: boolean; error: string }) {
  const [name, setName] = useState(initial?.razonSocial || ''), [trade, setTrade] = useState(initial?.nombreComercial || '');
  const [sector, setSector] = useState(initial?.sector || ''), [status, setStatus] = useState<ClientStatus>(initial?.estado || 'PROSPECTO');
  const [ruc, setRuc] = useState(initial?.numeroDocumento || ''), [notes, setNotes] = useState(initial?.notas || '');
  const [fiscal, setFiscal] = useState<DireccionFiscal | null>(null);
  const [consultando, setConsultando] = useState(false), [aviso, setAviso] = useState('');
  const rucValido = /^(10|15|16|17|20)\d{9}$/.test(ruc.trim());
  async function buscar() {
    setAviso('');
    if (!rucValido) { setAviso('El RUC tiene 11 dígitos y empieza con 10, 15, 16, 17 o 20.'); return; }
    setConsultando(true);
    try {
      const d = await api.padronRuc(ruc.trim());
      setName(d.razonSocial);
      // La dirección fiscal se propone al crear; al editar, las direcciones se gestionan en la ficha del cliente
      if (!initial && d.direccion) setFiscal({ direccion: d.direccion, distrito: d.distrito, provincia: d.provincia, departamento: d.departamento });
    } catch (e) { setAviso(e instanceof ApiError && e.status === 404 ? 'SUNAT no tiene datos de ese RUC. Completa los datos a mano.' : errorText(e)); }
    finally { setConsultando(false); }
  }
  function submit(event: FormEvent) {
    event.preventDefault();
    onSave({ razonSocial: name.trim(), nombreComercial: trade.trim(), sector: sector.trim(), estado: status,
      ...(ruc.trim() ? { tipoDocumento: 'RUC', numeroDocumento: ruc.trim() } : {}), notas: notes.trim(), ...(initial ? { version: initial.version } : {}) },
      fiscal && fiscal.direccion.trim() ? fiscal : null);
  }
  const setF = (k: keyof DireccionFiscal) => (e: { target: { value: string } }) => setFiscal(f => f && { ...f, [k]: e.target.value });
  return <form className="client-form" onSubmit={submit}>
    <div className="ruc-busqueda">
      <label htmlFor="cliente-ruc">RUC</label>
      <div className="ruc-busqueda-fila">
        <input id="cliente-ruc" value={ruc} onChange={e => { setRuc(e.target.value.replace(/\D/g, '')); setAviso(''); }}
          onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); buscar(); } }}
          inputMode="numeric" maxLength={11} pattern="[0-9]{11}" placeholder="11 dígitos" autoFocus={!initial} aria-describedby={aviso ? "cliente-ruc-ayuda" : undefined} />
        <button type="button" className="primary ruc-buscar" onClick={buscar} disabled={consultando || !ruc.trim()} aria-label="Buscar los datos de este RUC">
          {consultando ? <LoaderCircle size={16} className="spin" aria-hidden /> : <Search size={16} aria-hidden />} {consultando ? 'Buscando…' : 'Buscar'}</button>
      </div>
      {/* Solo se avisa cuando no se pudo consultar; si se encuentra, los datos aparecen abajo sin mensaje */}
      {aviso && <span id="cliente-ruc-ayuda" className="field-help aviso" role="alert">{aviso}</span>}
    </div>
    <div className="campos">
      <label>Razón social<input value={name} onChange={e => setName(e.target.value)} required maxLength={300} /></label>
      <label>Nombre comercial<input value={trade} onChange={e => setTrade(e.target.value)} maxLength={300} /></label>
      <div className="form-grid">
        <label>Sector<input value={sector} onChange={e => setSector(e.target.value)} maxLength={120} /></label>
        <label>Estado<select value={status} onChange={e => setStatus(e.target.value as ClientStatus)}><option value="PROSPECTO">Prospecto</option><option value="ACTIVO">Activo</option><option value="INACTIVO">Inactivo</option></select></label>
      </div>
    </div>
    {fiscal && <fieldset className="fiscal-sunat"><legend>Dirección fiscal</legend>
      <label>Dirección<input value={fiscal.direccion} onChange={setF('direccion')} maxLength={300} /></label>
      <div className="form-grid three">
        <label>Distrito<input value={fiscal.distrito} onChange={setF('distrito')} maxLength={100} /></label>
        <label>Provincia<input value={fiscal.provincia} onChange={setF('provincia')} maxLength={100} /></label>
        <label>Departamento<input value={fiscal.departamento} onChange={setF('departamento')} maxLength={100} /></label>
      </div>
      <button type="button" className="text-button" onClick={() => setFiscal(null)}>No guardar esta dirección</button></fieldset>}
    <label>Notas<textarea value={notes} onChange={e => setNotes(e.target.value)} rows={4} maxLength={4000} /></label>
    {status === 'ACTIVO' && !ruc && <p className="form-hint">Un cliente activo necesita un RUC válido.</p>}
    {error && <p className="form-error" role="alert">{error}</p>}
    <div className="form-actions"><button type="button" className="secondary" onClick={onCancel}>Cancelar</button><button className="primary" disabled={busy || (status === 'ACTIVO' && !ruc)}>{busy ? 'Guardando…' : initial ? 'Guardar cambios' : 'Crear cliente'}</button></div></form>;
}

export function Clients({ canWrite, canArchive, canReadActivities, canWriteActivities }: {
  canWrite: boolean; canArchive: boolean; canReadActivities: boolean; canWriteActivities: boolean;
}) {
  const [text, setText] = useState(''), [debounced, setDebounced] = useState(''), [filter, setFilter] = useState<ClientStatus | 'TODOS'>('TODOS');
  const [page, setPage] = useState(0), [archived, setArchived] = useState(false);
  const [data, setData] = useState<Page<ClientSummary> | null>(null), [detail, setDetail] = useState<ClientDetail | null>(null);
  const [mode, setMode] = useState<'view' | 'create' | 'edit' | null>(null), [error, setError] = useState(''), [formError, setFormError] = useState('');
  const [busy, setBusy] = useState(false), [refresh, setRefresh] = useState(0);
  const [cuentas, setCuentas] = useState<Record<string, number> | null>(null);
  useEffect(() => {
    let active = true;
    const contar = (estado?: ClientStatus) => api.clients(new URLSearchParams(estado ? { estado, tamano: '1' } : { tamano: '1' })).then(r => r.totalElementos);
    Promise.all([contar(), contar('ACTIVO'), contar('PROSPECTO'), contar('INACTIVO')])
      .then(([todos, activos, prospectos, inactivos]) => { if (active) setCuentas({ todos, activos, prospectos, inactivos }); }).catch(() => {});
    return () => { active = false; };
  }, [refresh]);
  const filtrar = (estado: ClientStatus | 'TODOS') => { setFilter(filter === estado ? 'TODOS' : estado); setPage(0); };
  useEffect(() => { const timer = window.setTimeout(() => setDebounced(text.trim()), 300); return () => window.clearTimeout(timer); }, [text]);
  useEffect(() => {
    let active = true;
    const query = new URLSearchParams({ pagina: String(page), tamano: '15' });
    if (debounced) query.set('texto', debounced);
    if (filter !== 'TODOS') query.set('estado', filter);
    if (archived) query.set('incluirArchivados', 'true');
    api.clients(query).then(result => { if (active) { setData(result); setError(''); } }).catch(e => { if (active) setError(errorText(e)); });
    return () => { active = false; };
  }, [debounced, filter, page, archived, refresh]);
  const reload = () => setRefresh(n => n + 1);
  const close = () => { setDetail(null); setMode(null); };
  async function open(id: string) { setFormError(''); try { setDetail(await api.client(id)); setMode('view'); } catch (e) { setError(errorText(e)); } }
  async function save(payload: object, fiscal: DireccionFiscal | null) {
    setBusy(true); setFormError('');
    try {
      let saved = mode === 'create' ? await api.createClient(payload) : await api.updateClient(detail!.id, payload);
      // Dirección fiscal traída de SUNAT al crear: se guarda como la dirección FISCAL del cliente
      if (mode === 'create' && fiscal) {
        try { await api.addAddress(saved.id, { tipo: 'FISCAL', direccion: fiscal.direccion.trim(), distrito: fiscal.distrito.trim() || null,
          provincia: fiscal.provincia.trim() || null, departamento: fiscal.departamento.trim() || null, codigoPais: 'PE' }); saved = await api.client(saved.id); }
        catch (e) { setError(`Cliente creado, pero no se guardó la dirección fiscal: ${errorText(e)}`); }
      }
      setDetail(saved); setMode('view'); reload();
    }
    catch (e) { setFormError(errorText(e)); } finally { setBusy(false); }
  }
  async function toggleArchive() {
    if (!detail) return;
    setBusy(true); setFormError('');
    try { const saved = detail.archivado ? await api.restoreClient(detail.id, detail.version) : await api.archiveClient(detail.id, detail.version); setDetail(saved); reload(); }
    catch (e) { setFormError(errorText(e)); } finally { setBusy(false); }
  }
  return <div className="screen-body">
    <div className="page-heading"><div><h1>Clientes</h1><p>Cartera de IQC Perú e IQC LATAM: datos, contactos, sedes y actividad.</p></div>
      {canWrite && <button className="primary" onClick={() => { setDetail(null); setFormError(''); setMode('create'); }}><Plus size={16} aria-hidden /> Nuevo cliente</button>}</div>
    <FilaCifras etiqueta="Filtrar por estado">
      <TarjetaCifra icono={Building2} etiqueta="Cartera" valor={cuentas?.todos} detalle="Clientes y prospectos sin archivar" activa={filter === 'TODOS'} alPulsar={() => filtrar('TODOS')} />
      <TarjetaCifra icono={BadgeCheck} etiqueta="Activos" valor={cuentas?.activos} detalle="Marcados como activos" tono="ok" activa={filter === 'ACTIVO'} alPulsar={() => filtrar('ACTIVO')} />
      <TarjetaCifra icono={Sparkles} etiqueta="Prospectos" valor={cuentas?.prospectos} detalle="Aún no contratan un servicio" activa={filter === 'PROSPECTO'} alPulsar={() => filtrar('PROSPECTO')} />
      <TarjetaCifra icono={MoonStar} etiqueta="Inactivos" valor={cuentas?.inactivos} detalle="Marcados como inactivos" tono="neutro" activa={filter === 'INACTIVO'} alPulsar={() => filtrar('INACTIVO')} />
    </FilaCifras>
    <section className="list-panel">
      <div className="list-toolbar">
        <SearchField value={text} onChange={v => { setText(v); setPage(0); }} placeholder="Nombre, código o RUC" label="Buscar clientes" />
        <select aria-label="Filtrar por estado" value={filter} onChange={e => { setFilter(e.target.value as ClientStatus | 'TODOS'); setPage(0); }}><option value="TODOS">Todos los estados</option><option value="PROSPECTO">Prospectos</option><option value="ACTIVO">Activos</option><option value="INACTIVO">Inactivos</option></select>
        <label className="check-row"><input type="checkbox" checked={archived} onChange={e => { setArchived(e.target.checked); setPage(0); }} /> Incluir archivados</label>
      </div>
      {error && <div className="notice" role="alert">{error} <button className="text-button" onClick={reload}>Reintentar</button></div>}
      <div className="table-wrap"><table><thead><tr><th>Cliente</th><th>Código</th><th>Estado</th><th>Responsable</th><th>Actualizado</th><th><span className="sr-only">Abrir</span></th></tr></thead>
        <tbody>{!data && <tr className="skeleton-row"><td colSpan={6}><span /></td></tr>}{data?.contenido.map(client => <tr key={client.id} className="clickable" onClick={() => open(client.id)}>
          <td><strong>{client.razonSocial}</strong>{client.numeroDocumento && <small className="tabular">{client.numeroDocumento}</small>}</td>
          <td className="mono">{client.codigo}</td><td><StatusLabel estado={client.estado} archivado={client.archivado} /></td>
          <td>{client.responsable?.nombre || '—'}</td><td className="tabular">{day(client.actualizadoEn)}</td>
          <td><button className="row-link" onClick={event => { event.stopPropagation(); open(client.id); }} aria-label={`Abrir ${client.razonSocial}`}><ChevronRight size={16} aria-hidden /></button></td></tr>)}</tbody></table></div>
      <div className="mobile-list">{data?.contenido.map(client => <button className="mobile-row" key={client.id} onClick={() => open(client.id)}>
        <span className="mobile-row-main"><strong>{client.razonSocial}</strong><StatusLabel estado={client.estado} archivado={client.archivado} /></span>
        <span className="mobile-row-meta">{client.codigo} · {client.responsable?.nombre || 'Sin responsable'} · {day(client.actualizadoEn)}</span></button>)}</div>
      {data && data.contenido.length === 0 && <p className="empty-state">{debounced || filter !== 'TODOS' ? 'Ningún cliente coincide con la búsqueda. Prueba con el RUC o con parte de la razón social.' : 'Aún no hay clientes. Registra el primero con «Nuevo cliente».'}</p>}
      <Pagination page={page} totalPages={data?.totalPaginas || 1} total={data?.totalElementos ?? null} noun="clientes" onPage={setPage} />
    </section>
    {mode && <Drawer onClose={close} title={mode === 'create' ? 'Nuevo cliente' : mode === 'edit' ? 'Editar cliente' : detail?.razonSocial}
      subtitle={mode !== 'create' && detail ? <><span className="mono">{detail.codigo}</span> · <StatusLabel estado={detail.estado} archivado={detail.archivado} /></> : undefined}>
      {mode === 'create' || mode === 'edit' ? <ClientForm key={mode + (detail?.id || '')} initial={mode === 'edit' ? detail! : undefined} onSave={save} onCancel={() => detail ? setMode('view') : close()} busy={busy} error={formError} />
        : detail && <div className="detail-body">
          <dl className="detail-grid">
            <div><dt>Nombre comercial</dt><dd>{detail.nombreComercial || '—'}</dd></div>
            <div><dt>{detail.tipoDocumento || 'Documento'}</dt><dd className="tabular">{detail.numeroDocumento || '—'}</dd></div>
            <div><dt>Sector</dt><dd>{detail.sector || '—'}</dd></div>
            <div><dt>Responsable</dt><dd>{detail.responsable?.nombre || '—'}</dd></div>
            <div><dt>Última actualización</dt><dd>{day(detail.actualizadoEn)}</dd></div>
          </dl>
          <ClientExtras detail={detail} onChanged={setDetail} canReadActivities={canReadActivities} canWriteActivities={canWriteActivities} />
          {detail.notas && <><h3>Notas</h3><p className="detail-notes">{detail.notas}</p></>}
          {formError && <p className="form-error" role="alert">{formError}</p>}
          <div className="form-actions">
            {canArchive && <button className="secondary" disabled={busy} onClick={toggleArchive}>{detail.archivado ? <><ArchiveRestore size={16} aria-hidden /> Restaurar</> : <><Archive size={16} aria-hidden /> Archivar</>}</button>}
            {detail.puedeEditar && !detail.archivado && <button className="primary" onClick={() => setMode('edit')}><Pencil size={16} aria-hidden /> Editar cliente</button>}
          </div>
        </div>}
    </Drawer>}
  </div>;
}
