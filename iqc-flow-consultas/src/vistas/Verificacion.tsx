import { useCallback, useEffect, useRef, useState } from 'react';
import { ErrorConsulta, verificar } from '../api/consultas';
import type { Consulta } from '../api/tipos';
import { Toast, type MensajeToast } from '../componentes/avisos/Toast';
import { HojaCargando } from '../componentes/certificado/Hoja';
import { Resultados } from '../componentes/certificado/Resultados';
import { Banda } from '../componentes/estructura/Banda';
import { FormularioVerificacion } from '../componentes/FormularioVerificacion';
import { MARCA } from '../config/portal';

const aMensaje = (e: unknown): MensajeToast => e instanceof ErrorConsulta
  ? { titulo: e.titulo, detalle: e.detalle }
  : { titulo: 'No pudimos consultar el registro', detalle: 'Intenta de nuevo en unos minutos.' };

/**
 * Portada (`/`): formulario a pantalla completa. Flujo:
 * - mientras consulta, la portada no cambia (el botón dice «Consultando…»);
 * - si llega un certificado, la cabecera se compacta y el certificado aparece debajo;
 * - si falla, vuelve o se queda en la portada y aparece el toast con el motivo.
 */
export function Verificacion() {
  const [cargando, setCargando] = useState(false);
  const [aviso, setAviso] = useState<MensajeToast | null>(null);
  const [consulta, setConsulta] = useState<Consulta | null>(null);
  const [compacta, setCompacta] = useState(false);
  const zona = useRef<HTMLElement>(null);

  // En el celular el certificado queda bajo el formulario: se lleva a la vista
  useEffect(() => {
    const el = zona.current;
    if (consulta && el && el.getBoundingClientRect().top > window.innerHeight * 0.55) {
      el.scrollIntoView({ behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'start' });
    }
  }, [consulta]);

  const cerrarAviso = useCallback(() => setAviso(null), []);

  async function consultar(documento: string, codigo: string) {
    setCargando(true); setAviso(null);
    try {
      setConsulta({ r: await verificar(documento, codigo), en: new Date() });
      setCompacta(true);
    } catch (e) {
      setConsulta(null); setCompacta(false); setAviso(aMensaje(e));
    } finally {
      setCargando(false);
    }
  }

  return <>
    <Banda modo={compacta ? 'compacta' : 'inicio'} titulo="Validación de certificados"
      texto={`Comprueba si un certificado de sistema de gestión o un certificado personal emitido por ${MARCA.nombre} es auténtico y está vigente.`}>
      <FormularioVerificacion cargando={cargando} alEnviar={consultar} />
    </Banda>
    <Toast mensaje={aviso} cerrar={cerrarAviso} />
    {compacta && (cargando || consulta) && <main className="contenido" aria-live="polite" ref={zona}>
      {cargando ? <HojaCargando /> : consulta && <Resultados r={consulta.r} consultadoEn={consulta.en} />}
    </main>}
  </>;
}
