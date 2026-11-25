import { useEffect, useState, type FormEvent } from 'react';
import { AlertTriangle, CheckCircle2, ExternalLink, Pencil, RefreshCw } from 'lucide-react';
import { api, type Brand, type PublicationStatus } from '../../lib/api';

const errorText = (error: unknown) => error instanceof Error ? error.message : 'Ocurrió un error inesperado.';
const stamp = (value: string) => new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
const relativo = new Intl.RelativeTimeFormat('es', { numeric: 'auto' });
/** «hace 3 minutos», «hace 2 horas»: cuándo se actualizó el portal por última vez */
function hace(valor: string) {
  const min = Math.round((new Date(valor).getTime() - Date.now()) / 60_000);
  if (Math.abs(min) < 1) return 'hace un momento';
  return Math.abs(min) < 60 ? relativo.format(min, 'minute') : Math.abs(min) < 1440 ? relativo.format(Math.round(min / 60), 'hour') : relativo.format(Math.round(min / 1440), 'day');
}
const sinProtocolo = (url: string) => url.replace(/^https?:\/\//, '').replace(/\/$/, '');

function BrandForm({ brand, onSaved, onCancel }: { brand: Brand; onSaved: () => void; onCancel: () => void }) {
  const [name, setName] = useState(brand.nombre), [url, setUrl] = useState(brand.urlConsulta);
  const [site, setSite] = useState(brand.sitioWeb || ''), [mail, setMail] = useState(brand.correo || '');
  const [phone, setPhone] = useState(brand.telefono || ''), [address, setAddress] = useState(brand.direccion || '');
  const [logo, setLogo] = useState<File | null>(null), [busy, setBusy] = useState(false), [error, setError] = useState('');
  async function submit(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('');
    try {
      const logoDocumentoId = logo ? (await api.uploadDocument('LOGO_MARCA', logo)).id : brand.logoDocumentoId;
      await api.saveBrand(brand.codigoPais, { nombre: name.trim(), urlConsulta: url.trim(), sitioWeb: site.trim() || null,
        correo: mail.trim() || null, telefono: phone.trim() || null, direccion: address.trim() || null, logoDocumentoId, version: brand.version });
      onSaved();
    } catch (e) { setError(errorText(e)); } finally { setBusy(false); }
  }
  return <form className="client-form" onSubmit={submit}><div className="form-grid">
    <label>Nombre visible<input required maxLength={80} value={name} onChange={e => setName(e.target.value)} /></label>
    <label>Dirección del portal (va en el QR)<input required maxLength={255} value={url} onChange={e => setUrl(e.target.value)} spellCheck={false} /></label>
    <label>Sitio web<input maxLength={255} value={site} onChange={e => setSite(e.target.value)} /></label>
    <label>Correo<input type="email" maxLength={255} value={mail} onChange={e => setMail(e.target.value)} /></label>
    <label>Teléfono<input maxLength={60} value={phone} onChange={e => setPhone(e.target.value)} /></label>
    <label>Logo para el QR (PNG)<input type="file" accept="image/png,image/jpeg" onChange={e => setLogo(e.target.files?.[0] || null)} /></label>
    <label className="full">Dirección<input maxLength={500} value={address} onChange={e => setAddress(e.target.value)} /></label>
  </div>
    {brand.codigoPais === 'PE' && <p className="form-hint">La consulta de Perú está impresa en los certificados y QR emitidos: no la cambies salvo que el dominio anterior siga redirigiendo.</p>}
    {error && <p className="form-error" role="alert">{error}</p>}
    <div className="form-actions"><button type="button" className="secondary" onClick={onCancel}>Cancelar</button><button className="primary" disabled={busy}>{busy ? 'Guardando…' : 'Guardar marca'}</button></div>
  </form>;
}

export function Brands({ canConfigure, canPublish }: { canConfigure: boolean; canPublish: boolean }) {
  const [brands, setBrands] = useState<Brand[]>([]), [status, setStatus] = useState<PublicationStatus | null>(null);
  const [editing, setEditing] = useState<string | null>(null), [error, setError] = useState(''), [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false), [refresh, setRefresh] = useState(0);
  useEffect(() => {
    api.brands().then(setBrands).catch(e => setError(errorText(e)));
    api.publication().then(setStatus).catch(() => setStatus(null));
  }, [refresh]);
  async function publish(forzar: boolean) {
    if (forzar && !window.confirm('Se volverán a enviar todos los certificados al portal de consulta. Úsalo solo si el portal muestra datos desactualizados. ¿Continuar?')) return;
    setBusy(true); setError(''); setNotice('');
    try {
      const r = await api.publish(forzar);
      setNotice(!r.habilitada ? 'El portal de consulta aún no está activado.' : !r.ejecutada ? 'Ya se está actualizando; el resultado se verá en unos segundos.'
        : r.fallidos ? `Portal actualizado: ${r.publicados} cambios publicados; ${r.fallidos} no se pudieron publicar.` : `Portal actualizado: ${r.publicados} cambios publicados.`);
      setRefresh(n => n + 1);
    } catch (e) { setError(errorText(e)); } finally { setBusy(false); }
  }
  return <div className="screen-body">
    <div className="page-heading"><div><h1>Marcas y portal de consulta</h1><p>Cómo se presenta cada marca y el estado del portal donde el público verifica sus certificados.</p></div></div>
    {error && <div className="notice" role="alert"><AlertTriangle size={16} aria-hidden /> {error}</div>}
    {notice && <p className="form-success" role="status"><CheckCircle2 size={14} aria-hidden /> {notice}</p>}
    <section className="panel portal-estado">
      <div className="panel-title"><div><h2>Portal de consulta</h2><p>Los cambios se publican solos cada minuto: no hace falta hacer nada.</p></div>
        {canPublish && <button className="secondary" disabled={busy} onClick={() => publish(false)}><RefreshCw size={16} aria-hidden /> Actualizar ahora</button>}</div>
      {!status ? <div className="skeleton-rows" aria-hidden><span /><span /></div> : <>
        <div className="portal-estado-cifras">
          <div><span>Estado</span>{status.errores.length
            ? <strong className="status-label"><span className="status-dot inactivo alerta" />{status.errores.length} {status.errores.length === 1 ? 'certificado pendiente' : 'certificados pendientes'}</strong>
            : <strong className="status-label"><span className="status-dot activo" />Al día</strong>}</div>
          <div><span>Certificados visibles al público</span><strong className="tabular">{status.publicados.toLocaleString('es-PE')}</strong></div>
          <div><span>Última actualización</span><strong>{status.ultimaPasada ? hace(status.ultimaPasada.ejecutadaEn) : 'Todavía no'}</strong></div>
        </div>
        {status.errores.length > 0 && <p className="portal-aviso"><AlertTriangle size={16} aria-hidden /> Algunos certificados no se pudieron publicar. Se reintenta solo; si siguen pendientes después de «Actualizar ahora», avisa a soporte.</p>}
        {canPublish && <details className="opciones-avanzadas"><summary>Opciones avanzadas</summary>
          <p>Si el portal muestra datos desactualizados aunque el estado diga «Al día», puedes volver a enviar todos los certificados. Tarda unos minutos.</p>
          <button className="secondary small" disabled={busy} onClick={() => publish(true)}><RefreshCw size={14} aria-hidden /> Volver a enviar todo</button>
          {status.errores.length > 0 && <ul className="error-list">{status.errores.map(e => <li key={e.certificadoId}><code>{e.certificadoId.slice(0, 8)}</code> {e.error} <span>({e.intentos} intentos, {stamp(e.ultimoIntentoEn)})</span></li>)}</ul>}
        </details>}
      </>}
    </section>
    <section className="brand-grid" aria-label="Marcas por país">{brands.map(b => <article className="brand-card" key={b.codigoPais}>
      <header><div>{b.logoDocumentoId ? <img src={api.documentUrl(b.logoDocumentoId)} alt="" /> : <span className="brand-initials">{b.codigoPais}</span>}<div><h2>{b.nombre}</h2><p>{b.nombrePais}</p></div></div>
        {status && <span className="status-label"><span className={`status-dot ${status.paises.includes(b.codigoPais) ? 'activo' : 'inactivo'}`} />{status.paises.includes(b.codigoPais) ? 'En línea' : 'Sin activar'}</span>}</header>
      {editing === b.codigoPais ? <BrandForm brand={b} onCancel={() => setEditing(null)} onSaved={() => { setEditing(null); setNotice(`Marca ${b.nombre} guardada.`); setRefresh(n => n + 1); }} /> : <>
        <dl><dt>Portal</dt><dd><a href={b.urlConsulta} target="_blank" rel="noreferrer">{sinProtocolo(b.urlConsulta)} <ExternalLink size={12} aria-hidden /></a></dd>
          <dt>Sitio web</dt><dd>{b.sitioWeb ? <a href={b.sitioWeb} target="_blank" rel="noreferrer">{sinProtocolo(b.sitioWeb)} <ExternalLink size={12} aria-hidden /></a> : '—'}</dd>
          <dt>Correo</dt><dd>{b.correo || '—'}</dd></dl>
        {canConfigure && <button className="secondary small" onClick={() => setEditing(b.codigoPais)}><Pencil size={14} aria-hidden /> Editar</button>}</>}
    </article>)}</section>
  </div>;
}
