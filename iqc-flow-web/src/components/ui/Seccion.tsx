import { useId, type ReactNode } from 'react';

/** Un bloque del formulario con su título. Es un grupo con nombre (lo anuncia el lector de pantalla) y una línea fina lo separa del anterior */
export function Seccion({ titulo, ayuda, children }: { titulo: string; ayuda?: string; children: ReactNode }) {
  const id = useId();
  return <section className="seccion-form" role="group" aria-labelledby={id}>
    <h3 id={id}>{titulo}</h3>
    {ayuda && <p className="field-help">{ayuda}</p>}
    {children}
  </section>;
}
