const FECHA = new Intl.DateTimeFormat('es-PE', { day: 'numeric', month: 'long', year: 'numeric' });
const FECHA_HORA = new Intl.DateTimeFormat('es-PE', { dateStyle: 'long', timeStyle: 'short' });

/**
 * «2025-05-02» → «2 de mayo de 2025». Se fija el mediodía para que la zona horaria del visitante no cambie el día.
 */
export const fecha = (v: string | null | undefined): string | null => v ? FECHA.format(new Date(`${v}T12:00:00`)) : null;

/** Fecha y hora de la consulta, para la constancia */
export const fechaHora = (d: Date): string => FECHA_HORA.format(d);
