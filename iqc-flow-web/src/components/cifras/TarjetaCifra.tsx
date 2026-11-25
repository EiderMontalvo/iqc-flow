import type { ComponentType, ReactNode } from 'react';
import type { LucideProps } from 'lucide-react';
import { number } from '../../lib/format';

export type TonoCifra = 'mal' | 'alerta' | 'ok' | 'accion' | 'neutro';

interface Props {
  icono: ComponentType<LucideProps>;
  etiqueta: string;
  valor: number | null | undefined;
  /** Qué cuenta la cifra, en una línea */
  detalle: string;
  tono?: TonoCifra;
  /** Tarjeta usada como filtro: marca la seleccionada */
  activa?: boolean;
  alPulsar?: () => void;
}

/**
 * Cifra destacada con su ícono, etiqueta y una línea que explica qué cuenta. Todas con el mismo fondo: el tono solo tiñe el ícono. Si recibe `alPulsar` es un botón (lleva a
 * la lista o aplica el filtro); con `activa` funciona como filtro seleccionable (aria-pressed).
 */
export function TarjetaCifra({ icono: Icono, etiqueta, valor, detalle, tono = 'accion', activa, alPulsar }: Props) {
  const clase = ['tarjeta-cifra', `tono-${tono}`, activa && 'activa'].filter(Boolean).join(' ');
  const contenido = <>
    <span className="tarjeta-cifra-cabeza"><span className="tarjeta-cifra-icono" aria-hidden><Icono size={18} /></span>{etiqueta}</span>
    <strong className="tarjeta-cifra-valor">{number(valor)}</strong>
    <span className="tarjeta-cifra-detalle">{detalle}</span>
  </>;
  return alPulsar
    ? <button type="button" className={clase} onClick={alPulsar} aria-pressed={activa === undefined ? undefined : activa}>{contenido}</button>
    : <div className={clase}>{contenido}</div>;
}

/** Fila de tarjetas de cifra: cuatro columnas en escritorio, dos en tableta y celular */
export function FilaCifras({ etiqueta, children }: { etiqueta: string; children: ReactNode }) {
  return <section className="fila-cifras" aria-label={etiqueta}>{children}</section>;
}
