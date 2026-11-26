import { useEffect, useMemo, useState } from 'react';
import { ChevronRight, Plus } from 'lucide-react';
import { api, type LetterSummary, type Page } from '../../lib/api';
import { day, errorText } from '../../lib/format';
import { Pagination } from '../../components/Pagination';
import { SearchField } from '../../components/SearchField';
import { Boton } from '../../components/ui/Boton';
import { NuevaCarta } from './NuevaCarta';
import { VerCarta } from './VerCarta';

/** Cartas a clientes: las emitidas (con su número CAR-AAAA-0001) y la redacción de una nueva desde las plantillas de IQC */
export function Cartas({ puedeGenerar }: { puedeGenerar: boolean }) {
  const [texto, setTexto] = useState(''), [buscado, setBuscado] = useState(''), [pagina, setPagina] = useState(0), [recarga, setRecarga] = useState(0);
  const [datos, setDatos] = useState<Page<LetterSummary> | null>(null), [error, setError] = useState('');
  const [creando, setCreando] = useState(false), [abierta, setAbierta] = useState<LetterSummary | null>(null);
  useEffect(() => { const t = window.setTimeout(() => setBuscado(texto.trim()), 300); return () => window.clearTimeout(t); }, [texto]);
  const consulta = useMemo(() => {
    const q = new URLSearchParams({ pagina: String(pagina), tamano: '20' });
    if (buscado) q.set('texto', buscado);
    return q;
  }, [buscado, pagina]);
  useEffect(() => {
    let vigente = true;
    api.letters(consulta).then(d => { if (vigente) { setDatos(d); setError(''); } }).catch(e => { if (vigente) setError(errorText(e)); });
    return () => { vigente = false; };
  }, [consulta, recarga]);

  return <div className="screen-body">
    <div className="page-heading"><div><h1>Cartas</h1><p>Cartas a clientes con el membrete de IQC y la firma de quien las emite.</p></div>
      {puedeGenerar && <Boton variante="primario" onClick={() => setCreando(true)}><Plus size={16} aria-hidden /> Nueva carta</Boton>}</div>
    {error && <div className="notice" role="alert">{error}</div>}

    <section className="list-panel">
      <div className="list-toolbar">
        <SearchField value={texto} onChange={v => { setTexto(v); setPagina(0); }} placeholder="Número, razón social o RUC" label="Buscar cartas" />
      </div>
      <div className="table-wrap"><table>
        <thead><tr><th>Carta</th><th>Cliente</th><th>Asunto</th><th>Firma</th><th><span className="sr-only">Abrir</span></th></tr></thead>
        <tbody>{datos?.contenido.map(c => <tr key={c.id} className="fila-clic" onClick={() => setAbierta(c)}>
          <td><strong className="mono">{c.numero}</strong><small>{day(c.fecha)} · {c.tipoNombre}</small></td>
          <td><strong>{c.cliente.nombre || '—'}</strong></td>
          <td className="celda-asunto">{c.asunto}</td>
          <td>{c.firmante || '—'}</td>
          <td><button className="row-link" onClick={e => { e.stopPropagation(); setAbierta(c); }} aria-label={`Abrir ${c.numero}`}><ChevronRight size={16} aria-hidden /></button></td>
        </tr>)}</tbody></table></div>
      <div className="mobile-list">{datos?.contenido.map(c => <button className="mobile-row" key={c.id} onClick={() => setAbierta(c)}>
        <span className="mobile-row-main"><strong>{c.cliente.nombre || '—'}</strong><span>{c.tipoNombre}</span></span>
        <span className="mobile-row-meta">{c.numero} · {day(c.fecha)}</span></button>)}</div>
      {datos && !datos.contenido.length && <p className="empty-state">{buscado ? 'Ninguna carta coincide con la búsqueda.'
        : puedeGenerar ? 'Aún no hay cartas. Redacta la primera con «Nueva carta».' : 'Aún no se ha emitido ninguna carta.'}</p>}
      <Pagination page={pagina} totalPages={datos?.totalPaginas ?? 1} total={datos?.totalElementos ?? null} noun="cartas" onPage={setPagina} />
    </section>

    {creando && <NuevaCarta alCerrar={() => setCreando(false)} alEmitir={c => { setCreando(false); setRecarga(n => n + 1); setAbierta(c); }} />}
    {abierta && <VerCarta carta={abierta} puedeDescargarWord={puedeGenerar} alCerrar={() => setAbierta(null)} />}
  </div>;
}
