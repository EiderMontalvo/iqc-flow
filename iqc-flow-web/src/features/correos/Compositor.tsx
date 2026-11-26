import { useEffect, useRef, useState, type FormEvent } from 'react';
import type { MailAccount, MailAssistantStatus } from '../../lib/api';
import { errorText } from '../../lib/format';
import { Icono } from '../../lib/iconos';
import { Send } from 'lucide-react';
import { Boton } from '../../components/ui/Boton';
import { Campo } from '../../components/ui/Campo';
import { Desplegable } from '../../components/ui/Desplegable';
import { Segmentado } from '../../components/ui/Segmentado';
import type { Borrador } from './borradores';
import { nombreCuenta } from './etiquetas';

export interface DatosEnvio { cuenta: string; para: string; asunto: string; cuerpo: string; marcarAtendido: boolean }
export type TonoIa = 'FORMAL' | 'CERCANO';

/**
 * Redactar un correo: la respuesta a uno recibido (el destinatario es quien escribió, solo se muestra) o uno nuevo. Cada cambio sube
 * a quien guarda el borrador. La IA solo propone (`alGenerar`): lo que genera cae en el borrador para que una persona lo revise y lo
 * envíe, y si ya había texto, antes se pregunta si se reemplaza.
 */
export function Compositor({ modo, cuentas, borrador, cuentaPorDefecto, ia, alGenerar, alCambiar, alEnviar, alCancelar }: {
  modo: 'respuesta' | 'nuevo'; cuentas: MailAccount[]; borrador: Borrador; cuentaPorDefecto: string;
  ia?: MailAssistantStatus | null; alGenerar?: (tono: TonoIa, instrucciones: string) => Promise<{ asunto: string; cuerpo: string }>;
  alCambiar: (b: Borrador) => void; alEnviar: (d: DatosEnvio) => Promise<void>; alCancelar: () => void;
}) {
  const listas = cuentas.filter(c => c.configurado);
  const [b, setB] = useState<Borrador>(borrador), [error, setError] = useState(''), [enviando, setEnviando] = useState(false);
  const [confirmando, setConfirmando] = useState<'descartar' | 'reemplazar' | null>(null);
  const [tono, setTono] = useState<TonoIa>('FORMAL'), [instrucciones, setInstrucciones] = useState(''), [generando, setGenerando] = useState(false), [errorIa, setErrorIa] = useState('');
  const seguirEditando = useRef<HTMLButtonElement>(null);
  useEffect(() => { if (confirmando) seguirEditando.current?.focus(); }, [confirmando]);
  const cuenta = b.cuenta ?? (listas.some(c => c.cuenta === cuentaPorDefecto) ? cuentaPorDefecto : listas[0]?.cuenta ?? '');
  const cambiar = (parte: Partial<Borrador>) => { const nuevo = { ...b, ...parte }; setB(nuevo); alCambiar(nuevo); };
  const hayTexto = !!b.cuerpo.trim();
  const iaDisponible = !!ia?.disponible && !!alGenerar;

  async function enviar(event: FormEvent) {
    event.preventDefault(); setEnviando(true); setError('');
    try { await alEnviar({ cuenta, para: (b.para ?? '').trim(), asunto: b.asunto.trim(), cuerpo: b.cuerpo.trim(), marcarAtendido: !!b.marcarAtendido }); }
    catch (e) { setError(errorText(e)); setEnviando(false); }
  }
  async function generar() {
    if (!alGenerar) return;
    setConfirmando(null); setGenerando(true); setErrorIa('');
    try { const r = await alGenerar(tono, instrucciones.trim()); cambiar({ asunto: r.asunto.trim() || b.asunto, cuerpo: r.cuerpo, deIa: true }); }
    catch (e) { setErrorIa(errorText(e)); } finally { setGenerando(false); }
  }

  return <form className="client-form correo-compositor" onSubmit={enviar} aria-label={modo === 'respuesta' ? 'Responder el correo' : 'Correo nuevo'}>
    {b.deIa && <p className="correo-aviso-ia" role="note"><Icono.ia size={16} aria-hidden /> Este texto lo redactó la IA. Léelo y corrígelo antes de enviarlo: no se envía solo.</p>}
    {listas.length === 0 && <p className="notice" role="alert">Ninguna cuenta de correo está lista para enviar. Quien administra el servidor debe configurarla.</p>}
    <div className="form-grid">
      <Campo etiqueta="Enviar desde">{a => <select {...a} value={cuenta} onChange={e => cambiar({ cuenta: e.target.value })} required disabled={listas.length === 0}>
        {listas.map(c => <option key={c.cuenta} value={c.cuenta}>{nombreCuenta(c.cuenta)} · {c.remitente}</option>)}</select>}</Campo>
      {modo === 'nuevo'
        ? <Campo etiqueta="Para">{a => <input {...a} type="email" required maxLength={254} value={b.para ?? ''} onChange={e => cambiar({ para: e.target.value })} placeholder="nombre@ejemplo.com" autoComplete="off" />}</Campo>
        : <div className="campo"><span className="etiqueta-campo">Para</span><span className="correo-para">{b.para}</span></div>}
    </div>
    <Campo etiqueta="Asunto">{a => <input {...a} required maxLength={200} value={b.asunto} onChange={e => cambiar({ asunto: e.target.value })} />}</Campo>

    {modo === 'respuesta' && alGenerar && <Desplegable titulo="Sugerir un texto con IA">
      {!iaDisponible && <p className="field-help">El asistente está apagado{ia?.motivo ? `: ${ia.motivo}` : ''}.</p>}
      <Segmentado etiqueta="Tono" valor={tono} onChange={setTono} opciones={[{ valor: 'FORMAL', etiqueta: 'Formal' }, { valor: 'CERCANO', etiqueta: 'Cercano' }]} />
      <Campo etiqueta="Qué debe decir (opcional)" ayuda="Por ejemplo: confirmar la reunión del jueves y pedir el RUC.">{a =>
        <textarea {...a} rows={3} maxLength={1000} value={instrucciones} onChange={e => setInstrucciones(e.target.value)} disabled={!iaDisponible} />}</Campo>
      {confirmando === 'reemplazar'
        ? <div className="correos-confirmar" role="alert"><span>Ya escribiste un texto. ¿Reemplazarlo por el que genere la IA?</span>
          <Boton type="button" variante="peligro" pequeno onClick={generar}>Sí, reemplazarlo</Boton>
          <Boton type="button" variante="secundario" pequeno ref={seguirEditando} onClick={() => setConfirmando(null)}>Conservar el mío</Boton></div>
        : <div><Boton type="button" variante="secundario" pequeno icono={Icono.ia} cargando={generando} disabled={!iaDisponible || generando}
          onClick={() => hayTexto ? setConfirmando('reemplazar') : void generar()} aria-describedby={!iaDisponible ? 'correos-ia-motivo' : undefined}>Generar borrador</Boton></div>}
      {errorIa && <p className="form-error" role="alert">{errorIa}</p>}
    </Desplegable>}

    <Campo etiqueta="Mensaje">{a => <textarea {...a} required rows={9} maxLength={20000} value={b.cuerpo} onChange={e => cambiar({ cuerpo: e.target.value })} autoFocus={modo === 'respuesta' && !b.deIa} />}</Campo>
    {modo === 'respuesta' && <label className="check-row"><input type="checkbox" checked={!!b.marcarAtendido} onChange={e => cambiar({ marcarAtendido: e.target.checked })} /> Marcar el correo como atendido al enviar</label>}
    {error && <p className="form-error" role="alert">{error}</p>}
    <div className="form-actions">
      {confirmando === 'descartar'
        ? <div className="correos-confirmar" role="alert"><span>¿Descartar el borrador? El texto se pierde.</span>
          <Boton type="button" variante="peligro" pequeno onClick={alCancelar}>Sí, descartarlo</Boton>
          <Boton type="button" variante="secundario" pequeno ref={seguirEditando} onClick={() => setConfirmando(null)}>Seguir editando</Boton></div>
        : <>
          <Boton type="button" variante="secundario" onClick={() => hayTexto ? setConfirmando('descartar') : alCancelar()} disabled={enviando}>{modo === 'respuesta' ? 'Descartar borrador' : 'Cancelar'}</Boton>
          <Boton type="submit" variante="primario" icono={Send} cargando={enviando} disabled={listas.length === 0 || !hayTexto}>Enviar</Boton></>}
    </div>
  </form>;
}
