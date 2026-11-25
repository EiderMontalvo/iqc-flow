/**
 * Contrato de la API pública (`/api/v1/publico/consultas/{portal}`).
 * Refleja los records de `ServicioConsultaPublica` en iqc-flow-api; las fechas llegan como `AAAA-MM-DD`.
 */

/** Certificado de sistema de gestión (ISO 9001, 14001, 45001…) */
export interface Empresarial {
  id: string;
  codigo: string;
  titular: string;
  documento: string | null;
  norma: string;
  esquema: string;
  organismoAcreditador: string;
  alcance: string | null;
  /** Estado en el registro: EMITIDO, SUSPENDIDO, RETIRADO, CANCELADO, REEMPLAZADO */
  estado: string;
  /** Lo que se muestra: el estado o, si está emitido, VIGENTE / VENCIDO / SIN_FECHA según la expiración */
  situacion: string;
  fechaOtorgamiento: string | null;
  fechaEmision: string | null;
  fechaExpiracion: string | null;
  marca: string;
  tienePdf: boolean;
}

/** Certificado personal (cursos y formación) */
export interface Personal {
  id: string;
  codigo: string;
  nombres: string;
  tipoDocumento: string;
  documento: string;
  curso: string;
  norma: string | null;
  subtitulo: string | null;
  descripcion: string | null;
  duracionHoras: number | null;
  modalidad: string | null;
  fechaInicio: string | null;
  fechaFin: string | null;
  fechaEmision: string;
  entrenador: string | null;
  /** EMITIDO o ANULADO */
  estado: string;
  marca: string;
  /** Token de la URL pública del PDF (`/personal-pdf/{token}`) */
  tokenPdf: string;
  tienePdf: boolean;
}

/**
 * Respuesta de verificación y de QR. Trae `empresarial` o `personal`; o solo `redireccion` cuando un QR antiguo
 * pertenece al portal de la otra marca.
 */
export interface Resultado {
  tipo: 'EMPRESA' | 'PERSONA' | null;
  empresarial: Empresarial | null;
  personal: Personal | null;
  redireccion: string | null;
}

/** Resultado junto con el momento de la consulta, que se imprime en la constancia */
export interface Consulta { r: Resultado; en: Date }
