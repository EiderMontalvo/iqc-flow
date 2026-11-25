import type { ReactNode } from 'react';
import { ArrowUpRight } from 'lucide-react';
import { MARCA, RAIZ } from '../../config/portal';
import { Fondo } from './Fondo';

export type ModoBanda = 'inicio' | 'compacta';

interface Props {
  titulo: string;
  texto: string;
  /** `inicio`: ocupa toda la primera pantalla, solo con el acceso. `compacta`: barra sobre el certificado */
  modo: ModoBanda;
  children?: ReactNode;
}

/**
 * Cabecera negra del portal. Una sola composición en ambos modos (sin tarjeta), para que al verificar la portada
 * se compacte sin saltar a otro diseño. En modo compacto el título y la bajada quedan solo para lectores de pantalla.
 */
export function Banda({ titulo, texto, modo, children }: Props) {
  return <header className={`banda ${modo}`}>
    <Fondo animado={modo === 'inicio'} />
    <div className="banda-contenido">
      <div className="barra">
        <a href={RAIZ} className="marca" aria-label={`${MARCA.nombre}, nueva consulta`}>
          <img src={`${RAIZ}logo-iqc-horizontal.png`} alt="IQC Integrated Quality Certifications" width={128} height={64} fetchPriority="high" />
        </a>
        <a className="sitio" href={MARCA.web} rel="noreferrer">{MARCA.dominio} <ArrowUpRight size={16} aria-hidden /></a>
      </div>
      {/* data-enter: título, bajada y formulario entran en secuencia al cargar (estilos/base.css) */}
      <div className="cuerpo" data-enter={modo === 'inicio' ? '' : undefined}>
        {modo === 'inicio' && <p className="antetitulo">Certificados empresariales y personales</p>}
        <h1>{titulo}</h1>
        <p className="bajada">{texto}</p>
        {children}
      </div>
    </div>
  </header>;
}
