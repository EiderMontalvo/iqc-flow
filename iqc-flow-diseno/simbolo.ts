/**
 * Símbolo de IQC trazado del logo, en un lienzo de 317 × 349 (viewBox recomendado: "-4 -4 325 357").
 * Tres piezas: anillo octogonal abierto, pieza de cierre a la derecha y marcador central.
 * Se dibuja en línea con el degradado del logo; nunca relleno sólido de color.
 */
export const SIMBOLO_IQC = [
  'M96 0H224L316 92V140H256V124L196 64H120L64 122V224L122 284H200L250 328L224 348H96L0 252V96Z',
  'M256 210H316V254L296 276L256 236Z',
  'M160 116L192 148V238L160 206L128 238V148Z',
] as const;

export const VISTA_SIMBOLO = '-4 -4 325 357';

/** Paradas del degradado del logo IQC, para un <linearGradient> (rojo → magenta → violeta → azul) */
export const DEGRADADO_IQC = [
  { offset: '0', color: '#d90435' },
  { offset: '.38', color: '#9002ae' },
  { offset: '.66', color: '#6701e0' },
  { offset: '1', color: '#0019d2' },
] as const;
