import { useId } from 'react';
import { FileUp } from 'lucide-react';

/**
 * Botón «Elegir PDF» en español (el control nativo dice «Choose File» según el idioma del navegador). El archivo se ve
 * enseguida en la vista previa; por eso aquí no se repite su nombre.
 */
export function ElegirPdf({ etiqueta = 'PDF', texto = 'Elegir PDF', requerido, alElegir }: {
  etiqueta?: string; texto?: string; requerido?: boolean; alElegir: (f: File | null) => void;
}) {
  const id = useId();
  return <div className="elegir-pdf">
    <span className="elegir-pdf-etiqueta" id={`${id}-e`}>{etiqueta}</span>
    <label className="secondary elegir-pdf-boton" htmlFor={id}><FileUp size={16} aria-hidden /> {texto}</label>
    <input id={id} type="file" accept="application/pdf" required={requerido} aria-labelledby={`${id}-e`} className="sr-only"
      onChange={e => alElegir(e.target.files?.[0] || null)} />
  </div>;
}
