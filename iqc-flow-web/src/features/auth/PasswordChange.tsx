import { useState, type FormEvent } from 'react';
import { AlertTriangle } from 'lucide-react';
import { api, type CurrentUser } from '../../lib/api';
import { errorText } from '../../lib/format';
import { AccesoLayout } from './AccesoLayout';

export function PasswordChange({ user, onDone }: { user: CurrentUser; onDone: (user: CurrentUser) => void }) {
  const [current, setCurrent] = useState(''), [next, setNext] = useState(''), [confirm, setConfirm] = useState('');
  const [error, setError] = useState(''), [busy, setBusy] = useState(false);
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (next.length < 12) return setError('La nueva contraseña debe tener al menos 12 caracteres.');
    if (next !== confirm) return setError('Las contraseñas nuevas no coinciden.');
    setBusy(true); setError('');
    try { await api.changePassword(current, next); onDone(await api.me()); }
    catch (e) { setError(errorText(e)); } finally { setBusy(false); }
  }
  return <AccesoLayout titulo="Actualiza tu contraseña" texto={`Para continuar, cambia la contraseña temporal de ${user.correo}.`}>
    <form className="acceso-form" onSubmit={submit} aria-labelledby="clave-titulo">
      <h2 id="clave-titulo">Nueva contraseña</h2>
      <label>Contraseña actual<input type="password" autoComplete="current-password" value={current} onChange={e => setCurrent(e.target.value)} required /></label>
      <label>Nueva contraseña<input type="password" autoComplete="new-password" value={next} onChange={e => setNext(e.target.value)} required minLength={12} />
        <span className="field-help">Al menos 12 caracteres. Una frase larga es más segura y fácil de recordar.</span></label>
      <label>Confirmar nueva contraseña<input type="password" autoComplete="new-password" value={confirm} onChange={e => setConfirm(e.target.value)} required minLength={12} /></label>
      {error && <p className="acceso-error" role="alert"><AlertTriangle size={16} aria-hidden /> {error}</p>}
      <button className="primary wide" disabled={busy}>{busy ? 'Guardando…' : 'Guardar contraseña'}</button>
    </form>
  </AccesoLayout>;
}
