import { useState } from 'react';
import type { Punto } from '../../lib/api';
import { anioVisible, formatoNumero, marcasEje, periodo, useAncho } from './escala';

const MARGEN = { arriba: 22, derecha: 4, abajo: 40, izquierda: 34 }, GROSOR_MAXIMO = 24, RADIO = 4;

/** Columna con la punta redondeada (4 px) y la base recta, creciendo desde el eje */
function columna(x: number, y: number, ancho: number, alto: number) {
  if (alto <= 0) return '';
  const r = Math.min(RADIO, alto, ancho / 2), base = y + alto;
  return `M${x},${base}V${y + r}Q${x},${y} ${x + r},${y}H${x + ancho - r}Q${x + ancho},${y} ${x + ancho},${y + r}V${base}Z`;
}

/**
 * Columnas por periodo (meses, semanas). Una sola serie en el color de gráficos; sin leyenda (el título la nombra).
 * Rotula solo el valor máximo; el resto se lee en el eje, en el tooltip (mouse o teclado) y en la vista de tabla.
 */
export function GraficoColumnas({ datos, unidad, etiquetaEje, alto: ALTO = 220 }: { datos: Punto[]; unidad: [string, string]; etiquetaEje?: string; alto?: number }) {
  const [ref, ancho] = useAncho<HTMLDivElement>();
  const [activo, setActivo] = useState<number | null>(null);
  const maximo = Math.max(0, ...datos.map(d => d.cantidad));
  const { tope, marcas } = marcasEje(maximo);
  const interior = Math.max(0, ancho - MARGEN.izquierda - MARGEN.derecha), altoInterior = ALTO - MARGEN.arriba - MARGEN.abajo;
  const banda = datos.length ? interior / datos.length : 0;
  const grosor = Math.max(4, Math.min(GROSOR_MAXIMO, banda - 6));
  const y = (v: number) => MARGEN.arriba + altoInterior - (v / tope) * altoInterior;
  // Etiquetas del eje X (mes abreviado, el año debajo cuando cambia): todas si caben; si no, una de cada dos
  const cadaCuantas = banda >= 30 ? 1 : banda >= 16 ? 2 : 3;
  const periodos = datos.map((d, i) => periodo(d.clave, i));
  const indiceMaximo = maximo > 0 ? datos.findIndex(d => d.cantidad === maximo) : -1;
  const texto = (d: Punto, i: number) => `${periodos[i].larga}: ${formatoNumero.format(d.cantidad)} ${d.cantidad === 1 ? unidad[0] : unidad[1]}`;

  return <div className="grafico-columnas" ref={ref}>
    {ancho > 0 && <svg width={ancho} height={ALTO} role="group" aria-label={etiquetaEje}>
      {marcas.map(m => <g key={m} className="eje-y">
        <line x1={MARGEN.izquierda} x2={ancho - MARGEN.derecha} y1={y(m)} y2={y(m)} />
        <text x={MARGEN.izquierda - 8} y={y(m)} dy="0.32em" textAnchor="end">{formatoNumero.format(m)}</text>
      </g>)}
      {datos.map((d, i) => {
        const x = MARGEN.izquierda + banda * i + (banda - grosor) / 2, alto = y(0) - y(d.cantidad);
        return <g key={d.clave} className={activo === i ? 'barra activa' : 'barra'} tabIndex={0} role="img" aria-label={texto(d, i)}
          onPointerEnter={() => setActivo(i)} onPointerLeave={() => setActivo(null)} onFocus={() => setActivo(i)} onBlur={() => setActivo(null)}>
          {/* Zona sensible: toda la franja, no solo la columna pintada */}
          <rect className="zona" x={MARGEN.izquierda + banda * i} y={MARGEN.arriba} width={banda} height={altoInterior + MARGEN.abajo} />
          <path d={columna(x, y(d.cantidad), grosor, alto)} />
          {i === indiceMaximo && <text className="valor" x={x + grosor / 2} y={y(d.cantidad) - 6} textAnchor="middle">{formatoNumero.format(d.cantidad)}</text>}
          {i % cadaCuantas === 0 && <text className="eje-x" x={x + grosor / 2} y={ALTO - MARGEN.abajo + 16} textAnchor="middle">{periodos[i].corta}
            {anioVisible(periodos, datos.map(p => p.clave), i, cadaCuantas) && <tspan className="eje-anio" x={x + grosor / 2} dy="1.25em">{anioVisible(periodos, datos.map(p => p.clave), i, cadaCuantas)}</tspan>}</text>}
        </g>;
      })}
    </svg>}
    {activo !== null && datos[activo] && <div className="grafico-tooltip" role="presentation"
      style={{ left: MARGEN.izquierda + banda * activo + banda / 2, top: Math.max(0, y(datos[activo].cantidad) - 10) }}>
      <strong>{formatoNumero.format(datos[activo].cantidad)}</strong><span>{periodos[activo].larga}</span>
    </div>}
  </div>;
}
