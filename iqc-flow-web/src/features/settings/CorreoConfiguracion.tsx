import { useEffect, useState, type FormEvent } from 'react';
import { CheckCircle2, CircleDashed, RefreshCw, Send } from 'lucide-react';
import { api, type MailAccount } from '../../lib/api';
import { errorText } from '../../lib/format';
import { Boton } from '../../components/ui/Boton';
import { Campo } from '../../components/ui/Campo';
import { nombreCuenta } from '../correos/etiquetas';


/**
 * Cuentas de correo con las que IQC Flow envía. Cada cuenta y su contraseña las define quien administra el servidor (variables
 * IQC_CORREO_CUENTAS_<NOMBRE>_USUARIO y _CLAVE): aquí solo se ve cuáles hay y si están listas, sin contraseñas, y se manda una
 * prueba con la que se elija para comprobarla.
 */
export function CorreoConfiguracion() {
  const [clasificando, setClasificando] = useState(false), [clasificado, setClasificado] = useState(''), [errorClasificar, setErrorClasificar] = useState('');
  const [cuentas, setCuentas] = useState<MailAccount[] | null>(null), [cuenta, setCuenta] = useState(''), [para, setPara] = useState('');
  const [enviando, setEnviando] = useState(false), [error, setError] = useState(''), [enviado, setEnviado] = useState('');
  useEffect(() => {
    api.mailAccounts().then(lista => { setCuentas(lista); setCuenta(lista.find(c => c.configurado)?.cuenta ?? ''); }).catch(e => setError(errorText(e)));
  }, []);
  const listas = cuentas?.filter(c => c.configurado) ?? [];
  async function reclasificar() {
    setClasificando(true); setClasificado(''); setErrorClasificar('');
    try { const r = await api.reclassifyMail(); setClasificado(`Se revisaron ${r.revisados} correos y ${r.cambiaron} cambiaron de tipo. Los que corregiste a mano no se tocan.`); }
    catch (e) { setErrorClasificar(errorText(e)); } finally { setClasificando(false); }
  }
  async function enviar(event: FormEvent) {
    event.preventDefault(); setEnviando(true); setError(''); setEnviado('');
    try { await api.sendTestMail(cuenta, para.trim()); setEnviado(`Enviado a ${para.trim()} desde ${cuenta}. Si no llega en un minuto, revisa el spam.`); }
    catch (e) { setError(errorText(e)); } finally { setEnviando(false); }
  }
  return <div className="panel config-firma">
    {cuentas && <ul className="cuentas-correo" aria-label="Cuentas de correo">{cuentas.map(c => <li key={c.cuenta} className={c.configurado ? 'lista' : 'apagada'}>
      {c.configurado ? <CheckCircle2 size={16} aria-hidden /> : <CircleDashed size={16} aria-hidden />}
      <strong>{nombreCuenta(c.cuenta)}</strong>
      <span>{c.configurado ? `${c.remitente} · ${c.servidor}` : 'Sin usuario y clave en el servidor'}</span>
    </li>)}</ul>}
    {cuentas && listas.length === 0 && <p className="field-help">Ninguna cuenta está lista. Quien administra el servidor define IQC_CORREO_CUENTAS_&lt;NOMBRE&gt;_USUARIO y _CLAVE y reinicia la API.</p>}
    <form className="client-form" onSubmit={enviar}>
      <div className="form-grid">
        <Campo etiqueta="Enviar desde">{a => <select {...a} value={cuenta} onChange={e => setCuenta(e.target.value)} disabled={listas.length === 0} required>
          {listas.map(c => <option key={c.cuenta} value={c.cuenta}>{nombreCuenta(c.cuenta)} · {c.remitente}</option>)}</select>}</Campo>
        <Campo etiqueta="Enviar la prueba a" ayuda="Usa una dirección tuya y revisa también el spam.">{a =>
          <input {...a} type="email" required maxLength={254} value={para} onChange={e => setPara(e.target.value)} placeholder="nombre@ejemplo.com" autoComplete="email" disabled={listas.length === 0} />}</Campo>
      </div>
      {error && <p className="form-error" role="alert">{error}</p>}
      {enviado && <p className="form-success" role="status"><CheckCircle2 size={16} aria-hidden /> {enviado}</p>}
      <div className="form-actions"><Boton type="submit" variante="primario" icono={Send} cargando={enviando} disabled={listas.length === 0}>Enviar correo de prueba</Boton></div>
    </form>
    <div className="config-bloque">
      <h3>Clasificación de los correos recibidos</h3>
      <p className="field-help">Cada correo que llega se clasifica solo (cotización, pago, auditoría, certificado, reclamo, publicidad u otro) según reglas. Si se ajustan las reglas, aquí se aplican de nuevo a los correos ya guardados. Los que cambiaste a mano no se tocan, y IQC Flow solo lee los buzones: no borra, mueve ni marca nada en el servidor.</p>
      <div><Boton type="button" variante="secundario" icono={RefreshCw} cargando={clasificando} onClick={reclasificar}>Volver a clasificar los correos</Boton></div>
      {clasificado && <p className="form-success" role="status"><CheckCircle2 size={16} aria-hidden /> {clasificado}</p>}
      {errorClasificar && <p className="form-error" role="alert">{errorClasificar}</p>}
    </div>
  </div>;
}
