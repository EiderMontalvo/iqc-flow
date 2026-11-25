import { PORTALES_OFICIALES } from '../config/portal';

/**
 * Un QR antiguo solo puede reenviar a un portal oficial de consulta, por HTTPS. Aunque el destino viene de la base
 * de datos, se valida aquí para que un dato alterado nunca convierta el portal en un redirector abierto.
 */
export function destinoOficial(url: string): boolean {
  try {
    const u = new URL(url);
    return u.protocol === 'https:' && (PORTALES_OFICIALES as readonly string[]).includes(u.hostname);
  } catch {
    return false;
  }
}

/**
 * Identificador del QR a partir de la ruta (`/<token>`). Devuelve `null` en la portada. Una ruta mal codificada
 * (p. ej. `/%E0`) no rompe la página: se trata como un QR que no existe.
 */
export function tokenDeRuta(pathname: string, raiz: string): string | null {
  const crudo = pathname.slice(raiz.length - 1).replace(/^\/+|\/+$/g, '');
  if (!crudo) return null;
  try {
    return decodeURIComponent(crudo);
  } catch {
    return crudo;
  }
}
