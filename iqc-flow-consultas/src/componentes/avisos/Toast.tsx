import { useEffect } from 'react';
import { AlertTriangle, X } from 'lucide-react';

export interface MensajeToast { titulo: string; detalle?: string }

const DURACION_MS = 8000;

/**
 * Aviso breve en una esquina (abajo en el celular), sin mover el formulario. Se cierra solo o con la X.
 * La región `status` existe siempre para que los lectores de pantalla anuncien cada mensaje nuevo.
 */
export function Toast({ mensaje, cerrar }: { mensaje: MensajeToast | null; cerrar: () => void }) {
  useEffect(() => {
    if (!mensaje) return;
    const t = window.setTimeout(cerrar, DURACION_MS);
    return () => window.clearTimeout(t);
  }, [mensaje, cerrar]);

  return <div className="toasts" role="status" aria-live="polite">
    {mensaje && <div className="toast">
      <AlertTriangle size={18} aria-hidden />
      <p><b>{mensaje.titulo}</b>{mensaje.detalle && <span>{mensaje.detalle}</span>}</p>
      <button type="button" onClick={cerrar} aria-label="Cerrar aviso"><X size={16} aria-hidden /></button>
    </div>}
  </div>;
}
