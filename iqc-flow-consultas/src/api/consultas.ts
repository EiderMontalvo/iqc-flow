import { API } from '../config/portal';
import type { Resultado } from './tipos';

/**
 * Error de consulta con un texto para mostrar al visitante: `titulo` dice qué pasó y `detalle` qué hacer.
 * El toast los presenta en dos líneas.
 */
export class ErrorConsulta extends Error {
  constructor(readonly titulo: string, readonly detalle: string) {
    super(`${titulo}. ${detalle}`);
    this.name = 'ErrorConsulta';
  }
}

async function pedir(ruta: string): Promise<Resultado> {
  let r: Response;
  try {
    r = await fetch(`${API}${ruta}`, { headers: { Accept: 'application/json' } });
  } catch {
    throw new ErrorConsulta('Sin conexión con el registro', 'Revisa tu conexión a internet e intenta de nuevo.');
  }
  if (r.status === 404) throw new ErrorConsulta('Certificado no encontrado', 'Revisa el documento y el código tal como figuran, con guiones y barras.');
  if (r.status === 429) throw new ErrorConsulta('Demasiadas consultas seguidas', 'Espera un minuto y vuelve a intentarlo.');
  if (!r.ok) {
    // La API responde errores en formato Problem Details (RFC 9457); `detail` ya viene redactado para el usuario
    const p = await r.json().catch(() => ({})) as { detail?: string };
    throw new ErrorConsulta('No pudimos consultar el registro', p.detail || 'Intenta de nuevo en unos minutos.');
  }
  return r.json() as Promise<Resultado>;
}

/** Verificación por documento del titular (RUC, DNI…) y código del certificado */
export const verificar = (documento: string, codigo: string) =>
  pedir(`/verificacion?${new URLSearchParams({ documento: documento.trim(), codigo: codigo.trim() })}`);

/** Lo que abre un QR impreso en el certificado (`/<token>`) */
export const porQr = (token: string) => pedir(`/qr/${encodeURIComponent(token)}`);

/** PDF de un certificado empresarial (la API puede redirigir al almacén anterior en los importados) */
export const urlPdfEmpresarial = (id: string) => `${API}/certificados/${id}/pdf`;

/** PDF de un certificado personal; deja de estar disponible si el certificado se anula */
export const urlPdfPersonal = (token: string) => `${API}/personal-pdf/${token}`;
