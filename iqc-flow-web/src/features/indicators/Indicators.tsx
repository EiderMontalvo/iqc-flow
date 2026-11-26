import { useEffect, useState } from 'react';
import { AlertTriangle } from 'lucide-react';
import { api, type Indicadores } from '../../lib/api';
import { errorText } from '../../lib/format';
import { PanelGrafico } from '../../components/charts/PanelGrafico';
import { DetalleGrafico } from './DetalleGrafico';
import { DibujoGrafico, definiciones, total, type Grafico } from './graficos';

/** Qué gráficos ve cada espacio de trabajo (ver SPACES en App.tsx) */
export type VistaIndicadores = 'direccion' | 'certificados' | 'operaciones' | 'comercial' | 'administracion';

/** En orden de importancia para cada rol: el primero es su pregunta principal y va arriba a la izquierda */
const POR_VISTA: Record<VistaIndicadores, Grafico[]> = {
  direccion: ['vencimientos', 'emisiones', 'normas', 'cartera', 'tareasPrioridad'],
  certificados: ['vencimientos', 'emisiones', 'personales', 'normas', 'esquemas'],
  operaciones: ['vencimientos', 'tareasTipo', 'tareasPrioridad', 'tareasSemana'],
  comercial: ['cartera', 'altas', 'vencimientos', 'tareasPrioridad'],
  administracion: ['emisiones', 'vencimientos', 'esquemas'],
};
/** Gráficos por categoría que con una sola categoría con datos pasan a una cifra en línea */
const UNA_CIFRA: Grafico[] = ['normas', 'esquemas', 'tareasTipo', 'cartera'];

/**
 * Gráficos del inicio de cada rol. Cada gráfico aparece solo si el usuario tiene permiso para los datos de su área;
 * al pulsar una tarjeta se abre su detalle (gráfico grande, resumen y tabla).
 */
export function Indicators({ vista }: { vista: VistaIndicadores }) {
  const [datos, setDatos] = useState<Indicadores | null>(null), [error, setError] = useState('');
  const [abierto, setAbierto] = useState<Grafico | null>(null);
  useEffect(() => {
    let activo = true;
    api.indicators().then(d => { if (activo) setDatos(d); }).catch(e => { if (activo) setError(errorText(e)); });
    return () => { activo = false; };
  }, []);

  if (error) return <div className="notice" role="alert"><AlertTriangle size={16} aria-hidden /> No se pudieron cargar los gráficos: {error}</div>;
  if (!datos) return <div className="indicadores" aria-busy="true" aria-label="Cargando gráficos">
    {POR_VISTA[vista].slice(0, 4).map(g => <div key={g} className="panel panel-grafico" aria-hidden>
      <div className="skeleton-rows"><span /><span /><span /></div></div>)}
  </div>;

  const defs = definiciones(datos);
  // Los que están en cero, y los de una sola categoría con datos, van al final, compactos y a todo el ancho
  const compacto = (g: Grafico) => total(defs[g]!.datos) === 0
    || (UNA_CIFRA.includes(g) && defs[g]!.datos.filter(p => p.cantidad > 0).length === 1);
  const disponibles = POR_VISTA[vista].filter(g => defs[g]);
  if (!disponibles.length) return null;
  const conDatos = disponibles.filter(g => !compacto(g));
  const compactos = [...disponibles.filter(g => compacto(g) && total(defs[g]!.datos) > 0), ...disponibles.filter(g => total(defs[g]!.datos) === 0)];
  // De dos en dos. Si quedan impares, uno va solo a todo el ancho (al final, para no dejar un hueco): mejor uno de
  // barras, medidores o segmentado, que a lo ancho se lee igual; uno de columnas o de área por mes quedaría con huecos
  // grandes entre sus marcas (las columnas no pasan de 24 px)
  const seLeeAncho = (g: Grafico) => defs[g]!.forma !== 'columnas' && defs[g]!.forma !== 'area';
  const solo = conDatos.length % 2 === 1 ? ([...conDatos].reverse().find(seLeeAncho) ?? conDatos[conDatos.length - 1]) : null;
  const orden = solo ? [...conDatos.filter(g => g !== solo), solo] : conDatos;
  const panel = (g: Grafico, ancho: boolean) => { const d = defs[g]!;
    return <PanelGrafico key={g} ancho={ancho} titulo={d.titulo} detalle={d.detalle} datos={d.datos} vacio={d.vacio} unica={d.unica}
      alAmpliar={() => setAbierto(g)}><DibujoGrafico d={d} /></PanelGrafico>; };
  return <>
    <div className="indicadores">
      {orden.map(g => panel(g, g === solo))}
      {compactos.map(g => panel(g, false))}
    </div>
    {abierto && defs[abierto] && <DetalleGrafico d={defs[abierto]!} cerrar={() => setAbierto(null)} />}
  </>;
}
