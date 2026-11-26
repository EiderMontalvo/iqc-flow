/**
 * Limpieza y validación de datos de los formularios del panel (certificados empresariales y personales), al escribir y antes
 * de enviar. La API valida lo mismo
 * (el número de documento con la misma regla, que ella misma envía en `patron`); aquí se evita que el error llegue hasta allá.
 */

/** Partículas que van en minúscula dentro de un nombre: «Diego de la Cruz», «Ana van der Berg» */
const PARTICULAS = new Set(['de', 'del', 'la', 'las', 'los', 'y', 'e', 'da', 'das', 'do', 'dos', 'van', 'von', 'der', 'den']);

/** Nombres en formato título, como están todos los certificados guardados: «DIEGO DE LA CRUZ» → «Diego de la Cruz» */
export function nombreEnTitulo(texto: string): string {
  const palabras = texto.trim().replace(/\s+/g, ' ').toLocaleLowerCase('es-PE').split(' ').filter(Boolean);
  return palabras.map((p, i) => i > 0 && PARTICULAS.has(p) ? p
    // también tras guion o apóstrofo: «Pérez-Rojas», «O'Higgins»
    : p.replace(/(^|[-'’])(\p{L})/gu, (_m, sep: string, letra: string) => sep + letra.toLocaleUpperCase('es-PE'))).join(' ');
}

/** Solo letras (con tildes y ñ), espacios, guiones, apóstrofos y puntos */
const NOMBRE_VALIDO = /^[\p{L}][\p{L} .'’-]*$/u;

export function errorNombre(texto: string): string | undefined {
  const t = texto.trim().replace(/\s+/g, ' ');
  if (!t) return 'Escribe los nombres y apellidos.';
  if (!NOMBRE_VALIDO.test(t)) return 'Solo letras: sin números ni símbolos.';
  if (t.length < 5 || t.split(' ').length < 2) return 'Escribe al menos un nombre y un apellido.';
  if (t.length > 120) return 'Máximo 120 caracteres.';
  return undefined;
}

/** La regla del documento es solo de dígitos (DNI, CE, cédulas): entonces se descarta todo lo que no sea número */
export const soloDigitos = (patron: string | undefined) => !!patron && /^\[0-9\]\{[\d,]+\}$/.test(patron);

/** Largo máximo que permite la regla: «[0-9]{8}» → 8, «[0-9]{9,12}» → 12 */
export function largoMaximo(patron: string | undefined): number | undefined {
  const m = patron ? /\{(\d+)(?:,(\d+))?\}/.exec(patron) : null;
  return m ? Number(m[2] ?? m[1]) : undefined;
}

/** Número de documento al escribir: solo dígitos si la regla lo pide; si no, mayúsculas sin espacios ni símbolos */
export function limpiarDocumento(texto: string, patron: string | undefined): string {
  const limpio = soloDigitos(patron) ? texto.replace(/\D/g, '') : texto.toUpperCase().replace(/[^A-Z0-9-]/g, '');
  const max = largoMaximo(patron);
  return max ? limpio.slice(0, max) : limpio;
}

export function errorDocumento(texto: string, patron: string | undefined, ayuda: string | undefined): string | undefined {
  if (!texto.trim()) return 'Escribe el número de documento.';
  if (patron && !new RegExp(`^(?:${patron})$`).test(texto.trim())) return ayuda ? `Debe tener ${ayuda.charAt(0).toLowerCase()}${ayuda.slice(1)}.` : 'Revisa el número.';
  return undefined;
}

/** Teléfono: dígitos, «+», espacios, guiones y paréntesis; entre 6 y 15 dígitos */
export const limpiarTelefono = (texto: string) => texto.replace(/[^\d+\s()-]/g, '');
export function errorTelefono(texto: string): string | undefined {
  if (!texto.trim()) return undefined;
  const digitos = texto.replace(/\D/g, '').length;
  return digitos < 6 || digitos > 15 ? 'Entre 6 y 15 dígitos, por ejemplo 956 798 868.' : undefined;
}

/** Código del certificado: siempre en mayúsculas, sin espacios y solo con los signos / . _ - */
export const limpiarCodigo = (texto: string) => texto.toUpperCase().replace(/\s+/g, '').replace(/[^A-Z0-9/._-]/g, '');

/** «37001» o «iso 37001» → «ISO 37001» (lo mismo que hace la API) */
export function normaConFamilia(texto: string): string {
  const n = texto.trim().replace(/\s+/g, ' ');
  if (!n) return '';
  if (/^\d/.test(n)) return `ISO ${n}`;
  return n.replace(/^(iso(?:\/iec)?)\s*/i, (_m, f: string) => `${f.toUpperCase()} `);
}

/** Año de la norma: solo dígitos, máximo 4 */
export const limpiarAnio = (texto: string) => texto.replace(/\D/g, '').slice(0, 4);

/** Subdivisión y clase del sector (IQC Global): solo números separados por comas, «46» o «70, 59» */
export const limpiarSector = (texto: string) => texto.replace(/[^\d,\s]/g, '');
export const SECTOR_VALIDO = /^\d{1,6}(\s*,\s*\d{1,6})*$/;

/** Procesos no aplicables (IQC Global): «NA» o numerales, «8.3» o «7.1.5, 8.3» */
export const limpiarProcesos = (texto: string) => texto.toUpperCase().replace(/[^NA\d.,;\s]/g, '');
export const PROCESOS_VALIDOS = /^(NA|\d+(\.\d+)*(\s*[,;]\s*\d+(\.\d+)*)*)$/;
