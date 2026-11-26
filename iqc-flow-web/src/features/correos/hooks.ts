import { useCallback, useEffect, useRef, useState } from 'react';
import { api, type MailAssistantStatus, type MailCounters, type MailDetail, type MailQuery, type MailSummary, type Page } from '../../lib/api';
import { errorText } from '../../lib/format';

/** Contadores de la bandeja, renovados cada minuto y solo mientras la pestaña se ve; `recargar` los pide ya (tras una acción) */
export function useContadoresCorreo(habilitado: boolean) {
  const [contadores, setContadores] = useState<MailCounters | null>(null);
  const recargar = useCallback(() => { if (habilitado) api.mailCounters().then(setContadores).catch(() => {}); }, [habilitado]);
  useEffect(() => {
    if (!habilitado) return;
    recargar();
    const timer = window.setInterval(() => { if (document.visibilityState === 'visible') recargar(); }, 60_000);
    const alVolver = () => { if (document.visibilityState === 'visible') recargar(); };
    document.addEventListener('visibilitychange', alVolver);
    return () => { window.clearInterval(timer); document.removeEventListener('visibilitychange', alVolver); };
  }, [habilitado, recargar]);
  return { contadores, recargar };
}

/** La página de correos que pide el filtro; `reemplazar` actualiza una fila sin volver a pedir todo */
export function useListaCorreos(consulta: MailQuery) {
  const [datos, setDatos] = useState<Page<MailSummary> | null>(null), [error, setError] = useState(''), [cargando, setCargando] = useState(true);
  const [recarga, setRecarga] = useState(0);
  const clave = JSON.stringify(consulta);
  useEffect(() => {
    let activo = true;
    setCargando(true);
    api.mailInbox(JSON.parse(clave)).then(p => { if (activo) { setDatos(p); setError(''); } })
      .catch(e => { if (activo) setError(errorText(e)); }).finally(() => { if (activo) setCargando(false); });
    return () => { activo = false; };
  }, [clave, recarga]);
  const reemplazar = (id: string, cambio: Partial<MailSummary>) =>
    setDatos(d => d && { ...d, contenido: d.contenido.map(m => m.id === id ? { ...m, ...cambio } : m) });
  return { datos, error, cargando, recargar: () => setRecarga(n => n + 1), reemplazar };
}

/** El correo abierto, siempre por su id: así se puede abrir uno que cita la IA aunque no esté en la página de la lista */
export function useCorreo(id: string | null) {
  const [detalle, setDetalle] = useState<MailDetail | null>(null), [error, setError] = useState(''), [cargando, setCargando] = useState(false);
  const [recarga, setRecarga] = useState(0), ultimo = useRef<string | null>(null);
  useEffect(() => {
    if (!id) { setDetalle(null); setError(''); return; }
    let activo = true;
    // Al cambiar de correo se vacía el anterior; al recargar el mismo, se deja a la vista mientras llega el nuevo
    if (ultimo.current !== id) setDetalle(null);
    ultimo.current = id; setCargando(true); setError('');
    api.mailMessage(id).then(d => { if (activo) setDetalle(d); }).catch(e => { if (activo) setError(errorText(e)); }).finally(() => { if (activo) setCargando(false); });
    return () => { activo = false; };
  }, [id, recarga]);
  return { detalle, setDetalle, error, cargando, recargar: () => setRecarga(n => n + 1) };
}

/**
 * ¿Hay asistente de IA? Mientras el servidor no lo tenga encendido (o no exista) responde que no, con el motivo: las pantallas
 * muestran los botones apagados y lo explican, en vez de ocultarlos sin decir nada.
 */
export function useAsistenteCorreo(habilitado: boolean) {
  const [estado, setEstado] = useState<MailAssistantStatus | null>(null);
  useEffect(() => {
    if (!habilitado) return;
    let activo = true;
    api.mailAssistantStatus().then(s => { if (activo) setEstado(s); })
      .catch(() => { if (activo) setEstado({ disponible: false, motivo: 'El asistente aún no está activado en el servidor.' }); });
    return () => { activo = false; };
  }, [habilitado]);
  return estado;
}
