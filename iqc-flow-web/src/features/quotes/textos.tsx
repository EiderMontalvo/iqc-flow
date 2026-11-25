import type { QuoteService, QuoteState } from '../../lib/api';

export const SERVICIOS: Record<QuoteService, string> = {
  CERTIFICACION_INICIAL: 'Certificación inicial', RECERTIFICACION: 'Recertificación', AMPLIACION: 'Ampliación',
  VIGILANCIA: 'Vigilancia', OTRO: 'Otro servicio',
};
export const ESTADOS: Record<QuoteState, string> = {
  BORRADOR: 'Borrador', ENVIADA: 'Enviada', ACEPTADA: 'Aceptada', RECHAZADA: 'Rechazada', ANULADA: 'Anulada',
};

const formatos = new Map<string, Intl.NumberFormat>();
/** Importe en su moneda («S/ 10 620,00», «US$ 1 200,00») */
export function dinero(valor: number | null | undefined, moneda = 'PEN') {
  if (valor == null) return '—';
  let f = formatos.get(moneda);
  if (!f) { f = new Intl.NumberFormat('es-PE', { style: 'currency', currency: moneda, minimumFractionDigits: 2 }); formatos.set(moneda, f); }
  return f.format(valor);
}

/** Estado en palabra y tinta (punto + palabra, sin pastilla): enviada con los días que lleva sin respuesta */
export function EstadoCotizacion({ estado, dias }: { estado: QuoteState; dias?: number | null }) {
  const extra = estado === 'ENVIADA' && dias != null ? ` · ${dias === 0 ? 'hoy' : dias === 1 ? 'hace 1 día' : `hace ${dias} días`}` : '';
  return <span className={`cert-tag cot-${estado.toLowerCase()}`}>{ESTADOS[estado]}{extra}</span>;
}
