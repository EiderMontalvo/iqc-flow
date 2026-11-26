import { useEffect, useState } from 'react';
import { api } from './api';
import { errorText } from './format';

/**
 * Vista previa en vivo de un PDF que dibuja la API con lo escrito en un formulario. Espera `retardo` ms sin cambios antes de
 * pedirlo y cancela la petición anterior si se sigue escribiendo, así no se acumulan ni llegan fuera de orden.
 * Con `cuerpo` nulo (faltan datos mínimos) no pide nada y conserva la última vista.
 */
export function useVistaPreviaEnVivo(ruta: string, cuerpo: object | null, activa: boolean, retardo = 700) {
  const [archivo, setArchivo] = useState<File | null>(null), [ocupado, setOcupado] = useState(false), [error, setError] = useState('');
  const clave = activa && cuerpo ? JSON.stringify(cuerpo) : '';
  useEffect(() => {
    if (!clave) return;
    const control = new AbortController();
    const espera = window.setTimeout(async () => {
      setOcupado(true);
      try {
        const pdf = await api.previewPdf(ruta, JSON.parse(clave), control.signal);
        setArchivo(new File([pdf], 'vista-previa.pdf', { type: 'application/pdf' })); setError('');
      } catch (e) { if (!control.signal.aborted) setError(errorText(e)); }
      finally { if (!control.signal.aborted) setOcupado(false); }
    }, retardo);
    return () => { window.clearTimeout(espera); control.abort(); };
  }, [clave, ruta, retardo]);
  return { archivo, ocupado, error };
}
