import { useId } from 'react';
import { DEGRADADO_IQC, SIMBOLO_IQC, VISTA_SIMBOLO } from '@iqc/diseno/simbolo';

/**
 * Fondo de la cabecera: negro con el símbolo de IQC en línea fina con el degradado de la marca.
 * En la portada (`animado`) el contorno se dibuja una vez y luego una luz recorre el anillo; todo se detiene con
 * «reducir movimiento» (ver `estilos/movimiento.css`). Es decorativo: queda oculto a los lectores de pantalla.
 */
export function Fondo({ animado }: { animado: boolean }) {
  const id = `g${useId().replace(/:/g, '')}`;
  return <div className={animado ? 'fondo portada' : 'fondo'} aria-hidden>
    <svg className="simbolo" viewBox={VISTA_SIMBOLO} preserveAspectRatio="xMidYMid meet">
      <defs>
        <linearGradient id={id} x1="0" y1="0" x2="1" y2="1">
          {DEGRADADO_IQC.map(p => <stop key={p.offset} offset={p.offset} stopColor={p.color} />)}
        </linearGradient>
      </defs>
      {SIMBOLO_IQC.map(d => <path key={d} d={d} className="relleno" fill={`url(#${id})`} />)}
      {SIMBOLO_IQC.map(d => <path key={d} d={d} className="trazo" fill="none" stroke={`url(#${id})`} pathLength={1} />)}
      <path d={SIMBOLO_IQC[0]} className="luz" fill="none" pathLength={1} />
    </svg>
  </div>;
}
