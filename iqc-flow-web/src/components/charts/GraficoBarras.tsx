import type { Punto } from '../../lib/api';
import { formatoNumero } from './escala';

/**
 * Barras horizontales ordenadas (partes de un total o categorías): una por fila, la cifra en la punta.
 * `ordenNatural`: respeta el orden que trae la serie (p. ej. prioridad Urgente → Baja) en lugar de ordenar por tamaño.
 */
export function GraficoBarras({ datos, unidad, ordenNatural }: { datos: Punto[]; unidad: [string, string]; ordenNatural?: boolean }) {
  const filas = ordenNatural ? datos : [...datos].sort((a, b) => b.cantidad - a.cantidad);
  const maximo = Math.max(1, ...filas.map(d => d.cantidad));
  return <ul className="grafico-barras">
    {filas.map(d => <li key={d.clave} tabIndex={0}
      aria-label={`${d.etiqueta}: ${formatoNumero.format(d.cantidad)} ${d.cantidad === 1 ? unidad[0] : unidad[1]}`}>
      <span className="nombre">{d.etiqueta}</span>
      <span className="pista"><span className="barra" style={{ width: `${(d.cantidad / maximo) * 100}%` }} /></span>
      <span className="cifra">{formatoNumero.format(d.cantidad)}</span>
    </li>)}
  </ul>;
}
