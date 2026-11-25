import { useEffect, useState } from 'react';
import { FilePen, FileScan, FileText, LoaderCircle, ScanLine, type LucideIcon } from 'lucide-react';
import { api, type PdfReading, type RegistrationMode } from '../lib/api';
import { errorText } from '../lib/format';

const MODES: Record<RegistrationMode, { label: string; icon: LucideIcon; help: string }> = {
  MANUAL: { label: 'Manual', icon: FilePen, help: 'Sube el PDF del certificado y completa los datos a mano.' },
  AUTOMATIZADO: { label: 'Automatizado', icon: FileScan, help: 'Sube el PDF: el sistema lee sus datos y completa el formulario para que lo revises.' },
  PLANTILLA: { label: 'Desde plantilla', icon: FileText, help: 'Completa el formulario y el PDF se genera con una plantilla Word.' },
};

export function ModeTabs({ modes, value, onChange }: { modes: RegistrationMode[]; value: RegistrationMode; onChange: (m: RegistrationMode) => void }) {
  if (modes.length === 0) return <p className="form-hint">No hay modos de registro habilitados. Pide a MASTER que active uno en Configuración.</p>;
  return <>
    {modes.length > 1 && <div className="mode-tabs" role="tablist" aria-label="Modo de registro">{modes.map(m => { const { label, icon: Icon } = MODES[m];
      return <button key={m} type="button" role="tab" aria-selected={value === m} onClick={() => onChange(m)}><Icon size={16} aria-hidden /> {label}</button>; })}</div>}
    <p className="mode-help">{MODES[value].help}</p>
  </>;
}

/** Sube el PDF y lo lee; devuelve la lectura y el documento ya subido para adjuntarlo sin volver a subirlo */
export function PdfReader({ tipo, onRead, archivoExterno, alOcupado }: {
  tipo: 'EMPRESARIAL' | 'PERSONAL';
  /** Lectura, documento ya subido y el archivo (para mostrarlo en la vista previa) */
  onRead: (reading: PdfReading, documentoId: string, archivo: File) => void;
  /** Un PDF soltado en la vista previa: se lee igual que uno elegido aquí */
  archivoExterno?: File | null;
  /** Avisa qué está haciendo (subiendo, leyendo) para mostrarlo sobre la vista previa */
  alOcupado?: (texto: string) => void;
}) {
  const [busy, setBusyLocal] = useState(''), [error, setError] = useState(''), [result, setResult] = useState<PdfReading | null>(null);
  const setBusy = (t: string) => { setBusyLocal(t); alOcupado?.(t); };
  async function read(file: File) {
    setError(''); setResult(null);
    try {
      setBusy('Subiendo PDF…');
      const doc = await api.uploadDocument('CERTIFICADO_PDF', file);
      setBusy('Leyendo los datos del PDF…');
      const r = await api.readPdf(tipo, doc.id);
      setResult(r); onRead(r, doc.id, file);
    } catch (e) { setError(errorText(e)); } finally { setBusy(''); }
  }
  useEffect(() => { if (archivoExterno) read(archivoExterno); }, [archivoExterno]); // eslint-disable-line react-hooks/exhaustive-deps
  const formatos: Record<PdfReading['formato'], string> = { INACAL_SAE: 'INACAL / SAE', IQC_GLOBAL: 'IQC Global (India)', PERSONAL: 'certificado personal', DESCONOCIDO: 'formato no reconocido' };
  return <div className="reader-drop">
    <label className="secondary elegir-pdf-boton lector"><ScanLine size={16} aria-hidden /> {result ? 'Leer otro PDF' : 'Elegir PDF para leer'}
      <input type="file" accept="application/pdf" disabled={!!busy} className="sr-only" onChange={e => { const f = e.target.files?.[0]; if (f) read(f); e.target.value = ''; }} /></label>
    {busy ? <p><LoaderCircle size={14} aria-hidden className="spin" /> {busy}</p> : !result && <p>Elígelo aquí o arrástralo a la vista previa. Los campos leídos se completan abajo: revísalos contra el PDF antes de registrar.</p>}
    {error && <p className="form-error" role="alert">{error}</p>}
    {result && <div className="reader-result" role="status">
      <span>{result.formato === 'DESCONOCIDO' ? 'No se reconoció el formato del PDF.' : `Leído como ${formatos[result.formato]}${result.origen === 'IA' ? ' con IA' : ''}. El PDF quedará adjunto.`}</span>
      {result.advertencias.length > 0 && <ul>{result.advertencias.map(a => <li key={a}>{a}</li>)}</ul>}
    </div>}
  </div>;
}
