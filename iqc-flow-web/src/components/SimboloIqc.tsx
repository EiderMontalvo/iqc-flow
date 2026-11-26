import { useId } from 'react';
import { DEGRADADO_IQC, SIMBOLO_IQC, VISTA_SIMBOLO } from '@iqc/diseno/simbolo';

/**
 * Símbolo de IQC en línea fina con el degradado del logo, del sistema de diseño compartido con el portal.
 * Es decorativo: siempre oculto a los lectores de pantalla.
 * `dibujar`: el contorno se traza una vez al aparecer y después una luz neón lo recorre en bucle, como en el portal de consultas
 * (solo en el acceso; el panel de trabajo no se anima).
 */
export function SimboloIqc({ className, dibujar }: { className?: string; dibujar?: boolean }) {
  const id = `g${useId().replace(/:/g, '')}`;
  return <svg className={['simbolo-iqc', dibujar && 'dibujar', className].filter(Boolean).join(' ')} viewBox={VISTA_SIMBOLO}
    preserveAspectRatio="xMidYMid meet" aria-hidden focusable="false">
    <defs>
      <linearGradient id={id} x1="0" y1="0" x2="1" y2="1">
        {DEGRADADO_IQC.map(p => <stop key={p.offset} offset={p.offset} stopColor={p.color} />)}
      </linearGradient>
    </defs>
    {SIMBOLO_IQC.map(d => <path key={d} d={d} className="relleno" fill={`url(#${id})`} />)}
    {SIMBOLO_IQC.map(d => <path key={d} d={d} className="trazo" fill="none" stroke={`url(#${id})`} pathLength={1} />)}
    {dibujar && <path d={SIMBOLO_IQC[0]} className="luz" fill="none" pathLength={1} />}
  </svg>;
}
