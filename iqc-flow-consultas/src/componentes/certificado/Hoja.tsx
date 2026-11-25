import type { ReactNode } from 'react';
import { FileText, Printer } from 'lucide-react';
import { MARCA } from '../../config/portal';
import { usePantallaAncha } from '../../hooks/usePantallaAncha';
import { fechaHora } from '../../lib/fechas';
import { situacion } from '../../lib/situaciones';
import { Sello } from './Sello';

interface Props {
  /** «Certificado de sistema de gestión» o «Certificado personal» */
  tipo: string;
  titular: string;
  codigo: string;
  /** Situación o estado, según `lib/situaciones.ts` */
  estado: string;
  marca: string;
  /** Frase del veredicto, ya con su fecha */
  linea: string;
  /** URL del PDF, o null si no hay PDF que mostrar */
  pdf: string | null;
  consultadoEn: Date;
  /** Los `Dato` del certificado */
  children: ReactNode;
}

/**
 * Hoja de resultado con aspecto de documento oficial: sello y veredicto, datos, acciones, PDF embebido (solo en
 * pantallas anchas) y la constancia de la consulta, que es lo que sale al imprimir.
 */
export function Hoja({ tipo, titular, codigo, estado, marca, linea, pdf, consultadoEn, children }: Props) {
  const s = situacion(estado), ancha = usePantallaAncha();
  return <article className={`hoja ${s.tono}`} aria-labelledby="titular">
    <div className="hoja-cabecera">
      <Sello estado={estado} marca={marca} />
      <p className="veredicto"><strong>{s.palabra}</strong><span>{linea}</span></p>
      <div className="hoja-titulo">
        <h2 id="titular">{titular}</h2>
        <p className="tipo">{tipo} emitido por {marca}</p>
      </div>
    </div>
    <dl className="campos">{children}</dl>
    <div className="hoja-pie">
      <div className="acciones">
        {pdf && <a className="boton primario" href={pdf} target="_blank" rel="noreferrer"><FileText size={18} aria-hidden /> Ver certificado en PDF</a>}
        <button type="button" className="boton secundario" onClick={() => window.print()}><Printer size={18} aria-hidden /> Imprimir constancia</button>
      </div>
      {pdf && ancha && <div className="visor">
        <iframe src={`${pdf}#view=FitH`} title={`Certificado ${codigo} en PDF`} loading="lazy" />
      </div>}
      <p className="constancia">
        Consultado el {fechaHora(consultadoEn)} en el registro oficial de certificados de {MARCA.nombre}.
      </p>
    </div>
  </article>;
}

/** Esqueleto mientras llega el certificado (solo cuando ya se está en la vista compacta, o al abrir un QR) */
export function HojaCargando() {
  return <div className="hoja cargando" aria-hidden>
    <div className="hoja-cabecera"><div className="sello-vacio" /><div className="lineas"><span /><span /><span /></div></div>
    <div className="lineas grilla"><span /><span /><span /><span /><span /><span /></div>
  </div>;
}
