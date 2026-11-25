import type { Indicadores, Punto } from '../../lib/api';
import { formatoNumero } from '../../components/charts/escala';
import { BarraSegmentada } from '../../components/charts/BarraSegmentada';
import { GraficoArea } from '../../components/charts/GraficoArea';
import { GraficoBarras } from '../../components/charts/GraficoBarras';
import { GraficoColumnas } from '../../components/charts/GraficoColumnas';
import { ListaMedidores } from '../../components/charts/ListaMedidores';

export type Grafico = 'vencimientos' | 'emisiones' | 'personales' | 'normas' | 'esquemas' | 'tareasTipo' | 'tareasPrioridad'
  | 'tareasSemana' | 'cartera' | 'altas';

/** Forma del gráfico según su pregunta (DESIGN.md, Gráficos) */
export type Forma = 'columnas' | 'area' | 'barras' | 'segmentada' | 'medidores';

export interface DefinicionGrafico {
  titulo: string;
  detalle: string;
  datos: Punto[];
  forma: Forma;
  unidad: [string, string];
  /** Encabezado de la primera columna en el detalle (Mes, Norma, Prioridad…) */
  categoria: string;
  vacio: string;
  /** Series en el tiempo: el detalle muestra promedio y variación entre periodos */
  temporal?: boolean;
  /** Gráficos por categoría con una sola categoría con datos: se muestra la cifra en una línea */
  unica?: (p: Punto) => string;
  etiquetaEje?: string;
}

export const total = (serie: Punto[]) => serie.reduce((t, p) => t + p.cantidad, 0);
const n = (v: number) => formatoNumero.format(v);
const CERT: [string, string] = ['certificado', 'certificados'], TAREA: [string, string] = ['seguimiento', 'seguimientos'];
const CLIENTE: [string, string] = ['cliente', 'clientes'];

/** Definición de cada gráfico con los datos de la API; null si el usuario no tiene permiso para esa área */
export function definiciones({ certificados: c, seguimientos: s, clientes: k }: Indicadores): Record<Grafico, DefinicionGrafico | null> {
  const ambito = s?.deTodos ? 'de todo el equipo' : 'a tu cargo';
  return {
    vencimientos: c && { titulo: 'Vencimientos por mes', forma: 'columnas', datos: c.vencimientosPorMes, unidad: CERT, categoria: 'Mes', temporal: true,
      detalle: `${n(total(c.vencimientosPorMes))} certificados vigentes vencen en los próximos 12 meses`,
      vacio: 'Ningún certificado vigente vence en los próximos 12 meses.', etiquetaEje: 'Certificados que vencen cada mes' },
    emisiones: c && { titulo: 'Certificados emitidos', forma: 'area', datos: c.emisionesPorMes, unidad: CERT, categoria: 'Mes', temporal: true,
      detalle: `${n(total(c.emisionesPorMes))} en los últimos 12 meses`,
      vacio: 'No se emitieron certificados empresariales en los últimos 12 meses.', etiquetaEje: 'Certificados emitidos cada mes' },
    personales: c && { titulo: 'Certificados personales', forma: 'area', datos: c.personalesPorMes, unidad: CERT, categoria: 'Mes', temporal: true,
      detalle: `${n(total(c.personalesPorMes))} emitidos en los últimos 12 meses`,
      vacio: 'No se emitieron certificados personales en los últimos 12 meses.', etiquetaEje: 'Certificados personales emitidos cada mes' },
    normas: c && { titulo: 'Vigentes por norma', forma: 'barras', datos: c.vigentesPorNorma, unidad: CERT, categoria: 'Norma',
      detalle: `${n(total(c.vigentesPorNorma))} certificados empresariales vigentes`, unica: p => `vigentes, todos de ${p.etiqueta}`,
      vacio: 'No hay certificados empresariales vigentes.' },
    esquemas: c && { titulo: 'Vigentes por acreditación', forma: 'barras', datos: c.vigentesPorEsquema, unidad: CERT, categoria: 'Acreditación',
      detalle: 'Certificados empresariales vigentes según su esquema', unica: p => `vigentes, todos de ${p.etiqueta}`,
      vacio: 'No hay certificados empresariales vigentes.' },
    tareasTipo: s && { titulo: 'Seguimientos abiertos por tipo', forma: 'barras', datos: s.abiertasPorTipo, unidad: TAREA, categoria: 'Tipo',
      detalle: `${n(total(s.abiertasPorTipo))} abiertos ${ambito}`, unica: p => `abiertos, todos de tipo «${p.etiqueta}»`,
      vacio: `No hay seguimientos abiertos ${ambito}.` },
    tareasPrioridad: s && { titulo: 'Seguimientos por prioridad', forma: 'medidores', datos: s.abiertasPorPrioridad, unidad: TAREA, categoria: 'Prioridad',
      detalle: `${n(total(s.abiertasPorPrioridad))} abiertos ${ambito}, de urgente a baja`, vacio: `No hay seguimientos abiertos ${ambito}.` },
    tareasSemana: s && { titulo: 'Seguimientos completados', forma: 'area', datos: s.completadasPorSemana, unidad: TAREA, categoria: 'Semana', temporal: true,
      detalle: `${n(total(s.completadasPorSemana))} en las últimas 8 semanas, ${ambito}`,
      vacio: `No se completaron seguimientos ${ambito} en las últimas 8 semanas.`, etiquetaEje: 'Seguimientos completados cada semana' },
    cartera: k && { titulo: 'Cartera por estado', forma: 'segmentada', datos: k.porEstado, unidad: CLIENTE, categoria: 'Estado',
      detalle: `${n(total(k.porEstado))} clientes y prospectos sin archivar`, unica: p => `clientes, todos ${p.etiqueta.toLowerCase()}`,
      vacio: 'Todavía no hay clientes registrados.' },
    altas: k && { titulo: 'Clientes nuevos por mes', forma: 'area', datos: k.altasPorMes, unidad: CLIENTE, categoria: 'Mes', temporal: true,
      detalle: `${n(total(k.altasPorMes))} registrados en IQC Flow en los últimos 12 meses`,
      vacio: 'Aún no hay clientes nuevos registrados en IQC Flow. Los importados del panel anterior no cuentan como altas.', etiquetaEje: 'Clientes nuevos cada mes' },
  };
}

/** Dibuja la forma del gráfico; `alto` agranda los de eje (columnas y área) en el detalle */
export function DibujoGrafico({ d, alto }: { d: DefinicionGrafico; alto?: number }) {
  switch (d.forma) {
    case 'columnas': return <GraficoColumnas datos={d.datos} unidad={d.unidad} etiquetaEje={d.etiquetaEje} alto={alto} />;
    case 'area': return <GraficoArea datos={d.datos} unidad={d.unidad} etiquetaEje={d.etiquetaEje} alto={alto} />;
    case 'barras': return <GraficoBarras datos={d.datos} unidad={d.unidad} />;
    case 'segmentada': return <BarraSegmentada datos={d.datos} unidad={d.unidad} />;
    case 'medidores': return <ListaMedidores datos={d.datos} unidad={d.unidad} />;
  }
}
