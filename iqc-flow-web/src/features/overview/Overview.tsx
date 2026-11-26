import { useEffect, useState } from 'react';
import { AlertTriangle, ArrowRight, CalendarCheck, CalendarClock, CalendarRange, CheckCircle2, Clock3, PauseCircle } from 'lucide-react';
import { api, type Certificate, type TaskCounts, type TaskView } from '../../lib/api';
import { day, errorText, number } from '../../lib/format';
import { FilaCifras, TarjetaCifra } from '../../components/cifras/TarjetaCifra';
import { VenceEn } from '../../components/VenceEn';
import { Indicators, type VistaIndicadores } from '../indicators/Indicators';
import { Boton } from '../../components/ui/Boton';

export function Overview({ canClients, canTasks, canCertificates, vista, go }: {
  canClients: boolean; canTasks: boolean; canCertificates: boolean; vista: VistaIndicadores;
  go: (screen: 'certificates' | 'clients' | 'tasks', vista?: TaskView) => void;
}) {
  const [tasks, setTasks] = useState<TaskCounts | null>(null);
  const [expiring, setExpiring] = useState<Certificate[] | null>(null), [expiringTotal, setExpiringTotal] = useState(0);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    const fail = (e: unknown) => { if (active) setError(errorText(e)); };
    if (canTasks) api.taskCounts().then(v => { if (active) setTasks(v); }).catch(fail);
    if (canCertificates) {
      api.certificates(new URLSearchParams({ estado: 'EMITIDO', vigencia: 'POR_VENCER', tamano: '50' })).then(r => {
        if (!active) return;
        setExpiringTotal(r.totalElementos);
        setExpiring([...r.contenido].sort((a, b) => (a.fechaExpiracion || '').localeCompare(b.fechaExpiracion || '')).slice(0, 6));
      }).catch(fail);
    }
    return () => { active = false; };
  }, [canTasks, canCertificates]);
  return <div className="screen-body">
    <div className="page-heading"><div><h1>Hoy</h1><p>Lo que necesita atención en IQC Perú e IQC LATAM.</p></div></div>
    {error && <div className="notice" role="alert"><AlertTriangle size={16} aria-hidden /> {error}</div>}

    {canTasks && <FilaCifras etiqueta="Mis seguimientos">
      <TarjetaCifra icono={Clock3} etiqueta="Atrasadas" valor={tasks?.atrasadas} detalle="Pasaron su fecha límite sin cerrarse"
        tono="mal" alPulsar={() => go('tasks', 'ATRASADAS')} />
      <TarjetaCifra icono={CalendarCheck} etiqueta="Para hoy" valor={tasks?.hoy} detalle="Vencen hoy" tono="alerta" alPulsar={() => go('tasks', 'HOY')} />
      <TarjetaCifra icono={CalendarRange} etiqueta="Próximas" valor={tasks?.proximas} detalle="En los próximos 7 días" alPulsar={() => go('tasks', 'PROXIMAS')} />
      <TarjetaCifra icono={PauseCircle} etiqueta="En espera" valor={tasks?.enEspera} detalle="Esperan una respuesta externa" tono="neutro" alPulsar={() => go('tasks', 'EN_ESPERA')} />
    </FilaCifras>}

    {canCertificates && <section className="panel">
      <div className="panel-title"><div><h2><CalendarClock size={18} aria-hidden /> Certificados por vencer</h2><p>Vencen en los próximos 90 días: {number(expiringTotal)}</p></div>
        <Boton variante="texto" onClick={() => go('certificates')}>Ver todos <ArrowRight size={14} aria-hidden /></Boton></div>
      {!expiring ? <div className="skeleton-rows" aria-hidden="true"><span /><span /><span /></div>
        : expiring.length === 0 ? <p className="empty-state"><CheckCircle2 size={18} aria-hidden /> Ningún certificado vence en los próximos 90 días.</p>
        : <ul className="expiring-list dos-columnas">{expiring.map(c => <li key={c.id}>
          <div><strong>{c.cliente.razonSocial}</strong><span className="mono">{c.codigo}</span><span>{c.designacion} · vence {day(c.fechaExpiracion)}</span></div>
          <VenceEn fecha={c.fechaExpiracion} />
        </li>)}</ul>}
    </section>}

    <Indicators vista={vista} />
  </div>;
}
