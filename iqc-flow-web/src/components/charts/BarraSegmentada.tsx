import { useState } from 'react';
import type { Punto } from '../../lib/api';
import { formatoNumero } from './escala';

/**
 * Un total partido en partes (≤ 6), leído de un vistazo: barra alta de 28 px, un bloque por parte con 2 px de aire,
 * su porcentaje dentro cuando cabe y una leyenda con los nombres y las cantidades. Los colores siguen a la categoría
 * (orden fijo de la paleta categórica, validada en claro y oscuro), nunca a su tamaño.
 */
export function BarraSegmentada({ datos, unidad }: { datos: Punto[]; unidad: [string, string] }) {
  const [activo, setActivo] = useState<string | null>(null);
  const total = datos.reduce((t, d) => t + d.cantidad, 0) || 1;
  const pct = (v: number) => Math.round((v / total) * 100);
  const texto = (d: Punto) => `${d.etiqueta}: ${formatoNumero.format(d.cantidad)} ${d.cantidad === 1 ? unidad[0] : unidad[1]} (${pct(d.cantidad)} %)`;
  return <div className="barra-segmentada">
    <div className="segmentos" role="list">
      {datos.map((d, i) => d.cantidad > 0 && <div key={d.clave} role="listitem" tabIndex={0} aria-label={texto(d)}
        className={`segmento serie-${i + 1}${activo === d.clave ? ' activo' : ''}`} style={{ flexGrow: d.cantidad }}
        onPointerEnter={() => setActivo(d.clave)} onPointerLeave={() => setActivo(null)} onFocus={() => setActivo(d.clave)} onBlur={() => setActivo(null)}>
        {pct(d.cantidad) >= 9 && <span aria-hidden>{pct(d.cantidad)} %</span>}
        {activo === d.clave && <div className="grafico-tooltip"><strong>{formatoNumero.format(d.cantidad)}</strong><span>{d.etiqueta} · {pct(d.cantidad)} %</span></div>}
      </div>)}
    </div>
    <ul className="leyenda">{datos.map((d, i) => <li key={d.clave}>
      <i className={`muestra serie-${i + 1}`} aria-hidden /><span>{d.etiqueta}</span><strong>{formatoNumero.format(d.cantidad)}</strong>
    </li>)}</ul>
  </div>;
}
