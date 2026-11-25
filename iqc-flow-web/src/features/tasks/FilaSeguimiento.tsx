import { AlarmClock, CalendarCheck, CalendarDays, CheckCircle2, PauseCircle, XCircle } from 'lucide-react';
import type { TaskSummary } from '../../lib/api';
import { initials } from '../../lib/format';

const PRIORIDAD: Record<TaskSummary['prioridad'], string> = { URGENTE: 'Urgente', ALTA: 'Alta', NORMAL: 'Normal', BAJA: 'Baja' };
const ESTADO: Record<TaskSummary['estado'], string> = { PENDIENTE: 'Pendiente', EN_CURSO: 'En curso', EN_ESPERA: 'En espera', COMPLETADA: 'Completada', CANCELADA: 'Cancelada' };
const fecha = new Intl.DateTimeFormat('es-PE', { day: 'numeric', month: 'short', year: 'numeric' });
const MS_DIA = 86_400_000;

/** Días entre hoy y la fecha límite (negativo si ya pasó), contados por día calendario */
function diasHasta(limite: string) {
  const hoy = new Date(); hoy.setHours(12, 0, 0, 0);
  return Math.round((new Date(`${limite}T12:00:00`).getTime() - hoy.getTime()) / MS_DIA);
}

/** Tinta e ícono según lo que pide el seguimiento: atrasado, para hoy, en espera, cerrado o por venir */
function situacion(t: TaskSummary) {
  if (t.estado === 'COMPLETADA') return { tono: 'ok', Icono: CheckCircle2, texto: 'Completada' };
  if (t.estado === 'CANCELADA') return { tono: 'neutro', Icono: XCircle, texto: 'Cancelada' };
  const dias = diasHasta(t.fechaLimite);
  if (t.vencida || dias < 0) return { tono: 'mal', Icono: AlarmClock, texto: dias === -1 ? 'Venció ayer' : `Venció hace ${-dias} días` };
  if (t.estado === 'EN_ESPERA') return { tono: 'neutro', Icono: PauseCircle, texto: dias === 0 ? 'Vence hoy' : `En ${dias} ${dias === 1 ? 'día' : 'días'}` };
  if (dias === 0) return { tono: 'alerta', Icono: CalendarCheck, texto: 'Vence hoy' };
  return { tono: 'accion', Icono: CalendarDays, texto: dias === 1 ? 'Mañana' : `En ${dias} días` };
}

/** Una fila de la lista de seguimientos: qué es, para quién, quién lo lleva y cuándo vence */
export function FilaSeguimiento({ t, alAbrir }: { t: TaskSummary; alAbrir: () => void }) {
  const s = situacion(t);
  return <button type="button" className={`fila-seguimiento tono-${s.tono}`} onClick={alAbrir}>
    <span className="fila-seguimiento-icono" aria-hidden><s.Icono size={18} /></span>
    <span className="fila-seguimiento-que">
      <strong>{t.titulo}</strong>
      <span>{t.cliente?.razonSocial || 'Sin cliente'} · {t.tipoNombre}</span>
    </span>
    <span className="fila-seguimiento-estado">
      <span className="status-label"><span className={`status-dot prioridad-${t.prioridad.toLowerCase()}`} />{PRIORIDAD[t.prioridad]}</span>
      <small>{ESTADO[t.estado]}</small>
    </span>
    <span className="fila-seguimiento-quien" title={t.responsable.nombre}>
      <span className="avatar-mini" aria-hidden>{initials(t.responsable.nombre)}</span><span>{t.responsable.nombre}</span>
    </span>
    <span className="fila-seguimiento-cuando">
      <strong>{s.texto}</strong>
      <span>{fecha.format(new Date(`${t.fechaLimite}T12:00:00`))}{t.horaLimite ? ` · ${t.horaLimite.slice(0, 5)}` : ''}</span>
    </span>
  </button>;
}
