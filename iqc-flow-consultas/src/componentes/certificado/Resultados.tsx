import { urlPdfEmpresarial, urlPdfPersonal } from '../../api/consultas';
import type { Empresarial, Personal, Resultado } from '../../api/tipos';
import { fecha } from '../../lib/fechas';
import { situacion } from '../../lib/situaciones';
import { Dato, TextoLargo } from './Dato';
import { Hoja } from './Hoja';

/** Certificado de sistema de gestión: el veredicto se apoya en la fecha de expiración */
function HojaEmpresarial({ c, consultadoEn }: { c: Empresarial; consultadoEn: Date }) {
  return <Hoja tipo="Certificado de sistema de gestión" titular={c.titular} codigo={c.codigo} estado={c.situacion} marca={c.marca}
    linea={situacion(c.situacion).linea(fecha(c.fechaExpiracion))} consultadoEn={consultadoEn}
    pdf={c.tienePdf ? urlPdfEmpresarial(c.id) : null}>
    <Dato titulo="Código del certificado" codigo><span className="codigo">{c.codigo}</span></Dato>
    <Dato titulo="Norma">{c.norma}</Dato>
    <Dato titulo="Acreditación">{c.organismoAcreditador}</Dato>
    <Dato titulo="Documento del titular"><span className="cifra">{c.documento}</span></Dato>
    <Dato titulo="Otorgamiento">{fecha(c.fechaOtorgamiento)}</Dato>
    <Dato titulo="Emisión">{fecha(c.fechaEmision)}</Dato>
    <Dato titulo="Expiración">{fecha(c.fechaExpiracion)}</Dato>
    <Dato titulo="Alcance" ancho>{c.alcance && <TextoLargo texto={c.alcance} etiqueta="alcance" />}</Dato>
  </Hoja>;
}

/** Normas guardadas sin prefijo en los personales migrados («37001:2025») se muestran como «ISO 37001:2025» */
const normaLegible = (norma: string | null) => norma && /^\d/.test(norma) ? `ISO ${norma}` : norma;

/** Fechas del curso: un día, o «Del … al …» */
function fechasCurso(inicio: string | null, fin: string | null) {
  if (!inicio) return null;
  return fin && fin !== inicio ? `Del ${fecha(inicio)} al ${fecha(fin)}` : fecha(inicio);
}

/** Certificado personal: el veredicto se apoya en la emisión; el PDF solo se ofrece mientras siga emitido */
function HojaPersonal({ c, consultadoEn }: { c: Personal; consultadoEn: Date }) {
  return <Hoja tipo="Certificado personal" titular={c.nombres} codigo={c.codigo} estado={c.estado} marca={c.marca}
    linea={situacion(c.estado).linea(fecha(c.fechaEmision))} consultadoEn={consultadoEn}
    pdf={c.tienePdf && c.estado === 'EMITIDO' ? urlPdfPersonal(c.tokenPdf) : null}>
    <Dato titulo="Código de verificación" codigo><span className="codigo">{c.codigo}</span></Dato>
    <Dato titulo="Curso">{c.curso}</Dato>
    <Dato titulo="Norma">{[normaLegible(c.norma), c.subtitulo].filter(Boolean).join(' · ')}</Dato>
    <Dato titulo={c.tipoDocumento}><span className="cifra">{c.documento}</span></Dato>
    <Dato titulo="Duración">{c.duracionHoras != null ? `${c.duracionHoras} horas` : null}</Dato>
    <Dato titulo="Modalidad">{c.modalidad}</Dato>
    <Dato titulo="Fechas del curso">{fechasCurso(c.fechaInicio, c.fechaFin)}</Dato>
    <Dato titulo="Entrenador">{c.entrenador}</Dato>
    <Dato titulo="Descripción" ancho>{c.descripcion && <TextoLargo texto={c.descripcion} etiqueta="descripción" />}</Dato>
  </Hoja>;
}

/** Elige la hoja según el tipo de certificado que devolvió la API */
export function Resultados({ r, consultadoEn }: { r: Resultado; consultadoEn: Date }) {
  if (r.empresarial) return <HojaEmpresarial c={r.empresarial} consultadoEn={consultadoEn} />;
  if (r.personal) return <HojaPersonal c={r.personal} consultadoEn={consultadoEn} />;
  return null;
}
