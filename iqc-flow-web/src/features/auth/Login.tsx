import { useState, type FormEvent } from 'react';
import { AlertTriangle, ArrowLeft, ArrowRight, CheckCircle2, LoaderCircle, LockKeyhole } from 'lucide-react';
import { api, type CurrentUser } from '../../lib/api';
import { errorText } from '../../lib/format';
import { AccesoLayout } from './AccesoLayout';

/**
 * «Olvidé mi contraseña»: mientras no hay correo configurado, la solicitud avisa a Dirección, que asigna una contraseña
 * temporal desde Usuarios. La respuesta es la misma exista o no la cuenta.
 */
function Olvido({ correoInicial, volver }: { correoInicial: string; volver: () => void }) {
  const [correo, setCorreo] = useState(correoInicial), [enviado, setEnviado] = useState(false);
  const [busy, setBusy] = useState(false), [error, setError] = useState('');
  async function submit(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('');
    try { await api.forgotPassword(correo.trim()); setEnviado(true); }
    catch (e) { setError(errorText(e)); } finally { setBusy(false); }
  }
  return <form className="acceso-form" onSubmit={submit} aria-labelledby="olvido-titulo">
    <h2 id="olvido-titulo">Recuperar el acceso</h2>
    {enviado ? <>
      <p className="acceso-exito" role="status"><CheckCircle2 size={16} aria-hidden /> Listo. Si el correo está registrado, Dirección recibió tu solicitud y te dará una contraseña temporal.</p>
      <p className="acceso-ayuda">Al ingresar con ella, el sistema te pedirá crear una nueva.</p>
    </> : <>
      <p className="acceso-ayuda">Escribe el correo con el que ingresas. Avisaremos a Dirección para que te asigne una contraseña temporal.</p>
      <label>Correo electrónico<input type="email" autoComplete="username" value={correo} onChange={e => setCorreo(e.target.value)} required autoFocus /></label>
      {error && <p className="acceso-error" role="alert"><AlertTriangle size={16} aria-hidden /> {error}</p>}
      <button className="primary wide" disabled={busy}>{busy ? <><LoaderCircle size={16} className="spin" aria-hidden /> Enviando…</> : 'Pedir una contraseña temporal'}</button>
    </>}
    <button type="button" className="acceso-enlace" onClick={volver}><ArrowLeft size={14} aria-hidden /> Volver a iniciar sesión</button>
  </form>;
}

export function Login({ onLogin }: { onLogin: (user: CurrentUser) => void }) {
  const [correo, setCorreo] = useState(''), [contrasena, setContrasena] = useState('');
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [olvido, setOlvido] = useState(false);
  async function submit(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('');
    try { await api.login(correo, contrasena); onLogin(await api.me()); }
    catch (e) { setError(errorText(e)); } finally { setBusy(false); }
  }
  return <AccesoLayout titulo="IQC Flow" texto="Certificados, clientes y seguimientos de IQC Perú e IQC LATAM, en un solo lugar.">
    {olvido ? <Olvido correoInicial={correo} volver={() => setOlvido(false)} /> : <form className="acceso-form" onSubmit={submit} aria-labelledby="acceso-titulo">
      <h2 id="acceso-titulo">Iniciar sesión</h2>
      <label>Correo electrónico<input type="email" autoComplete="username" value={correo} onChange={e => setCorreo(e.target.value)} required autoFocus /></label>
      <label><span className="acceso-etiqueta-fila">Contraseña<button type="button" className="acceso-enlace" onClick={() => setOlvido(true)}>¿Olvidaste tu contraseña?</button></span>
        <input type="password" autoComplete="current-password" value={contrasena} onChange={e => setContrasena(e.target.value)} required /></label>
      {error && <p className="acceso-error" role="alert"><AlertTriangle size={16} aria-hidden /> {error}</p>}
      <button className="primary wide" disabled={busy}>
        {busy ? <><LoaderCircle size={16} className="spin" aria-hidden /> Ingresando…</> : <>Ingresar <ArrowRight size={16} aria-hidden /></>}
      </button>
      <p className="acceso-nota"><LockKeyhole size={14} aria-hidden /> Tras 5 intentos fallidos la cuenta se bloquea 15 minutos.</p>
    </form>}
  </AccesoLayout>;
}
