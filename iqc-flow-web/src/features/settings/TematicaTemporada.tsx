import type { ReactNode } from 'react';
import { CalendarHeart, CircleOff } from 'lucide-react';
import { TEMATICAS, tematicaDeFecha, type ClaveTematica } from '../../app/tematica/tematicas';

interface Props { valor: string | undefined; ocupado: boolean; alElegir: (valor: string) => void }

/**
 * Temática visual de temporada para todos: ninguna, automática según el calendario o una fija. Cada opción muestra sus
 * íconos en sus tintas para elegir viéndola.
 */
export function TematicaTemporada({ valor, ocupado, alElegir }: Props) {
  const hoy = tematicaDeFecha();
  const opcion = (clave: string, nombre: string, detalle: string, muestra: ReactNode) =>
    <button key={clave} type="button" role="radio" aria-checked={valor === clave} disabled={ocupado}
      className={`tematica-opcion ${valor === clave ? 'elegida' : ''}`} onClick={() => alElegir(clave)}>
      <span className="tematica-muestra" aria-hidden>{muestra}</span>
      <strong>{nombre}</strong><span>{detalle}</span>
    </button>;
  return <div className="tematica" role="radiogroup" aria-label="Temática de temporada">
    <div className="panel">
      <h3>Cómo se elige</h3>
      <p className="config-nota">Cambia el fondo del panel de todos por uno blanco con íconos de la fecha y suma un saludo en la cabecera.</p>
      <div className="tematica-opciones">
        {opcion('NINGUNA', 'Ninguna', 'El panel de siempre', <CircleOff size={22} />)}
        {opcion('AUTOMATICA', 'Automática', hoy ? `Hoy toca: ${TEMATICAS[hoy].nombre}` : 'Se activa sola en cada fecha del calendario', <CalendarHeart size={22} />)}
      </div>
    </div>
    <div className="panel">
      <h3>Una fecha fija</h3>
      <p className="config-nota">Se queda puesta hasta que elijas otra. En orden del calendario.</p>
      <div className="tematica-opciones">
        {(Object.keys(TEMATICAS) as ClaveTematica[]).map(k => { const t = TEMATICAS[k];
          return opcion(k, t.nombre, t.cuando, [...new Set(t.iconos)].slice(0, 3).map((Icono, i) => <Icono key={i} size={20} style={{ color: t.colores[i % t.colores.length] }} />)); })}
      </div>
    </div>
  </div>;
}
