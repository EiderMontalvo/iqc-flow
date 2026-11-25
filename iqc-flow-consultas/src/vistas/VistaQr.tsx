import { useEffect, useState } from 'react';
import { ArrowLeft } from 'lucide-react';
import { porQr } from '../api/consultas';
import type { Consulta } from '../api/tipos';
import { Aviso } from '../componentes/avisos/Aviso';
import { HojaCargando } from '../componentes/certificado/Hoja';
import { Resultados } from '../componentes/certificado/Resultados';
import { Banda } from '../componentes/estructura/Banda';
import { RAIZ } from '../config/portal';
import { destinoOficial } from '../lib/seguridad';

/**
 * Lo que abre el QR impreso en un certificado (`/<token>`). Si el QR antiguo pertenece al portal de la otra marca,
 * la API devuelve `redireccion` y se reenvía allí, solo si es un portal oficial.
 */
export function VistaQr({ token }: { token: string }) {
  const [consulta, setConsulta] = useState<Consulta | null>(null), [error, setError] = useState(false);

  useEffect(() => {
    porQr(token)
      .then(r => {
        if (!r.redireccion) setConsulta({ r, en: new Date() });
        else if (destinoOficial(r.redireccion)) window.location.replace(r.redireccion);
        else setError(true);
      })
      .catch(() => setError(true));
  }, [token]);

  return <>
    <Banda modo="compacta" titulo="Resultado de la validación"
      texto="Datos del certificado asociado al código QR que escaneaste, tal como figuran en el registro oficial.">
      <a className="boton nueva" href={RAIZ}><ArrowLeft size={18} aria-hidden /> Hacer otra consulta</a>
    </Banda>
    <main className="contenido" aria-live="polite">
      {!consulta && !error && <><p className="sr">Consultando el certificado…</p><HojaCargando /></>}
      {error && <Aviso>No encontramos el certificado de este código QR. Si el certificado es reciente, puede que aún no esté publicado: verifícalo con el documento del titular y el código del certificado.</Aviso>}
      {consulta && <Resultados r={consulta.r} consultadoEn={consulta.en} />}
    </main>
  </>;
}
