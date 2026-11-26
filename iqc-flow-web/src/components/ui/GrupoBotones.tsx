import { useId } from 'react';

interface Opcion<T extends string> { valor: T; etiqueta: string; titulo?: string }

/**
 * Elegir entre pocas opciones cuando elegir GUARDA algo: cada una es un botón que se activa con Enter, Espacio o clic. A diferencia
 * de `Segmentado` (radios), mover el foco con las flechas no cambia nada: una flecha nunca debe modificar un dato sin pedirlo.
 * Se ve igual que el segmentado; la elegida lleva `aria-pressed`.
 */
export function GrupoBotones<T extends string>({ etiqueta, valor, opciones, onElegir, deshabilitado }: {
  etiqueta: string; valor: T; opciones: Opcion<T>[]; onElegir: (valor: T) => void; deshabilitado?: boolean;
}) {
  const id = useId();
  return <div className="campo">
    <span className="etiqueta-campo" id={id}>{etiqueta}</span>
    <div className="segmentado" role="group" aria-labelledby={id}>
      {opciones.map(o => <button key={o.valor} type="button" className="segmentado-boton" aria-pressed={valor === o.valor} title={o.titulo}
        disabled={deshabilitado} onClick={() => { if (o.valor !== valor) onElegir(o.valor); }}>{o.etiqueta}</button>)}
    </div>
  </div>;
}
