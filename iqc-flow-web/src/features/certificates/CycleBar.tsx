import { day } from '../../lib/format';

const MS_DIA = 86_400_000;
const parse = (value: string) => new Date(value + 'T12:00:00').getTime();

/**
 * Ciclo de certificación de un vistazo: de la emisión al vencimiento, con los aniversarios (vigilancias 1 y 2, que
 * se realizan antes de cumplirse) y la marca de hoy. Sin fechas no dibuja nada inventado.
 */
export function CycleBar({ start, end, inactive, detailed }: { start: string | null; end: string | null; inactive?: boolean; detailed?: boolean }) {
  if (!start || !end) return <span className="cycle-empty">Sin fechas</span>;
  const desde = parse(start), hasta = parse(end), hoy = Date.now();
  const total = Math.max(hasta - desde, MS_DIA);
  const pos = (t: number) => Math.min(100, Math.max(0, (t - desde) / total * 100));
  const aniversarios = [1, 2].map(n => { const d = new Date(desde); d.setFullYear(d.getFullYear() + n); return d.getTime(); }).filter(t => t < hasta);
  const restantes = Math.ceil((hasta - hoy) / MS_DIA);
  const estado = inactive ? 'off' : restantes < 0 ? 'vencido' : restantes <= 90 ? 'por-vencer' : 'vigente';
  const avance = pos(hoy);
  const texto = inactive ? 'Ciclo detenido' : restantes < 0 ? `Venció hace ${Math.abs(restantes)} días` : restantes === 0 ? 'Vence hoy' : `Quedan ${restantes} días`;
  return <div className={`cycle ${estado} ${detailed ? 'detailed' : ''}`} role="img" aria-label={`Ciclo del ${day(start)} al ${day(end)}. ${texto}.`}>
    <div className="cycle-track">
      <span className="cycle-fill" style={{ width: `${avance}%` }} />
      {aniversarios.map((t, i) => <span key={t} className="cycle-tick" style={{ left: `${pos(t)}%` }} title={`${i + 1}.ª vigilancia antes del ${day(new Date(t).toISOString().slice(0, 10))}`} />)}
      {hoy > desde && hoy < hasta && <span className="cycle-today" style={{ left: `${avance}%` }} />}
    </div>
    {detailed ? <div className="cycle-legend">
      <span><b>Emisión</b>{day(start)}</span>
      {aniversarios.map((t, i) => <span key={t}><b>{i + 1}.ª vigilancia</b>antes del {day(new Date(t).toISOString().slice(0, 10))}</span>)}
      <span><b>Vencimiento</b>{day(end)}</span>
    </div> : <span className="cycle-caption tabular">{texto}</span>}
  </div>;
}
