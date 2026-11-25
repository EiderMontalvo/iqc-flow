import type { ComponentType } from 'react';
import {
  Candy, CandyCane, Coffee, Flower, Flower2, Ghost, Gift, Heart, HeartHandshake, Leaf, Moon, Music, PartyPopper, Shirt, Snowflake,
  Sparkles, Sprout, Star, Sun, TreePine, Trophy, Wine, type LucideProps,
} from 'lucide-react';
import { BanderaPeru, Calabaza } from './iconosPropios';

/** En el orden del calendario: así aparecen en Configuración */
export type ClaveTematica = 'ANO_NUEVO' | 'SAN_VALENTIN' | 'DIA_DE_LA_MUJER' | 'DIA_DE_LA_MADRE' | 'DIA_DEL_PADRE' | 'FIESTAS_PATRIAS'
  | 'PRIMAVERA' | 'HALLOWEEN' | 'NAVIDAD';
export type ValorTematica = 'NINGUNA' | 'AUTOMATICA' | ClaveTematica;

export interface Tematica {
  nombre: string;
  /** Cuándo se activa sola con «Automática» (calendario de Perú) */
  cuando: string;
  saludo: string;
  iconos: Array<ComponentType<LucideProps>>;
  /** Tintas de los íconos del fondo, en orden; se reparten entre los íconos */
  colores: string[];
}

export const TEMATICAS: Record<ClaveTematica, Tematica> = {
  ANO_NUEVO: { nombre: 'Año Nuevo', cuando: '27 de diciembre al 6 de enero', saludo: '¡Feliz Año Nuevo!',
    iconos: [PartyPopper, Sparkles, Wine, Star], colores: ['#b8860b', '#1a33e0', '#6701e0'] },
  SAN_VALENTIN: { nombre: 'San Valentín', cuando: '7 al 14 de febrero', saludo: '¡Feliz Día del Amor y la Amistad!',
    iconos: [Heart, HeartHandshake, Gift, Sparkles], colores: ['#d81b60', '#c8102e', '#e5739a'] },
  DIA_DE_LA_MUJER: { nombre: 'Día de la Mujer', cuando: '1 al 8 de marzo', saludo: '¡Feliz Día Internacional de la Mujer!',
    iconos: [Flower, Sparkles, Heart, Star], colores: ['#7b2cbf', '#9d4edd', '#c2185b'] },
  DIA_DE_LA_MADRE: { nombre: 'Día de la Madre', cuando: 'La semana del segundo domingo de mayo', saludo: '¡Feliz Día de la Madre!',
    iconos: [Heart, Flower2, Gift, Sparkles], colores: ['#c2185b', '#d81b60', '#8e24aa'] },
  DIA_DEL_PADRE: { nombre: 'Día del Padre', cuando: 'La semana del tercer domingo de junio', saludo: '¡Feliz Día del Padre!',
    iconos: [Trophy, Coffee, Shirt, Gift], colores: ['#1a33e0', '#0f5c8c', '#5b6472'] },
  FIESTAS_PATRIAS: { nombre: 'Fiestas Patrias', cuando: '15 al 31 de julio', saludo: '¡Felices Fiestas Patrias!',
    iconos: [BanderaPeru, Star, BanderaPeru, Music, PartyPopper], colores: ['#d91023', '#a10d1c'] },
  PRIMAVERA: { nombre: 'Primavera', cuando: '20 al 23 de septiembre', saludo: '¡Feliz Día de la Primavera!',
    iconos: [Flower2, Sun, Leaf, Sprout], colores: ['#2e9d4f', '#d4a017', '#e5739a'] },
  HALLOWEEN: { nombre: 'Halloween', cuando: '24 al 31 de octubre', saludo: '¡Feliz Halloween!',
    iconos: [Calabaza, Ghost, Moon, Candy], colores: ['#e8741c', '#6b2fa3', '#3a3a46'] },
  NAVIDAD: { nombre: 'Navidad', cuando: '1 al 26 de diciembre', saludo: '¡Feliz Navidad!',
    iconos: [TreePine, Gift, Snowflake, Star, CandyCane], colores: ['#c8102e', '#0f7b3f', '#b8860b'] },
};

/** n-ésimo domingo del mes (mes 0-11) */
function domingo(anio: number, mes: number, n: number) {
  const primero = new Date(anio, mes, 1), desfase = (7 - primero.getDay()) % 7;
  return new Date(anio, mes, 1 + desfase + 7 * (n - 1));
}
const entre = (hoy: Date, desde: Date, hasta: Date) => hoy >= desde && hoy <= hasta;

/** Temática que corresponde a una fecha con «Automática» */
export function tematicaDeFecha(fecha = new Date()): ClaveTematica | null {
  const a = fecha.getFullYear(), m = fecha.getMonth(), d = fecha.getDate();
  const hoy = new Date(a, m, d);
  if (m === 11 && d <= 26) return 'NAVIDAD';
  if ((m === 11 && d >= 27) || (m === 0 && d <= 6)) return 'ANO_NUEVO';
  if (m === 1 && d >= 7 && d <= 14) return 'SAN_VALENTIN';
  if (m === 2 && d <= 8) return 'DIA_DE_LA_MUJER';
  if (m === 6 && d >= 15) return 'FIESTAS_PATRIAS';
  if (m === 8 && d >= 20 && d <= 23) return 'PRIMAVERA';
  if (m === 9 && d >= 24) return 'HALLOWEEN';
  const madre = domingo(a, 4, 2), padre = domingo(a, 5, 3), semana = (x: Date) => new Date(x.getFullYear(), x.getMonth(), x.getDate() - 6);
  if (entre(hoy, semana(madre), madre)) return 'DIA_DE_LA_MADRE';
  if (entre(hoy, semana(padre), padre)) return 'DIA_DEL_PADRE';
  return null;
}

/** Temática vigente a partir del valor configurado por MASTER */
export function tematicaActiva(valor: string | undefined): ClaveTematica | null {
  if (!valor || valor === 'NINGUNA') return null;
  if (valor === 'AUTOMATICA') return tematicaDeFecha();
  return valor in TEMATICAS ? valor as ClaveTematica : null;
}
