import type { ReactNode } from 'react';
import { Check } from 'lucide-react';

interface Props {
  titulo: string; detalle: ReactNode; nota?: ReactNode;
  encendido: boolean; ocupado: boolean; guardado: boolean; bloqueado?: boolean;
  alCambiar: () => void;
}

/** Fila de un interruptor: título y explicación a la izquierda, confirmación de guardado y el interruptor a la derecha */
export function Interruptor({ titulo, detalle, nota, encendido, ocupado, guardado, bloqueado, alCambiar }: Props) {
  return <li>
    <div><strong>{titulo}</strong><span>{detalle}</span>{nota}</div>
    <span className="switch-state">{guardado && <Check size={14} aria-label="Guardado" />}</span>
    <button type="button" role="switch" aria-checked={encendido} aria-label={titulo} className={`switch ${encendido ? 'on' : ''}`}
      disabled={ocupado || bloqueado} onClick={alCambiar}><span /></button>
  </li>;
}
