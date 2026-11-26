import { useState, type FormEvent } from 'react';
import { Send } from 'lucide-react';
import { Icono } from '../../lib/iconos';
import { api, type MailAccount, type MailAiAnswer, type MailAssistantStatus } from '../../lib/api';
import { errorText, stamp } from '../../lib/format';
import { Drawer } from '../../components/Drawer';
import { Boton } from '../../components/ui/Boton';
import { Campo } from '../../components/ui/Campo';
import { nombreCuenta } from './etiquetas';

/**
 * «Pregúntale a la bandeja»: una pregunta en lenguaje natural sobre los correos guardados («¿qué pagos llegaron esta semana?»).
 * La respuesta cita los correos en que se apoya, que se abren con un clic. Solo lee: no envía ni cambia nada.
 */
export function PreguntarBandeja({ ia, cuentas, alAbrirCorreo, alCerrar }: {
  ia: MailAssistantStatus | null; cuentas: MailAccount[]; alAbrirCorreo: (id: string) => void; alCerrar: () => void;
}) {
  const [pregunta, setPregunta] = useState(''), [cuenta, setCuenta] = useState(''), [desde, setDesde] = useState(''), [hasta, setHasta] = useState('');
  const [respuesta, setRespuesta] = useState<MailAiAnswer | null>(null), [ocupado, setOcupado] = useState(false), [error, setError] = useState('');
  const disponible = !!ia?.disponible;
  async function preguntar(event: FormEvent) {
    event.preventDefault(); setOcupado(true); setError(''); setRespuesta(null);
    try {
      setRespuesta(await api.mailAiAsk({ pregunta: pregunta.trim(), ...(cuenta ? { cuenta } : {}), ...(desde ? { desde } : {}), ...(hasta ? { hasta } : {}) }));
    } catch (e) { setError(errorText(e)); } finally { setOcupado(false); }
  }
  return <Drawer onClose={alCerrar} title="Pregúntale a la bandeja" subtitle="Consulta tus correos en lenguaje natural. Solo lee: no envía ni cambia nada." wide>
    <form className="client-form" onSubmit={preguntar}>
      {!disponible && <p className="notice" role="status"><Icono.ia size={16} aria-hidden /> El asistente de IA está apagado: {ia?.motivo || 'comprobando si está disponible…'}</p>}
      <p className="notice correos-privacidad" role="note"><Icono.privacidad size={16} aria-hidden /> <span><strong>Privacidad:</strong> para responder, el texto de los correos que encuentre sale de IQC Flow hacia el proveedor de IA.</span></p>
      <Campo etiqueta="Tu pregunta">{a => <textarea {...a} required rows={3} maxLength={1000} value={pregunta} onChange={e => setPregunta(e.target.value)}
        placeholder="Ej.: ¿Qué solicitudes de validación de certificados llegaron esta semana?" disabled={!disponible} />}</Campo>
      <div className="form-grid three">
        <Campo etiqueta="Buzón">{a => <select {...a} value={cuenta} onChange={e => setCuenta(e.target.value)} disabled={!disponible}>
          <option value="">Todos</option>{cuentas.filter(c => c.configurado).map(c => <option key={c.cuenta} value={c.cuenta}>{nombreCuenta(c.cuenta)}</option>)}</select>}</Campo>
        <Campo etiqueta="Desde">{a => <input {...a} type="date" value={desde} onChange={e => setDesde(e.target.value)} disabled={!disponible} />}</Campo>
        <Campo etiqueta="Hasta">{a => <input {...a} type="date" value={hasta} onChange={e => setHasta(e.target.value)} disabled={!disponible} />}</Campo>
      </div>
      {error && <p className="form-error" role="alert">{error}</p>}
      <div className="form-actions"><Boton type="submit" variante="primario" icono={Send} cargando={ocupado} disabled={!disponible || !pregunta.trim()}>Preguntar</Boton></div>
    </form>
    {/* La región existe antes de que llegue la respuesta: así el lector de pantalla la anuncia al aparecer */}
    <section className="detail-body correo-respuesta-ia" aria-label="Respuesta de la IA" aria-live="polite">
      {respuesta && <>
      <p className="correo-aviso-ia"><Icono.ia size={14} aria-hidden /> Respuesta generada por IA. Confirma abriendo los correos en los que se apoya.</p>
      <div className="correo-texto">{respuesta.respuesta}</div>
      {respuesta.correos.length > 0 ? <>
        <h3>Correos en los que se apoya ({respuesta.correos.length})</h3>
        <ul className="correo-hilo">{respuesta.correos.map(c => <li key={c.id}>
          <Boton variante="texto" onClick={() => alAbrirCorreo(c.id)}>{c.asunto || '(sin asunto)'}</Boton>
          <span>{c.remitenteCorreo} · <time dateTime={c.fecha}>{stamp(c.fecha)}</time></span></li>)}</ul>
      </> : <p className="field-help">No citó ningún correo.</p>}
      </>}
    </section>
  </Drawer>;
}
