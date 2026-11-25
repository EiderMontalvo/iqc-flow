import { useId, useMemo } from 'react';
import { CircleX, ShieldAlert, ShieldCheck, ShieldQuestion } from 'lucide-react';
import { roseta } from '../../lib/roseta';
import { situacion, type Tono } from '../../lib/situaciones';

const ICONOS: Record<Tono, typeof ShieldCheck> = { ok: ShieldCheck, mal: CircleX, alerta: ShieldAlert, neutro: ShieldQuestion };

/**
 * Sello del estado: anillo con la palabra del veredicto y la marca, roseta de seguridad e ícono.
 * Se «estampa» al aparecer (estilos/certificado.css). Para lectores de pantalla es una imagen con el estado.
 */
export function Sello({ estado, marca }: { estado: string; marca: string }) {
  const s = situacion(estado), id = useId().replace(/:/g, '');
  const Icono = ICONOS[s.tono];
  const anillo = `${s.palabra.toUpperCase()} • ${marca.toUpperCase()} • REGISTRO OFICIAL •`;
  const interior = useMemo(() => roseta(60, 60, 3.6, 9, 4, 5.4, 900), []);
  return <div className={`sello ${s.tono}`} role="img" aria-label={`Estado: ${s.palabra}`}>
    <svg viewBox="0 0 120 120" aria-hidden focusable="false">
      <defs><path id={`anillo-${id}`} d="M60,60 m-46,0 a46,46 0 1,1 92,0 a46,46 0 1,1 -92,0" /></defs>
      <circle cx="60" cy="60" r="58" className="borde" />
      <circle cx="60" cy="60" r="55" className="fino" />
      <text className="anillo"><textPath href={`#anillo-${id}`} textLength="286" lengthAdjust="spacingAndGlyphs">{anillo}</textPath></text>
      <circle cx="60" cy="60" r="38" className="fino" />
      <path d={interior} className="trama" />
      <Icono x={44} y={44} width={32} height={32} strokeWidth={1.75} className="icono" />
    </svg>
  </div>;
}
