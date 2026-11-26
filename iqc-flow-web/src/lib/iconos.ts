import {
  BriefcaseBusiness, Building2, ClipboardCheck, FileSignature, FileText, PenLine, GraduationCap, Handshake, Inbox, ListTodo, Mail, Megaphone, MessageSquareWarning,
  Receipt, Reply, ShieldAlert, ShieldCheck, Sparkles, UserRoundX, UserSearch,
} from 'lucide-react';

/**
 * Un ícono por concepto del negocio, el mismo en el carril, las cifras y los resúmenes. Todos de lucide (trazo de 2 px):
 * se elige por lo que significa el concepto, no por adorno.
 */
export const Icono = {
  certificadoEmpresarial: ShieldCheck,
  certificadoPersonal: GraduationCap,
  cliente: Building2,
  cartera: BriefcaseBusiness,
  prospecto: UserSearch,
  clienteActivo: Handshake,
  clienteInactivo: UserRoundX,
  cotizacion: FileSignature,
  carta: FileText,
  firma: PenLine,
  seguimiento: ListTodo,
  bandeja: Inbox,
  correo: Mail,
  responder: Reply,
  /** Todo lo que hace la IA, en cualquier pantalla: leer una solicitud, resumir, redactar, consultar */
  ia: Sparkles,
  /** Aviso de que algo sale del sistema (p. ej. el texto de los correos va al proveedor de IA) */
  privacidad: ShieldAlert,
  // Tipos de correo: cada uno con el ícono de lo que trata (cotización y certificado, los mismos de sus pantallas)
  correoPago: Receipt,
  correoAuditoria: ClipboardCheck,
  correoReclamo: MessageSquareWarning,
  correoPublicidad: Megaphone,
} as const;
