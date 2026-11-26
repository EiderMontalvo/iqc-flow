import { useEffect, useMemo, useRef, useState } from 'react';
import { PenLine, RefreshCw, X } from 'lucide-react';
import {
  api, ApiError, type ClientDetail, type MailAccount, type MailCounters, type MailQuery, type MailSyncAccount, type MailType, type Page, type TaskType,
} from '../../lib/api';
import { day, errorText, relativa } from '../../lib/format';
import { Icono } from '../../lib/iconos';
import { Drawer } from '../../components/Drawer';
import { Pagination } from '../../components/Pagination';
import { SearchField } from '../../components/SearchField';
import { Boton, BotonIcono } from '../../components/ui/Boton';
import { Campo } from '../../components/ui/Campo';
import { Desplegable } from '../../components/ui/Desplegable';
import { Segmentado } from '../../components/ui/Segmentado';
import { NuevaCotizacion } from '../quotes/NuevaCotizacion';
import { NewTask } from '../tasks/Tasks';
import { borradores } from './borradores';
import { Compositor, type DatosEnvio } from './Compositor';
import { ESTADOS, TIPOS, nombreCuenta, nombreEml, quienEscribe } from './etiquetas';
import { useAsistenteCorreo, useCorreo, useListaCorreos } from './hooks';
import { LecturaCorreo } from './LecturaCorreo';
import { ListaCorreos } from './ListaCorreos';
import { PreguntarBandeja } from './PreguntarBandeja';

type Vista = 'PENDIENTES' | 'ATENDIDOS' | 'DESCARTADOS' | 'TODOS' | 'PUBLICIDAD';
const VISTAS: Array<{ valor: Exclude<Vista, 'PUBLICIDAD'>; etiqueta: string }> = [
  { valor: 'PENDIENTES', etiqueta: 'Pendientes' }, { valor: 'ATENDIDOS', etiqueta: 'Atendidos' }, { valor: 'DESCARTADOS', etiqueta: 'Descartados' }, { valor: 'TODOS', etiqueta: 'Todos' },
];
const VACIO: Record<Vista, string> = {
  PENDIENTES: 'No hay correos pendientes. Pulsa «Revisar ahora» para buscar nuevos.', ATENDIDOS: 'Aún no hay correos atendidos.', DESCARTADOS: 'No hay correos descartados.',
  TODOS: 'No hay correos con esos filtros.', PUBLICIDAD: 'No hay publicidad pendiente.',
};
type Aviso = { tipo: 'ok' | 'error'; texto: string };
/** Ancho del panel por debajo del cual la lista y la lectura pasan a una sola columna (el mismo valor que en correos.css) */
const UNA_COLUMNA = 880;

export function Correos({ contadores, recargarContadores, puedeResponder, puedeCotizar, puedeSeguimiento, puedeCrearClientes, irAClientes }: {
  contadores: MailCounters | null; recargarContadores: () => void; puedeResponder: boolean; puedeCotizar: boolean; puedeSeguimiento: boolean;
  puedeCrearClientes: boolean; irAClientes: () => void;
}) {
  // Filtros: viven en memoria mientras la pantalla está abierta
  const [cuenta, setCuenta] = useState(''), [vista, setVista] = useState<Vista>('PENDIENTES'), [tipo, setTipo] = useState<MailType | ''>('');
  const [texto, setTexto] = useState(''), [buscado, setBuscado] = useState(''), [desde, setDesde] = useState(''), [hasta, setHasta] = useState(''), [pagina, setPagina] = useState(0);
  useEffect(() => { const t = window.setTimeout(() => { setBuscado(texto.trim()); setPagina(0); }, 300); return () => window.clearTimeout(t); }, [texto]);
  const consulta: MailQuery = useMemo(() => ({
    pagina, tamano: 25, ...(cuenta && { cuenta }), ...(buscado && { texto: buscado }), ...(desde && { desde }), ...(hasta && { hasta }),
    ...(vista === 'PENDIENTES' && { sinAtender: true }), ...(vista === 'ATENDIDOS' && { estado: 'ATENDIDO' as const }), ...(vista === 'DESCARTADOS' && { estado: 'DESCARTADO' as const }),
    ...(vista === 'PUBLICIDAD' ? { tipo: 'PUBLICIDAD' as const, sinAtender: true } : tipo ? { tipo } : {}),
  }), [pagina, cuenta, buscado, desde, hasta, vista, tipo]);
  const claveConsulta = JSON.stringify(consulta);
  const lista = useListaCorreos(consulta);

  const [abiertoId, setAbiertoId] = useState<string | null>(null), [abiertoMovil, setAbiertoMovil] = useState(false);
  const correo = useCorreo(abiertoId), panelLectura = useRef<HTMLDivElement>(null), panel = useRef<HTMLElement>(null);
  const [sync, setSync] = useState<MailSyncAccount[]>([]), [cuentasEnvio, setCuentasEnvio] = useState<MailAccount[]>([]);
  const [sincronizando, setSincronizando] = useState(false), [ocupado, setOcupado] = useState(false);
  const [aviso, setAviso] = useState<Aviso | null>(null), [anuncio, setAnuncio] = useState('');
  const [confirmando, setConfirmando] = useState(false), cancelarDescarte = useRef<HTMLButtonElement>(null);
  const [redactando, setRedactando] = useState(false), [preguntando, setPreguntando] = useState(false);
  const [cotizar, setCotizar] = useState<{ archivo?: File; texto?: string } | null>(null);
  const [tarea, setTarea] = useState<{ tipos: TaskType[]; inicial: { titulo: string; descripcion: string; cliente: ClientDetail | null } } | null>(null);
  const [errorTarea, setErrorTarea] = useState('');
  const ia = useAsistenteCorreo(puedeResponder);

  // Un correo nuevo se lee desde su principio, no desde donde quedó el anterior
  useEffect(() => { panelLectura.current?.scrollTo({ top: 0 }); }, [abiertoId]);
  // El total se anuncia solo cuando cambia el filtro o la página (no en cada actualización de datos), y si el correo abierto ya no está en la vista se cierra
  const esperando = useRef<{ datos: Page<unknown> | null } | null>(null);
  useEffect(() => { esperando.current = { datos: lista.datos }; }, [claveConsulta]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    const e = esperando.current;
    if (!e || !lista.datos || lista.datos === e.datos) return;
    esperando.current = null;
    let frase = `${lista.datos.totalElementos} correos. Página ${lista.datos.pagina + 1} de ${Math.max(1, lista.datos.totalPaginas)}.`;
    if (abiertoId && !lista.datos.contenido.some(m => m.id === abiertoId)) { setAbiertoId(null); setAbiertoMovil(false); frase += ' El correo que tenías abierto no está en esta vista: se cerró.'; }
    setAnuncio(frase);
  }, [lista.datos]); // eslint-disable-line react-hooks/exhaustive-deps

  // Borradores: viven fuera de la pantalla (ver borradores.ts); aquí solo se repinta cuando uno aparece o desaparece
  const [, pintar] = useState(0);
  const cambiarBorrador = (id: string, b: Parameters<typeof borradores.guardar>[1] | null) => {
    const habia = borradores.hay(id);
    if (b) borradores.guardar(id, b); else borradores.borrar(id);
    if (habia !== !!b) pintar(n => n + 1);
  };

  const cargarSync = () => api.mailSyncStatus().then(setSync).catch(() => {});
  useEffect(() => { void cargarSync(); if (puedeResponder) api.mailAccounts().then(setCuentasEnvio).catch(() => {}); }, [puedeResponder]);
  // Un aviso de éxito se retira solo; uno de error se queda hasta que se cierre
  useEffect(() => { if (aviso?.tipo !== 'ok') return; const t = window.setTimeout(() => setAviso(null), 8000); return () => window.clearTimeout(t); }, [aviso]);
  // La confirmación de descartar publicidad no sobrevive a salir de esa vista; al abrirla, el foco va a «Cancelar»
  useEffect(() => { if (vista !== 'PUBLICIDAD') setConfirmando(false); }, [vista]);
  useEffect(() => { if (confirmando) cancelarDescarte.current?.focus(); }, [confirmando]);

  const cuentasSync = sync.filter(c => c.sincronizable);
  const ultima = cuentasSync.map(c => c.ultimaPasada).filter((x): x is string => !!x).sort().pop();
  const fallos = cuentasSync.filter(c => c.error);
  const filtrado = !!(cuenta || buscado || desde || hasta || tipo);
  const iaApagada = puedeResponder && !!ia && !ia.disponible;

  const abrir = (id: string) => { setAbiertoId(id); setAbiertoMovil(true); };
  const volver = () => { setAbiertoMovil(false); window.requestAnimationFrame(() => document.querySelector<HTMLElement>('[role="option"][aria-selected="true"]')?.focus()); };
  // Al abrir un correo se avisa cuál es (no se lee entero); con la lista oculta (una columna) el foco pasa al título del correo
  const idAbierto = correo.detalle?.id;
  useEffect(() => {
    if (!correo.detalle) return;
    setAnuncio(`Abierto: ${correo.detalle.asunto || 'sin asunto'}.`);
    if ((panel.current?.clientWidth ?? Infinity) <= UNA_COLUMNA) document.getElementById('correo-asunto')?.focus({ preventScroll: true });
  }, [idAbierto]); // eslint-disable-line react-hooks/exhaustive-deps
  const avisar = (a: Aviso) => setAviso(a);
  const refrescar = () => { lista.recargar(); recargarContadores(); void cargarSync(); };

  async function revisar() {
    setSincronizando(true); setAviso(null);
    try {
      const r = await api.mailSyncNow();
      const nuevos = r.cuentas.reduce((t, c) => t + c.nuevos, 0), conError = r.cuentas.filter(c => c.error);
      if (conError.length) avisar({ tipo: 'error', texto: `No se pudo revisar ${conError.map(c => nombreCuenta(c.cuenta)).join(' ni ')}. ${nuevos ? `Del resto llegaron ${nuevos}.` : ''}` });
      else avisar({ tipo: 'ok', texto: r.enCurso && !r.cuentas.length ? 'Ya hay una revisión en curso; en un momento se ve el resultado.' : nuevos ? `Llegaron ${nuevos} correo${nuevos === 1 ? '' : 's'} nuevo${nuevos === 1 ? '' : 's'}.` : 'No hay correos nuevos.' });
      refrescar();
    } catch (e) { avisar({ tipo: 'error', texto: errorText(e) }); } finally { setSincronizando(false); }
  }

  /** Cambia tipo, estado o cliente del correo abierto, con la versión que se vio; mientras guarda los controles quedan apagados, así lo mostrado nunca se adelanta a lo guardado */
  async function actualizar(parche: object) {
    const d = correo.detalle; if (!d || ocupado) return;
    setOcupado(true); setAviso(null);
    try {
      const nuevo = await api.updateMailMessage(d.id, { ...parche, version: d.version });
      correo.setDetalle(nuevo);
      lista.reemplazar(d.id, { tipo: nuevo.tipo, estado: nuevo.estado, origenTipo: nuevo.origenTipo, clienteId: nuevo.cliente?.id ?? null, version: nuevo.version });
      recargarContadores(); setAnuncio(`Correo actualizado: ${TIPOS[nuevo.tipo].etiqueta}, ${ESTADOS[nuevo.estado].toLowerCase()}.`);
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) { correo.recargar(); lista.recargar(); avisar({ tipo: 'error', texto: 'Otra persona cambió este correo mientras lo mirabas. Ya está actualizado: repite el cambio si sigue haciendo falta.' }); }
      else avisar({ tipo: 'error', texto: errorText(e) });
    } finally { setOcupado(false); }
  }
  /** Tomar un correo para trabajarlo (cotización o seguimiento creados desde él) lo pasa de «Nuevo» a «En curso» */
  const tomarCorreo = () => { if (correo.detalle?.estado === 'NUEVO') void actualizar({ estado: 'EN_CURSO' }); };

  async function descartarPublicidad() {
    setConfirmando(false); setOcupado(true);
    try { const r = await api.discardMailAds(); avisar({ tipo: 'ok', texto: `Se descartaron ${r.descartados} correo${r.descartados === 1 ? '' : 's'} de publicidad. Se pueden recuperar en Descartados.` }); refrescar(); }
    catch (e) { avisar({ tipo: 'error', texto: errorText(e) }); } finally { setOcupado(false); }
  }

  async function enviarNuevo(d: DatosEnvio) {
    await api.composeMail({ cuenta: d.cuenta, para: d.para, asunto: d.asunto, cuerpo: d.cuerpo });
    cambiarBorrador('nuevo', null); setRedactando(false); avisar({ tipo: 'ok', texto: `Correo enviado a ${d.para}.` });
  }

  async function abrirCotizacion() {
    const d = correo.detalle; if (!d) return;
    try { setCotizar(d.tieneEml ? { archivo: await api.mailEmlFile(d.id, nombreEml(d.asunto)) } : { texto: d.texto }); }
    catch (e) { avisar({ tipo: 'error', texto: errorText(e) }); }
  }
  async function abrirSeguimiento() {
    const d = correo.detalle; if (!d) return;
    try {
      const [tipos, cliente] = await Promise.all([api.taskTypes(), d.cliente ? api.client(d.cliente.id) : Promise.resolve(null)]);
      setErrorTarea('');
      setTarea({ tipos, inicial: { titulo: `Responder a ${quienEscribe(d)}: ${d.asunto}`.slice(0, 200), descripcion: `Correo recibido el ${day(d.fecha)} en el buzón ${nombreCuenta(d.cuenta)}.`, cliente } });
    } catch (e) { avisar({ tipo: 'error', texto: errorText(e) }); }
  }
  async function guardarTarea(datos: object) {
    setOcupado(true); setErrorTarea('');
    try { await api.createTask(datos); setTarea(null); avisar({ tipo: 'ok', texto: 'Seguimiento creado. Lo ves en Seguimientos.' }); setOcupado(false); tomarCorreo(); }
    catch (e) { setErrorTarea(errorText(e)); setOcupado(false); }
  }

  const abierto = abiertoMovil && !!abiertoId;
  const publicidad = contadores ? ` (${contadores.publicidad})` : '';
  const motivoIa = iaApagada ? 'correos-ia-motivo' : undefined;
  return <div className={`screen-body correos ${abierto ? 'leyendo' : ''}`}>
    <div className="page-heading"><div><h1>Correos</h1>
      <p><strong className="tabular">{contadores?.nuevos ?? '—'}</strong> sin tratar · <strong className="tabular">{contadores?.enCurso ?? '—'}</strong> en curso · {ultima
        ? <>revisado <time dateTime={ultima} title={new Date(ultima).toLocaleString('es-PE')}>{relativa(ultima)}</time></> : 'sin revisar aún'}</p></div>
      <div className="heading-actions">
        {puedeResponder && <Boton variante="secundario" icono={RefreshCw} cargando={sincronizando} onClick={revisar}>Revisar ahora</Boton>}
        {puedeResponder && <Boton variante="secundario" icono={Icono.ia} onClick={() => setPreguntando(true)} disabled={!ia?.disponible} aria-describedby={motivoIa}>Preguntar con IA</Boton>}
        {puedeResponder && <Boton variante="primario" icono={PenLine} onClick={() => setRedactando(true)}>Redactar correo</Boton>}
      </div></div>
    {iaApagada && <p id="correos-ia-motivo" className="field-help correos-ia-motivo"><Icono.ia size={14} aria-hidden /><span>Asistente de IA apagado: {(ia?.motivo || 'no está activado en el servidor').replace(/\.$/, '')}.</span></p>}

    {fallos.length > 0 && <div className="notice" role="alert">{fallos.map(c => `${nombreCuenta(c.cuenta)}: ${c.error}`).join(' · ')} <Boton variante="texto" onClick={revisar} disabled={sincronizando}>Reintentar</Boton></div>}
    {/* Las regiones en vivo existen siempre; el aviso es el único canal de lo que acaba de pasar (no se repite en el anuncio) */}
    <div className="correos-avisos" aria-live="polite">{aviso && <p className={`correos-aviso ${aviso.tipo === 'ok' ? 'form-success' : 'form-error'}`} role={aviso.tipo === 'error' ? 'alert' : undefined}>
      <span>{aviso.texto}</span><BotonIcono etiqueta="Cerrar el aviso" icono={X} tamano={16} onClick={() => setAviso(null)} /></p>}</div>
    <p className="sr-only" role="status" aria-live="polite">{anuncio}</p>

    <section ref={panel} className={`list-panel correos-panel ${abierto ? 'con-lectura' : ''}`}>
      <div className="list-toolbar correos-filtros">
        {cuentasSync.length > 1 && <label className="correos-tipo-filtro"><span className="sr-only">Buzón</span>
          <select value={cuenta} onChange={e => { setCuenta(e.target.value); setPagina(0); }}>
            <option value="">Todos los buzones</option>{cuentasSync.map(c => <option key={c.cuenta} value={c.cuenta}>{nombreCuenta(c.cuenta)}</option>)}</select></label>}
        <Segmentado etiqueta="Qué mostrar" ocultarEtiqueta valor={vista} onChange={v => { setVista(v); setPagina(0); }} opciones={[...VISTAS, { valor: 'PUBLICIDAD', etiqueta: `Publicidad${publicidad}` }]} />
        <label className="correos-tipo-filtro"><span className="sr-only">Tipo de correo</span>
          <select value={vista === 'PUBLICIDAD' ? 'PUBLICIDAD' : tipo} disabled={vista === 'PUBLICIDAD'} onChange={e => { setTipo(e.target.value as MailType | ''); setPagina(0); }}>
            {vista === 'PUBLICIDAD' ? <option value="PUBLICIDAD">Publicidad</option> : <option value="">Todos los tipos</option>}
            {(Object.keys(TIPOS) as MailType[]).filter(t => t !== 'PUBLICIDAD').map(t => <option key={t} value={t}>{TIPOS[t].etiqueta}</option>)}</select></label>
        <SearchField value={texto} onChange={setTexto} placeholder="Buscar correos" label="Buscar correos por asunto, remitente o texto" />
      </div>
      <div className="correos-mas"><Desplegable titulo="Filtrar por fechas">
        <div className="form-grid">
          <Campo etiqueta="Desde">{a => <input {...a} type="date" value={desde} max={hasta || undefined} onChange={e => { setDesde(e.target.value); setPagina(0); }} />}</Campo>
          <Campo etiqueta="Hasta">{a => <input {...a} type="date" value={hasta} min={desde || undefined} onChange={e => { setHasta(e.target.value); setPagina(0); }} />}</Campo>
        </div>
      </Desplegable></div>
      {vista === 'PUBLICIDAD' && <div className="correos-publicidad">
        <p>Publicidad y correo masivo. No cuenta en «sin tratar» y no se descarta sola: si ves un correo real aquí, cámbiale el tipo.</p>
        {puedeResponder && (confirmando
          ? <div className="correos-confirmar" role="alert" onKeyDown={e => { if (e.key === 'Escape') { e.stopPropagation(); setConfirmando(false); } }}>
            <span>¿Descartar la publicidad sin tratar de todos los buzones{contadores ? ` (hasta ${contadores.publicidad})` : ''}? Se pueden recuperar en Descartados.</span>
            <Boton variante="peligro" pequeno onClick={descartarPublicidad}>Sí, descartarla</Boton>
            <Boton variante="secundario" pequeno ref={cancelarDescarte} onClick={() => setConfirmando(false)}>Cancelar</Boton></div>
          : <Boton variante="secundario" pequeno disabled={!contadores?.publicidad || ocupado} onClick={() => setConfirmando(true)}>Descartar toda la publicidad nueva</Boton>)}
      </div>}

      <div className={`correos-cuerpo ${abierto ? 'con-lectura' : ''}`}>
        <div className="correos-lista">
          <ListaCorreos datos={lista.datos} cargando={lista.cargando} error={lista.error} seleccionId={abiertoId} hayBorrador={id => borradores.hay(id)} alAbrir={abrir}
            alReintentar={lista.recargar} mostrarCuenta={cuentasSync.length > 1 && !cuenta} textoVacio={filtrado ? 'Ningún correo coincide con los filtros.' : VACIO[vista]} />
          <Pagination page={pagina} totalPages={lista.datos?.totalPaginas || 1} total={lista.datos?.totalElementos ?? null} noun="correos" onPage={setPagina} />
        </div>
        <div className="correos-lectura" ref={panelLectura}>
          {!abiertoId ? <p className="empty-state">Elige un correo de la lista para leerlo.</p>
            : correo.error ? <div className="notice" role="alert">{correo.error} <Boton variante="texto" onClick={correo.recargar}>Reintentar</Boton></div>
            : !correo.detalle ? <div className="skeleton-rows" aria-busy="true"><span /><span /><span /></div>
            : <LecturaCorreo key={correo.detalle.id} detalle={correo.detalle} guardando={ocupado} puedeResponder={puedeResponder} puedeCotizar={puedeCotizar} puedeSeguimiento={puedeSeguimiento}
              ia={ia} cuentasEnvio={cuentasEnvio} borrador={borradores.leer(correo.detalle.id)} alCambiarBorrador={b => cambiarBorrador(correo.detalle!.id, b)}
              alActualizar={actualizar} alEnviado={(e, atendido) => { avisar({ tipo: 'ok', texto: `Respuesta enviada a ${e.para}.${atendido ? ' El correo quedó como atendido.' : ''}` }); correo.recargar(); refrescar(); }}
              alAbrirOtro={abrir} alVolver={volver} alCotizar={abrirCotizacion} alSeguimiento={abrirSeguimiento} alIrAClientes={irAClientes} />}
        </div>
      </div>
    </section>

    {redactando && <Drawer onClose={() => setRedactando(false)} title="Redactar correo" subtitle="Un correo nuevo, desde la cuenta que elijas." wide>
      <Compositor modo="nuevo" cuentas={cuentasEnvio} cuentaPorDefecto="" borrador={borradores.leer('nuevo') ?? { asunto: '', cuerpo: '', para: '' }}
        alCambiar={b => borradores.guardar('nuevo', b)} alEnviar={enviarNuevo} alCancelar={() => { cambiarBorrador('nuevo', null); setRedactando(false); }} />
    </Drawer>}
    {preguntando && <PreguntarBandeja ia={ia} cuentas={cuentasEnvio} alCerrar={() => setPreguntando(false)}
      alAbrirCorreo={id => { setPreguntando(false); abrir(id); }} />}
    {cotizar && <NuevaCotizacion puedeCrearClientes={puedeCrearClientes} inicial={cotizar} alCerrar={() => setCotizar(null)}
      alGuardar={q => { setCotizar(null); avisar({ tipo: 'ok', texto: `Cotización ${q.numero} creada. La ves en Cotizaciones.` }); tomarCorreo(); }} />}
    {tarea && <NewTask types={tarea.tipos} onSave={guardarTarea} onClose={() => setTarea(null)} busy={ocupado} error={errorTarea} inicial={tarea.inicial} />}
  </div>;
}
