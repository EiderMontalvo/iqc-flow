import { useEffect, useState, type FormEvent } from 'react';
import { Ban, Check, Copy, Pencil, Send } from 'lucide-react';
import { api, type Quote } from '../../lib/api';
import { day, errorText, isoDay } from '../../lib/format';
import { Drawer } from '../../components/Drawer';
import { FormularioCotizacion } from './FormularioCotizacion';
import { EstadoCotizacion, SERVICIOS, dinero } from './textos';
import { Boton } from '../../components/ui/Boton';

type Accion = 'ver' | 'editar' | 'enviar' | 'responder' | 'anular';

/**
 * Detalle de una cotización y sus pasos: editar o enviar el borrador, registrar la respuesta del cliente, anular o
 * duplicar. Cada paso pide solo lo necesario (fecha, destinatarios, motivo).
 */
export function DetalleCotizacion({ id, puedeGestionar, alCerrar, alCambiar, alAbrir }: {
  id: string; puedeGestionar: boolean; alCerrar: () => void; alCambiar: () => void; alAbrir: (id: string) => void;
}) {
  const [q, setQ] = useState<Quote | null>(null), [accion, setAccion] = useState<Accion>('ver');
  const [error, setError] = useState(''), [aviso, setAviso] = useState(''), [ocupado, setOcupado] = useState(false);
  const [fecha, setFecha] = useState(isoDay(new Date())), [destinatarios, setDestinatarios] = useState(''), [aceptada, setAceptada] = useState('SI'), [motivo, setMotivo] = useState('');
  useEffect(() => { api.quote(id).then(setQ).catch(e => setError(errorText(e))); }, [id]);
  function abrirPaso(a: Accion) {
    setAccion(a); setError(''); setAviso(''); setMotivo(''); setFecha(isoDay(new Date()));
    if (a === 'enviar') setDestinatarios(q?.contactoCorreo || '');
  }
  async function correr(paso: () => Promise<Quote>, ok: string) {
    setOcupado(true); setError(''); setAviso('');
    try { const n = await paso(); setQ(n); setAccion('ver'); setAviso(ok); alCambiar(); }
    catch (e) { setError(errorText(e)); } finally { setOcupado(false); }
  }
  async function duplicar() {
    if (!q) return;
    setOcupado(true); setError('');
    try { const n = await api.duplicateQuote(q.id); alCambiar(); alAbrir(n.id); }
    catch (e) { setError(errorText(e)); } finally { setOcupado(false); }
  }
  const enviarPaso = (e: FormEvent) => { e.preventDefault(); q && correr(() => api.sendQuote(q.id, { fecha, destinatarios, version: q.version }), 'Cotización marcada como enviada. Si no hay respuesta, se creará una tarea de seguimiento.'); };
  const responderPaso = (e: FormEvent) => { e.preventDefault(); q && correr(() => api.answerQuote(q.id, { aceptada: aceptada === 'SI', fecha, motivo, version: q.version }), aceptada === 'SI' ? 'Respuesta registrada: aceptada.' : 'Respuesta registrada: rechazada.'); };
  const anularPaso = (e: FormEvent) => { e.preventDefault(); q && correr(() => api.voidQuote(q.id, { motivo, version: q.version }), 'Cotización anulada.'); };

  return <Drawer onClose={alCerrar} wide title={<span className="mono">{q?.numero || 'Cargando…'}</span>}
    subtitle={q ? <>{SERVICIOS[q.tipoServicio]} · emitida el {day(q.fechaEmision)}</> : undefined}>
    {error && <p className="form-error" role="alert">{error}</p>}
    {aviso && <p className="form-success" role="status"><Check size={14} aria-hidden /> {aviso}</p>}
    {!q ? <div className="skeleton-rows" aria-hidden><span /><span /><span /></div>
      : accion === 'editar' ? <FormularioCotizacion inicial={q} alGuardar={n => { setQ(n); setAccion('ver'); setAviso('Cambios guardados.'); alCambiar(); }} alCancelar={() => setAccion('ver')} />
      : <div className="detail-body ficha">
        <section className="ficha-titular" aria-label="Cliente">
          <div className="ficha-estado"><EstadoCotizacion estado={q.estado} dias={q.diasSinRespuesta} /><span>Responsable: {q.responsable.nombre}</span></div>
          <h3 className="titular">{q.cliente.razonSocial}</h3>
          <p>{[q.cliente.numeroDocumento ? `${q.cliente.tipoDocumento || 'Documento'} ${q.cliente.numeroDocumento}` : null, q.contactoNombre, q.contactoCorreo].filter(Boolean).join(' · ')}</p>
        </section>

        <section className="ficha-seccion"><h3>Normas y montos</h3>
          <div className="table-wrap"><table className="cot-tabla">
            <thead><tr><th>Concepto</th><th className="num">Días auditor</th><th className="num">Monto</th></tr></thead>
            <tbody>{q.items.map(i => <tr key={i.id}><td>{i.descripcion}</td><td className="num tabular">{i.diasAuditor.toLocaleString('es-PE')}</td><td className="num tabular">{dinero(i.monto, q.moneda)}</td></tr>)}</tbody>
            <tfoot>
              <tr><td>Subtotal</td><td className="num tabular">{q.diasAuditorTotal.toLocaleString('es-PE')}</td><td className="num tabular">{dinero(q.subtotal, q.moneda)}</td></tr>
              <tr><td>{q.nombreImpuesto} {q.tasaImpuesto} %</td><td /><td className="num tabular">{dinero(q.impuesto, q.moneda)}</td></tr>
              <tr className="total"><td>Total</td><td /><td className="num tabular">{dinero(q.total, q.moneda)}</td></tr>
            </tfoot>
          </table></div>
          {q.tarifaDia != null && <p className="field-help">Tarifa por día auditor: {dinero(q.tarifaDia, q.moneda)}</p>}
        </section>

        <dl className="ficha-datos">
          <div><dt>Acreditación</dt><dd>{q.esquemaCodigo || 'Sin definir'}</dd></div>
          <div><dt>Sedes</dt><dd>{q.numeroSedes}{q.sedesDetalle ? ` · ${q.sedesDetalle}` : ''}</dd></div>
          <div><dt>Válida hasta</dt><dd className="tabular">{day(q.validaHasta)} ({q.validezDias} días)</dd></div>
          <div><dt>Condiciones de pago</dt><dd>{q.condicionesPago || '—'}</dd></div>
        </dl>
        {q.alcance && <section className="ficha-seccion"><h3>Alcance</h3><p className="detail-notes">{q.alcance}</p></section>}

        <section className="ficha-seccion"><h3>Historial</h3>
          <ol className="cot-historial">
            <li><strong>Emitida</strong><span>{day(q.fechaEmision)}{q.creadoPor ? ` · ${q.creadoPor.nombre}` : ''}</span></li>
            {q.enviadaEn && <li><strong>Enviada</strong><span>{day(q.enviadaEn)}{q.destinatarios ? ` · ${q.destinatarios}` : ''}</span></li>}
            {q.respondidaEn && <li><strong>{q.estado === 'ANULADA' ? 'Anulada' : q.estado === 'ACEPTADA' ? 'Aceptada por el cliente' : 'Rechazada por el cliente'}</strong>
              <span>{day(q.respondidaEn)}{q.motivoRespuesta ? ` · ${q.motivoRespuesta}` : ''}</span></li>}
          </ol>
        </section>
        {q.notas && <section className="ficha-seccion"><h3>Notas internas</h3><p className="detail-notes">{q.notas}</p></section>}

        {accion === 'enviar' && <form className="paso-cotizacion" onSubmit={enviarPaso}>
          <h3>Marcar como enviada</h3>
          <div className="form-grid corto-largo">
            <label>Fecha de envío<input type="date" required max={isoDay(new Date())} value={fecha} onChange={e => setFecha(e.target.value)} /></label>
            <label>Destinatarios<input maxLength={1000} value={destinatarios} onChange={e => setDestinatarios(e.target.value)} placeholder="Correos o nombres, separados por comas" /></label>
          </div>
          <div className="form-actions"><Boton type="button" variante="secundario" onClick={() => setAccion('ver')}>Volver</Boton><Boton type="submit" variante="primario" disabled={ocupado}>Marcar como enviada</Boton></div>
        </form>}
        {accion === 'responder' && <form className="paso-cotizacion" onSubmit={responderPaso}>
          <h3>Respuesta del cliente</h3>
          <div className="form-grid">
            <label>Respuesta<select value={aceptada} onChange={e => setAceptada(e.target.value)}><option value="SI">Aceptó la cotización</option><option value="NO">Rechazó la cotización</option></select></label>
            <label>Fecha<input type="date" required max={isoDay(new Date())} value={fecha} onChange={e => setFecha(e.target.value)} /></label>
          </div>
          <label>{aceptada === 'SI' ? 'Comentario (opcional)' : 'Motivo del rechazo'}<input required={aceptada === 'NO'} maxLength={1000} value={motivo} onChange={e => setMotivo(e.target.value)}
            placeholder={aceptada === 'SI' ? 'Ej.: aceptó por correo el 24/11' : 'Ej.: eligió otra certificadora por precio'} /></label>
          <div className="form-actions"><Boton type="button" variante="secundario" onClick={() => setAccion('ver')}>Volver</Boton><Boton type="submit" variante="primario" disabled={ocupado}>Registrar respuesta</Boton></div>
        </form>}
        {accion === 'anular' && <form className="paso-cotizacion" onSubmit={anularPaso}>
          <h3>Anular cotización</h3>
          <label>Motivo<input required maxLength={1000} value={motivo} onChange={e => setMotivo(e.target.value)} placeholder="Ej.: se emitió con datos equivocados" /></label>
          <div className="form-actions"><Boton type="button" variante="secundario" onClick={() => setAccion('ver')}>Volver</Boton><Boton type="submit" variante="peligro" disabled={ocupado}>Confirmar anulación</Boton></div>
        </form>}

        {puedeGestionar && accion === 'ver' && <div className="form-actions">
          <Boton variante="secundario" onClick={duplicar} disabled={ocupado}><Copy size={16} aria-hidden /> Duplicar</Boton>
          {(q.estado === 'BORRADOR' || q.estado === 'ENVIADA') && <Boton variante="secundario" onClick={() => abrirPaso('anular')}><Ban size={16} aria-hidden /> Anular</Boton>}
          {q.estado === 'BORRADOR' && <Boton variante="secundario" onClick={() => abrirPaso('editar')}><Pencil size={16} aria-hidden /> Editar</Boton>}
          {q.estado === 'BORRADOR' && <Boton variante="primario" onClick={() => abrirPaso('enviar')}><Send size={16} aria-hidden /> Marcar como enviada</Boton>}
          {q.estado === 'ENVIADA' && <Boton variante="primario" onClick={() => abrirPaso('responder')}><Check size={16} aria-hidden /> Registrar respuesta</Boton>}
        </div>}
      </div>}
  </Drawer>;
}
