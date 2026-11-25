import { useEffect, useState } from 'react';
import { Check } from 'lucide-react';
import { api, type ClientSummary } from '../lib/api';

/**
 * Busca un cliente por razón social, código o RUC. Si se busca un RUC que no es cliente, muestra qué empresa es según
 * SUNAT para confirmarla antes de registrarla en Clientes.
 */
export function BuscadorCliente({ etiqueta = 'Cliente', value, onChange, initialQuery = '' }: {
  etiqueta?: string; value: ClientSummary | null; onChange: (client: ClientSummary | null) => void; initialQuery?: string;
}) {
  const [query, setQuery] = useState(value?.razonSocial || initialQuery), [matches, setMatches] = useState<ClientSummary[]>([]);
  useEffect(() => {
    if (value || query.trim().length < 2) { setMatches([]); return; }
    let active = true;
    const timer = window.setTimeout(() => {
      api.clients(new URLSearchParams({ texto: query.trim(), tamano: '8' }))
        .then(result => { if (active) setMatches(result.contenido); }).catch(() => { if (active) setMatches([]); });
    }, 250);
    return () => { active = false; window.clearTimeout(timer); };
  }, [query, value]);
  // Un RUC que no está como cliente: se muestra qué empresa es según SUNAT, para confirmar antes de registrarla
  const [sunat, setSunat] = useState<string | null>(null);
  const rucBuscado = /^(10|15|16|17|20)\d{9}$/.test(query.trim()) ? query.trim() : null;
  useEffect(() => {
    setSunat(null);
    if (!rucBuscado || value || matches.length) return;
    let active = true;
    api.padronRuc(rucBuscado).then(d => { if (active) setSunat(d.razonSocial); }).catch(() => {});
    return () => { active = false; };
  }, [rucBuscado, value, matches.length]);
  return <div className="client-picker full">
    <label>{etiqueta}<input value={query} required onChange={event => { setQuery(event.target.value); onChange(null); }} placeholder="Razón social, código o RUC" autoComplete="off" /></label>
    {value && <p className="field-help"><Check size={14} aria-hidden /> {value.codigo} · {value.tipoDocumento || 'Documento'} {value.numeroDocumento || 'sin registrar'} <button type="button" className="text-button" onClick={() => { setQuery(''); onChange(null); }}>Cambiar</button></p>}
    {!value && matches.length > 0 && <div className="picker-results" role="listbox">{matches.map(client =>
      <button type="button" role="option" aria-selected="false" key={client.id} onClick={() => { onChange(client); setQuery(client.razonSocial); setMatches([]); }}>
        <strong>{client.razonSocial}</strong><span className="tabular">{client.codigo} · {client.numeroDocumento || 'sin documento'}</span>
      </button>)}</div>}
    {!value && query.trim().length >= 2 && matches.length === 0 && <p className="field-help">
      {sunat ? <>Ese RUC no está registrado como cliente. Según SUNAT es <strong>{sunat}</strong>: regístralo primero en Clientes.</>
        : 'Sin coincidencias. Si el cliente es nuevo, regístralo primero en Clientes.'}</p>}
  </div>;
}
