import { Fragment, useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import {
  BadgeCheck, CalendarClock, CalendarX2, Check, ChevronRight, Copy, Download, ExternalLink, FileSpreadsheet, FileText, FilterX, Plus, QrCode, Upload,
} from 'lucide-react';
import { Icono } from '../../lib/iconos';
import { FilaCifras, TarjetaCifra } from '../../components/cifras/TarjetaCifra';
import {
  api, type Brand, type Certificate, type CertificateState, type ConsultLink, type Page, type PdfVersion, type Scheme, type Standard, type Tools, type Validity,
} from '../../lib/api';
import { VistaPdf, type FuentePdf } from '../../components/pdf/VistaPdf';
import { day, errorText, kb, number, stamp } from '../../lib/format';
import { Drawer } from '../../components/Drawer';
import { Pagination } from '../../components/Pagination';
import { SearchField } from '../../components/SearchField';
import { CycleBar } from './CycleBar';
import { RegistrarCertificados } from './RegistrarCertificados';
import { ErrorCampo, Obligatorio, enfocarPrimerError, vacio } from '../../components/ui/Validacion';
import { Boton } from '../../components/ui/Boton';

const stateNames: Record<CertificateState, string> = {
  BORRADOR: 'Borrador', REVISADO: 'Revisado', EMITIDO: 'Emitido', SUSPENDIDO: 'Suspendido',
  RETIRADO: 'Retirado', CANCELADO: 'Anulado', REEMPLAZADO: 'Reemplazado',
};
const validityNames: Record<Validity, string> = { VIGENTE: 'Vigente', POR_VENCER: 'Por vencer', VENCIDO: 'Vencido', SIN_FECHA: 'Sin fecha' };
const serviceNames: Record<string, string> = { CERTIFICACION_INICIAL: 'Certificación inicial', RECERTIFICACION: 'Recertificación', AMPLIACION: 'Ampliación' };
const schemeShort = (code: string) => code === 'IQC_GLOBAL' ? 'IQC Global' : code;

export function StatusTag({ certificate }: { certificate: Certificate }) {
  const inactive = certificate.estado !== 'EMITIDO';
  const key = inactive ? certificate.estado.toLowerCase() : certificate.vigencia.toLowerCase();
  return <span className={`cert-tag ${key}`}>{inactive ? stateNames[certificate.estado] : validityNames[certificate.vigencia]}</span>;
}

/** Alcance del certificado: largo en muchos casos; se muestran unas líneas y se despliega el resto */
function Alcance({ texto }: { texto: string }) {
  const [abierto, setAbierto] = useState(false), largo = texto.length > 360 || texto.split('\n').length > 6;
  return <section className="ficha-seccion"><h3>Alcance</h3>
    <p className={`detail-notes ficha-alcance ${largo && !abierto ? 'recortado' : ''}`}>{texto}</p>
    {largo && <Boton type="button" variante="texto" onClick={() => setAbierto(!abierto)} aria-expanded={abierto}>{abierto ? 'Ver menos' : 'Ver alcance completo'}</Boton>}
  </section>;
}

/** inicial: la fila de la lista; la ficha se muestra con ella al instante y se actualiza con la respuesta de la API */
function CertificateDetail({ id, inicial, brands, schemes, canManage, canGeneratePdf, onClose, onChanged }: {
  id: string; inicial?: Certificate; brands: Brand[]; schemes: Scheme[]; canManage: boolean; canGeneratePdf: boolean; onClose: () => void; onChanged: () => void;
}) {
  const [cert, setCert] = useState<Certificate | null>(inicial ?? null), [link, setLink] = useState<ConsultLink | null>(null);
  const [versions, setVersions] = useState<PdfVersion[]>([]), [error, setError] = useState(''), [notice, setNotice] = useState('');
  const [file, setFile] = useState<File | null>(null), [pdfReason, setPdfReason] = useState('');
  const [brand, setBrand] = useState(inicial?.marcaManual ? inicial.paisMarca : ''), [brandReason, setBrandReason] = useState(''), [busy, setBusy] = useState(false);
  const [intentoMarca, setIntentoMarca] = useState(false), elegirVersion = useRef<HTMLInputElement>(null);
  const [seed, setSeed] = useState(0), [copied, setCopied] = useState(false);
  /** Versión del PDF en la vista previa (por defecto la vigente) */
  const [versionVista, setVersionVista] = useState<number | null>(null);
  useEffect(() => {
    let active = true;
    Promise.all([api.certificate(id), api.pdfVersions(id)]).then(([c, v]) => { if (active) { setCert(c); setVersions(v); setBrand(c.marcaManual ? c.paisMarca : ''); } })
      .catch(e => { if (active) setError(errorText(e)); });
    api.consultLink(id).then(l => { if (active) setLink(l); }).catch(() => {});
    return () => { active = false; };
  }, [id, seed]);
  const published = !!cert && cert.estado !== 'BORRADOR' && cert.estado !== 'REVISADO';
  const brandName = (pais: string) => brands.find(b => b.codigoPais === pais)?.nombre || pais;
  async function uploadPdf(event: FormEvent) {
    event.preventDefault();
    if (!cert || !file) return;
    setBusy(true); setError(''); setNotice('');
    try {
      const doc = await api.uploadDocument('CERTIFICADO_PDF', file);
      await api.setCertificatePdf(cert.id, { documentoId: doc.id, motivo: pdfReason.trim() || undefined, version: cert.version });
      setFile(null); setPdfReason(''); setVersionVista(null); setNotice('PDF actualizado. La consulta pública lo mostrará en menos de un minuto.'); setSeed(n => n + 1); onChanged();
    } catch (e) { setError(errorText(e)); } finally { setBusy(false); }
  }
  async function generatePdf() {
    if (!cert) return;
    setBusy(true); setError(''); setNotice('');
    try {
      await api.generateCertificatePdf(cert.id);
      setFile(null); setVersionVista(null); setNotice('PDF generado con la plantilla. La consulta pública lo mostrará en menos de un minuto.'); setSeed(n => n + 1); onChanged();
    } catch (e) { setError(errorText(e)); } finally { setBusy(false); }
  }
  async function changeBrand(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setIntentoMarca(true);
    if (!cert) return;
    if (vacio(brandReason)) { enfocarPrimerError(event.currentTarget); return; }
    setBusy(true); setError(''); setNotice('');
    try {
      await api.setCertificateBrand(cert.id, { pais: brand || null, motivo: brandReason.trim(), version: cert.version });
      setBrandReason(''); setIntentoMarca(false); setNotice('Marca cambiada. El QR y la consulta pública usan ahora la nueva marca.'); setSeed(n => n + 1); onChanged();
    } catch (e) { setError(errorText(e)); } finally { setBusy(false); }
  }
  async function copy() {
    if (!link) return;
    try { await navigator.clipboard.writeText(link.url); setCopied(true); window.setTimeout(() => setCopied(false), 1800); } catch { setNotice(link.url); }
  }
  const schemeName = cert ? schemes.find(x => x.codigo === cert.esquema)?.nombre || cert.esquema : '';
  const vigente = versions.find(v => v.vigente), vistaVersion = versions.find(v => v.numero === versionVista) ?? vigente;
  // La nueva versión elegida se ve antes de subirla; si no, la versión elegida de la lista o la vigente
  const fuente: FuentePdf | null = file ? { tipo: 'archivo', archivo: file }
    : vistaVersion ? { tipo: 'documento', id: vistaVersion.documentoId, nombre: vistaVersion.nombre, tamano: vistaVersion.tamanoBytes }
    : cert?.documentoPdfId ? { tipo: 'documento', id: cert.documentoPdfId, nombre: `${cert.codigo}.pdf` } : cert?.urlDocumentoLegado ? { tipo: 'enlace', url: cert.urlDocumentoLegado, nombre: `${cert.codigo}.pdf` } : null;
  const tituloVista = file ? 'Nueva versión, aún sin subir' : vistaVersion ? `Versión ${vistaVersion.numero}${vistaVersion.vigente ? ' · vigente' : ' · anterior'}` : 'PDF del certificado';
  const vista = cert ? <VistaPdf fuente={fuente} titulo={tituloVista}
    vacio="Sin PDF. La consulta pública muestra los datos sin documento." /> : undefined;
  return <Drawer onClose={onClose} vista={vista} title={<span className="mono">{cert?.codigo || 'Cargando…'}</span>}
    subtitle={cert ? <>{cert.designacion} · {schemeName}{cert.emitidoPorIndia ? ' · emitido por India' : ''}</> : undefined}>
    {error && <p className="form-error" role="alert">{error}</p>}
    {notice && <p className="form-success" role="status"><Check size={14} aria-hidden /> {notice}</p>}
    {!cert ? <div className="skeleton-rows" aria-hidden="true"><span /><span /><span /></div> : <div className="detail-body ficha">
      {/* Ficha: estado, titular y datos clave, en el orden en que se consultan */}
      <section className="ficha-titular" aria-label="Titular">
        <div className="ficha-estado"><StatusTag certificate={cert} /><span>{brandName(cert.paisMarca)}{cert.marcaManual ? ' (elegida a mano)' : ''}</span></div>
        <h3 className="titular">{cert.cliente.razonSocial}</h3>
        <p>{[cert.cliente.tipoDocumento && cert.cliente.numeroDocumento ? `${cert.cliente.tipoDocumento} ${cert.cliente.numeroDocumento}` : null, `País ${cert.cliente.codigoPais}`].filter(Boolean).join(' · ')}</p>
      </section>
      <dl className="ficha-datos">
        <div><dt>Norma</dt><dd>{cert.designacion}</dd></div>
        <div><dt>Acreditación</dt><dd>{schemeName}{cert.emitidoPorIndia ? ' · India' : ''}</dd></div>
        <div><dt>Otorgamiento</dt><dd className="tabular">{day(cert.fechaOtorgamiento)}</dd></div>
        <div><dt>Estado del registro</dt><dd>{stateNames[cert.estado]}</dd></div>
        {cert.esquema === 'IQC_GLOBAL' && <>
          <div><dt>Subdivisión</dt><dd>{cert.subdivision || '—'}</dd></div>
          <div><dt>Clase</dt><dd>{cert.clase || '—'}</dd></div>
          <div><dt>Procesos no aplicables</dt><dd>{cert.procesosNoAplicables || '—'}</dd></div>
          <div><dt>Anexos</dt><dd>{cert.anexos || '—'}</dd></div>
        </>}
      </dl>
      <section className="ficha-seccion"><h3>Ciclo de certificación</h3><CycleBar start={cert.fechaEmision} end={cert.fechaExpiracion} inactive={cert.estado !== 'EMITIDO'} detailed /></section>
      {published && <section className="ficha-seccion"><h3>Consulta pública</h3>
        <div className="ficha-qr">
          <img src={`${api.qrUrl(cert.id, true, 480)}&v=${seed}`} alt={`QR de ${cert.codigo}`} width={112} height={112} />
          <div>
            {link ? <a className="ficha-enlace mono" href={link.url} target="_blank" rel="noreferrer">{link.url.replace(/^https?:\/\//, '')} <ExternalLink size={12} aria-hidden /></a> : <span className="muted">Generando enlace…</span>}
            <div className="qr-actions">
              <Boton type="button" variante="secundario" pequeno onClick={copy} disabled={!link}>{copied ? <><Check size={14} aria-hidden /> Copiado</> : <><Copy size={14} aria-hidden /> Copiar enlace</>}</Boton>
              <a className="secondary small" href={api.qrUrl(cert.id, true, 1200)} download={`QR-${cert.codigo}.png`}><Download size={14} aria-hidden /> Descargar QR</a>
            </div>
          </div>
        </div>
      </section>}
      {cert.alcance && <Alcance texto={cert.alcance} />}

      <section className="ficha-seccion"><h3>Documento PDF</h3>
        {!versions.length && <p className="muted">{cert.urlDocumentoLegado ? 'PDF importado de la plataforma anterior (a la derecha). Si subes una versión nueva, quedará guardado en IQC Flow.' : 'Sin PDF. La consulta pública muestra los datos sin documento.'}</p>}
        {versions.length > 0 && <ol className="version-list">{versions.map(v => { const viendo = !file && vistaVersion?.numero === v.numero;
          return <li key={v.numero} className={viendo ? 'viendo' : undefined}>
            <button type="button" className="version-ver" aria-pressed={viendo} onClick={() => { setFile(null); setVersionVista(v.numero); }}>
              <FileText size={14} aria-hidden />Versión {v.numero}</button>
            {v.vigente && <span className="status-label"><span className="status-dot activo" />Vigente</span>}
            <span>{stamp(v.subidoEn)} · {v.subidoPor?.nombre || '—'} · {kb(v.tamanoBytes)}</span>{v.motivo && <em>{v.motivo}</em>}
          </li>; })}</ol>}
        {canManage && cert.estado !== 'CANCELADO' && cert.estado !== 'REEMPLAZADO' && <>
          <input ref={elegirVersion} type="file" accept="application/pdf" className="sr-only" onChange={e => { setFile(e.target.files?.[0] || null); }} />
          {!file ? <div className="acciones-pdf">
            {canGeneratePdf && cert.estado === 'EMITIDO' && <Boton variante="secundario" pequeno icono={FileText} cargando={busy} onClick={generatePdf}>{versions.length ? 'Regenerar PDF con la plantilla' : 'Generar PDF con la plantilla'}</Boton>}
            <Boton variante="secundario" pequeno icono={Upload} onClick={() => elegirVersion.current?.click()}>Subir una versión nueva</Boton>
          </div> : <form className="form-ficha" onSubmit={uploadPdf}>
            <p className="field-help">{file.name}: revísala a la derecha antes de subirla.</p>
            <label>Motivo (opcional)<input maxLength={500} value={pdfReason} onChange={e => setPdfReason(e.target.value)} placeholder="Ej.: corrección del alcance" /></label>
            <div className="acciones-pdf">
              <Boton variante="secundario" pequeno onClick={() => { setFile(null); setPdfReason(''); if (elegirVersion.current) elegirVersion.current.value = ''; }}>Cancelar</Boton>
              <Boton type="submit" variante="primario" pequeno icono={Upload} cargando={busy}>Subir esta versión</Boton>
            </div>
          </form>}
        </>}
      </section>

      {canManage && published && <section className="ficha-seccion"><h3>Marca con que se muestra</h3>
        <form className="form-ficha" onSubmit={changeBrand} noValidate>
          <label>Marca<select value={brand} onChange={e => setBrand(e.target.value)}>
            <option value="">La del país del titular ({brandName(cert.cliente.codigoPais)})</option>
            {brands.map(b => <option key={b.codigoPais} value={b.codigoPais}>{b.nombre}</option>)}</select></label>
          <label>Motivo<Obligatorio /><input required aria-invalid={intentoMarca && vacio(brandReason)} maxLength={500} value={brandReason} onChange={e => setBrandReason(e.target.value)} placeholder="Queda en la bitácora" />
            <ErrorCampo mensaje={intentoMarca && vacio(brandReason) ? 'Escribe el motivo: queda en la bitácora.' : undefined} /></label>
          <Boton type="submit" variante="secundario" disabled={busy || (brand || null) === (cert.marcaManual ? cert.paisMarca : null)}>Cambiar marca</Boton>
        </form>
        <p className="field-help">Suspender, levantar o retirar se hace desde el expediente, con su decisión y control de imparcialidad.</p>
      </section>}
    </div>}
  </Drawer>;
}

export function Certificates({ canManage, initialCountry = '', initialValidity = '' }: { canManage: boolean; initialCountry?: string; initialValidity?: string }) {
  const [text, setText] = useState(''), [debounced, setDebounced] = useState('');
  const [scheme, setScheme] = useState(''), [standard, setStandard] = useState(''), [country, setCountry] = useState(initialCountry);
  const [state, setState] = useState(initialValidity ? 'EMITIDO' : ''), [validity, setValidity] = useState(initialValidity), [page, setPage] = useState(0);
  const [data, setData] = useState<Page<Certificate> | null>(null), [error, setError] = useState(''), [refresh, setRefresh] = useState(0);
  const [schemes, setSchemes] = useState<Scheme[]>([]), [standards, setStandards] = useState<Standard[]>([]), [brands, setBrands] = useState<Brand[]>([]);
  const [counts, setCounts] = useState<Record<string, number> | null>(null), [tools, setTools] = useState<Tools | null>(null);
  const [open, setOpen] = useState<string | null>(null), [registering, setRegistering] = useState(false);
  useEffect(() => {
    api.schemes().then(setSchemes).catch(() => {}); api.standards().then(setStandards).catch(() => {}); api.brands().then(setBrands).catch(() => {}); api.tools().then(setTools).catch(() => {});
  }, []);
  useEffect(() => { const timer = window.setTimeout(() => setDebounced(text.trim()), 300); return () => window.clearTimeout(timer); }, [text]);
  const query = useMemo(() => {
    const q = new URLSearchParams();
    if (debounced) q.set('texto', debounced);
    if (scheme) q.set('esquema', scheme);
    if (standard) q.set('norma', standard);
    if (country) q.set('pais', country);
    if (state) q.set('estado', state);
    if (validity) q.set('vigencia', validity);
    return q;
  }, [debounced, scheme, standard, country, state, validity]);
  useEffect(() => {
    let active = true;
    const q = new URLSearchParams(query); q.set('pagina', String(page)); q.set('tamano', '25');
    api.certificates(q).then(result => { if (active) { setData(result); setError(''); } }).catch(e => { if (active) setError(errorText(e)); });
    return () => { active = false; };
  }, [query, page, refresh]);
  useEffect(() => {
    let active = true;
    const count = (vigencia: Validity) => api.certificates(new URLSearchParams({ estado: 'EMITIDO', vigencia, tamano: '1' })).then(r => r.totalElementos);
    Promise.all([api.certificates(new URLSearchParams({ tamano: '1' })).then(r => r.totalElementos), count('VIGENTE'), count('POR_VENCER'), count('VENCIDO')])
      .then(([todos, vigentes, porVencer, vencidos]) => { if (active) setCounts({ todos, vigentes, porVencer, vencidos }); }).catch(() => {});
    return () => { active = false; };
  }, [refresh]);
  const [copiedId, setCopiedId] = useState<string | null>(null);
  async function copyLink(id: string) {
    try { const link = await api.consultLink(id); await navigator.clipboard.writeText(link.url); setCopiedId(id); window.setTimeout(() => setCopiedId(null), 1600); }
    catch (e) { setError(errorText(e)); }
  }
  const reset = (fn: () => void) => { fn(); setPage(0); };
  const quick = (v: Validity) => reset(() => { if (validity === v) { setState(''); setValidity(''); } else { setState('EMITIDO'); setValidity(v); } });
  const filtered = !!(debounced || scheme || standard || country || state || validity);
  const clear = () => reset(() => { setText(''); setScheme(''); setStandard(''); setCountry(''); setState(''); setValidity(''); });
  return <div className="screen-body">
    <div className="page-heading"><div><h1>Certificados empresariales</h1><p>Sistemas de gestión de IQC Perú e IQC LATAM, con su ciclo, su QR y su PDF.</p></div>
      <div className="heading-actions"><a className="secondary" href={api.certificateExportUrl(query)}><FileSpreadsheet size={16} aria-hidden /> Exportar</a>
        {canManage && <Boton variante="primario" onClick={() => setRegistering(true)}><Plus size={16} aria-hidden /> Registrar certificado</Boton>}</div></div>
    <FilaCifras etiqueta="Filtrar por vigencia">
      <TarjetaCifra icono={Icono.certificadoEmpresarial} etiqueta="Todos" valor={counts?.todos} detalle="Registrados en IQC Perú e IQC LATAM" activa={!validity}
        alPulsar={() => reset(() => { setState(''); setValidity(''); })} />
      <TarjetaCifra icono={BadgeCheck} etiqueta="Vigentes" valor={counts?.vigentes} detalle="Emitidos y dentro de su ciclo" tono="ok"
        activa={validity === 'VIGENTE'} alPulsar={() => quick('VIGENTE')} />
      <TarjetaCifra icono={CalendarClock} etiqueta="Por vencer" valor={counts?.porVencer} detalle="Vencen en los próximos 90 días" tono="alerta"
        activa={validity === 'POR_VENCER'} alPulsar={() => quick('POR_VENCER')} />
      <TarjetaCifra icono={CalendarX2} etiqueta="Vencidos" valor={counts?.vencidos} detalle="Vencieron y no se renovaron" tono="mal"
        activa={validity === 'VENCIDO'} alPulsar={() => quick('VENCIDO')} />
    </FilaCifras>
    <section className="list-panel">
      <div className="list-toolbar">
        <SearchField value={text} onChange={v => reset(() => setText(v))} placeholder="Código, razón social o RUC" label="Buscar certificados" />
        <select aria-label="Esquema" value={scheme} onChange={e => reset(() => setScheme(e.target.value))}><option value="">Esquema</option>{schemes.map(s => <option key={s.codigo} value={s.codigo}>{s.nombre}</option>)}</select>
        <select aria-label="Norma" value={standard} onChange={e => reset(() => setStandard(e.target.value))}><option value="">Norma</option>{standards.map(s => <option key={s.codigo} value={s.codigo}>{s.nombreCorto}</option>)}</select>
        <select aria-label="Marca" value={country} onChange={e => reset(() => setCountry(e.target.value))}><option value="">Marca</option>{brands.map(b => <option key={b.codigoPais} value={b.codigoPais}>{b.nombre}</option>)}</select>
        <select aria-label="Estado" value={state} onChange={e => reset(() => setState(e.target.value))}><option value="">Estado</option>{(['EMITIDO', 'SUSPENDIDO', 'RETIRADO', 'CANCELADO', 'REEMPLAZADO', 'BORRADOR'] as CertificateState[]).map(s => <option key={s} value={s}>{stateNames[s]}</option>)}</select>
        {filtered && <Boton variante="texto" onClick={clear}><FilterX size={14} aria-hidden /> Limpiar</Boton>}
      </div>
      {error && <div className="notice" role="alert">{error} <Boton variante="texto" onClick={() => setRefresh(n => n + 1)}>Reintentar</Boton></div>}
      <div className="table-wrap"><table className="cert-table"><thead><tr><th>Certificado</th><th>Titular</th><th>Esquema</th><th>Marca</th><th>Ciclo</th><th>Situación</th><th><span className="sr-only">Abrir</span></th></tr></thead>
        <tbody>{!data && <tr className="skeleton-row"><td colSpan={7}><span /></td></tr>}
          {data?.contenido.map(c => <tr key={c.id} onClick={() => setOpen(c.id)} className={`clickable ${c.estado === 'EMITIDO' && c.vigencia !== 'VIGENTE' ? `row-${c.vigencia.toLowerCase()}` : ''}`}>
            <td><span className="mono code">{partirEnBarras(c.codigo)}</span><small>{c.designacion}</small></td>
            <td><strong>{c.cliente.razonSocial}</strong><small className="tabular">{c.cliente.numeroDocumento || 'sin documento'}</small></td>
            <td>{schemeShort(c.esquema)}{c.emitidoPorIndia && <small>India</small>}</td><td>{c.paisMarca}</td>
            <td><CycleBar start={c.fechaEmision} end={c.fechaExpiracion} inactive={c.estado !== 'EMITIDO'} /></td>
            <td><StatusTag certificate={c} />{!c.tienePdf && !c.urlDocumentoLegado && <small className="muted">sin PDF</small>}</td>
            <td><div className="row-actions" onClick={event => event.stopPropagation()}>
              {c.estado !== 'BORRADOR' && c.estado !== 'REVISADO' && <>
                <a className="row-link reveal" href={api.qrUrl(c.id, true, 1200)} download={`QR-${c.codigo}.png`} title="Descargar QR" aria-label={`Descargar el QR de ${c.codigo}`}><QrCode size={16} aria-hidden /></a>
                <button className="row-link reveal" title="Copiar enlace de consulta" aria-label={`Copiar el enlace de consulta de ${c.codigo}`} onClick={() => copyLink(c.id)}>{copiedId === c.id ? <Check size={16} aria-hidden /> : <Copy size={16} aria-hidden />}</button>
              </>}
              <button className="row-link" onClick={() => setOpen(c.id)} aria-label={`Abrir ${c.codigo}`}><ChevronRight size={16} aria-hidden /></button>
            </div></td></tr>)}</tbody></table></div>
      <div className="mobile-list">{data?.contenido.map(c => <button className="mobile-row" key={c.id} onClick={() => setOpen(c.id)}>
        <span className="mobile-row-main"><strong className="mono">{c.codigo}</strong><StatusTag certificate={c} /></span>
        <span className="mobile-row-meta">{c.cliente.razonSocial} · {c.designacion}</span>
        <CycleBar start={c.fechaEmision} end={c.fechaExpiracion} inactive={c.estado !== 'EMITIDO'} /></button>)}</div>
      {data && data.contenido.length === 0 && <div className="empty-state"><QrCode size={20} aria-hidden /><p>{filtered ? 'Ningún certificado coincide con estos filtros.' : 'Aún no hay certificados registrados.'}</p>{filtered && <Boton variante="secundario" pequeno onClick={clear}><FilterX size={14} aria-hidden /> Quitar filtros</Boton>}</div>}
      <Pagination page={page} totalPages={data?.totalPaginas || 1} total={data?.totalElementos ?? null} noun="certificados" onPage={setPage} />
    </section>
    {open && <CertificateDetail id={open} inicial={data?.contenido.find(c => c.id === open)} brands={brands} schemes={schemes} canManage={canManage} canGeneratePdf={!!tools?.generacionPdf} onClose={() => setOpen(null)} onChanged={() => setRefresh(n => n + 1)} />}
    {registering && <RegistrarCertificados schemes={schemes} standards={standards} tools={tools} onClose={() => setRegistering(false)}
      onDone={first => { setRegistering(false); setRefresh(n => n + 1); if (first) setOpen(first.id); }} />}
  </div>;
}

/** Código con puntos de corte solo después de cada «/»: «PER/QEO/JAS-C0140/RC1/0567» nunca se parte a mitad de número */
function partirEnBarras(codigo: string) {
  const partes = codigo.split('/');
  return partes.map((p, i) => <Fragment key={i}>{p}{i < partes.length - 1 && <>/<wbr /></>}</Fragment>);
}
