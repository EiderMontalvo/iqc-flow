/**
 * Configuración del portal. Cada compilación sirve a una sola marca:
 * - `npm run build:pe`    → IQC Perú  (consultas.iqcperu.com)
 * - `npm run build:latam` → IQC LATAM (consultas.iqc-latam.com)
 * Las variables vienen de `.env.pe` / `.env.latam` (ver README).
 */

export type Portal = 'PE' | 'LATAM';

export const PORTAL: Portal = import.meta.env.VITE_PORTAL === 'LATAM' ? 'LATAM' : 'PE';

/** Base de los endpoints públicos de la API. Vacía = mismo dominio (demo y desarrollo con el proxy de Vite) */
export const API = `${(import.meta.env.VITE_API_URL ?? '').replace(/\/+$/, '')}/api/v1/publico/consultas/${PORTAL}`;

/** Raíz donde se publica el portal: «/» en producción; una subcarpeta cuando se publica dentro de otro sitio */
export const RAIZ = import.meta.env.BASE_URL;

export interface Marca { nombre: string; region: string; web: string; dominio: string }

export const MARCA: Marca = PORTAL === 'LATAM'
  ? { nombre: 'IQC LATAM', region: 'Ecuador · Colombia · Panamá', web: 'https://iqc-latam.com', dominio: 'iqc-latam.com' }
  : { nombre: 'IQC Perú', region: 'Perú', web: 'https://iqcperu.com', dominio: 'iqcperu.com' };

/** Únicos destinos a los que puede reenviar un QR antiguo (ver `lib/seguridad.ts`) */
export const PORTALES_OFICIALES = ['consultas.iqcperu.com', 'consultas.iqc-latam.com'] as const;

/** Textos del formulario. Uno solo para certificados empresariales y personales: el registro sabe de qué tipo es cada código */
export const CAMPOS = {
  documento: PORTAL === 'LATAM' ? 'RUC, NIT o cédula del titular' : 'RUC o DNI del titular',
  ejemploDocumento: PORTAL === 'LATAM' ? 'Empresa o persona' : 'Ej. 20123456789 o 45678912',
  codigo: 'Código del certificado',
  ejemploCodigo: PORTAL === 'LATAM' ? 'Tal como figura en el certificado' : 'Ej. PER/… o IQC/PE/…',
} as const;

/** Mismos límites que valida la API (ServicioConsultaPublica.verificar) */
export const LIMITES = { documento: { min: 5, max: 20 }, codigo: { min: 4, max: 64 } } as const;
