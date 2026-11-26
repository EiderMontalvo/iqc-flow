import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { Ban, BadgeCheck, Check, ChevronRight, Copy, ExternalLink, FileText, FilterX, Globe2, Pencil, Plus, Upload } from 'lucide-react';
import { Icono } from '../../lib/iconos';
import { Seccion } from '../../components/ui/Seccion';
import { useVistaPreviaEnVivo } from '../../lib/useVistaPreviaEnVivo';
import { FilaCifras, TarjetaCifra } from '../../components/cifras/TarjetaCifra';
import { VistaPdf, type FuentePdf } from '../../components/pdf/VistaPdf';
import { ElegirPdf } from '../../components/pdf/ElegirPdf';
import { api, type Brand, type DocumentType, type Page, type PdfReading, type PersonalCertificate, type PersonalState, type PersonalSummary, type RegistrationMode, type Tools } from '../../lib/api';
import { ModeTabs, PdfReader } from '../../components/RegistrationModes';
import { day, errorText, isoDay } from '../../lib/format';
import { Drawer } from '../../components/Drawer';
import { Pagination } from '../../components/Pagination';
import { SearchField } from '../../components/SearchField';
import { Boton } from '../../components/ui/Boton';

const stateNames: Record<PersonalState, string> = { EMITIDO: 'Emitido', ANULADO: 'Anulado' };
const MODALIDADES = ['Presencial', 'Virtual', 'Semipresencial', 'In company'];

function PersonalTag({ estado }: { estado: PersonalState }) {
  return <span className={`cert-tag ${estado === 'EMITIDO' ? 'vigente' : 'cancelado'}`}>{stateNames[estado]}</span>;
}

type Datos = { curso: string; normaReferencia: string; normaAnio: string; subtitulo: string; descripcion: string; duracionHoras: string; modalidad: string; fechaInicio: string; fechaFin: string; fechaEmision: string; entrenador: string };
const datosVacios = (): Datos => ({ curso: '', normaReferencia: '', normaAnio: '', subtitulo: '', descripcion: '', duracionHoras: '', modalidad: 'Virtual', fechaInicio: '', fechaFin: '', fechaEmision: isoDay(new Date()), entrenador: '' });
const datosDe = (c: PersonalCertificate): Datos => ({ curso: c.curso, normaReferencia: c.normaReferencia || '', normaAnio: c.normaAnio || '', subtitulo: c.subtitulo || '', descripcion: c.descripcion || '', duracionHoras: c.duracionHoras == null ? '' : String(c.duracionHoras), modalidad: c.modalidad || '', fechaInicio: c.fechaInicio || '', fechaFin: c.fechaFin || '', fechaEmision: c.fechaEmision, entrenador: c.entrenador || '' });
const payload = (d: Datos) => ({
  curso: d.curso.trim(), normaReferencia: d.normaReferencia.trim() || null, normaAnio: d.normaAnio.trim() || null, subtitulo: d.subtitulo.trim() || null,
  descripcion: d.descripcion.trim() || null, duracionHoras: d.duracionHoras ? Number(d.duracionHoras) : null, modalidad: d.modalidad.trim() || null,
  fechaInicio: d.fechaInicio || null, fechaFin: d.fechaFin || null, fechaEmision: d.fechaEmision, entrenador: d.entrenador.trim() || null,
});

function CourseFields({ value, onChange }: { value: Datos; onChange: (d: Datos) => void }) {
  const set = (k: keyof Datos) => (e: { target: { value: string } }) => onChange({ ...value, [k]: e.target.value });
  // Cada fila se llena de lado a lado: curso, norma con su año, duración, fechas, entrenador y descripción
  return <div className="campos">
    <label>Curso<input required maxLength={255} value={value.curso} onChange={set('curso')} placeholder="Ej.: Auditor interno ISO 37001" /></label>
    <div className="form-grid largo-corto">
      <label>Norma de referencia<input maxLength={120} value={value.normaReferencia} onChange={set('normaReferencia')} placeholder="ISO 37001" /></label>
      <label>Año de la norma<input inputMode="numeric" pattern="\d{4}" maxLength={4} value={value.normaAnio} onChange={set('normaAnio')} placeholder="2016" /></label>
    </div>
    <label>Subtítulo<input maxLength={255} value={value.subtitulo} onChange={set('subtitulo')} /></label>
    <div className="form-grid corto-largo">
      <label>Horas<input type="number" min="0.5" step="0.5" value={value.duracionHoras} onChange={set('duracionHoras')} /></label>
      <label>Modalidad<input list="modalidades" maxLength={50} value={value.modalidad} onChange={set('modalidad')} /><datalist id="modalidades">{MODALIDADES.map(m => <option key={m} value={m} />)}</datalist></label>
    </div>
    <div className="form-grid fechas">
      <label>Inicio<input type="date" value={value.fechaInicio} onChange={set('fechaInicio')} /></label>
      <label>Fin<input type="date" value={value.fechaFin} min={value.fechaInicio || undefined} onChange={set('fechaFin')} /></label>
      <label>Emisión<input type="date" required value={value.fechaEmision} onChange={set('fechaEmision')} /></label>
    </div>
    <label>Entrenador<input maxLength={255} value={value.entrenador} onChange={set('entrenador')} /></label>
    <label>Descripción<textarea rows={3} maxLength={4000} value={value.descripcion} onChange={set('descripcion')} /></label>
  </div>;
}

function RegisterPersonal({ types, brands, tools, onClose, onDone }: { types: Record<string, DocumentType[]>; brands: Brand[]; tools: Tools | null; onClose: () => void; onDone: (c: PersonalCertificate) => void }) {
  const modes = tools?.modosPersonal ?? ['MANUAL'];
  const [mode, setMode] = useState<RegistrationMode>(modes[0] ?? 'MANUAL'), [readId, setReadId] = useState<string | null>(null), [formKey, setFormKey] = useState(0);
  const countries = Object.keys(types);
  const [pais, setPais] = useState(countries[0] || 'PE'), [tipo, setTipo] = useState(''), [numero, setNumero] = useState('');
  const [nombres, setNombres] = useState(''), [correo, setCorreo] = useState(''), [telefono, setTelefono] = useState('');
  const [known, setKnown] = useState(false), [codigo, setCodigo] = useState(''), [datos, setDatos] = useState<Datos>(datosVacios);
  const [file, setFile] = useState<File | null>(null), [busy, setBusy] = useState(''), [error, setError] = useState('');
  // Vista previa: el PDF leído (automatizado), el soltado para leer y lo que hace el lector
  const [archivoLeido, setArchivoLeido] = useState<File | null>(null), [archivoLectura, setArchivoLectura] = useState<File | null>(null), [leyendo, setLeyendo] = useState('');
  const tiposPais = types[pais] || [];
  // Con el modo plantilla, el certificado se dibuja mientras se escribe (la API acepta todo vacío: lo que falta sale con guion)
  const borrador = { codigoPais: pais, tipoDocumento: tipo, numeroDocumento: numero.trim(), nombres: nombres.trim(), codigo: codigo.trim(),
    ...payload(datos), fechaEmision: datos.fechaEmision || null };
  const enVivo = useVistaPreviaEnVivo('/certificados-personales/vista-previa-borrador', borrador, mode === 'PLANTILLA');
  useEffect(() => { setTipo(t => tiposPais.some(x => x.codigo === t) ? t : tiposPais[0]?.codigo || ''); }, [pais]); // eslint-disable-line react-hooks/exhaustive-deps
  function applyReading(r: PdfReading, documentoId: string, archivo: File) {
    setReadId(documentoId); setArchivoLeido(archivo);
    const p = r.personal;
    if (!p) return;
    const paisCodigo = /^IQC\/([A-Z]{2})\//.exec(p.codigo || '')?.[1];
    if (paisCodigo && types[paisCodigo]) setPais(paisCodigo);
    if (p.tipoDocumento) setTipo(p.tipoDocumento);
    if (p.numeroDocumento) setNumero(p.numeroDocumento);
    if (p.nombres) setNombres(p.nombres);
    if (p.codigo) setCodigo(p.codigo);
    setDatos(d => ({ ...d, curso: p.curso || d.curso, normaReferencia: p.normaReferencia || d.normaReferencia, normaAnio: p.normaAnio || d.normaAnio,
      subtitulo: p.subtitulo || d.subtitulo, descripcion: p.descripcion || d.descripcion, duracionHoras: p.duracionHoras != null ? String(p.duracionHoras) : d.duracionHoras,
      modalidad: p.modalidad || d.modalidad, fechaInicio: p.fechaInicio || d.fechaInicio, fechaFin: p.fechaFin || d.fechaFin, fechaEmision: p.fechaEmision || d.fechaEmision,
      entrenador: p.entrenador || d.entrenador }));
    setFormKey(k => k + 1);
  }
  const ayuda = tiposPais.find(t => t.codigo === tipo)?.ayuda;
  const [segunSunat, setSegunSunat] = useState(false);
  /** Primero en IQC Flow (persona ya registrada); si no está y es un DNI de Perú, se completa el nombre con SUNAT */
  async function lookup() {
    if (!numero.trim() || !tipo) return;
    setSegunSunat(false);
    try { const p = await api.personByDocument(pais, tipo, numero.trim()); setNombres(p.nombres); setCorreo(p.correo || ''); setTelefono(p.telefono || ''); setKnown(true); return; }
    catch { setKnown(false); }
    if (pais === 'PE' && tipo === 'DNI' && /^\d{8}$/.test(numero.trim()) && !nombres.trim()) {
      try { const d = await api.padronDni(numero.trim()); setNombres(d.nombreCompleto); setSegunSunat(true); } catch { /* se escribe a mano */ }
    }
  }
  async function submit(event: FormEvent) {
    event.preventDefault(); setError('');
    try {
      let documentoPdfId: string | undefined = mode === 'AUTOMATIZADO' && readId ? readId : undefined;
      if (!documentoPdfId && file && mode !== 'PLANTILLA') { setBusy('Subiendo PDF…'); documentoPdfId = (await api.uploadDocument('CERTIFICADO_PDF', file)).id; }
      setBusy('Registrando…');
      const creado = await api.registerPersonal({ persona: { codigoPais: pais, tipoDocumento: tipo, numeroDocumento: numero.trim(), nombres: nombres.trim(), correo: correo.trim() || null, telefono: telefono.trim() || null },
        codigo: codigo.trim(), datos: payload(datos), ...(documentoPdfId ? { documentoPdfId } : {}) });
      if (mode !== 'PLANTILLA') return onDone(creado);
      setBusy('Generando el PDF con la plantilla…');
      try { onDone(await api.generatePersonalPdf(creado.id)); }
      catch (e) { setError(`El certificado se registró, pero el PDF no se generó: ${errorText(e)} Ciérralo y genera el PDF desde su ficha.`); }
    } catch (e) { setError(errorText(e)); } finally { setBusy(''); }
  }
  const brandName = (code: string) => brands.find(b => b.codigoPais === code)?.nombre || code;
  const archivoVista = mode === 'AUTOMATIZADO' ? archivoLeido : mode === 'MANUAL' ? file : null;
  const vista = mode === 'PLANTILLA'
    ? <VistaPdf fuente={!enVivo.error && enVivo.archivo ? { tipo: 'archivo', archivo: enVivo.archivo } : null} titulo="Vista previa del certificado" ocupado={enVivo.ocupado ? 'Dibujando…' : undefined}
        vacio={enVivo.error || 'Empieza a llenar el formulario y aquí verás el certificado con la plantilla de IQC.'} />
    : <VistaPdf fuente={archivoVista ? { tipo: 'archivo', archivo: archivoVista } : null} ocupado={leyendo}
        alElegir={f => mode === 'AUTOMATIZADO' ? setArchivoLectura(f) : setFile(f)} />;
  return <Drawer onClose={onClose} title="Registrar certificado personal" subtitle="Curso o competencia de una persona" vista={vista}>
    <form className="client-form" onSubmit={submit}>
      <ModeTabs modes={modes} value={mode} onChange={m => { setMode(m); setReadId(null); setArchivoLeido(null); }} />
      {mode === 'AUTOMATIZADO' && <PdfReader tipo="PERSONAL" onRead={applyReading} archivoExterno={archivoLectura} alOcupado={setLeyendo} />}
      <Seccion key={formKey} titulo="Persona"><div className="form-grid three">
        <label>País<select value={pais} onChange={e => { setPais(e.target.value); setKnown(false); }}>{countries.map(c => <option key={c} value={c}>{brandName(c)}</option>)}</select></label>
        <label>Documento<select value={tipo} onChange={e => { setTipo(e.target.value); setKnown(false); }}>{tiposPais.map(t => <option key={t.codigo} value={t.codigo}>{t.etiqueta}</option>)}</select></label>
        <label>Número<input required value={numero} onChange={e => { setNumero(e.target.value); setKnown(false); }} onBlur={lookup} autoComplete="off" />{ayuda && <span className="field-help">{ayuda}</span>}</label>
        <label className="full">Nombres y apellidos<input required maxLength={255} value={nombres} onChange={e => setNombres(e.target.value)} />{known && <span className="field-help"><Check size={14} aria-hidden /> Persona ya registrada: se usarán sus datos y se actualizarán si los cambias.</span>}
          {segunSunat && <span className="field-help"><Check size={14} aria-hidden /> Completado con SUNAT. Revisa tildes y el orden contra el PDF.</span>}</label>
        <label className="span-2c">Correo<input type="email" maxLength={255} value={correo} onChange={e => setCorreo(e.target.value)} /></label>
        <label>Teléfono<input maxLength={30} value={telefono} onChange={e => setTelefono(e.target.value)} /></label>
      </div></Seccion>
      <Seccion titulo="Certificado">
        <div className="form-grid"><label>Código<input required maxLength={64} pattern="[A-Za-z0-9/._-]+" className="mono" value={codigo} onChange={e => setCodigo(e.target.value)} placeholder="IQC/PE/ABM6400" spellCheck={false} /></label>
          {mode === 'MANUAL' && (file ? <p className="pdf-adjunto viendo"><FileText size={14} aria-hidden /><span>{file.name}</span></p>
            : <ElegirPdf alElegir={setFile} />)}
          {mode === 'AUTOMATIZADO' && <p className="field-help">{readId ? 'Se adjuntará el PDF leído (a la derecha).' : 'Elige o arrastra el PDF para leerlo.'}</p>}
</div>
        <CourseFields value={datos} onChange={setDatos} />
      </Seccion>
      {error && <p className="form-error" role="alert">{error}</p>}
      <div className="form-actions"><Boton type="button" variante="secundario" onClick={onClose}>Cancelar</Boton><Boton type="submit" variante="primario" disabled={!!busy}>{busy || 'Registrar certificado'}</Boton></div>
    </form>
  </Drawer>;
}

function PersonalDetail({ id, brands, canManage, canGeneratePdf, onClose, onChanged }: { id: string; brands: Brand[]; canManage: boolean; canGeneratePdf: boolean; onClose: () => void; onChanged: () => void }) {
  const [cert, setCert] = useState<PersonalCertificate | null>(null), [mode, setMode] = useState<'view' | 'edit' | 'void'>('view');
  const [datos, setDatos] = useState<Datos>(datosVacios), [reason, setReason] = useState(''), [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [notice, setNotice] = useState(''), [copied, setCopied] = useState(false);
  const load = () => api.personalCertificate(id).then(c => { setCert(c); setDatos(datosDe(c)); }).catch(e => setError(errorText(e)));
  useEffect(() => { load(); }, [id]); // eslint-disable-line react-hooks/exhaustive-deps
  async function run(action: () => Promise<PersonalCertificate>, ok: string) {
    setBusy(true); setError(''); setNotice('');
    try { const c = await action(); setCert(c); setDatos(datosDe(c)); setMode('view'); setReason(''); setFile(null); setNotice(ok); onChanged(); }
    catch (e) { setError(errorText(e)); } finally { setBusy(false); }
  }
  const brandName = (code: string) => brands.find(b => b.codigoPais === code)?.nombre || code;
  // La vista previa muestra el PDF nuevo elegido antes de subirlo; si no, el vigente
  const fuente: FuentePdf | null = file ? { tipo: 'archivo', archivo: file } : cert?.documentoPdfId ? { tipo: 'documento', id: cert.documentoPdfId, nombre: `${cert.codigo}.pdf` }
    : cert?.urlDocumentoLegado ? { tipo: 'enlace', url: cert.urlDocumentoLegado, nombre: `${cert.codigo}.pdf` } : null;
  const vista = cert ? <VistaPdf fuente={fuente} titulo={file ? 'PDF nuevo, aún sin subir' : 'PDF del certificado'} vacio="Este certificado no tiene PDF." /> : undefined;
  return <Drawer onClose={onClose} vista={vista} title={<span className="mono">{cert?.codigo || 'Cargando…'}</span>} subtitle={cert ? <>{cert.curso}</> : undefined}>
    {error && <p className="form-error" role="alert">{error}</p>}
    {notice && <p className="form-success" role="status"><Check size={14} aria-hidden /> {notice}</p>}
    {!cert ? <div className="skeleton-rows" aria-hidden="true"><span /><span /></div> : <div className="detail-body ficha">
      <section className="ficha-titular" aria-label="Persona">
        <div className="ficha-estado"><PersonalTag estado={cert.estado} /><span>{cert.persona ? brandName(cert.persona.codigoPais) : ''}</span>
          {cert.motivoAnulacion && <span>Motivo: {cert.motivoAnulacion}</span>}</div>
        {cert.persona && <><h3 className="titular">{cert.persona.nombres}</h3>
          <p>{[`${cert.persona.tipoDocumento} ${cert.persona.numeroDocumento}`, cert.persona.correo, cert.persona.telefono].filter(Boolean).join(' · ')}</p></>}
      </section>
      {mode === 'edit' ? <form className="client-form" onSubmit={e => { e.preventDefault(); run(() => api.correctPersonal(cert.id, { datos: payload(datos), motivo: reason.trim(), version: cert.version }), 'Datos corregidos.'); }}>
        <p className="field-help">Corrige los datos mirando el PDF de la derecha.</p>
        <CourseFields value={datos} onChange={setDatos} />
        <label>Motivo de la corrección<input required maxLength={500} value={reason} onChange={e => setReason(e.target.value)} placeholder="Queda en la bitácora" /></label>
        <div className="form-actions"><Boton type="button" variante="secundario" onClick={() => { setMode('view'); setDatos(datosDe(cert)); }}>Cancelar</Boton><Boton type="submit" variante="primario" disabled={busy}>{busy ? 'Guardando…' : 'Guardar corrección'}</Boton></div>
      </form> : <>
        <dl className="ficha-datos">
          <div className="span-2"><dt>Curso</dt><dd>{cert.curso}{cert.subtitulo ? ` — ${cert.subtitulo}` : ''}</dd></div>
          <div><dt>Norma</dt><dd>{[cert.normaReferencia, cert.normaAnio].filter(Boolean).join(':') || '—'}</dd></div>
          <div><dt>Horas · modalidad</dt><dd>{[cert.duracionHoras != null ? `${cert.duracionHoras} h` : null, cert.modalidad].filter(Boolean).join(' · ') || '—'}</dd></div>
          <div><dt>Fechas del curso</dt><dd className="tabular">{cert.fechaInicio ? `${day(cert.fechaInicio)} – ${day(cert.fechaFin)}` : '—'}</dd></div>
          <div><dt>Emisión</dt><dd className="tabular">{day(cert.fechaEmision)}</dd></div>
          <div className="span-2"><dt>Entrenador</dt><dd>{cert.entrenador || '—'}</dd></div>
        </dl>
        {cert.descripcion && <section className="ficha-seccion"><h3>Descripción</h3><p className="detail-notes">{cert.descripcion}</p></section>}
        <section className="ficha-seccion"><h3>Consulta pública</h3>
          <a className="ficha-enlace mono" href={cert.urlConsulta} target="_blank" rel="noreferrer">{cert.urlConsulta.replace(/^https?:\/\//, '')} <ExternalLink size={12} aria-hidden /></a>
          <div className="qr-actions"><Boton type="button" variante="secundario" pequeno onClick={async () => { try { await navigator.clipboard.writeText(cert.urlConsulta); setCopied(true); window.setTimeout(() => setCopied(false), 1600); } catch { setNotice(cert.urlConsulta); } }}>{copied ? <><Check size={14} aria-hidden /> Copiado</> : <><Copy size={14} aria-hidden /> Copiar enlace</>}</Boton></div>
        </section>
        {canManage && cert.estado === 'EMITIDO' && <section className="ficha-seccion"><h3>Documento PDF</h3>
          {canGeneratePdf && <Boton variante="secundario" pequeno icono={FileText} cargando={busy} onClick={() => run(() => api.generatePersonalPdf(cert.id), 'PDF generado con la plantilla.')}>{cert.tienePdf ? 'Regenerar PDF con la plantilla' : 'Generar PDF con la plantilla'}</Boton>}
          <form className="inline-form" onSubmit={async e => { e.preventDefault(); if (!file) return; await run(async () => { const doc = await api.uploadDocument('CERTIFICADO_PDF', file); return api.setPersonalPdf(cert.id, { documentoId: doc.id, version: cert.version }); }, 'PDF actualizado.'); }}>
            <div><ElegirPdf etiqueta="Reemplazar PDF" texto={file ? 'Elegir otro' : 'Elegir PDF'} requerido alElegir={setFile} />
              {file && <span className="field-help">{file.name}: revísalo en la vista previa antes de subirlo.</span>}</div>
            <span />
            <Boton type="submit" variante="primario" disabled={busy || !file}><Upload size={16} aria-hidden /> Subir PDF</Boton>
          </form>
        </section>}
        {mode === 'void' && <form className="task-action-form" onSubmit={e => { e.preventDefault(); run(() => api.voidPersonal(cert.id, { motivo: reason.trim(), version: cert.version }), 'Certificado anulado. Deja de mostrarse en la consulta pública.'); }}>
          <h3>Anular certificado</h3>
          <label>Motivo<input required maxLength={500} value={reason} onChange={e => setReason(e.target.value)} placeholder="Ej.: emitido por error" /></label>
          <div className="form-actions"><Boton type="button" variante="secundario" onClick={() => setMode('view')}>Volver</Boton><Boton type="submit" variante="peligro" disabled={busy}>Confirmar anulación</Boton></div>
        </form>}
        {canManage && cert.estado === 'EMITIDO' && mode === 'view' && <div className="form-actions">
          <Boton variante="secundario" onClick={() => { setReason(''); setMode('void'); }}><Ban size={16} aria-hidden /> Anular</Boton>
          <Boton variante="primario" onClick={() => { setReason(''); setMode('edit'); }}><Pencil size={16} aria-hidden /> Corregir datos</Boton>
        </div>}
      </>}
    </div>}
  </Drawer>;
}

export function PersonalCertificates({ canManage, initialCountry = '' }: { canManage: boolean; initialCountry?: string }) {
  const [text, setText] = useState(''), [debounced, setDebounced] = useState(''), [country, setCountry] = useState(initialCountry);
  const [state, setState] = useState(''), [page, setPage] = useState(0), [refresh, setRefresh] = useState(0);
  const [data, setData] = useState<Page<PersonalCertificate> | null>(null), [error, setError] = useState('');
  const [brands, setBrands] = useState<Brand[]>([]), [types, setTypes] = useState<Record<string, DocumentType[]>>({});
  const [open, setOpen] = useState<string | null>(null), [registering, setRegistering] = useState(false);
  const [tools, setTools] = useState<Tools | null>(null), [resumen, setResumen] = useState<PersonalSummary[] | null>(null);
  useEffect(() => { api.personalSummary().then(setResumen).catch(() => {}); }, [refresh]);
  useEffect(() => { api.brands().then(setBrands).catch(() => {}); api.documentTypes().then(setTypes).catch(() => {}); api.tools().then(setTools).catch(() => {}); }, []);
  useEffect(() => { const t = window.setTimeout(() => setDebounced(text.trim()), 300); return () => window.clearTimeout(t); }, [text]);
  const query = useMemo(() => { const q = new URLSearchParams(); if (debounced) q.set('texto', debounced); if (country) q.set('pais', country); if (state) q.set('estado', state); return q; }, [debounced, country, state]);
  useEffect(() => {
    let active = true;
    const q = new URLSearchParams(query); q.set('pagina', String(page)); q.set('tamano', '25');
    api.personalCertificates(q).then(r => { if (active) { setData(r); setError(''); } }).catch(e => { if (active) setError(errorText(e)); });
    return () => { active = false; };
  }, [query, page, refresh]);
  const reset = (fn: () => void) => { fn(); setPage(0); };
  const filtered = !!(debounced || country || state);
  const brandName = (code: string) => brands.find(b => b.codigoPais === code)?.nombre || code;
  return <div className="screen-body">
    <div className="page-heading"><div><h1>Certificados personales</h1><p>Cursos y competencias de personas, con su PDF y su enlace de consulta.</p></div>
      {canManage && <Boton variante="primario" onClick={() => setRegistering(true)}><Plus size={16} aria-hidden /> Registrar certificado</Boton>}</div>
    <FilaCifras etiqueta="Filtrar por estado">
      <TarjetaCifra icono={Icono.certificadoPersonal} etiqueta="Todos" valor={resumen?.reduce((t, r) => t + r.emitidos + r.anulados, 0)}
        detalle="Registrados de todas las marcas" activa={!state} alPulsar={() => reset(() => setState(''))} />
      <TarjetaCifra icono={BadgeCheck} etiqueta="Emitidos" valor={resumen?.reduce((t, r) => t + r.emitidos, 0)} detalle="Válidos en la consulta pública"
        tono="ok" activa={state === 'EMITIDO'} alPulsar={() => reset(() => setState(state === 'EMITIDO' ? '' : 'EMITIDO'))} />
      <TarjetaCifra icono={Ban} etiqueta="Anulados" valor={resumen?.reduce((t, r) => t + r.anulados, 0)} detalle="La consulta los muestra como anulados"
        tono="neutro" activa={state === 'ANULADO'} alPulsar={() => reset(() => setState(state === 'ANULADO' ? '' : 'ANULADO'))} />
      <TarjetaCifra icono={Globe2} etiqueta="Marcas con certificados" valor={resumen?.filter(r => r.emitidos + r.anulados > 0).length}
        detalle={resumen ? resumen.filter(r => r.emitidos + r.anulados > 0).map(r => brandName(r.codigoPais)).join(' · ') || 'Ninguna todavía' : '…'} tono="neutro" />
    </FilaCifras>
    <section className="list-panel">
      <div className="list-toolbar">
        <SearchField value={text} onChange={v => reset(() => setText(v))} placeholder="Código, curso, nombre o documento" label="Buscar certificados personales" />
        <select aria-label="País" value={country} onChange={e => reset(() => setCountry(e.target.value))}><option value="">País</option>{brands.map(b => <option key={b.codigoPais} value={b.codigoPais}>{b.nombre}</option>)}</select>
        <select aria-label="Estado" value={state} onChange={e => reset(() => setState(e.target.value))}><option value="">Estado</option><option value="EMITIDO">Emitidos</option><option value="ANULADO">Anulados</option></select>
        {filtered && <Boton variante="texto" onClick={() => reset(() => { setText(''); setCountry(''); setState(''); })}><FilterX size={14} aria-hidden /> Limpiar</Boton>}
      </div>
      {error && <div className="notice" role="alert">{error}</div>}
      <div className="table-wrap"><table><thead><tr><th>Código</th><th>Persona</th><th>Curso</th><th>Emisión</th><th>País</th><th>Estado</th><th><span className="sr-only">Abrir</span></th></tr></thead>
        <tbody>{!data && <tr className="skeleton-row"><td colSpan={7}><span /></td></tr>}{data?.contenido.map(c => <tr key={c.id} className="clickable" onClick={() => setOpen(c.id)}>
          <td><span className="mono code">{c.codigo}</span></td>
          <td><strong>{c.persona?.nombres || '—'}</strong><small className="tabular">{c.persona ? `${c.persona.tipoDocumento} ${c.persona.numeroDocumento}` : ''}</small></td>
          <td>{c.curso}{c.normaReferencia && <small>{[c.normaReferencia, c.normaAnio].filter(Boolean).join(':')}</small>}</td>
          <td className="tabular">{day(c.fechaEmision)}</td><td>{c.persona ? brandName(c.persona.codigoPais) : '—'}</td><td><PersonalTag estado={c.estado} />{!c.tienePdf && <small className="muted">sin PDF</small>}</td>
          <td><button className="row-link" onClick={e => { e.stopPropagation(); setOpen(c.id); }} aria-label={`Abrir ${c.codigo}`}><ChevronRight size={16} aria-hidden /></button></td></tr>)}</tbody></table></div>
      <div className="mobile-list">{data?.contenido.map(c => <button className="mobile-row" key={c.id} onClick={() => setOpen(c.id)}>
        <span className="mobile-row-main"><strong>{c.persona?.nombres || c.codigo}</strong><PersonalTag estado={c.estado} /></span>
        <span className="mobile-row-meta"><span className="mono">{c.codigo}</span> · {c.curso} · {day(c.fechaEmision)}</span></button>)}</div>
      {data && data.contenido.length === 0 && <div className="empty-state"><Icono.certificadoPersonal size={20} aria-hidden /><p>{filtered ? 'Ningún certificado personal coincide con la búsqueda.' : 'Aún no hay certificados personales.'}</p></div>}
      <Pagination page={page} totalPages={data?.totalPaginas || 1} total={data?.totalElementos ?? null} noun="certificados" onPage={setPage} />
    </section>
    {open && <PersonalDetail id={open} brands={brands} canManage={canManage} canGeneratePdf={!!tools?.generacionPdf} onClose={() => setOpen(null)} onChanged={() => setRefresh(n => n + 1)} />}
    {registering && <RegisterPersonal types={types} brands={brands} tools={tools} onClose={() => setRegistering(false)} onDone={c => { setRegistering(false); setRefresh(n => n + 1); setOpen(c.id); }} />}
  </div>;
}
