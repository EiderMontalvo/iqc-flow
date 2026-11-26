import { useMemo } from 'react';
import { TEMATICAS, type ClaveTematica } from './tematicas';

/** Pseudoaleatorio con semilla: el fondo es siempre el mismo para una temática (no «salta» entre visitas) */
function semilla(n: number) { let x = n; return () => { x = (x * 1664525 + 1013904223) % 4294967296; return x / 4294967296; }; }

/**
 * Fondo de temporada: íconos de la fecha esparcidos en una rejilla con un poco de desorden, en sus tintas y muy suaves.
 * Va detrás del contenido, quieto, y no recibe el puntero.
 */
/** enCajon: dentro de un cajón o formulario, más tenue y sin el hueco del título de pantalla */
export function FondoTematico({ clave, enCajon }: { clave: ClaveTematica; enCajon?: boolean }) {
  const t = TEMATICAS[clave];
  const piezas = useMemo(() => {
    const azar = semilla(clave.length * 97 + 13), lista = [];
    const COLUMNAS = 9, FILAS = 6;
    for (let f = 0; f < FILAS; f++) for (let c = 0; c < COLUMNAS; c++) {
      const i = f * COLUMNAS + c;
      // Arriba a la izquierda va el título de cada pantalla: ahí no se dibuja nada, para no pasar por detrás de las letras
      if (!enCajon && f === 0 && c < 5) { azar(); azar(); azar(); azar(); continue; }
      lista.push({
        i, Icono: t.iconos[i % t.iconos.length], color: t.colores[(i + f) % t.colores.length],
        x: (c + 0.15 + azar() * 0.7) / COLUMNAS * 100, y: (f + 0.15 + azar() * 0.7) / FILAS * 100,
        giro: Math.round((azar() - 0.5) * 50), tam: 22 + Math.round(azar() * 18),
      });
    }
    return lista;
  }, [clave, t, enCajon]);
  return <div className="fondo-tematico" aria-hidden>
    {piezas.map(({ i, Icono, color, x, y, giro, tam }) => <span key={i} style={{ left: `${x}%`, top: `${y}%`, color,
      transform: `translate(-50%, -50%) rotate(${giro}deg)` }}><Icono size={tam} strokeWidth={1.6} /></span>)}
  </div>;
}
