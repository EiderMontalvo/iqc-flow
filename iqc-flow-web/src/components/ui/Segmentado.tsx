import { useId } from 'react';

interface Opcion<T extends string> { valor: T; etiqueta: string }

/**
 * Elige una opción entre pocas (2 a 4) con todas a la vista: más rápido que un desplegable y sin abrir nada. Son radios
 * nativos agrupados, así que el teclado (flechas) y el lector de pantalla funcionan sin código extra. La opción elegida
 * se marca con el color de acción y un contorno, no solo con el fondo, para que se distinga también en el tema oscuro.
 */
export function Segmentado<T extends string>({ etiqueta, valor, opciones, onChange, ayuda, ocultarEtiqueta }: {
  etiqueta: string; valor: T; opciones: Opcion<T>[]; onChange: (valor: T) => void;
  /** En barras de filtros: la etiqueta sigue existiendo para el lector de pantalla, pero no se ve */
  ocultarEtiqueta?: boolean;
  /** Explica la elección o lo que provoca (p. ej. por qué aparecen más campos) */
  ayuda?: string;
}) {
  const nombre = useId(), idEtiqueta = `${nombre}-etiqueta`, idAyuda = `${nombre}-ayuda`;
  return <div className="campo">
    <span className={ocultarEtiqueta ? 'sr-only' : 'etiqueta-campo'} id={idEtiqueta}>{etiqueta}</span>
    <div className="segmentado" role="radiogroup" aria-labelledby={idEtiqueta} aria-describedby={ayuda ? idAyuda : undefined}>
      {opciones.map(o => <label key={o.valor} className="segmentado-opcion">
        <input type="radio" name={nombre} value={o.valor} checked={valor === o.valor} onChange={() => onChange(o.valor)} />
        <span>{o.etiqueta}</span>
      </label>)}
    </div>
    {ayuda && <span className="field-help" id={idAyuda}>{ayuda}</span>}
  </div>;
}
