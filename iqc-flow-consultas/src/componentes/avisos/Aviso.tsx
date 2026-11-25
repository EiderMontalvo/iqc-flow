import type { ReactNode } from 'react';
import { AlertTriangle } from 'lucide-react';

/** Aviso dentro del contenido (zona clara), para errores que reemplazan al certificado, como un QR inexistente */
export function Aviso({ children }: { children: ReactNode }) {
  return <p className="aviso" role="alert"><AlertTriangle size={20} aria-hidden /> <span>{children}</span></p>;
}
