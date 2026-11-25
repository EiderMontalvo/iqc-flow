/** Color del sello y del veredicto */
export type Tono = 'ok' | 'mal' | 'alerta' | 'neutro';

export interface Situacion {
  /** Palabra del veredicto y del anillo del sello */
  palabra: string;
  tono: Tono;
  /** Frase que acompaña al veredicto; recibe la fecha ya formateada (expiración o emisión) */
  linea: (fecha: string | null) => string;
}

/**
 * Cómo se presenta cada estado. Empresariales: la `situacion` calculada por la API (VIGENTE, VENCIDO…).
 * Personales: el `estado` (EMITIDO, ANULADO). La fecha de anulación no se muestra por decisión de producto.
 */
const SITUACIONES: Record<string, Situacion> = {
  VIGENTE: { palabra: 'Vigente', tono: 'ok', linea: f => f ? `Válido hasta el ${f}` : 'Certificado válido' },
  VENCIDO: { palabra: 'Vencido', tono: 'mal', linea: f => f ? `Expiró el ${f}` : 'Su vigencia terminó' },
  SIN_FECHA: { palabra: 'Registrado', tono: 'neutro', linea: () => 'No tiene fecha de expiración registrada' },
  SUSPENDIDO: { palabra: 'Suspendido', tono: 'alerta', linea: () => 'No es válido mientras dure la suspensión' },
  RETIRADO: { palabra: 'Retirado', tono: 'mal', linea: () => 'Fue retirado y ya no es válido' },
  CANCELADO: { palabra: 'Anulado', tono: 'mal', linea: () => 'Fue anulado y no es válido' },
  REEMPLAZADO: { palabra: 'Reemplazado', tono: 'neutro', linea: () => 'Existe una versión posterior de este certificado' },
  EMITIDO: { palabra: 'Válido', tono: 'ok', linea: f => f ? `Emitido el ${f}` : 'Certificado emitido' },
  ANULADO: { palabra: 'Anulado', tono: 'mal', linea: () => 'Fue anulado y no es válido' },
};

/** Un estado desconocido se muestra tal cual, en tono neutro, en vez de romper la página */
export const situacion = (codigo: string): Situacion =>
  SITUACIONES[codigo] ?? { palabra: codigo, tono: 'neutro', linea: () => '' };
