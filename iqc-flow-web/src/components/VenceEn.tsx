import { day } from '../lib/format';

const MS_DIA = 86_400_000, VENTANA = 90;

/**
 * Días que le quedan a un certificado que vence pronto, sobre la ventana de 90 días de «por vencer».
 * La barra se acorta a medida que se acerca la fecha; la tinta sube de alerta a mal en los últimos 30 días.
 * (La barra de ciclo de 3 años no sirve aquí: en esta lista todas las filas salen casi llenas.)
 */
export function VenceEn({ fecha }: { fecha: string | null }) {
  if (!fecha) return <span className="vence-en neutro">Sin fecha</span>;
  const restantes = Math.ceil((new Date(`${fecha}T12:00:00`).getTime() - Date.now()) / MS_DIA);
  const tono = restantes <= 30 ? 'mal' : 'alerta';
  const texto = restantes < 0 ? `Venció hace ${-restantes} días` : restantes === 0 ? 'Vence hoy' : restantes === 1 ? 'Queda 1 día' : `Quedan ${restantes} días`;
  const ancho = Math.max(0, Math.min(1, restantes / VENTANA)) * 100;
  return <span className={`vence-en ${tono}`} role="img" aria-label={`${texto}; vence el ${day(fecha)}`}>
    <span className="vence-en-texto tabular">{texto}</span>
    <span className="vence-en-pista"><span style={{ width: `${ancho}%` }} /></span>
  </span>;
}
