import { useEffect, useState } from 'react';
import { AlertTriangle, ArrowRight, BadgeCheck, CalendarClock, CheckCircle2, Plus, UserRound } from 'lucide-react';
import { api, type Brand, type BrandSummary, type Certificate, type PersonalCertificate, type PersonalSummary, type PublicationStatus } from '../../lib/api';
import { day, errorText, number } from '../../lib/format';
import { VenceEn } from '../../components/VenceEn';
import { TarjetaTipo } from './resumen/TarjetaTipo';
import { Indicators } from '../indicators/Indicators';

export type CertificatesTarget = { screen: 'business' | 'personal'; country?: string; validity?: 'VIGENTE' | 'POR_VENCER' | 'VENCIDO' };

/** Inicio del espacio de Certificados: empresariales y personales, por país o marca */
export function CertificatesHome({ canManage, go }: { canManage: boolean; go: (target: CertificatesTarget | 'brands') => void }) {
  const [business, setBusiness] = useState<BrandSummary[] | null>(null), [personal, setPersonal] = useState<PersonalSummary[] | null>(null);
  const [brands, setBrands] = useState<Brand[]>([]), [expiring, setExpiring] = useState<Certificate[] | null>(null);
  const [latest, setLatest] = useState<PersonalCertificate[] | null>(null), [publication, setPublication] = useState<PublicationStatus | null>(null);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    const fail = (e: unknown) => { if (active) setError(errorText(e)); };
    api.certificateSummary().then(v => { if (active) setBusiness(v); }).catch(fail);
    api.personalSummary().then(v => { if (active) setPersonal(v); }).catch(fail);
    api.brands().then(v => { if (active) setBrands(v); }).catch(() => {});
    api.publication().then(v => { if (active) setPublication(v); }).catch(() => {});
    api.certificates(new URLSearchParams({ estado: 'EMITIDO', vigencia: 'POR_VENCER', tamano: '50' })).then(r => {
      if (active) setExpiring([...r.contenido].sort((a, b) => (a.fechaExpiracion || '').localeCompare(b.fechaExpiracion || '')).slice(0, 5));
    }).catch(fail);
    api.personalCertificates(new URLSearchParams({ tamano: '5' })).then(r => { if (active) setLatest(r.contenido); }).catch(fail);
    return () => { active = false; };
  }, []);
  const sum = (key: keyof Omit<BrandSummary, 'codigoPais'>) => business?.reduce((t, b) => t + b[key], 0);
  const personalTotal = personal?.reduce((t, p) => t + p.emitidos, 0);
  const personalVoid = personal?.reduce((t, p) => t + p.anulados, 0);
  const countries = brands.length ? brands.map(b => b.codigoPais) : (business || []).map(b => b.codigoPais);
  const nameOf = (code: string) => brands.find(b => b.codigoPais === code)?.nombre || code;
  const rowB = (code: string) => business?.find(b => b.codigoPais === code);
  const rowP = (code: string) => personal?.find(p => p.codigoPais === code);
  const failures = publication?.errores.length ?? 0;
  return <div className="screen-body">
    <div className="page-heading"><div><h1>Certificados</h1><p>Empresariales y personales de IQC Perú e IQC LATAM.</p></div>
      {canManage && <div className="heading-actions">
        <button className="secondary" onClick={() => go({ screen: 'personal' })}><Plus size={16} aria-hidden /> Personal</button>
        <button className="primary" onClick={() => go({ screen: 'business' })}><Plus size={16} aria-hidden /> Empresarial</button></div>}</div>
    {error && <div className="notice" role="alert"><AlertTriangle size={16} aria-hidden /> {error}</div>}

    <section className="kind-split" aria-label="Resumen por tipo">
      <TarjetaTipo icono={BadgeCheck} titulo="Certificados empresariales" valor={sum('vigentes')} unidad="vigentes" alAbrir={() => go({ screen: 'business' })}
        datos={[
          { etiqueta: 'Vencen en 90 días', valor: number(sum('porVencer')), tono: 'alerta', alPulsar: () => go({ screen: 'business', validity: 'POR_VENCER' }) },
          { etiqueta: 'Vencidos sin renovar', valor: number(sum('vencidos')), tono: 'mal', alPulsar: () => go({ screen: 'business', validity: 'VENCIDO' }) },
          { etiqueta: 'Suspendidos', valor: number(sum('suspendidos')), tono: 'neutro' },
        ]} />
      <TarjetaTipo icono={UserRound} titulo="Certificados personales" valor={personalTotal} unidad="emitidos" alAbrir={() => go({ screen: 'personal' })}
        datos={[
          { etiqueta: 'Último emitido', valor: latest?.[0] ? day(latest[0].fechaEmision) : '—' },
          { etiqueta: 'Anulados', valor: number(personalVoid), tono: 'neutro' },
        ]} />
    </section>

    <div className="overview-grid">
      <section className="panel">
        <div className="panel-title"><div><h2><CalendarClock size={18} aria-hidden /> Vencen pronto</h2><p>Empresariales en los próximos 90 días</p></div>
          <button className="text-button" onClick={() => go({ screen: 'business', validity: 'POR_VENCER' })}>Ver todos <ArrowRight size={14} aria-hidden /></button></div>
        {!expiring ? <div className="skeleton-rows" aria-hidden="true"><span /><span /></div> : expiring.length === 0
          ? <p className="empty-state"><CheckCircle2 size={18} aria-hidden /> Ningún certificado vence en los próximos 90 días.</p>
          : <ul className="expiring-list">{expiring.map(c => <li key={c.id}><div><strong>{c.cliente.razonSocial}</strong><span className="mono">{c.codigo}</span><span>{c.designacion} · vence {day(c.fechaExpiracion)}</span></div>
            <VenceEn fecha={c.fechaExpiracion} /></li>)}</ul>}
      </section>
      <section className="panel">
        <div className="panel-title"><div><h2><UserRound size={18} aria-hidden /> Últimos personales</h2><p>Emitidos más recientemente</p></div>
          <button className="text-button" onClick={() => go({ screen: 'personal' })}>Ver todos <ArrowRight size={14} aria-hidden /></button></div>
        {!latest ? <div className="skeleton-rows" aria-hidden="true"><span /><span /></div> : latest.length === 0
          ? <p className="empty-state">Aún no hay certificados personales.</p>
          : <ul className="latest-list">{latest.map(c => <li key={c.id}><div><strong>{c.persona?.nombres || '—'}</strong><span>{c.curso}</span></div><span className="mono">{c.codigo}</span><span className="tabular">{day(c.fechaEmision)}</span></li>)}</ul>}
      </section>
    </div>

    <Indicators vista="certificados" />

    <section className="panel">
      <div className="panel-title"><div><h2>Por país y marca</h2><p>Certificados de cada marca y estado de su portal de consulta.</p></div>
        <button className="text-button" onClick={() => go('brands')}>Marcas y portal <ArrowRight size={14} aria-hidden /></button></div>
      <div className="table-wrap"><table className="brand-table">
        <thead><tr><th>Marca</th><th className="num">Empresariales vigentes</th><th className="num">Por vencer</th><th className="num">Vencidos</th><th className="num">Suspendidos</th><th className="num">Personales</th><th>Portal de consulta</th></tr></thead>
        <tbody>{countries.map(code => { const b = rowB(code), p = rowP(code); const connected = publication?.paises.includes(code); return <tr key={code}>
          <td><strong>{nameOf(code)}</strong><small>{code}</small></td>
          <td className="num"><button className="cell-link" disabled={!b?.vigentes} onClick={() => go({ screen: 'business', country: code, validity: 'VIGENTE' })}>{number(b?.vigentes)}</button></td>
          <td className="num"><button className="cell-link warn" disabled={!b?.porVencer} onClick={() => go({ screen: 'business', country: code, validity: 'POR_VENCER' })}>{number(b?.porVencer)}</button></td>
          <td className="num"><button className="cell-link bad" disabled={!b?.vencidos} onClick={() => go({ screen: 'business', country: code, validity: 'VENCIDO' })}>{number(b?.vencidos)}</button></td>
          <td className="num"><span className="cell-link estatico">{number(b?.suspendidos)}</span></td>
          <td className="num"><button className="cell-link" disabled={!p?.emitidos} onClick={() => go({ screen: 'personal', country: code })}>{number(p?.emitidos ?? 0)}</button></td>
          <td>{publication ? <span className="status-label"><span className={`status-dot ${connected ? 'activo' : 'inactivo'}`} />{connected ? 'En línea' : 'Sin publicar'}</span> : '—'}</td>
        </tr>; })}</tbody></table></div>
      {failures > 0 && <button className="text-button danger" onClick={() => go('brands')}><AlertTriangle size={14} aria-hidden /> {failures} certificados con error de publicación</button>}
    </section>

  </div>;
}
