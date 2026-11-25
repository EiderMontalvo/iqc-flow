import type { LucideProps } from 'lucide-react';

/**
 * Íconos de temporada que la librería no trae, con la misma firma que los de Lucide (size, style).
 * La bandera usa sus colores oficiales (rojo y blanco, sin escudo); la calabaza se dibuja en línea como el resto.
 */
export function BanderaPeru({ size = 24, style }: LucideProps) {
  const alto = Math.round(Number(size) * 0.7);
  return <svg width={size} height={alto} viewBox="0 0 30 20" style={style} aria-hidden>
    <rect x="0.5" y="0.5" width="29" height="19" rx="1.5" fill="#ffffff" stroke="#b4b9c4" />
    <rect x="0.5" y="0.5" width="10" height="19" rx="1.5" fill="#d91023" />
    <rect x="19.5" y="0.5" width="10" height="19" rx="1.5" fill="#d91023" />
    <rect x="8" y="0.5" width="3" height="19" fill="#d91023" /><rect x="19" y="0.5" width="3" height="19" fill="#d91023" />
  </svg>;
}

export function Calabaza({ size = 24, strokeWidth = 2, style }: LucideProps) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={strokeWidth}
    strokeLinecap="round" strokeLinejoin="round" style={style} aria-hidden>
    <path d="M12 6c-1-2 0-3.5 1.5-4" />
    <path d="M12 6C7 6 3 9 3 14s4 7 9 7 9-2 9-7-4-8-9-8Z" />
    <path d="M12 6c-2.5 0-4 3.5-4 8s1.5 7 4 7 4-2.5 4-7-1.5-8-4-8Z" />
  </svg>;
}
