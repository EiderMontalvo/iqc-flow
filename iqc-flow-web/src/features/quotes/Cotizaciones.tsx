import { useEffect, useMemo, useState } from 'react';
import { ChevronRight, CircleCheck, FilePen, Hourglass, Percent, Plus } from 'lucide-react';
import { api, type Page, type QuoteCounts, type QuoteState, type QuoteSummary } from '../../lib/api';
import { day, errorText } from '../../lib/format';
import { Pagination } from '../../components/Pagination';
import { SearchField } from '../../components/SearchField';
import { FilaCifras, TarjetaCifra } from '../../components/cifras/TarjetaCifra';
import { DetalleCotizacion } from './DetalleCotizacion';
import { NuevaCotizacion } from './NuevaCotizacion';
import { ESTADOS, EstadoCotizacion, SERVICIOS, dinero } from './textos';

type Filtro = '' | QuoteState | 'SIN_RESPUESTA';

/** Cotizaciones: cifras del embudo comercial, búsqueda, lista y el detalle con sus pasos */
export function Cotizaciones({ puedeGestionar, puedeCrearClientes }: { puedeGestionar: boolean; puedeCrearClientes: boolean }) {
  const [texto, setTexto] = useState(''), [buscado, setBuscado] = useState(''), [filtro, setFiltro] = useState<Filtro>('');
  const [pagina, setPagina] = useState(0), [recarga, setRecarga] = useState(0);
  const [datos, setDatos] = useState<Page<QuoteSummary> | null>(null), [cifras, setCifras] = useState<QuoteCounts | null>(null);
  const [abierta, setAbierta] = useState<string | null>(null), [creando, setCreando] = useState(false), [error, setError] = useState('');
  useEffect(() => { const t = window.setTimeout(() => setBuscado(texto.trim()), 300); return () => window.clearTimeout(t); }, [texto]);
  const consulta = useMemo(() => {
    const q = new URLSearchParams({ pagina: String(pagina), tamano: '20' });
    if (buscado) q.set('texto', buscado);
    if (filtro === 'SIN_RESPUESTA') q.set('sinRespuesta', 'true'); else if (filtro) q.set('estado', filtro);
    return q;
  }, [buscado, filtro, pagina]);
  useEffect(() => {
    let vigente = true;
    api.quotes(consulta).then(d => { if (vigente) { setDatos(d); setError(''); } }).catch(e => { if (vigente) setError(errorText(e)); });
    return () => { vigente = false; };
  }, [consulta, recarga]);
  useEffect(() => { api.quoteCounts().then(setCifras).catch(() => {}); }, [recarga]);
  const recargar = () => setRecarga(n => n + 1);
  const elegir = (f: Filtro) => { setFiltro(filtro === f ? '' : f); setPagina(0); };

  return <div className="screen-body">
    <div className="page-heading"><div><h1>Cotizaciones</h1><p>Propuestas a clientes: de borrador a enviada y la respuesta del cliente.</p></div>
      {puedeGestionar && <button className="primary" onClick={() => setCreando(true)}><Plus size={16} aria-hidden /> Nueva cotización</button>}</div>
    {error && <div className="notice" role="alert">{error}</div>}

    <FilaCifras etiqueta="Resumen de cotizaciones">
      <TarjetaCifra icono={FilePen} etiqueta="Borradores" valor={cifras?.borradores} detalle="Por terminar y enviar" tono="neutro" activa={filtro === 'BORRADOR'} alPulsar={() => elegir('BORRADOR')} />
      <TarjetaCifra icono={Hourglass} etiqueta="Sin respuesta" valor={cifras?.enviadasSinRespuesta}
        detalle={cifras ? `Enviadas hace ${cifras.diasSinRespuesta} días o más` : 'Enviadas sin respuesta'} tono="alerta" activa={filtro === 'SIN_RESPUESTA'} alPulsar={() => elegir('SIN_RESPUESTA')} />
      <TarjetaCifra icono={CircleCheck} etiqueta="Aceptadas este mes" valor={cifras?.aceptadasMes} detalle={cifras ? `${cifras.emitidasMes} enviadas este mes` : '…'} tono="ok" activa={filtro === 'ACEPTADA'} alPulsar={() => elegir('ACEPTADA')} />
      <TarjetaCifra icono={Percent} etiqueta="Conversión" valor={cifras?.conversion12Meses} detalle={cifras?.conversion12Meses == null ? 'Aún sin respuestas en 12 meses' : '% aceptadas de las respondidas, 12 meses'} />
    </FilaCifras>

    <section className="list-panel">
      <div className="list-toolbar">
        <SearchField value={texto} onChange={v => { setTexto(v); setPagina(0); }} placeholder="Número, razón social o RUC" label="Buscar cotizaciones" />
        <select aria-label="Estado" value={filtro} onChange={e => { setFiltro(e.target.value as Filtro); setPagina(0); }}>
          <option value="">Todos los estados</option><option value="SIN_RESPUESTA">Enviadas sin respuesta</option>
          {(Object.keys(ESTADOS) as QuoteState[]).map(k => <option key={k} value={k}>{ESTADOS[k]}</option>)}</select>
      </div>
      <div className="table-wrap"><table>
        <thead><tr><th>Cotización</th><th>Cliente</th><th>Normas</th><th className="num">Total</th><th>Estado</th><th><span className="sr-only">Abrir</span></th></tr></thead>
        <tbody>{datos?.contenido.map(c => <tr key={c.id} className="fila-clic" onClick={() => setAbierta(c.id)}>
          <td><strong className="mono">{c.numero}</strong><small>{day(c.fechaEmision)} · {SERVICIOS[c.tipoServicio]}</small></td>
          <td><strong>{c.cliente.razonSocial}</strong><small>{c.cliente.numeroDocumento || 'sin documento'}</small></td>
          <td>{c.normas.join(', ') || '—'}</td>
          <td className="num tabular">{dinero(c.total, c.moneda)}</td>
          <td><EstadoCotizacion estado={c.estado} dias={c.diasSinRespuesta} /></td>
          <td><button className="row-link" onClick={e => { e.stopPropagation(); setAbierta(c.id); }} aria-label={`Abrir ${c.numero}`}><ChevronRight size={16} aria-hidden /></button></td>
        </tr>)}</tbody></table></div>
      <div className="mobile-list">{datos?.contenido.map(c => <button className="mobile-row" key={c.id} onClick={() => setAbierta(c.id)}>
        <span className="mobile-row-main"><strong>{c.cliente.razonSocial}</strong><EstadoCotizacion estado={c.estado} dias={c.diasSinRespuesta} /></span>
        <span className="mobile-row-meta">{c.numero} · {dinero(c.total, c.moneda)}</span></button>)}</div>
      {datos && !datos.contenido.length && <p className="empty-state">{buscado || filtro ? 'Ninguna cotización coincide con la búsqueda.' : 'Aún no hay cotizaciones. Crea la primera con «Nueva cotización».'}</p>}
      <Pagination page={pagina} totalPages={datos?.totalPaginas ?? 1} total={datos?.totalElementos ?? null} noun="cotizaciones" onPage={setPagina} />
    </section>

    {creando && <NuevaCotizacion puedeCrearClientes={puedeCrearClientes} alCerrar={() => setCreando(false)}
      alGuardar={q => { setCreando(false); recargar(); setAbierta(q.id); }} />}
    {abierta && <DetalleCotizacion key={abierta} id={abierta} puedeGestionar={puedeGestionar} alCerrar={() => setAbierta(null)} alCambiar={recargar} alAbrir={setAbierta} />}
  </div>;
}
