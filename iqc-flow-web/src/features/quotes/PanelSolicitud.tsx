import { useState } from 'react';
import { AlertTriangle, Building2, Check, Copy, Sparkles, UserPlus } from 'lucide-react';
import { api, type ClientSummary, type RequestAnalysis } from '../../lib/api';
import { errorText } from '../../lib/format';
import { VistaPdf } from '../../components/pdf/VistaPdf';

/**
 * Lo que dice la solicitud, al lado del formulario: resumen, puntos clave, lo que falta pedir al cliente y la empresa
 * (con la opción de registrarla como prospecto si aún no es cliente). Debajo, el documento original.
 */
export function PanelSolicitud({ analisis: a, archivo, texto, puedeCrearClientes, clienteElegido, alCrearCliente }: {
  analisis: RequestAnalysis; archivo: File | null; texto: string; puedeCrearClientes: boolean;
  clienteElegido: ClientSummary | null; alCrearCliente: (c: ClientSummary) => void;
}) {
  const [copiado, setCopiado] = useState(false), [creando, setCreando] = useState(false), [error, setError] = useState('');
  const esPdf = archivo && (archivo.type === 'application/pdf' || archivo.name.toLowerCase().endsWith('.pdf'));
  async function copiarFaltantes() {
    try { await navigator.clipboard.writeText(a.faltantes.map(f => `- ${f}`).join('\n')); setCopiado(true); window.setTimeout(() => setCopiado(false), 1600); }
    catch { /* el navegador no permitió copiar */ }
  }
  async function registrar() {
    if (!a.empresa.razonSocial) return;
    setCreando(true); setError('');
    try {
      const c = await api.createClient({ razonSocial: a.empresa.razonSocial, estado: 'PROSPECTO', ...(a.empresa.ruc ? { tipoDocumento: 'RUC', numeroDocumento: a.empresa.ruc } : {}) });
      if (a.empresa.direccion) {
        await api.addAddress(c.id, { tipo: 'FISCAL', direccion: a.empresa.direccion, distrito: a.empresa.distrito, provincia: a.empresa.provincia,
          departamento: a.empresa.departamento, codigoPais: 'PE' }).catch(() => {});
      }
      alCrearCliente(c);
    } catch (e) { setError(errorText(e)); } finally { setCreando(false); }
  }
  const esCliente = !!(clienteElegido && (clienteElegido.id === a.empresa.clienteId || clienteElegido.numeroDocumento === a.empresa.ruc));

  return <div className="panel-solicitud">
    <div className="panel-solicitud-lectura">
      <p className="panel-solicitud-origen">{a.origen === 'IA' ? <><Sparkles size={14} aria-hidden /> Leída con IA. Revisa antes de guardar.</> : 'Leída con reglas: revisa y completa los datos.'}</p>
      {a.advertencias.map(w => <p key={w} className="panel-solicitud-aviso"><AlertTriangle size={14} aria-hidden /> {w}</p>)}
      {a.resumen && <section><h3>Resumen</h3><p>{a.resumen}</p></section>}
      {a.puntosClave.length > 0 && <section><h3>Puntos clave</h3><ul>{a.puntosClave.map(p => <li key={p}>{p}</li>)}</ul></section>}
      {a.faltantes.length > 0 && <section><h3>Falta pedir al cliente
        <button type="button" className="text-button" onClick={copiarFaltantes}>{copiado ? <><Check size={13} aria-hidden /> Copiado</> : <><Copy size={13} aria-hidden /> Copiar</>}</button></h3>
        <ul>{a.faltantes.map(f => <li key={f}>{f}</li>)}</ul></section>}
      {(a.empresa.razonSocial || a.empresa.ruc) && <section><h3>Empresa</h3>
        <p className="panel-solicitud-empresa"><Building2 size={15} aria-hidden /><span><strong>{a.empresa.razonSocial || 'Sin razón social'}</strong>
          {a.empresa.ruc && <> · RUC {a.empresa.ruc}</>}{a.empresa.direccion && <><br />{a.empresa.direccion}{a.empresa.distrito ? `, ${a.empresa.distrito}` : ''}</>}</span></p>
        {esCliente || a.empresa.clienteId ? <p className="panel-solicitud-ok"><Check size={14} aria-hidden /> Ya es cliente: quedó elegida en la cotización.</p>
          : puedeCrearClientes && a.empresa.razonSocial ? <button type="button" className="secondary small" disabled={creando} onClick={registrar}>
            <UserPlus size={14} aria-hidden /> {creando ? 'Registrando…' : 'Registrar como prospecto y usarla'}</button>
          : <p className="panel-solicitud-aviso">Aún no es cliente: pide que la registren en Clientes para poder cotizar.</p>}
        {error && <p className="form-error" role="alert">{error}</p>}
      </section>}
    </div>
    <div className="panel-solicitud-documento">
      {esPdf ? <VistaPdf fuente={{ tipo: 'archivo', archivo: archivo! }} titulo="Solicitud del cliente" />
        : (a.texto || texto) ? <section><h3>{archivo ? archivo.name : 'Texto de la solicitud'}</h3><pre className="panel-solicitud-texto">{a.texto || texto}</pre></section>
        : archivo ? <p className="panel-solicitud-nombre">Documento analizado: {archivo.name}</p> : null}
    </div>
  </div>;
}
