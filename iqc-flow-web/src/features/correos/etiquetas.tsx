import type { LucideIcon } from 'lucide-react';
import { Icono } from '../../lib/iconos';
import type { MailState, MailSummary, MailType } from '../../lib/api';

export const TIPOS: Record<MailType, { etiqueta: string; icono: LucideIcon }> = {
  COTIZACION: { etiqueta: 'Cotización', icono: Icono.cotizacion },
  PAGO: { etiqueta: 'Pago', icono: Icono.correoPago },
  AUDITORIA: { etiqueta: 'Auditoría', icono: Icono.correoAuditoria },
  CERTIFICADO: { etiqueta: 'Certificado', icono: Icono.certificadoEmpresarial },
  RECLAMO: { etiqueta: 'Reclamo', icono: Icono.correoReclamo },
  PUBLICIDAD: { etiqueta: 'Publicidad', icono: Icono.correoPublicidad },
  OTRO: { etiqueta: 'Otro', icono: Icono.correo },
};
/** «Nuevo» quiere decir «sin tratar»: nadie lo ha tomado ni respondido. No es «no leído»: abrirlo no cambia su estado */
export const ESTADOS: Record<MailState, string> = { NUEVO: 'Nuevo', EN_CURSO: 'En curso', ATENDIDO: 'Atendido', DESCARTADO: 'Descartado' };

const CUENTAS: Record<string, string> = { administracion: 'Administración', comercial: 'Comercial', gmail: 'Gmail' };
export const nombreCuenta = (id: string) => CUENTAS[id] ?? id.charAt(0).toUpperCase() + id.slice(1);
export const quienEscribe = (m: Pick<MailSummary, 'remitenteNombre' | 'remitenteCorreo'>) => m.remitenteNombre?.trim() || m.remitenteCorreo;

/** El tipo con su ícono: el texto va siempre, así no se depende solo del dibujo ni del color */
export function EtiquetaTipo({ tipo }: { tipo: MailType }) {
  const { etiqueta, icono: Icon } = TIPOS[tipo];
  return <span className={`correo-tipo tipo-${tipo.toLowerCase()}`}><Icon size={13} aria-hidden />{etiqueta}</span>;
}

/** Lo nuevo va con punto y el texto en negrita de la fila (más un aviso para el lector de pantalla); los demás estados se escriben */
export function EtiquetaEstado({ estado }: { estado: MailState }) {
  return <span className="status-label"><span className={`status-dot correo-${estado.toLowerCase()}`} />
    {estado === 'NUEVO' ? <span className="sr-only">Nuevo, sin tratar</span> : ESTADOS[estado]}</span>;
}

/** Nombre del archivo `.eml` que se le entrega a «Crear cotización»: el asunto sin símbolos raros, para que se reconozca en la lista de archivos */
export function nombreEml(asunto: string) {
  const base = asunto.replace(/[^\p{L}\p{N} ._-]+/gu, ' ').replace(/\s+/g, ' ').trim().slice(0, 60).trim();
  return `${base || 'correo'}.eml`;
}
