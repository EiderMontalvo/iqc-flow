import { useEffect, useMemo, useRef, useState } from 'react';
import { ArrowLeft, Download, FileText, Paperclip } from 'lucide-react';
import { api, type MailAccount, type MailAiSummary, type MailAssistantStatus, type MailDetail, type MailSent, type MailState, type MailType } from '../../lib/api';
import { errorText, kb, stamp } from '../../lib/format';
import { Icono } from '../../lib/iconos';
import { BuscadorCliente } from '../../components/BuscadorCliente';
import { Boton, BotonEnlace } from '../../components/ui/Boton';
import { Campo } from '../../components/ui/Campo';
import { GrupoBotones } from '../../components/ui/GrupoBotones';
import type { Borrador } from './borradores';
import { Compositor, type DatosEnvio, type TonoIa } from './Compositor';
import { EtiquetaTipo, ESTADOS, TIPOS, nombreCuenta, quienEscribe } from './etiquetas';

const ESTADOS_OPCIONES = (Object.keys(ESTADOS) as MailState[]).map(valor => ({ valor, etiqueta: ESTADOS[valor], titulo: valor === 'NUEVO' ? 'Nuevo: sin tratar. Nadie lo ha tomado ni respondido' : undefined }));
const respuestaDe = (asunto: string) => /^re:/i.test(asunto.trim()) ? asunto.trim() : `Re: ${asunto.trim()}`;

function origenDelTipo(d: MailDetail) {
  const motivo = d.motivoTipo ? `: ${d.motivoTipo.replace(/\.$/, '')}` : '';
  return d.origenTipo === 'MANUAL' ? 'Tipo elegido por una persona' : `Clasificado por ${d.origenTipo === 'IA' ? 'IA' : 'reglas'}${motivo}`;
}

interface Props {
  detalle: MailDetail; guardando: boolean; puedeResponder: boolean; puedeCotizar: boolean; puedeSeguimiento: boolean; ia: MailAssistantStatus | null;
  cuentasEnvio: MailAccount[]; borrador: Borrador | undefined;
  alCambiarBorrador: (b: Borrador | null) => void; alActualizar: (parche: object) => Promise<void>;
  alEnviado: (enviado: MailSent, atendido: boolean) => void; alAbrirOtro: (id: string) => void; alVolver: () => void;
  alCotizar: () => void; alSeguimiento: () => void; alIrAClientes: () => void;
}

/** Lectura de un correo: quién lo escribió, qué hacer con él, el texto (siempre texto plano), y debajo la respuesta, los adjuntos y el hilo */
export function LecturaCorreo(p: Props) {
  const d = p.detalle;
  const [respondiendo, setRespondiendo] = useState(!!p.borrador), [tipoElegido, setTipoElegido] = useState<MailType>(d.tipo), [vinculando, setVinculando] = useState(false);
  const [resumen, setResumen] = useState<MailAiSummary | null>(null), [resumiendo, setResumiendo] = useState(false), [errorIa, setErrorIa] = useState('');
  const compositor = useRef<HTMLElement>(null), respuestasPrevias = useRef(d.respuestas.length), esperaFoco = useRef(false);
  const iaLista = !!p.ia?.disponible;
  // El texto se muestra sin las rachas de líneas vacías que dejan los correos con plantilla
  const texto = useMemo(() => d.texto.replace(/(?:\r?\n[ \t]*){3,}/g, '\n\n').trim(), [d.texto]);
  useEffect(() => { setTipoElegido(d.tipo); }, [d.tipo]);
  useEffect(() => { if (respondiendo) compositor.current?.scrollIntoView({ block: 'nearest' }); }, [respondiendo]);
  // Tras enviar, el foco pasa al encabezado de las respuestas enviadas (si no, caería al principio de la página)
  useEffect(() => {
    if (d.respuestas.length > respuestasPrevias.current) { document.getElementById('correo-resp-t')?.focus(); esperaFoco.current = false; }
    respuestasPrevias.current = d.respuestas.length;
  }, [d.respuestas.length]);

  const borradorBase: Borrador = p.borrador ?? { asunto: respuestaDe(d.asunto), cuerpo: '', para: d.remitenteCorreo, cuenta: d.cuenta, marcarAtendido: true };
  async function resumir() {
    setResumiendo(true); setErrorIa('');
    try { setResumen(await api.mailAiSummary(d.id)); } catch (e) { setErrorIa(errorText(e)); } finally { setResumiendo(false); }
  }
  const generar = (tono: TonoIa, instrucciones: string) => api.mailAiDraft(d.id, { tono, ...(instrucciones ? { instrucciones } : {}) });
  async function enviar(datos: DatosEnvio) {
    const enviado = await api.replyMail(d.id, { cuenta: datos.cuenta, asunto: datos.asunto, cuerpo: datos.cuerpo, marcarAtendido: datos.marcarAtendido });
    p.alCambiarBorrador(null); setRespondiendo(false); p.alEnviado(enviado, datos.marcarAtendido);
    // Red de seguridad: si la lista de respuestas no llega a tiempo, el foco no se queda perdido en el cuerpo de la página
    esperaFoco.current = true;
    window.setTimeout(() => { if (esperaFoco.current && document.activeElement === document.body) document.getElementById('correo-asunto')?.focus(); esperaFoco.current = false; }, 2000);
  }
  const quitarBorrador = () => { p.alCambiarBorrador(null); setRespondiendo(false); };
  const motivoIa = iaLista ? undefined : 'correos-ia-motivo';

  return <article className="correo-lectura-cuerpo" aria-labelledby="correo-asunto">
    <Boton variante="texto" icono={ArrowLeft} className="solo-movil" onClick={p.alVolver}>Volver a la lista</Boton>
    <header className="correo-cabecera">
      <h2 id="correo-asunto" tabIndex={-1}>{d.asunto || '(sin asunto)'}</h2>
      <p className="correo-de"><strong>{quienEscribe(d)}</strong>{d.remitenteNombre && <> <span className="correo-direccion">&lt;{d.remitenteCorreo}&gt;</span></>}
        {' · '}<time dateTime={d.fecha}>{stamp(d.fecha)}</time>{' · '}buzón {nombreCuenta(d.cuenta)}
        {d.tieneEml && <>{' · '}<BotonEnlace variante="texto" icono={Download} href={api.mailEmlUrl(d.id)}>Original (.eml)</BotonEnlace></>}</p>
      {(d.cc || /[,;]/.test(d.para ?? '')) && <p className="correo-de correo-direccion">Para {d.para}{d.cc && <> · Cc {d.cc}</>}</p>}
    </header>

    <div className="correo-acciones" role="group" aria-label="Qué hacer con este correo">
      {p.puedeResponder && <Boton variante="primario" pequeno icono={Icono.responder} onClick={() => setRespondiendo(true)} disabled={respondiendo}>Responder</Boton>}
      {p.puedeCotizar && <Boton variante="secundario" pequeno icono={Icono.cotizacion} onClick={p.alCotizar}>Crear cotización</Boton>}
      {p.puedeSeguimiento && <Boton variante="secundario" pequeno icono={Icono.seguimiento} onClick={p.alSeguimiento}>Crear seguimiento</Boton>}
      {p.puedeResponder && <Boton variante="secundario" pequeno icono={Icono.ia} onClick={resumir} cargando={resumiendo} disabled={!iaLista || resumiendo} aria-describedby={motivoIa}>Resumir con IA</Boton>}
    </div>

    <div className="correo-clasificacion">
      <Campo etiqueta="Tipo" ayuda={origenDelTipo(d)}>{a => <div className="correo-tipo-fila">
        <select {...a} value={tipoElegido} disabled={!p.puedeResponder || p.guardando} onChange={e => setTipoElegido(e.target.value as MailType)}>
          {(Object.keys(TIPOS) as MailType[]).map(t => <option key={t} value={t}>{TIPOS[t].etiqueta}</option>)}</select>
        {tipoElegido !== d.tipo && <Boton variante="primario" pequeno disabled={p.guardando} onClick={() => void p.alActualizar({ tipo: tipoElegido })}>Aplicar</Boton>}
      </div>}</Campo>
      {p.puedeResponder
        ? <GrupoBotones etiqueta="Estado" valor={d.estado} opciones={ESTADOS_OPCIONES} deshabilitado={p.guardando} onElegir={estado => void p.alActualizar({ estado })} />
        : <div className="campo"><span className="etiqueta-campo">Estado</span><span>{ESTADOS[d.estado]}</span></div>}
      <div className="campo correo-cliente">
        <span className="etiqueta-campo">Cliente</span>
        {d.cliente
          ? <div className="correo-cliente-fila"><Icono.cliente size={16} aria-hidden /><strong>{d.cliente.razonSocial}</strong>
            <Boton variante="texto" onClick={p.alIrAClientes}>Ver en Clientes</Boton>
            {p.puedeResponder && <Boton variante="texto" className="danger" disabled={p.guardando} onClick={() => void p.alActualizar({ quitarCliente: true })}>Quitar vínculo</Boton>}</div>
          : <div className="correo-cliente-fila"><span className="correo-direccion">Sin vincular</span>
            {p.puedeResponder && <Boton variante="texto" onClick={() => setVinculando(v => !v)} aria-expanded={vinculando}>{vinculando ? 'Cancelar' : 'Vincular a un cliente'}</Boton>}</div>}
      </div>
    </div>
    {vinculando && !d.cliente && <BuscadorCliente etiqueta="Buscar cliente" value={null} onChange={c => { if (c) { setVinculando(false); void p.alActualizar({ clienteId: c.id }); } }} />}

    {/* La región existe siempre: un lector de pantalla solo anuncia lo que aparece en una región que ya estaba en la página */}
    <div className="correo-resumen-zona" aria-live="polite">
      {errorIa && <p className="form-error" role="alert">{errorIa}</p>}
      {resumen && <div className="correo-resumen" role="region" aria-label="Resumen generado por IA">
        <p className="correo-aviso-ia"><Icono.ia size={14} aria-hidden /> Resumen generado por IA. Confirma con el texto del correo.</p>
        <p>{resumen.resumen}</p>
        {resumen.puntosClave.length > 0 && <ul>{resumen.puntosClave.map(k => <li key={k}>{k}</li>)}</ul>}
        {resumen.accionSugerida && <p><strong>Acción sugerida:</strong> {resumen.accionSugerida}</p>}
        {resumen.tipoSugerido && resumen.tipoSugerido !== d.tipo && <p>Tipo sugerido: <EtiquetaTipo tipo={resumen.tipoSugerido} />{' '}
          <Boton variante="texto" onClick={() => void p.alActualizar({ tipo: resumen.tipoSugerido })}>Aplicar</Boton></p>}
      </div>}
    </div>

    <section className="correo-texto-zona" aria-label="Texto del correo">
      {d.textoTruncado && <p className="notice" role="note">El correo es muy largo y aquí se muestra solo el principio. El mensaje completo está en el original (.eml).</p>}
      <div className="correo-texto">{texto || '(Sin texto: el correo solo trae adjuntos o imágenes.)'}</div>
    </section>

    {p.puedeResponder && respondiendo && <section ref={compositor} aria-labelledby="correo-resp-nueva-t" className="correo-compositor-zona">
      <h3 id="correo-resp-nueva-t">Tu respuesta</h3>
      <Compositor modo="respuesta" cuentas={p.cuentasEnvio} cuentaPorDefecto={d.cuenta} borrador={borradorBase} ia={p.ia} alGenerar={generar}
        alCambiar={p.alCambiarBorrador} alEnviar={enviar} alCancelar={quitarBorrador} />
    </section>}

    {d.adjuntos.length > 0 && <section aria-labelledby="correo-adjuntos-t">
      <h3 id="correo-adjuntos-t"><Paperclip size={14} aria-hidden /> Adjuntos ({d.adjuntos.length})</h3>
      <ul className="correo-adjuntos">{d.adjuntos.map(a => <li key={a.indice}>
        <a href={api.mailAttachmentUrl(d.id, a.indice)} download><FileText size={14} aria-hidden />{a.nombre}</a><span>{a.tipo} · {kb(a.tamano)}</span></li>)}</ul>
      <p className="field-help">Los adjuntos se descargan sin abrirse. Ábrelos solo si confías en quien los envía.</p>
    </section>}

    {d.respuestas.length > 0 && <section aria-labelledby="correo-resp-t"><h3 id="correo-resp-t" tabIndex={-1}>Respuestas enviadas ({d.respuestas.length})</h3>
      {d.respuestas.map(r => <article className="correo-respuesta" key={r.id}>
        <p className="correo-respuesta-meta">De {nombreCuenta(r.cuenta)} a <span className="correo-direccion">{r.para}</span> · <time dateTime={r.enviadoEn}>{stamp(r.enviadoEn)}</time></p>
        <div className="correo-texto">{r.texto}</div></article>)}</section>}

    {d.hilo.length > 0 && <section aria-labelledby="correo-hilo-t"><h3 id="correo-hilo-t">En la misma conversación</h3>
      <ul className="correo-hilo">{d.hilo.map(h => <li key={h.id}><Boton variante="texto" onClick={() => p.alAbrirOtro(h.id)}>{h.asunto || '(sin asunto)'}</Boton>
        <span>{h.remitenteCorreo} · <time dateTime={h.fecha}>{stamp(h.fecha)}</time></span></li>)}</ul></section>}
  </article>;
}
