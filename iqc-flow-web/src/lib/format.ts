/** Formatos compartidos (es-PE). Las fechas sin hora se interpretan al mediodía para no cambiar de día por zona */
const fechaCorta = new Intl.DateTimeFormat('es-PE', { day: '2-digit', month: 'short', year: 'numeric' });
const fechaHora = new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short' });

export const day = (value: string | null | undefined) => value ? fechaCorta.format(new Date(value.length === 10 ? value + 'T12:00:00' : value)) : '—';
export const stamp = (value: string | null | undefined) => value ? fechaHora.format(new Date(value)) : '—';
export const number = (value: number | null | undefined) => value == null ? '—' : value.toLocaleString('es-PE');
export const errorText = (error: unknown) => error instanceof Error ? error.message : 'Ocurrió un error inesperado.';
export const isoDay = (date: Date) => [date.getFullYear(), String(date.getMonth() + 1).padStart(2, '0'), String(date.getDate()).padStart(2, '0')].join('-');
export const plusYears = (value: string, years: number, days = 0) => {
  if (!value) return '';
  const date = new Date(value + 'T12:00:00'); date.setFullYear(date.getFullYear() + years); date.setDate(date.getDate() + days);
  return isoDay(date);
};
export const kb = (bytes: number) => bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`;
export const initials = (name: string) => name.split(/\s+/).map(p => p.replace(/[^\p{L}]/gu, '')).filter(Boolean).slice(0, 2).map(p => p[0]).join('').toUpperCase() || '?';

/** «hace 5 min», «hace 3 h», «ayer», «hace 4 días»; pasada una semana, la fecha corta. La fecha exacta va aparte, en un `title` */
export function relativa(value: string | null | undefined, ahora = Date.now()) {
  if (!value) return '—';
  const t = new Date(value).getTime(), min = Math.round((ahora - t) / 60_000);
  if (min < 1) return 'ahora';
  if (min < 60) return `hace ${min} min`;
  const dia = (ms: number) => new Date(ms).toDateString();
  if (dia(t) === dia(ahora)) return `hace ${Math.floor(min / 60)} h`;
  const dias = Math.round((new Date(dia(ahora)).getTime() - new Date(dia(t)).getTime()) / 86_400_000);
  if (dias === 1) return 'ayer';
  return dias < 7 ? `hace ${dias} días` : day(value);
}
