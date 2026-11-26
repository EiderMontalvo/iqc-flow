import type { ReactNode } from 'react';
import { ChevronRight } from 'lucide-react';

/**
 * Esconde lo que casi nunca hace falta tocar, sin quitarlo: `<details>` nativo (teclado y lectores de pantalla ya lo
 * entienden). Es controlable (`abierto`) para poder abrirlo desde fuera, p. ej. cuando un campo que contiene falla la
 * validación: un campo inválido dentro de un bloque cerrado no se puede enfocar y el envío fallaría sin decir nada.
 */
export function Desplegable({ titulo, abierto, alCambiar, children }: {
  titulo: string; abierto?: boolean; alCambiar?: (abierto: boolean) => void; children: ReactNode;
}) {
  return <details className="desplegable" open={abierto} onToggle={e => alCambiar?.(e.currentTarget.open)}>
    <summary><ChevronRight size={16} aria-hidden /><span>{titulo}</span></summary>
    <div className="desplegable-cuerpo">{children}</div>
  </details>;
}
