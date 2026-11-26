import { useState, type FormEvent } from 'react';
import { CheckCircle2, KeyRound, Wand2 } from 'lucide-react';
import { api, type AppUser } from '../../lib/api';
import { errorText } from '../../lib/format';
import { LONGITUD_MINIMA_CONTRASENA } from '../../lib/politicaContrasena';
import { Boton } from '../../components/ui/Boton';

/** Sugerencia legible y fuerte: cuatro bloques sin caracteres que se confundan (0/O, 1/l) */
function sugerir() {
  const letras = 'abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789', azar = new Uint32Array(16);
  crypto.getRandomValues(azar);
  const bloque = (i: number) => Array.from(azar.slice(i, i + 4), n => letras[n % letras.length]).join('');
  return `${bloque(0)}-${bloque(4)}-${bloque(8)}-${bloque(12)}`;
}

/**
 * Para quien olvidó su contraseña (el aviso llega a Notificaciones): se le asigna una temporal, se le comunica por un
 * canal seguro y, al ingresar, el sistema le obliga a cambiarla.
 */
export function ContrasenaTemporal({ user, alGuardar }: { user: AppUser; alGuardar: (u: AppUser) => void }) {
  const [clave, setClave] = useState(''), [ocupado, setOcupado] = useState(false);
  const [error, setError] = useState(''), [lista, setLista] = useState('');
  async function guardar(e: FormEvent) {
    e.preventDefault(); setOcupado(true); setError(''); setLista('');
    try { const u = await api.setTemporaryPassword(user.id, clave, user.version); alGuardar(u); setLista(clave); setClave(''); }
    catch (err) { setError(errorText(err)); } finally { setOcupado(false); }
  }
  return <form className="client-form contrasena-temporal" onSubmit={guardar}>
    <h3><KeyRound size={16} aria-hidden /> Contraseña temporal</h3>
    <p className="form-hint">Úsala cuando la persona olvidó su contraseña. Deberá cambiarla al ingresar; su contraseña anterior deja de funcionar.</p>
    <div className="contrasena-temporal-fila">
      <label>Nueva contraseña temporal<input value={clave} onChange={e => setClave(e.target.value)} minLength={LONGITUD_MINIMA_CONTRASENA} maxLength={128} required spellCheck={false} autoComplete="off" /></label>
      <Boton type="button" variante="secundario" onClick={() => setClave(sugerir())}><Wand2 size={16} aria-hidden /> Sugerir</Boton>
      <Boton type="submit" variante="primario" disabled={ocupado}>{ocupado ? 'Guardando…' : 'Asignar'}</Boton>
    </div>
    {error && <p className="form-error" role="alert">{error}</p>}
    {lista && <p className="form-success" role="status"><CheckCircle2 size={14} aria-hidden /> Asignada. Compártela con {user.nombre} por un canal seguro: <code>{lista}</code></p>}
  </form>;
}
