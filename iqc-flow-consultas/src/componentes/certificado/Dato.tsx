import { useId, useState, type ReactNode } from 'react';

interface Props {
  titulo: string;
  children: ReactNode;
  /** Ocupa todo el ancho de la grilla (alcance, descripción) */
  ancho?: boolean;
  /** Ocupa dos columnas y no parte el código en dos líneas */
  codigo?: boolean;
}

/** Par etiqueta/valor de la hoja. Si el valor está vacío no se dibuja nada, para no mostrar campos en blanco */
export function Dato({ titulo, children, ancho, codigo }: Props) {
  if (children == null || children === '' || children === false) return null;
  const clases = ['dato', ancho && 'ancho', codigo && 'es-codigo'].filter(Boolean).join(' ');
  return <div className={clases}><dt>{titulo}</dt><dd>{children}</dd></div>;
}

const LARGO_MAXIMO = 420;

/** Textos de varios párrafos (alcance, descripción): recortados a 4 líneas para que el veredicto y el PDF no queden lejos */
export function TextoLargo({ texto, etiqueta }: { texto: string; etiqueta: string }) {
  const largo = texto.length > LARGO_MAXIMO, [abierto, setAbierto] = useState(false), id = useId();
  return <>
    <span id={id} className={largo && !abierto ? 'alcance recortado' : 'alcance'}>{texto}</span>
    {largo && <button type="button" className="mostrar" aria-expanded={abierto} aria-controls={id} onClick={() => setAbierto(!abierto)}>
      {abierto ? 'Mostrar menos' : `Ver ${etiqueta} completo`}
    </button>}
  </>;
}
