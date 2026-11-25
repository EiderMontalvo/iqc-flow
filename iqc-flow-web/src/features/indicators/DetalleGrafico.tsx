import { useEffect } from 'react';
import { X } from 'lucide-react';
import { formatoNumero, periodo } from '../../components/charts/escala';
import { DibujoGrafico, total, type DefinicionGrafico } from './graficos';

const n = (v: number) => formatoNumero.format(v);
const pct = (v: number, t: number) => t ? `${Math.round((v / t) * 100)} %` : '—';

/**
 * Detalle de un gráfico al pulsar su tarjeta: el gráfico más grande, las cifras que lo resumen y la tabla con cada
 * valor (participación y, en series de tiempo, la variación respecto del periodo anterior).
 */
export function DetalleGrafico({ d, cerrar }: { d: DefinicionGrafico; cerrar: () => void }) {
  useEffect(() => {
    const escape = (e: KeyboardEvent) => { if (e.key === 'Escape') cerrar(); };
    document.addEventListener('keydown', escape);
    return () => document.removeEventListener('keydown', escape);
  }, [cerrar]);
  const suma = total(d.datos), con = d.datos.filter(p => p.cantidad > 0);
  const mayor = d.datos.reduce((m, p) => p.cantidad > m.cantidad ? p : m, d.datos[0]);
  const nombre = (clave: string, i: number) => d.temporal ? periodo(clave, i).larga : d.datos[i].etiqueta;
  const indiceMayor = d.datos.indexOf(mayor);
  const resumen: Array<[string, string]> = d.temporal
    ? [['Total', n(suma)], ['Promedio por periodo', n(Math.round(suma / Math.max(1, d.datos.length)))],
       ['Periodo más alto', mayor?.cantidad ? `${nombre(mayor.clave, indiceMayor)} (${n(mayor.cantidad)})` : '—'],
       ['Periodos con datos', `${con.length} de ${d.datos.length}`]]
    : [['Total', n(suma)], ['Mayor', mayor?.cantidad ? `${mayor.etiqueta} (${pct(mayor.cantidad, suma)})` : '—'],
       ['Categorías con datos', `${con.length} de ${d.datos.length}`]];

  return <div className="dialog-backdrop" onMouseDown={e => { if (e.target === e.currentTarget) cerrar(); }}>
    <section className="dialog detalle-grafico" role="dialog" aria-modal="true" aria-labelledby="detalle-grafico-titulo">
      <div className="dialog-header"><div><h2 id="detalle-grafico-titulo">{d.titulo}</h2><p>{d.detalle}</p></div>
        <button type="button" className="icon-button" onClick={cerrar} aria-label="Cerrar"><X size={18} aria-hidden /></button></div>
      <dl className="detalle-grafico-resumen">{resumen.map(([t, v]) => <div key={t}><dt>{t}</dt><dd>{v}</dd></div>)}</dl>
      <div className="detalle-grafico-dibujo">{suma ? <DibujoGrafico d={d} alto={300} /> : <p className="grafico-vacio">{d.vacio}</p>}</div>
      <div className="table-wrap"><table className="tabla-grafico">
        <thead><tr><th>{d.categoria}</th><th className="num">Cantidad</th><th className="num">Del total</th>{d.temporal && <th className="num">Frente al anterior</th>}</tr></thead>
        <tbody>{d.datos.map((p, i) => { const previo = i > 0 ? d.datos[i - 1].cantidad : null, cambio = previo === null ? null : p.cantidad - previo;
          return <tr key={p.clave}><td>{nombre(p.clave, i)}</td><td className="num">{n(p.cantidad)}</td><td className="num">{pct(p.cantidad, suma)}</td>
            {d.temporal && <td className={`num ${cambio && cambio > 0 ? 'sube' : cambio && cambio < 0 ? 'baja' : ''}`}>{cambio === null ? '—' : cambio === 0 ? 'Igual' : `${cambio > 0 ? '+' : '−'}${n(Math.abs(cambio))}`}</td>}</tr>; })}</tbody>
      </table></div>
    </section>
  </div>;
}
