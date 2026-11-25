import { useEffect, useMemo, useState, type FormEvent } from 'react';
import {
  BadgeCheck, CalendarClock, CalendarX2, Check, ChevronRight, Copy, Download, ExternalLink, FileSpreadsheet, FileText, FilterX, Files, Plus, QrCode, Trash2, Upload,
} from 'lucide-react';
import { FilaCifras, TarjetaCifra } from '../../components/cifras/TarjetaCifra';
import {
  api, type Brand, type Certificate, type CertificateState, type ClientSummary, type ConsultLink, type Page,
  type PdfReading, type PdfVersion, type RegistrationMode, type Scheme, type Standard, type Tools, type Validity,
} from '../../lib/api';
import { ModeTabs, PdfReader } from '../../components/RegistrationModes';
import { VistaPdf, type FuentePdf } from '../../components/pdf/VistaPdf';
import { BuscadorCliente } from '../../components/BuscadorCliente';
import { ElegirPdf } from '../../components/pdf/ElegirPdf';
import { day, errorText, isoDay, kb, number, plusYears, stamp } from '../../lib/format';
import { Drawer } from '../../components/Drawer';
import { Pagination } from '../../components/Pagination';
import { SearchField } from '../../components/SearchField';
import { CycleBar } from './CycleBar';

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

type Line = { key: number; norma: string; edicion: string; codigo: string; file: File | null; documentoId?: string };

function RegisterCertificates({ schemes, standards, tools, onClose, onDone }: {
  schemes: Scheme[]; standards: Standard[]; tools: Tools | null; onClose: () => void; onDone: (first: Certificate | null) => void;
}) {
  const modes = tools?.modosEmpresarial ?? ['MANUAL'];
  const [mode, setMode] = useState<RegistrationMode>(modes[0] ?? 'MANUAL'), [titularLeido, setTitularLeido] = useState(''), [searchKey, setSearchKey] = useState(0);
  const today = isoDay(new Date());
  const [client, setClient] = useState<ClientSummary | null>(null);
  const [scheme, setScheme] = useState(schemes[0]?.codigo || ''), [service, setService] = useState('CERTIFICACION_INICIAL');
  const [scope, setScope] = useState(''), [granted, setGranted] = useState(today), [issued, setIssued] = useState(today);
  const [expires, setExpires] = useState(plusYears(today, 3, -1));
  const [watch1, setWatch1] = useState(plusYears(today, 1, -1)), [watch2, setWatch2] = useState(plusYears(today, 2, -1));
  const [india, setIndia] = useState(false), [reason, setReason] = useState('');
  const [lines, setLines] = useState<Line[]>([{ key: 1, norma: '', edicion: '', codigo: '', file: null }]);
  const [busy, setBusy] = useState(''), [error, setError] = useState('');
  // Vista previa: qué norma se está viendo, el PDF soltado para leer y lo que hace el lector
  const [lineaVista, setLineaVista] = useState(1), [archivoLectura, setArchivoLectura] = useState<File | null>(null), [leyendo, setLeyendo] = useState('');
  useEffect(() => { if (!scheme && schemes[0]) setScheme(schemes[0].codigo); }, [scheme, schemes]);
  useEffect(() => { setIndia(scheme === 'IQC_GLOBAL'); }, [scheme]);
  function grant(value: string) {
    setGranted(value);
    setExpires(plusYears(value, 3, -1)); setWatch1(plusYears(value, 1, -1)); setWatch2(plusYears(value, 2, -1));
  }
  async function applyReading(r: PdfReading, documentoId: string, archivo: File) {
    const e = r.empresarial;
    setLineaVista(lines[0].key);
    if (!e) { setLines(all => [{ ...all[0], documentoId, file: archivo }, ...all.slice(1)]); return; }
    if (e.esquema && schemes.some(x => x.codigo === e.esquema)) setScheme(e.esquema);
    if (e.ciclo && e.ciclo > 1) setService('RECERTIFICACION');
    if (e.alcance) setScope(e.alcance);
    if (e.fechaOtorgamiento) setGranted(e.fechaOtorgamiento);
    if (e.fechaEmision) setIssued(e.fechaEmision);
    if (e.fechaExpiracion) setExpires(e.fechaExpiracion);
    if (e.limiteVigilancia1) setWatch1(e.limiteVigilancia1);
    if (e.limiteVigilancia2) setWatch2(e.limiteVigilancia2);
    setLines(all => [{ ...all[0], norma: r.normaCodigo || all[0].norma, edicion: r.edicionValida && e.edicion ? e.edicion : all[0].edicion,
      codigo: e.codigo || all[0].codigo, file: archivo, documentoId }, ...all.slice(1)]);
    if (e.titular) {
      const consulta = e.titular.split(/\s+/).slice(0, 3).join(' ');
      setClient(null); setTitularLeido(consulta); setSearchKey(k => k + 1);
      try {
        const found = await api.clients(new URLSearchParams({ texto: consulta, tamano: '10' }));
        const plano = (t: string) => t.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^A-Za-z0-9]/g, '').toUpperCase();
        const exacto = found.contenido.find(c => plano(c.razonSocial) === plano(e.titular!));
        if (exacto || found.contenido.length === 1) setClient(exacto ?? found.contenido[0]);
      } catch { /* se elige a mano */ }
    }
  }
  const update = (key: number, change: Partial<Line>) => setLines(all => all.map(line => line.key === key ? { ...line, ...change } : line));
  const editions = (norma: string) => standards.find(s => s.codigo === norma)?.ediciones || [];
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!client) return setError('Elige el titular del certificado de la lista de coincidencias.');
    setError('');
    try {
      const certificados = [];
      for (const [index, line] of lines.entries()) {
        let documentoPdfId: string | undefined = line.documentoId;
        if (!documentoPdfId && line.file && mode !== 'PLANTILLA') { setBusy(`Subiendo PDF ${index + 1} de ${lines.length}…`); documentoPdfId = (await api.uploadDocument('CERTIFICADO_PDF', line.file)).id; }
        certificados.push({ norma: line.norma, edicion: line.edicion, codigo: line.codigo.trim().toUpperCase(), ...(documentoPdfId ? { documentoPdfId } : {}) });
      }
      setBusy('Registrando…');
      const result = await api.registerCertificates({
        clienteId: client.id, esquema: scheme, tipoServicio: service, alcance: scope.trim() || undefined,
        fechaOtorgamiento: granted, fechaEmision: issued, fechaExpiracion: expires,
        limiteVigilancia1: watch1 || undefined, limiteVigilancia2: watch2 || undefined,
        emitidoPorIndia: india, motivo: reason.trim(), certificados,
      });
      onDone(result.certificados[0] || null);
    } catch (e) { setError(errorText(e)); } finally { setBusy(''); }
  }
  const fuenteDe = (l: Line): FuentePdf | null => l.file ? { tipo: 'archivo', archivo: l.file } : l.documentoId ? { tipo: 'documento', id: l.documentoId } : null;
  const nombreNorma = (l: Line, i: number) => standards.find(x => x.codigo === l.norma)?.nombreCorto || `Norma ${i + 1}`;
  const activa = lines.find(l => l.key === lineaVista) ?? lines[0];
  // En automatizado, el PDF de la primera norma se lee; el de las demás solo se adjunta
  const elegirEnVista = (f: File) => mode === 'AUTOMATIZADO' && activa.key === lines[0].key ? setArchivoLectura(f) : update(activa.key, { file: f, documentoId: undefined });
  const vista = mode === 'PLANTILLA'
    ? <VistaPdf fuente={null} vacio="En este modo el PDF se genera con la plantilla Word al registrar." />
    : <VistaPdf fuente={fuenteDe(activa)} titulo={`PDF de ${nombreNorma(activa, lines.indexOf(activa))}`} alElegir={elegirEnVista} ocupado={leyendo}
        pestanas={lines.map((l, i) => ({ clave: String(l.key), etiqueta: nombreNorma(l, i), fuente: fuenteDe(l) }))}
        activa={String(activa.key)} alCambiarPestana={k => setLineaVista(Number(k))} />;
  return <Drawer onClose={onClose} title="Registrar certificado" subtitle="Para certificados ya emitidos: de India, atrasados o correcciones." vista={vista}>
    <form className="client-form" onSubmit={submit}>
      <ModeTabs modes={modes} value={mode} onChange={m => { setMode(m); setLines(all => all.map(l => ({ ...l, documentoId: undefined }))); }} />
      {mode === 'AUTOMATIZADO' && <PdfReader tipo="EMPRESARIAL" onRead={applyReading} archivoExterno={archivoLectura} alOcupado={setLeyendo} />}
      <fieldset><legend>Titular y servicio</legend><div className="form-grid">
        <BuscadorCliente key={searchKey} etiqueta="Titular del certificado" value={client} onChange={setClient} initialQuery={titularLeido} />
        <label>Esquema<select value={scheme} onChange={e => setScheme(e.target.value)} required>{schemes.map(s => <option key={s.codigo} value={s.codigo}>{s.nombre}</option>)}</select></label>
        <label>Tipo de servicio<select value={service} onChange={e => setService(e.target.value)}>{Object.entries(serviceNames).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></label>
        <label className="full">Alcance<textarea rows={3} maxLength={4000} value={scope} onChange={e => setScope(e.target.value)} placeholder="Tal como figura en el certificado" /></label>
        <label className="check-row full"><input type="checkbox" checked={india} onChange={e => setIndia(e.target.checked)} /> Emitido por India (IQC Global)</label>
      </div></fieldset>
      <fieldset><legend>Ciclo</legend><div className="form-grid three">
        <label>Otorgamiento<input type="date" value={granted} onChange={e => grant(e.target.value)} required /></label>
        <label>Emisión<input type="date" value={issued} onChange={e => setIssued(e.target.value)} required /></label>
        <label>Expiración<input type="date" value={expires} onChange={e => setExpires(e.target.value)} required /></label>
      </div>
      <div className="form-grid">
        <label>Límite 1.ª vigilancia<input type="date" value={watch1} onChange={e => setWatch1(e.target.value)} /></label>
        <label>Límite 2.ª vigilancia<input type="date" value={watch2} onChange={e => setWatch2(e.target.value)} /></label>
      </div>
        <CycleBar start={issued} end={expires} detailed />
        <p className="field-help">Las vigilancias futuras crean sus tareas de seguimiento.</p>
      </fieldset>
      <fieldset><legend>Normas certificadas</legend>
        <div className="cert-lines">{lines.map((line, index) => <div className="cert-line" key={line.key}>
          <label>Norma<select required value={line.norma} onChange={e => { const ed = editions(e.target.value); update(line.key, { norma: e.target.value, edicion: (ed.find(x => x.vigente) || ed[0])?.edicion || '' }); }}>
            <option value="">Elige…</option>{standards.map(s => <option key={s.codigo} value={s.codigo}>{s.nombreCorto}</option>)}</select></label>
          <label>Edición<select required value={line.edicion} onChange={e => update(line.key, { edicion: e.target.value })}>
            <option value="">—</option>{editions(line.norma).map(ed => <option key={ed.edicion} value={ed.edicion}>{ed.designacion}{ed.vigente ? '' : ' (anterior)'}</option>)}</select></label>
          <label>Código<input required maxLength={60} value={line.codigo} onChange={e => update(line.key, { codigo: e.target.value })} placeholder="PE-INA-01520240132" spellCheck={false} className="mono" /></label>
          {mode === 'PLANTILLA' ? <p className="field-help">El PDF se genera con la plantilla.</p>
            : line.file || line.documentoId ? <button type="button" className={`pdf-adjunto ${activa.key === line.key ? 'viendo' : ''}`} onClick={() => setLineaVista(line.key)}
                title="Ver en la vista previa"><FileText size={14} aria-hidden /><span>{line.file?.name || 'PDF leído'}</span></button>
            : <ElegirPdf alElegir={f => { update(line.key, { file: f }); setLineaVista(line.key); }} />}
          {lines.length > 1 && <button type="button" className="icon-button danger" onClick={() => setLines(all => all.filter(l => l.key !== line.key))} aria-label={`Quitar norma ${index + 1}`}><Trash2 size={16} aria-hidden /></button>}
        </div>)}</div>
        <button type="button" className="secondary" onClick={() => { const key = Date.now(); setLines(all => [...all, { key, norma: '', edicion: '', codigo: '', file: null }]); setLineaVista(key); }}><Plus size={16} aria-hidden /> Agregar otra norma</button>
      </fieldset>
      <label>Motivo del registro<input required maxLength={500} value={reason} onChange={e => setReason(e.target.value)} placeholder="Ej.: certificado recibido de India el 18/11" /><span className="field-help">Queda en la bitácora.</span></label>
      {error && <p className="form-error" role="alert">{error}</p>}
      <div className="form-actions"><button type="button" className="secondary" onClick={onClose}>Cancelar</button><button className="primary" disabled={!!busy}>{busy || 'Registrar certificado'}</button></div>
    </form>
  </Drawer>;
}

/** Alcance del certificado: largo en muchos casos; se muestran unas líneas y se despliega el resto */
function Alcance({ texto }: { texto: string }) {
  const [abierto, setAbierto] = useState(false), largo = texto.length > 360 || texto.split('\n').length > 6;
  return <section className="ficha-seccion"><h3>Alcance</h3>
    <p className={`detail-notes ficha-alcance ${largo && !abierto ? 'recortado' : ''}`}>{texto}</p>
    {largo && <button type="button" className="text-button" onClick={() => setAbierto(!abierto)} aria-expanded={abierto}>{abierto ? 'Ver menos' : 'Ver alcance completo'}</button>}
  </section>;
}

function CertificateDetail({ id, brands, schemes, canManage, onClose, onChanged }: {
  id: string; brands: Brand[]; schemes: Scheme[]; canManage: boolean; onClose: () => void; onChanged: () => void;
}) {
  const [cert, setCert] = useState<Certificate | null>(null), [link, setLink] = useState<ConsultLink | null>(null);
  const [versions, setVersions] = useState<PdfVersion[]>([]), [error, setError] = useState(''), [notice, setNotice] = useState('');
  const [file, setFile] = useState<File | null>(null), [pdfReason, setPdfReason] = useState('');
  const [brand, setBrand] = useState(''), [brandReason, setBrandReason] = useState(''), [busy, setBusy] = useState(false);
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
  async function changeBrand(event: FormEvent) {
    event.preventDefault();
    if (!cert) return;
    setBusy(true); setError(''); setNotice('');
    try {
      await api.setCertificateBrand(cert.id, { pais: brand || null, motivo: brandReason.trim(), version: cert.version });
      setBrandReason(''); setNotice('Marca cambiada. El QR y la consulta pública usan ahora la nueva marca.'); setSeed(n => n + 1); onChanged();
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
      </dl>
      <section className="ficha-seccion"><h3>Ciclo de certificación</h3><CycleBar start={cert.fechaEmision} end={cert.fechaExpiracion} inactive={cert.estado !== 'EMITIDO'} detailed /></section>
      {published && <section className="ficha-seccion"><h3>Consulta pública</h3>
        <div className="ficha-qr">
          <img src={`${api.qrUrl(cert.id, true, 480)}&v=${seed}`} alt={`QR de ${cert.codigo}`} width={112} height={112} />
          <div>
            {link ? <a className="ficha-enlace mono" href={link.url} target="_blank" rel="noreferrer">{link.url.replace(/^https?:\/\//, '')} <ExternalLink size={12} aria-hidden /></a> : <span className="muted">Generando enlace…</span>}
            <div className="qr-actions">
              <button type="button" className="secondary small" onClick={copy} disabled={!link}>{copied ? <><Check size={14} aria-hidden /> Copiado</> : <><Copy size={14} aria-hidden /> Copiar enlace</>}</button>
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
        {canManage && cert.estado !== 'CANCELADO' && cert.estado !== 'REEMPLAZADO' && <form className="inline-form" onSubmit={uploadPdf}>
          <div><ElegirPdf etiqueta="Nueva versión" texto={file ? 'Elegir otro' : 'Elegir PDF'} requerido alElegir={setFile} />
            {file && <span className="field-help">{file.name}: revísala en la vista previa antes de subirla.</span>}</div>
          <label>Motivo<input maxLength={500} value={pdfReason} onChange={e => setPdfReason(e.target.value)} placeholder="Ej.: corrección del alcance" /></label>
          <button className="primary" disabled={busy || !file}><Upload size={16} aria-hidden /> {busy ? 'Subiendo…' : 'Subir PDF'}</button>
        </form>}
      </section>

      {canManage && published && <section className="ficha-seccion"><h3>Marca con que se muestra</h3>
        <form className="inline-form" onSubmit={changeBrand}>
          <label>Marca<select value={brand} onChange={e => setBrand(e.target.value)}>
            <option value="">La del país del titular ({brandName(cert.cliente.codigoPais)})</option>
            {brands.map(b => <option key={b.codigoPais} value={b.codigoPais}>{b.nombre}</option>)}</select></label>
          <label>Motivo<input required maxLength={500} value={brandReason} onChange={e => setBrandReason(e.target.value)} placeholder="Queda en la bitácora" /></label>
          <button className="secondary" disabled={busy || (brand || null) === (cert.marcaManual ? cert.paisMarca : null)}>Cambiar marca</button>
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
        {canManage && <button className="primary" onClick={() => setRegistering(true)}><Plus size={16} aria-hidden /> Registrar certificado</button>}</div></div>
    <FilaCifras etiqueta="Filtrar por vigencia">
      <TarjetaCifra icono={Files} etiqueta="Todos" valor={counts?.todos} detalle="Registrados en IQC Perú e IQC LATAM" activa={!validity}
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
        {filtered && <button className="text-button" onClick={clear}><FilterX size={14} aria-hidden /> Limpiar</button>}
      </div>
      {error && <div className="notice" role="alert">{error} <button className="text-button" onClick={() => setRefresh(n => n + 1)}>Reintentar</button></div>}
      <div className="table-wrap"><table className="cert-table"><thead><tr><th>Certificado</th><th>Titular</th><th>Esquema</th><th>Marca</th><th>Ciclo</th><th>Situación</th><th><span className="sr-only">Abrir</span></th></tr></thead>
        <tbody>{!data && <tr className="skeleton-row"><td colSpan={7}><span /></td></tr>}
          {data?.contenido.map(c => <tr key={c.id} onClick={() => setOpen(c.id)} className={`clickable ${c.estado === 'EMITIDO' && c.vigencia !== 'VIGENTE' ? `row-${c.vigencia.toLowerCase()}` : ''}`}>
            <td><span className="mono code">{c.codigo}</span><small>{c.designacion}</small></td>
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
      {data && data.contenido.length === 0 && <div className="empty-state"><QrCode size={20} aria-hidden /><p>{filtered ? 'Ningún certificado coincide con estos filtros.' : 'Aún no hay certificados registrados.'}</p>{filtered && <button className="secondary small" onClick={clear}><FilterX size={14} aria-hidden /> Quitar filtros</button>}</div>}
      <Pagination page={page} totalPages={data?.totalPaginas || 1} total={data?.totalElementos ?? null} noun="certificados" onPage={setPage} />
    </section>
    {open && <CertificateDetail id={open} brands={brands} schemes={schemes} canManage={canManage} onClose={() => setOpen(null)} onChanged={() => setRefresh(n => n + 1)} />}
    {registering && <RegisterCertificates schemes={schemes} standards={standards} tools={tools} onClose={() => setRegistering(false)}
      onDone={first => { setRegistering(false); setRefresh(n => n + 1); if (first) setOpen(first.id); }} />}
  </div>;
}
