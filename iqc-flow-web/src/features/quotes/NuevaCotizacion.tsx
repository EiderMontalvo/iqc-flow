import { useState, type DragEvent } from 'react';
import { FileUp, LoaderCircle } from 'lucide-react';
import { Icono } from '../../lib/iconos';
import { api, type ClientSummary, type Quote, type RequestAnalysis } from '../../lib/api';
import { errorText } from '../../lib/format';
import { Drawer } from '../../components/Drawer';
import { FormularioCotizacion, type PropuestaCotizacion } from './FormularioCotizacion';
import { PanelSolicitud } from './PanelSolicitud';
import { Boton } from '../../components/ui/Boton';

const ACEPTA = '.pdf,.docx,.eml,.msg,.txt,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,message/rfc822,application/vnd.ms-outlook,text/plain';

/**
 * Nueva cotización. Primero se ofrece partir de la solicitud del cliente (PDF, Word o el texto del correo): se analiza
 * y el formulario empieza lleno, con la solicitud y sus puntos clave al lado. También se puede empezar sin solicitud.
 */
export function NuevaCotizacion({ puedeCrearClientes, alGuardar, alCerrar, inicial }: {
  puedeCrearClientes: boolean; alGuardar: (q: Quote) => void; alCerrar: () => void;
  /** La solicitud ya en mano (p. ej. un correo de la bandeja): solo falta pulsar «Analizar» */
  inicial?: { archivo?: File | null; texto?: string };
}) {
  const [paso, setPaso] = useState<'solicitud' | 'formulario'>('solicitud');
  /** Si la solicitud ya viene de un correo, no hace falta explicar cómo descargarlo ni soltarlo */
  const desdeCorreo = !!inicial?.archivo || !!inicial?.texto;
  const [archivo, setArchivo] = useState<File | null>(inicial?.archivo ?? null), [texto, setTexto] = useState(inicial?.texto ?? ''), [encima, setEncima] = useState(false);
  const [analisis, setAnalisis] = useState<RequestAnalysis | null>(null), [cliente, setCliente] = useState<ClientSummary | null>(null);
  const [ocupado, setOcupado] = useState(false), [error, setError] = useState('');

  async function analizar() {
    setOcupado(true); setError('');
    const datos = new FormData();
    if (archivo) datos.append('archivo', archivo);
    if (texto.trim()) datos.append('texto', texto.trim());
    try {
      const a = await api.analyzeRequest(datos);
      setAnalisis(a);
      if (a.empresa.clienteId) setCliente(await api.client(a.empresa.clienteId));
      setPaso('formulario');
    } catch (e) { setError(errorText(e)); } finally { setOcupado(false); }
  }
  function soltar(e: DragEvent) {
    e.preventDefault(); setEncima(false);
    const f = e.dataTransfer.files?.[0];
    if (f) setArchivo(f);
  }
  const propuesta: PropuestaCotizacion | undefined = analisis ? {
    contacto: [analisis.contacto.nombre, analisis.contacto.cargo].filter(Boolean).join(', ') || undefined,
    correo: analisis.contacto.correo || undefined, servicio: analisis.tipoServicio, alcance: analisis.alcance || undefined,
    sedes: analisis.numeroSedes ?? undefined, detalleSedes: analisis.sedesDetalle || undefined, normas: analisis.normas,
    notas: [analisis.resumen && `Solicitud: ${analisis.resumen}`, analisis.numeroTrabajadores != null && `Trabajadores: ${analisis.numeroTrabajadores}`,
      analisis.plazo && `Plazo deseado: ${analisis.plazo}`].filter(Boolean).join('\n') || undefined,
  } : undefined;

  if (paso === 'formulario') {
    return <Drawer onClose={alCerrar} title="Nueva cotización" subtitle="Revisa los datos propuestos antes de guardar. Se numera sola al guardarla."
      vista={analisis ? <PanelSolicitud analisis={analisis} archivo={archivo} texto={texto} puedeCrearClientes={puedeCrearClientes} clienteElegido={cliente}
        alCrearCliente={setCliente} /> : undefined} wide>
      <FormularioCotizacion propuesta={propuesta} cliente={cliente} alGuardar={alGuardar} alCancelar={alCerrar} />
    </Drawer>;
  }

  return <Drawer onClose={alCerrar} title="Nueva cotización" subtitle="Parte de la solicitud del cliente o empieza en blanco." wide>
    <div className="client-form solicitud-paso">
      <div className="solicitud-intro">
        <Icono.ia size={20} aria-hidden />
        <div><strong>{desdeCorreo ? 'Usaremos este correo' : '¿Tienes la solicitud del cliente?'}</strong>
          <p>{desdeCorreo
            ? 'Al analizarlo se leen la empresa, el contacto, las normas, las sedes y los trabajadores, y se proponen en la cotización. Tú revisas todo antes de guardar.'
            : 'Suelta el correo guardado (en Gmail: ⋮ → «Descargar mensaje»; en Outlook: arrástralo al escritorio) o pégalo: se leen la empresa, el contacto, las normas, las sedes y los trabajadores, y se proponen en la cotización. Tú revisas todo antes de guardar.'}</p></div>
      </div>
      <label className={`solicitud-soltar ${encima ? 'encima' : ''} ${archivo ? 'con-archivo' : ''}`}
        onDragOver={e => { e.preventDefault(); setEncima(true); }} onDragLeave={() => setEncima(false)} onDrop={soltar}>
        <FileUp size={26} aria-hidden />
        <strong>{archivo ? archivo.name : 'Arrastra aquí la solicitud'}</strong>
        <span>{archivo ? 'Haz clic para cambiarla' : 'o haz clic para elegirla · correo guardado (.eml, .msg), PDF o Word'}</span>
        <input type="file" accept={ACEPTA} className="sr-only" onChange={e => setArchivo(e.target.files?.[0] || null)} />
      </label>
      <label>O pega el texto del correo<textarea rows={7} value={texto} onChange={e => setTexto(e.target.value)} maxLength={60000}
        placeholder="Copia aquí el correo o mensaje del cliente" /></label>
      {error && <p className="form-error" role="alert">{error}</p>}
      <div className="form-actions">
        <Boton type="button" variante="secundario" onClick={() => setPaso('formulario')}>Empezar sin solicitud</Boton>
        <Boton type="button" variante="primario" disabled={ocupado || (!archivo && !texto.trim())} onClick={analizar}>
          {ocupado ? <><LoaderCircle size={16} className="spin" aria-hidden /> Analizando…</> : <><Icono.ia size={16} aria-hidden /> Analizar solicitud</>}</Boton>
      </div>
    </div>
  </Drawer>;
}
