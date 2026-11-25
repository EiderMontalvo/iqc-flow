import type { Punto } from '../../lib/api';
import { formatoNumero } from './escala';

/** Tinta de severidad por clave (prioridades de seguimiento); el resto usa el color de gráficos */
const SEVERIDAD: Record<string, string> = { URGENTE: 'mal', ALTA: 'alerta', NORMAL: 'serie', BAJA: 'neutro' };

/**
 * Lista de medidores: cada fila es la parte de un total (nombre, medidor y cifra), en el orden natural de la serie.
 * El relleno lleva la severidad (urgente → mal, alta → alerta) y la pista es un paso claro de la misma tinta;
 * el nombre y la cifra siempre van en texto, así el color nunca es la única señal.
 */
export function ListaMedidores({ datos, unidad }: { datos: Punto[]; unidad: [string, string] }) {
  const total = datos.reduce((t, d) => t + d.cantidad, 0) || 1;
  return <ul className="lista-medidores">
    {datos.map(d => { const parte = d.cantidad / total, tono = SEVERIDAD[d.clave] ?? 'serie';
      return <li key={d.clave} className={`medidor ${tono}`} tabIndex={0}
        aria-label={`${d.etiqueta}: ${formatoNumero.format(d.cantidad)} ${d.cantidad === 1 ? unidad[0] : unidad[1]}, ${Math.round(parte * 100)} % del total`}>
        <span className="nombre">{d.etiqueta}</span>
        <span className="pista"><span style={{ width: `${parte * 100}%` }} /></span>
        <span className="cifra"><strong>{formatoNumero.format(d.cantidad)}</strong><small>{Math.round(parte * 100)} %</small></span>
      </li>; })}
  </ul>;
}
