import { useState, type FormEvent } from 'react';
import { CheckCircle2, X } from 'lucide-react';
import { api, type CurrentUser } from '../../lib/api';
import { errorText, initials } from '../../lib/format';

/** Datos de la cuenta y cambio de contraseña voluntario */
export function PerfilDialogo({ user, espacio, cerrar }: { user: CurrentUser; espacio: string; cerrar: () => void }) {
  const [actual, setActual] = useState(''), [nueva, setNueva] = useState(''), [confirmar, setConfirmar] = useState('');
  const [error, setError] = useState(''), [listo, setListo] = useState(false), [ocupado, setOcupado] = useState(false);
  async function guardar(e: FormEvent) {
    e.preventDefault(); setListo(false);
    if (nueva.length < 12) return setError('La nueva contraseña debe tener al menos 12 caracteres.');
    if (nueva !== confirmar) return setError('Las contraseñas nuevas no coinciden.');
    setOcupado(true); setError('');
    try { await api.changePassword(actual, nueva); setListo(true); setActual(''); setNueva(''); setConfirmar(''); }
    catch (err) { setError(errorText(err)); } finally { setOcupado(false); }
  }
  return <div className="dialog-backdrop" onMouseDown={e => { if (e.target === e.currentTarget) cerrar(); }}>
    <section className="dialog perfil-dialogo" role="dialog" aria-modal="true" aria-labelledby="perfil-titulo">
      <div className="dialog-header"><div><h2 id="perfil-titulo">Mi perfil</h2></div>
        <button type="button" className="icon-button" onClick={cerrar} aria-label="Cerrar"><X size={18} aria-hidden /></button></div>
      <div className="perfil-quien">
        <span className="avatar-cabecera grande" aria-hidden>{initials(user.nombre)}</span>
        <div><strong>{user.nombre}</strong><span>{user.correo}</span><span>{espacio} · {user.organizacion.nombre}</span></div>
      </div>
      <form className="client-form" onSubmit={guardar}>
        <h3>Cambiar contraseña</h3>
        <div className="form-grid">
          <label className="full">Contraseña actual<input type="password" autoComplete="current-password" value={actual} onChange={e => setActual(e.target.value)} required /></label>
          <label>Nueva contraseña<input type="password" autoComplete="new-password" minLength={12} value={nueva} onChange={e => setNueva(e.target.value)} required /></label>
          <label>Confirmar<input type="password" autoComplete="new-password" minLength={12} value={confirmar} onChange={e => setConfirmar(e.target.value)} required /></label>
        </div>
        <span className="field-help">Al menos 12 caracteres. Una frase larga es más segura y fácil de recordar.</span>
        {error && <p className="form-error" role="alert">{error}</p>}
        {listo && <p className="form-success" role="status"><CheckCircle2 size={14} aria-hidden /> Contraseña actualizada.</p>}
        <div className="form-actions"><button type="button" className="secondary" onClick={cerrar}>Cerrar</button>
          <button className="primary" disabled={ocupado}>{ocupado ? 'Guardando…' : 'Guardar contraseña'}</button></div>
      </form>
    </section>
  </div>;
}
