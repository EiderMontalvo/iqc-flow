import { useEffect, useRef, useState, type DragEvent } from 'react';
import { AlertTriangle, ExternalLink, FileText, FileUp, LoaderCircle, RefreshCw } from 'lucide-react';
import { api } from '../../lib/api';
import { errorText, kb } from '../../lib/format';

/** De dónde sale el PDF: un archivo recién elegido, un documento ya guardado o un enlace de la plataforma anterior */
export type FuentePdf =
  | { tipo: 'archivo'; archivo: File }
  | { tipo: 'documento'; id: string; nombre?: string; tamano?: number }
  | { tipo: 'enlace'; url: string; nombre?: string };

export interface PestanaPdf { clave: string; etiqueta: string; fuente: FuentePdf | null }

interface Props {
  fuente: FuentePdf | null;
  /** Encabezado del visor (p. ej. «PDF del certificado», «Versión 2») */
  titulo?: string;
  /** Si existe, se puede arrastrar o elegir un PDF y cambiarlo */
  alElegir?: (archivo: File) => void;
  /** Texto cuando no hay PDF y no se puede elegir uno */
  vacio?: string;
  /** Varios PDF en el mismo visor (una norma por pestaña) */
  pestanas?: PestanaPdf[];
  activa?: string;
  alCambiarPestana?: (clave: string) => void;
  /** Ocupado (subiendo, leyendo): se muestra sobre el visor */
  ocupado?: string;
}

const esPdf = (f: File) => f.type === 'application/pdf' || f.name.toLowerCase().endsWith('.pdf');

/**
 * Visor de PDF para revisar el documento junto a sus datos. Usa el visor del navegador sobre una copia local
 * (blob:): sirve igual para un archivo que aún no se sube y para un documento de la API (que no se deja enmarcar).
 * Los PDF de la plataforma anterior se muestran desde su enlace. Acepta arrastrar y soltar.
 */
export function VistaPdf({ fuente, titulo = 'PDF del certificado', alElegir, vacio, pestanas, activa, alCambiarPestana, ocupado }: Props) {
  const [url, setUrl] = useState<string | null>(null), [cargando, setCargando] = useState(false), [error, setError] = useState('');
  const [encima, setEncima] = useState(false), entrada = useRef<HTMLInputElement>(null);
  const claveFuente = !fuente ? '' : fuente.tipo === 'archivo' ? `a:${fuente.archivo.name}:${fuente.archivo.size}:${fuente.archivo.lastModified}`
    : fuente.tipo === 'documento' ? `d:${fuente.id}` : `e:${fuente.url}`;

  useEffect(() => {
    setError(''); setUrl(null);
    if (!fuente || fuente.tipo === 'enlace') return;
    let creada: string | null = null, vigente = true;
    if (fuente.tipo === 'archivo') { creada = URL.createObjectURL(fuente.archivo); setUrl(creada); }
    else {
      setCargando(true);
      api.documentBlob(fuente.id)
        .then(b => { if (!vigente) return; creada = URL.createObjectURL(new Blob([b], { type: 'application/pdf' })); setUrl(creada); })
        .catch(e => { if (vigente) setError(errorText(e)); })
        .finally(() => { if (vigente) setCargando(false); });
    }
    return () => { vigente = false; if (creada) URL.revokeObjectURL(creada); };
  }, [claveFuente]); // eslint-disable-line react-hooks/exhaustive-deps

  function soltar(e: DragEvent) {
    e.preventDefault(); setEncima(false);
    const f = e.dataTransfer.files?.[0];
    if (!alElegir) return;
    if (f && esPdf(f)) alElegir(f); else setError('Solo se aceptan archivos PDF.');
  }
  const nombre = !fuente ? null : fuente.tipo === 'archivo' ? fuente.archivo.name : fuente.nombre || (fuente.tipo === 'enlace' ? 'PDF de la plataforma anterior' : 'PDF guardado');
  const tamano = fuente?.tipo === 'archivo' ? fuente.archivo.size : fuente?.tipo === 'documento' ? fuente.tamano : undefined;
  const abrir = fuente?.tipo === 'enlace' ? fuente.url : url;

  return <div className={`vista-pdf ${encima ? 'encima' : ''}`}
    onDragOver={alElegir ? e => { e.preventDefault(); setEncima(true); } : undefined}
    onDragLeave={alElegir ? e => { if (!e.currentTarget.contains(e.relatedTarget as Node)) setEncima(false); } : undefined}
    onDrop={alElegir ? soltar : undefined}>
    {pestanas && pestanas.length > 1 && <div className="vista-pdf-pestanas" role="tablist" aria-label="PDF por norma">{pestanas.map(p =>
      <button key={p.clave} type="button" role="tab" aria-selected={p.clave === activa} onClick={() => alCambiarPestana?.(p.clave)}>
        <span className={`vista-pdf-punto ${p.fuente ? 'con' : ''}`} aria-hidden />{p.etiqueta}</button>)}</div>}
    <header className="vista-pdf-barra">
      <FileText size={16} aria-hidden />
      <div><strong>{nombre ?? titulo}</strong>{nombre && <span>{titulo}{fuente?.tipo === 'enlace' ? ' · plataforma anterior' : ''}{tamano ? ` · ${kb(tamano)}` : ''}</span>}</div>
      {abrir && <a className="icon-button" href={abrir} target="_blank" rel="noreferrer" title="Abrir en otra pestaña" aria-label="Abrir el PDF en otra pestaña"><ExternalLink size={16} aria-hidden /></a>}
      {alElegir && fuente && <button type="button" className="secondary small" onClick={() => entrada.current?.click()}><RefreshCw size={14} aria-hidden /> Cambiar</button>}
    </header>
    {alElegir && <input ref={entrada} type="file" accept="application/pdf" hidden onChange={e => { const f = e.target.files?.[0]; if (f) alElegir(f); e.target.value = ''; }} />}

    <div className="vista-pdf-cuerpo">
      {/* PDF de la plataforma anterior (Google Cloud Storage): se muestra directo; el almacenamiento no permite
          descargarlo desde el panel (sin CORS), pero sí verlo en un marco */}
      {fuente?.tipo === 'enlace' ? <iframe key={fuente.url} src={`${fuente.url}#navpanes=0&view=FitH`} title={`Vista previa: ${nombre ?? titulo}`} />
        : url ? <iframe key={url} src={`${url}#navpanes=0&view=FitH`} title={`Vista previa: ${nombre ?? titulo}`} />
        : cargando ? <div className="vista-pdf-vacio"><LoaderCircle size={24} className="spin" aria-hidden /><p>Cargando el PDF…</p></div>
        : error ? <div className="vista-pdf-vacio error"><AlertTriangle size={24} aria-hidden /><strong>No se pudo mostrar el PDF</strong><p>{error}</p></div>
        : alElegir ? <button type="button" className="vista-pdf-vacio soltar" onClick={() => entrada.current?.click()}>
            <FileUp size={30} aria-hidden /><strong>Arrastra aquí el PDF</strong><p>o haz clic para elegirlo. Lo verás aquí antes de guardar.</p></button>
        : <div className="vista-pdf-vacio"><FileText size={28} aria-hidden /><p>{vacio ?? 'Sin PDF.'}</p></div>}
      {ocupado && <div className="vista-pdf-ocupado" role="status"><LoaderCircle size={18} className="spin" aria-hidden /> {ocupado}</div>}
      {encima && <div className="vista-pdf-soltar" aria-hidden><FileUp size={30} /> Suelta el PDF</div>}
    </div>
  </div>;
}
