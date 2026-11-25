import { useEffect, useRef, useState } from 'react';

/**
 * Marcas del eje Y en números redondos (pasos de 1, 2 o 5 × 10ⁿ), unas cuatro.
 * Devuelve el tope del eje y las marcas, siempre desde 0.
 */
export function marcasEje(maximo: number, cuantas = 4): { tope: number; marcas: number[] } {
  if (maximo <= 0) return { tope: 1, marcas: [0, 1] };
  const bruto = maximo / cuantas, potencia = 10 ** Math.floor(Math.log10(bruto));
  const paso = [1, 2, 5, 10].map(f => f * potencia).find(p => p >= bruto) ?? 10 * potencia;
  const pasoEntero = Math.max(1, Math.round(paso));
  const tope = Math.ceil(maximo / pasoEntero) * pasoEntero;
  const marcas: number[] = [];
  for (let v = 0; v <= tope; v += pasoEntero) marcas.push(v);
  return { tope, marcas };
}

/** Ancho real del contenedor (los gráficos se dibujan a su tamaño, sin escalar el texto) */
export function useAncho<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const [ancho, setAncho] = useState(0);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const medir = () => setAncho(Math.floor(el.getBoundingClientRect().width));
    medir();
    const observador = new ResizeObserver(medir);
    observador.observe(el);
    return () => observador.disconnect();
  }, []);
  return [ref, ancho] as const;
}

export const formatoNumero = new Intl.NumberFormat('es-PE');

const MES_CORTO = new Intl.DateTimeFormat('es-PE', { month: 'short' });
const MES_LARGO = new Intl.DateTimeFormat('es-PE', { month: 'long', year: 'numeric' });
const DIA_MES = new Intl.DateTimeFormat('es-PE', { day: 'numeric', month: 'short' });
const DIA_LARGO = new Intl.DateTimeFormat('es-PE', { day: 'numeric', month: 'long', year: 'numeric' });
const sinPunto = (t: string) => t.replace('.', '');

export interface Periodo { corta: string; anio: string | null; larga: string }

/**
 * Etiquetas de un periodo a partir de su clave de la API: «2026-11» (mes) o «2026-11-17» (semana que empieza ese día).
 * El año se escribe completo y solo en el primer periodo y cuando cambia (enero), para que «nov 26» no se lea como un día.
 */
export function periodo(clave: string, indice: number): Periodo {
  if (/^\d{4}-\d{2}$/.test(clave)) {
    const [a, m] = clave.split('-').map(Number), fecha = new Date(a, m - 1, 15);
    return { corta: sinPunto(MES_CORTO.format(fecha)), anio: indice === 0 || m === 1 ? String(a) : null, larga: MES_LARGO.format(fecha) };
  }
  if (/^\d{4}-\d{2}-\d{2}$/.test(clave)) {
    const fecha = new Date(`${clave}T12:00:00`);
    return { corta: sinPunto(DIA_MES.format(fecha)), anio: indice === 0 || fecha.getMonth() === 0 && fecha.getDate() <= 7 ? String(fecha.getFullYear()) : null,
      larga: `Semana del ${DIA_LARGO.format(fecha)}` };
  }
  return { corta: clave, anio: null, larga: clave };
}

/** Año a escribir bajo una etiqueta visible: si en los periodos ocultos desde la anterior cambió el año, se muestra aquí */
export function anioVisible(periodos: Periodo[], claves: string[], i: number, cadaCuantas: number): string | null {
  for (let j = Math.max(0, i - cadaCuantas + 1); j <= i; j++) if (periodos[j].anio) return claves[i].slice(0, 4);
  return null;
}
