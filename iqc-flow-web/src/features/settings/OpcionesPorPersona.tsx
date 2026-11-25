import { useMemo, useState } from 'react';
import { Search, UserRound } from 'lucide-react';
import type { AppUser, OrgParameter } from '../../lib/api';
import { GRUPOS } from './opciones';
import { Interruptor } from './Interruptor';

interface Props {
  personas: AppUser[];
  params: Record<string, OrgParameter>;
  guardando: string; guardado: string;
  alCambiar: (clave: string, usuarioId: string, habilitado: boolean) => void;
}

/** Grupos que tienen opciones que se pueden apagar a una persona */
const GRUPOS_POR_PERSONA = (params: Record<string, OrgParameter>) => GRUPOS
  .map(g => ({ ...g, opciones: g.opciones.filter(o => params[o.clave]?.porPersona) }))
  .filter(g => g.opciones.length);

/**
 * Opciones de MASTER para una persona: se elige a alguien de la lista y se le apagan las opciones que no debe usar,
 * aunque sigan encendidas para el resto. Una opción apagada para toda la organización no se puede encender aquí.
 */
export function OpcionesPorPersona({ personas, params, guardando, guardado, alCambiar }: Props) {
  const [texto, setTexto] = useState(''), [elegidaId, setElegidaId] = useState<string | null>(null);
  const grupos = useMemo(() => GRUPOS_POR_PERSONA(params), [params]);
  const apagadas = (id: string) => grupos.reduce((n, g) => n + g.opciones.filter(o => params[o.clave]?.desactivadoPara.includes(id)).length, 0);
  const filtro = texto.trim().toLowerCase();
  const visibles = personas.filter(p => !filtro || p.nombre.toLowerCase().includes(filtro) || p.correo.toLowerCase().includes(filtro));
  const elegida = personas.find(p => p.id === elegidaId) ?? visibles[0] ?? null;

  return <section className="panel por-persona" aria-label="Opciones por persona">
    <div className="por-persona-lista">
      <label className="search-field"><Search size={16} aria-hidden /><span className="sr-only">Buscar persona</span>
        <input value={texto} onChange={e => setTexto(e.target.value)} placeholder="Buscar por nombre o correo" /></label>
      <ul role="listbox" aria-label="Personas">{visibles.map(p => { const n = apagadas(p.id); return <li key={p.id}>
        <button type="button" role="option" aria-selected={elegida?.id === p.id} onClick={() => setElegidaId(p.id)}>
          <span className="por-persona-nombre"><strong>{p.nombre}</strong><small>{p.roles.join(', ') || 'Sin rol'}</small></span>
          {n > 0 && <span className="por-persona-cuenta" title={`${n} ${n === 1 ? 'opción apagada' : 'opciones apagadas'}`}>{n} {n === 1 ? 'apagada' : 'apagadas'}</span>}
        </button></li>; })}</ul>
      {!visibles.length && <p className="empty-state">Nadie coincide con la búsqueda.</p>}
    </div>

    <div className="por-persona-detalle">
      {!elegida ? <p className="empty-state">Elige una persona para ver sus opciones.</p> : <>
        <header className="por-persona-cabecera">
          <span className="avatar-claro" aria-hidden><UserRound size={18} /></span>
          <div><h2>{elegida.nombre}</h2><p>{elegida.correo} · {elegida.roles.join(', ') || 'Sin rol'}</p></div>
        </header>
        <p className="por-persona-ayuda">Apaga lo que esta persona no debe usar. El resto de la organización no cambia.</p>
        {grupos.map(g => <div className="por-persona-grupo" key={g.titulo}>
          <h3>{g.titulo}</h3>
          <ul className="switch-list">{g.opciones.map(o => {
            const p = params[o.clave], deTodos = p.valor === 'SI', suya = !p.desactivadoPara.includes(elegida.id);
            const clave = `${o.clave}:${elegida.id}`;
            return <Interruptor key={o.clave} titulo={o.titulo} detalle={o.detalle}
              nota={!deTodos ? <small className="nota-bloqueo">Apagada para toda la organización: enciéndela primero en su sección de Configuración.</small> : undefined}
              encendido={deTodos && suya} bloqueado={!deTodos} ocupado={guardando === clave} guardado={guardado === clave}
              alCambiar={() => alCambiar(o.clave, elegida.id, !suya)} />;
          })}</ul>
        </div>)}
      </>}
    </div>
  </section>;
}
