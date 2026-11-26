import { useId, type ReactNode } from 'react';
import { Maximize2 } from 'lucide-react';
import type { Punto } from '../../lib/api';
import { formatoNumero } from './escala';
import { BotonIcono } from '../../components/ui/Boton';

interface Props {
  titulo: string;
  /** Qué mide y en qué periodo; con el total cuando ayuda */
  detalle: string;
  datos: Punto[];
  /** Texto cuando toda la serie está en cero: explica por qué y qué esperar */
  vacio: string;
  ancho?: boolean;
  /**
   * Gráficos por categoría: cuando solo una categoría tiene datos, un gráfico de una barra no dice nada (antipatrón de
   * la skill de visualización). Se muestra la cifra en una línea con este texto: recibe la categoría única.
   */
  unica?: (p: Punto) => string;
  /** Abre el detalle del gráfico: se pulsa la tarjeta entera o el botón de ampliar */
  alAmpliar?: () => void;
  children: ReactNode;
}

/** Marco común de los gráficos: título, detalle, botón para ampliar y el estado vacío */
export function PanelGrafico({ titulo, detalle, datos, vacio, ancho, unica, alAmpliar, children }: Props) {
  const id = useId();
  const sinDatos = datos.every(d => d.cantidad === 0);
  const conDatos = datos.filter(d => d.cantidad > 0), soloUna = unica && conDatos.length === 1 ? conDatos[0] : null;
  const ampliable = !!alAmpliar && !sinDatos;
  const clase = ['panel', 'panel-grafico', ancho && !sinDatos && !soloUna && 'ancho', sinDatos && 'vacio', soloUna && 'compacto',
    ampliable && 'ampliable'].filter(Boolean).join(' ');
  return <section className={clase} aria-labelledby={id} onClick={ampliable ? alAmpliar : undefined}>
    <div className="panel-title">
      <div><h2 id={id}>{titulo}</h2><p>{detalle}</p></div>
      {ampliable && <BotonIcono etiqueta={`Ver detalle de ${titulo}`} icono={Maximize2} tamano={16} type="button" onClick={e => { e.stopPropagation(); alAmpliar!(); }}
        title="Ver detalle" />}
    </div>
    {sinDatos ? <p className="grafico-vacio">{vacio}</p>
      : soloUna && unica ? <p className="grafico-cifra"><strong>{formatoNumero.format(soloUna.cantidad)}</strong> {unica(soloUna)}</p>
      : children}
  </section>;
}
