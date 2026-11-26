import type { ComponentType, ReactNode } from 'react';
import { ArrowRight, type LucideProps } from 'lucide-react';
import { number } from '../../../lib/format';
import { Boton } from '../../../components/ui/Boton';

export interface DatoTipo { etiqueta: string; valor: ReactNode; tono?: 'alerta' | 'mal' | 'neutro'; alPulsar?: () => void }

/**
 * Tarjeta de un tipo de certificado en el Resumen: título con su ícono, la cifra principal y una fila de datos
 * separados por filetes. Los datos con `alPulsar` abren la lista ya filtrada.
 */
export function TarjetaTipo({ icono: Icono, titulo, valor, unidad, datos, alAbrir }: {
  icono: ComponentType<LucideProps>; titulo: string; valor: number | null | undefined; unidad: string; datos: DatoTipo[]; alAbrir: () => void;
}) {
  return <article className="tarjeta-tipo">
    <header>
      <span className="tarjeta-tipo-icono" aria-hidden><Icono size={20} /></span>
      <h2>{titulo}</h2>
      <Boton type="button" variante="texto" onClick={alAbrir}>Ver todos <ArrowRight size={14} aria-hidden /></Boton>
    </header>
    <p className="tarjeta-tipo-cifra"><strong>{number(valor)}</strong> {unidad}</p>
    <dl className="tarjeta-tipo-datos">{datos.map(d => <div key={d.etiqueta} className={d.tono ? `tono-${d.tono}` : undefined}>
      <dt>{d.etiqueta}</dt>
      <dd>{d.alPulsar ? <button type="button" className="enlace-cifra" onClick={d.alPulsar}>{d.valor}</button> : d.valor}</dd>
    </div>)}</dl>
  </article>;
}
