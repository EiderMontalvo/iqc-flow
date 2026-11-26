import { useId, type ReactNode } from 'react';

/**
 * Un campo con su etiqueta y, aparte, su texto de ayuda. La ayuda va fuera de la etiqueta y se liga con `aria-describedby`:
 * dentro de la etiqueta, el lector de pantalla la leería como parte del nombre del campo (y otra vez como descripción).
 * El campo recibe sus atributos por función, así sirve para input, select o textarea.
 */
export function Campo({ etiqueta, ayuda, children }: {
  etiqueta: string; ayuda?: string;
  children: (atributos: { id: string; 'aria-describedby'?: string }) => ReactNode;
}) {
  const id = useId(), idAyuda = `${id}-ayuda`;
  return <div className="campo">
    <label htmlFor={id}>{etiqueta}</label>
    {children({ id, 'aria-describedby': ayuda ? idAyuda : undefined })}
    {ayuda && <span className="field-help" id={idAyuda}>{ayuda}</span>}
  </div>;
}
