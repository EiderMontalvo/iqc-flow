import { useEffect, useState } from 'react';

const CONSULTA = '(min-width: 900px)';

/**
 * `true` en pantallas anchas. El PDF se embebe solo ahí: en el celular los visores integrados fallan y
 * descargarían el archivo sin que la persona lo pida.
 */
export function usePantallaAncha(): boolean {
  const [ancha, setAncha] = useState(() => window.matchMedia(CONSULTA).matches);
  useEffect(() => {
    const m = window.matchMedia(CONSULTA);
    const cambio = () => setAncha(m.matches);
    m.addEventListener('change', cambio);
    return () => m.removeEventListener('change', cambio);
  }, []);
  return ancha;
}
