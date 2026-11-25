import { useState, type KeyboardEvent, type PointerEvent } from 'react';
import type { Punto } from '../../lib/api';
import { anioVisible, formatoNumero, marcasEje, periodo, useAncho } from './escala';

const MARGEN = { arriba: 22, derecha: 16, abajo: 40, izquierda: 34 };

/**
 * Tendencia en el tiempo (meses, semanas): una serie como línea de 2 px con un velo del 10 % debajo.
 * Especificación de la skill de visualización: sin leyenda (el título nombra la serie), solo se rotulan el máximo y el
 * último punto; una línea vertical sigue al puntero y se ajusta al periodo más cercano, con tooltip. También se recorre
 * con las flechas del teclado, y la vista de tabla tiene todos los valores.
 */
export function GraficoArea({ datos, unidad, etiquetaEje, alto: ALTO = 220 }: { datos: Punto[]; unidad: [string, string]; etiquetaEje?: string; alto?: number }) {
  const [ref, ancho] = useAncho<HTMLDivElement>();
  const [activo, setActivo] = useState<number | null>(null);
  const maximo = Math.max(0, ...datos.map(d => d.cantidad));
  const { tope, marcas } = marcasEje(maximo);
  const interior = Math.max(0, ancho - MARGEN.izquierda - MARGEN.derecha), altoInterior = ALTO - MARGEN.arriba - MARGEN.abajo;
  const paso = datos.length > 1 ? interior / (datos.length - 1) : 0;
  const x = (i: number) => MARGEN.izquierda + paso * i;
  const y = (v: number) => MARGEN.arriba + altoInterior - (v / tope) * altoInterior;
  const linea = datos.map((d, i) => `${i ? 'L' : 'M'}${x(i)},${y(d.cantidad)}`).join('');
  const area = datos.length ? `${linea}L${x(datos.length - 1)},${y(0)}L${x(0)},${y(0)}Z` : '';
  // Mes abreviado (unos 26 px) con el año debajo cuando cambia: todas si caben; si no, una de cada dos o tres
  const cadaCuantas = paso >= 32 ? 1 : paso >= 18 ? 2 : 3;
  const periodos = datos.map((d, i) => periodo(d.clave, i));
  const indiceMaximo = maximo > 0 ? datos.findIndex(d => d.cantidad === maximo) : -1, ultimo = datos.length - 1;
  const texto = (d: Punto) => `${periodos[datos.indexOf(d)].larga}: ${formatoNumero.format(d.cantidad)} ${d.cantidad === 1 ? unidad[0] : unidad[1]}`;

  function seguir(e: PointerEvent<SVGSVGElement>) {
    const caja = e.currentTarget.getBoundingClientRect();
    const i = Math.round((e.clientX - caja.left - MARGEN.izquierda) / (paso || 1));
    setActivo(Math.max(0, Math.min(ultimo, i)));
  }
  function teclado(e: KeyboardEvent<SVGSVGElement>) {
    if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight' && e.key !== 'Home' && e.key !== 'End') return;
    e.preventDefault();
    setActivo(a => e.key === 'Home' ? 0 : e.key === 'End' ? ultimo
      : Math.max(0, Math.min(ultimo, (a ?? (e.key === 'ArrowLeft' ? ultimo + 1 : -1)) + (e.key === 'ArrowLeft' ? -1 : 1))));
  }
  const punto = activo !== null ? datos[activo] : null;

  return <div className="grafico-area" ref={ref}>
    {ancho > 0 && datos.length > 1 && <svg width={ancho} height={ALTO} tabIndex={0} role="img"
      aria-label={`${etiquetaEje ?? ''}. ${punto ? texto(punto) : 'Usa las flechas para recorrer los periodos.'}`}
      onPointerMove={seguir} onPointerLeave={() => setActivo(null)} onKeyDown={teclado} onBlur={() => setActivo(null)}>
      {marcas.map(m => <g key={m} className="eje-y">
        <line x1={MARGEN.izquierda} x2={ancho - MARGEN.derecha} y1={y(m)} y2={y(m)} />
        <text x={MARGEN.izquierda - 8} y={y(m)} dy="0.32em" textAnchor="end">{formatoNumero.format(m)}</text>
      </g>)}
      <path className="velo" d={area} />
      <path className="linea" d={linea} />
      {datos.map((d, i) => i % cadaCuantas === 0 || i === ultimo && x(ultimo) - x(ultimo - (ultimo % cadaCuantas)) >= 30
        ? <text key={d.clave} className="eje-x" x={x(i)} y={ALTO - MARGEN.abajo + 16} textAnchor="middle">{periodos[i].corta}
          {anioVisible(periodos, datos.map(p => p.clave), i, cadaCuantas) && <tspan className="eje-anio" x={x(i)} dy="1.25em">{anioVisible(periodos, datos.map(p => p.clave), i, cadaCuantas)}</tspan>}</text> : null)}
      {/* Rótulos selectivos: el máximo y el último periodo */}
      {[indiceMaximo, ultimo].filter((v, i, a) => v >= 0 && a.indexOf(v) === i && activo === null).map(i => <g key={i}>
        <circle className="punto" cx={x(i)} cy={y(datos[i].cantidad)} r={4} />
        <text className="valor" x={x(i)} y={y(datos[i].cantidad) - 10} textAnchor={i === ultimo ? 'end' : 'middle'}>{formatoNumero.format(datos[i].cantidad)}</text>
      </g>)}
      {punto && activo !== null && <g className="cursor">
        <line x1={x(activo)} x2={x(activo)} y1={MARGEN.arriba} y2={y(0)} />
        <circle className="punto" cx={x(activo)} cy={y(punto.cantidad)} r={5} />
      </g>}
    </svg>}
    {punto && activo !== null && <div className="grafico-tooltip" role="presentation"
      style={{ left: Math.min(Math.max(x(activo), 60), ancho - 60), top: Math.max(0, y(punto.cantidad) - 12) }}>
      <strong>{formatoNumero.format(punto.cantidad)}</strong><span>{periodos[activo].larga}</span>
    </div>}
  </div>;
}
