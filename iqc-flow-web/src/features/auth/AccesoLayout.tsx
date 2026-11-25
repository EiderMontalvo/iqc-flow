import type { ReactNode } from 'react';
import { BrandMark } from '../../components/BrandMark';
import { SimboloIqc } from '../../components/SimboloIqc';

/**
 * Pantallas de acceso (inicio de sesión y cambio de contraseña obligatorio): el negro de marca con el símbolo IQC,
 * el mismo lenguaje que la portada del portal de consultas. Sin tarjeta: el formulario va directo sobre el negro.
 */
export function AccesoLayout({ titulo, texto, children }: { titulo: string; texto: string; children: ReactNode }) {
  return <main className="acceso">
    <SimboloIqc className="acceso-simbolo" dibujar />
    <header className="acceso-barra"><BrandMark alto={64} /><span>IQC Flow · acceso interno</span></header>
    <div className="acceso-cuerpo">
      <div className="acceso-texto"><h1>{titulo}</h1><p>{texto}</p></div>
      {children}
    </div>
  </main>;
}
