import { useEffect, useState, type FormEvent } from 'react';
import { FileText, Plus, Trash2 } from 'lucide-react';
import { useVistaPreviaEnVivo } from '../../lib/useVistaPreviaEnVivo';
import {
  api, type Certificate, type ClientSummary, type PdfReading, type RegistrationMode, type Scheme, type Standard, type Tools,
} from '../../lib/api';
import { ModeTabs, PdfReader } from '../../components/RegistrationModes';
import { VistaPdf, type FuentePdf } from '../../components/pdf/VistaPdf';
import { BuscadorCliente } from '../../components/BuscadorCliente';
import { ElegirPdf } from '../../components/pdf/ElegirPdf';
import { day, errorText, isoDay, plusYears } from '../../lib/format';
import { Drawer } from '../../components/Drawer';
import { Boton, BotonIcono } from '../../components/ui/Boton';
import { Campo } from '../../components/ui/Campo';
import { Desplegable } from '../../components/ui/Desplegable';
import { Segmentado } from '../../components/ui/Segmentado';
import { Seccion } from '../../components/ui/Seccion';

const SERVICIOS = [
  { valor: 'CERTIFICACION_INICIAL', etiqueta: 'Certificación inicial' }, { valor: 'RECERTIFICACION', etiqueta: 'Recertificación' }, { valor: 'AMPLIACION', etiqueta: 'Ampliación' },
];
const etiquetaEsquema = (codigo: string) => codigo === 'IQC_GLOBAL' ? 'IQC Global' : codigo;
/** Lo que se escribe por defecto en el motivo: queda en la bitácora y se puede cambiar */
const MOTIVO_INICIAL = 'Registro de certificado emitido';

type Line = { key: number; norma: string; edicion: string; codigo: string; procesos: string; anexos: string; file: File | null; documentoId?: string };
const nuevaLinea = (key: number): Line => ({ key, norma: '', edicion: '', codigo: '', procesos: 'NA', anexos: 'NA', file: null });
// Los mismos formatos que valida la API (ImpresionCertificados.PATRON_SECTOR y PATRON_PROCESOS)
const PATRON_SECTOR = '\\d{1,6}(\\s*,\\s*\\d{1,6})*';
const PATRON_PROCESOS = 'NA|\\d+(\\.\\d+)*(\\s*[,;]\\s*\\d+(\\.\\d+)*)*';

/**
 * Registro de certificados ya emitidos (de India, atrasados o correcciones), en los tres modos. El formulario muestra solo lo que
 * aplica al esquema elegido y esconde lo que casi nunca cambia (las fechas calculadas), para que quien registra no tenga que
 * pensar qué completar.
 */
export function RegistrarCertificados({ schemes, standards, tools, onClose, onDone }: {
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
  const [reason, setReason] = useState(MOTIVO_INICIAL);
  const [lines, setLines] = useState<Line[]>([nuevaLinea(1)]);
  // Datos del sector que lleva el certificado de IQC Global (la subdivisión y la clase son del titular, iguales en todas sus normas)
  const [subdivision, setSubdivision] = useState(''), [clase, setClase] = useState('');
  /** Los certificados de IQC Global los emite India: no se pregunta, sale del esquema */
  const esGlobal = scheme === 'IQC_GLOBAL';
  /** La emisión acompaña al otorgamiento (casi siempre es el mismo día) hasta que alguien la cambia a mano */
  const [emisionManual, setEmisionManual] = useState(false), [fechasAbiertas, setFechasAbiertas] = useState(false);
  const [busy, setBusy] = useState(''), [error, setError] = useState('');
  // Vista previa: qué norma se está viendo, el PDF soltado para leer y lo que hace el lector
  const [lineaVista, setLineaVista] = useState(1), [archivoLectura, setArchivoLectura] = useState<File | null>(null), [leyendo, setLeyendo] = useState('');
  useEffect(() => { if (!scheme && schemes[0]) setScheme(schemes[0].codigo); }, [scheme, schemes]);
  function grant(value: string) {
    setGranted(value);
    if (!emisionManual) setIssued(value);
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
    if (e.fechaEmision) { setIssued(e.fechaEmision); setEmisionManual(true); }
    if (e.fechaExpiracion) setExpires(e.fechaExpiracion);
    if (e.limiteVigilancia1) setWatch1(e.limiteVigilancia1);
    if (e.limiteVigilancia2) setWatch2(e.limiteVigilancia2);
    if (e.subdivision) setSubdivision(e.subdivision);
    if (e.clase) setClase(e.clase);
    setLines(all => [{ ...all[0], norma: r.normaCodigo || all[0].norma, edicion: r.edicionValida && e.edicion ? e.edicion : all[0].edicion,
      codigo: e.codigo || all[0].codigo, procesos: e.procesosNoAplicables || all[0].procesos, anexos: e.anexos || all[0].anexos, file: archivo, documentoId }, ...all.slice(1)]);
    if (e.titular) {
      const consulta = e.titular.split(/\s+/).slice(0, 3).join(' ');
      setClient(null); setTitularLeido(consulta); setSearchKey(k => k + 1);
      try {
        const found = await api.clients(new URLSearchParams({ texto: consulta, tamano: '10' }));
        const plano = (t: string) => t.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^A-Za-z0-9]/g, '').toUpperCase();
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
        certificados.push({ norma: line.norma, edicion: line.edicion, codigo: line.codigo.trim().toUpperCase(), ...(documentoPdfId ? { documentoPdfId } : {}),
          ...(esGlobal ? { procesosNoAplicables: line.procesos.trim() || null, anexos: line.anexos.trim() || null } : {}) });
      }
      setBusy('Registrando…');
      const result = await api.registerCertificates({
        clienteId: client.id, esquema: scheme, tipoServicio: service, alcance: scope.trim() || undefined,
        fechaOtorgamiento: granted, fechaEmision: issued, fechaExpiracion: expires,
        limiteVigilancia1: watch1 || undefined, limiteVigilancia2: watch2 || undefined,
        emitidoPorIndia: esGlobal, motivo: reason.trim(), certificados,
        ...(esGlobal ? { subdivision: subdivision.trim() || null, clase: clase.trim() || null } : {}),
      });
      if (mode === 'PLANTILLA') {
        setBusy('Generando el PDF con la plantilla…');
        try { for (const c of result.certificados) await api.generateCertificatePdf(c.id); }
        catch (e) { return setError(`Los certificados se registraron, pero el PDF no se generó: ${errorText(e)} Ciérralo y genera el PDF desde la ficha de cada certificado.`); }
      }
      onDone(result.certificados[0] || null);
    } catch (e) { setError(errorText(e)); } finally { setBusy(''); }
  }
  const fuenteDe = (l: Line): FuentePdf | null => l.file ? { tipo: 'archivo', archivo: l.file } : l.documentoId ? { tipo: 'documento', id: l.documentoId } : null;
  const nombreNorma = (l: Line, i: number) => standards.find(x => x.codigo === l.norma)?.nombreCorto || `Norma ${i + 1}`;
  const activa = lines.find(l => l.key === lineaVista) ?? lines[0];
  // Con el modo plantilla, el certificado de la norma que se mira se dibuja mientras se llena
  const borrador = client && scheme && activa.norma && activa.edicion && granted && issued && expires ? {
    clienteId: client.id, esquema: scheme, norma: activa.norma, edicion: activa.edicion, alcance: scope.trim() || null,
    codigo: activa.codigo.trim().toUpperCase() || null, fechaOtorgamiento: granted, fechaEmision: issued, fechaExpiracion: expires,
    limiteVigilancia1: watch1 || null, limiteVigilancia2: watch2 || null,
    ...(esGlobal ? { subdivision: subdivision.trim() || null, clase: clase.trim() || null,
      procesosNoAplicables: activa.procesos.trim() || null, anexos: activa.anexos.trim() || null } : {}),
  } : null;
  const enVivo = useVistaPreviaEnVivo('/certificados/vista-previa-borrador', borrador, mode === 'PLANTILLA');
  const fuenteEnVivo: FuentePdf | null = !enVivo.error && enVivo.archivo ? { tipo: 'archivo', archivo: enVivo.archivo } : null;
  const ayudaEnVivo = enVivo.error || 'Elige el titular y la norma con su edición, y aquí verás el certificado con la plantilla de IQC.';
  // En automatizado, el PDF de la primera norma se lee; el de las demás solo se adjunta
  const elegirEnVista = (f: File) => mode === 'AUTOMATIZADO' && activa.key === lines[0].key ? setArchivoLectura(f) : update(activa.key, { file: f, documentoId: undefined });
  const vista = mode === 'PLANTILLA'
    ? <VistaPdf fuente={fuenteEnVivo} titulo={`Vista previa · ${nombreNorma(activa, lines.indexOf(activa))}`} ocupado={enVivo.ocupado ? 'Dibujando…' : undefined} vacio={ayudaEnVivo}
        pestanas={lines.length > 1 ? lines.map((l, i) => ({ clave: String(l.key), etiqueta: nombreNorma(l, i), fuente: l.key === activa.key ? fuenteEnVivo : null })) : undefined}
        activa={String(activa.key)} alCambiarPestana={k => setLineaVista(Number(k))} />
    : <VistaPdf fuente={fuenteDe(activa)} titulo={`PDF de ${nombreNorma(activa, lines.indexOf(activa))}`} alElegir={elegirEnVista} ocupado={leyendo}
        pestanas={lines.map((l, i) => ({ clave: String(l.key), etiqueta: nombreNorma(l, i), fuente: fuenteDe(l) }))}
        activa={String(activa.key)} alCambiarPestana={k => setLineaVista(Number(k))} />;
  return <Drawer onClose={onClose} title="Registrar certificado" subtitle="Para certificados ya emitidos: de India, atrasados o correcciones." vista={vista}>
    <form className="client-form" onSubmit={submit} onInvalidCapture={e => {
      if ((e.target as HTMLElement).closest('details:not([open])')) { setFechasAbiertas(true); const formulario = e.currentTarget; window.setTimeout(() => formulario.reportValidity(), 0); }
    }}>
      <ModeTabs modes={modes} value={mode} onChange={m => { setMode(m); setLines(all => all.map(l => ({ ...l, documentoId: undefined }))); }} />
      {mode === 'AUTOMATIZADO' && <PdfReader tipo="EMPRESARIAL" onRead={applyReading} archivoExterno={archivoLectura} alOcupado={setLeyendo} />}

      <Seccion titulo="Titular">
        <BuscadorCliente key={searchKey} etiqueta="Titular del certificado" value={client} onChange={setClient} initialQuery={titularLeido} />
      </Seccion>

      <Seccion titulo="Certificación">
        <Segmentado etiqueta="Esquema de acreditación" valor={scheme} onChange={setScheme} opciones={schemes.map(s => ({ valor: s.codigo, etiqueta: etiquetaEsquema(s.codigo) }))}
          ayuda={esGlobal ? 'Lo emite IQC Global (India): pide subdivisión, clase, procesos no aplicables y anexos.' : undefined} />
        {esGlobal && <div className="form-grid alinear-inicio">
          <Campo etiqueta="Subdivisión" ayuda="Del sector del titular. Varias, separadas por coma.">{a =>
            <input {...a} value={subdivision} onChange={e => setSubdivision(e.target.value)} maxLength={40} pattern={PATRON_SECTOR} placeholder="Ej.: 46" inputMode="numeric" spellCheck={false} />}</Campo>
          <Campo etiqueta="Clase" ayuda="Actividad dentro de la subdivisión.">{a =>
            <input {...a} value={clase} onChange={e => setClase(e.target.value)} maxLength={40} pattern={PATRON_SECTOR} placeholder="Ej.: 4610" inputMode="numeric" spellCheck={false} />}</Campo>
        </div>}
        <div className="cert-lines">{lines.map((line, index) => <div className="cert-line" key={line.key}>
          <label>Norma<select required value={line.norma} onChange={e => { const ed = editions(e.target.value); update(line.key, { norma: e.target.value, edicion: (ed.find(x => x.vigente) || ed[0])?.edicion || '' }); }}>
            <option value="">Elige…</option>{standards.map(s => <option key={s.codigo} value={s.codigo}>{s.nombreCorto}</option>)}</select></label>
          <label>Edición<select required value={line.edicion} onChange={e => update(line.key, { edicion: e.target.value })}>
            <option value="">—</option>{editions(line.norma).map(ed => <option key={ed.edicion} value={ed.edicion}>{ed.designacion}{ed.vigente ? '' : ' (anterior)'}</option>)}</select></label>
          <label>Código<input required maxLength={60} value={line.codigo} onChange={e => update(line.key, { codigo: e.target.value })} placeholder="PE-INA-01520240132" spellCheck={false} className="mono" /></label>
          {esGlobal && <div className="cert-line-global">
            <Campo etiqueta="Procesos no aplicables" ayuda="«NA» o los numerales: 8.3 o 7.1.5, 8.3.">{a =>
              <input {...a} value={line.procesos} onChange={e => update(line.key, { procesos: e.target.value })} maxLength={60} pattern={PATRON_PROCESOS} placeholder="NA" spellCheck={false} />}</Campo>
            <Campo etiqueta="Anexos" ayuda="«NA» si no hay anexos.">{a =>
              <input {...a} value={line.anexos} onChange={e => update(line.key, { anexos: e.target.value })} maxLength={200} placeholder="NA" />}</Campo>
          </div>}
          {mode === 'PLANTILLA' ? null
            : line.file || line.documentoId ? <button type="button" className={`pdf-adjunto ${activa.key === line.key ? 'viendo' : ''}`} onClick={() => setLineaVista(line.key)}
                title="Ver en la vista previa"><FileText size={14} aria-hidden /><span>{line.file?.name || 'PDF leído'}</span></button>
            : <ElegirPdf alElegir={f => { update(line.key, { file: f }); setLineaVista(line.key); }} />}
          {lines.length > 1 && <BotonIcono etiqueta={`Quitar norma ${index + 1}`} icono={Trash2} tamano={16} peligro type="button" onClick={() => setLines(all => all.filter(l => l.key !== line.key))} />}
        </div>)}</div>
        <Boton type="button" variante="texto" icono={Plus} onClick={() => { const key = Date.now(); setLines(all => [...all, nuevaLinea(key)]); setLineaVista(key); }}>Agregar otra norma</Boton>
        <label>Alcance<textarea rows={3} maxLength={4000} value={scope} onChange={e => setScope(e.target.value)} placeholder="Tal como figura en el certificado" /></label>
      </Seccion>

      <Seccion titulo="Servicio y vigencia" ayuda="Las vigilancias futuras crean sus tareas de seguimiento.">
        <div className="form-grid">
          <label>Tipo de servicio<select value={service} onChange={e => setService(e.target.value)}>{SERVICIOS.map(s => <option key={s.valor} value={s.valor}>{s.etiqueta}</option>)}</select></label>
          <label>Fecha de otorgamiento<input type="date" value={granted} onChange={e => grant(e.target.value)} required /></label>
        </div>
        <dl className="resumen-vigencia">
          <div><dt>Emisión</dt><dd>{day(issued)}</dd></div>
          {watch1 && <div><dt>1.ª vigilancia</dt><dd>antes del {day(watch1)}</dd></div>}
          {watch2 && <div><dt>2.ª vigilancia</dt><dd>antes del {day(watch2)}</dd></div>}
          <div><dt>Vencimiento</dt><dd>{day(expires)}</dd></div>
        </dl>
        <Desplegable titulo="Ajustar emisión, expiración y vigilancias" abierto={fechasAbiertas} alCambiar={setFechasAbiertas}>
          <div className="form-grid">
            <label>Emisión<input type="date" value={issued} onChange={e => { setIssued(e.target.value); setEmisionManual(true); }} required /></label>
            <label>Expiración<input type="date" value={expires} onChange={e => setExpires(e.target.value)} required /></label>
            <label>Límite 1.ª vigilancia<input type="date" value={watch1} onChange={e => setWatch1(e.target.value)} /></label>
            <label>Límite 2.ª vigilancia<input type="date" value={watch2} onChange={e => setWatch2(e.target.value)} /></label>
          </div>
        </Desplegable>
        <Campo etiqueta="Motivo del registro" ayuda="Queda en la bitácora.">{a => <input {...a} required maxLength={500} value={reason} onChange={e => setReason(e.target.value)} />}</Campo>
      </Seccion>
      {error && <p className="form-error" role="alert">{error}</p>}
      <div className="form-actions"><Boton type="button" variante="secundario" onClick={onClose}>Cancelar</Boton><Boton type="submit" variante="primario" disabled={!!busy}>{busy || 'Registrar certificado'}</Boton></div>
    </form>
  </Drawer>;
}
