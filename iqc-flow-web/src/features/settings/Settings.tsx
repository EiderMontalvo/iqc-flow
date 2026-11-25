import { useEffect, useState, type ComponentType } from 'react';
import { AlertTriangle, Bot, ClipboardList, Handshake, Palette, PenLine, UserCog, Wrench, type LucideProps } from 'lucide-react';
import { api, type AppUser, type OrgParameter } from '../../lib/api';
import { errorText, stamp } from '../../lib/format';
import { FIRMA, GRUPOS, type GrupoOpciones } from './opciones';
import { Interruptor } from './Interruptor';
import { OpcionesPorPersona } from './OpcionesPorPersona';
import { TematicaTemporada } from './TematicaTemporada';

type Seccion = 'funciones' | 'registro' | 'comercial' | 'administracion' | 'firma' | 'personas' | 'tematica';
interface DefSeccion { titulo: string; resumen: string; icono: ComponentType<LucideProps> }

/** Índice de Configuración, agrupado por tema */
const INDICE: Array<[string, Seccion[]]> = [
  ['Funciones', ['funciones', 'registro', 'comercial', 'administracion', 'firma']],
  ['Personas', ['personas']],
  ['Apariencia', ['tematica']],
];
const SECCIONES: Record<Seccion, DefSeccion> = {
  funciones: { titulo: 'Funciones generales', resumen: 'Inteligencia artificial y automatizaciones para toda la organización.', icono: Bot },
  registro: { titulo: 'Registro de certificados', resumen: 'Qué modos aparecen al registrar certificados empresariales y personales.', icono: ClipboardList },
  comercial: { titulo: 'Comercial', resumen: 'Cuándo se avisa al responsable de una cotización sin respuesta.', icono: Handshake },
  administracion: { titulo: 'Administración y logística', resumen: 'Herramientas del rol Administración.', icono: Wrench },
  firma: { titulo: 'Firma de certificados', resumen: 'Quién firma antes de emitir (DDC-PRO-003).', icono: PenLine },
  personas: { titulo: 'Opciones por persona', resumen: 'Apaga opciones a alguien concreto sin cambiar al resto.', icono: UserCog },
  tematica: { titulo: 'Temática de temporada', resumen: 'Fondo con íconos de la fecha y saludo para todos.', icono: Palette },
};
const grupo = (titulo: string) => GRUPOS.find(g => g.titulo === titulo)!;

/**
 * Configuración de MASTER, en secciones: a la izquierda el índice, a la derecha la sección elegida. Lo que se cambia
 * vale para toda la organización, salvo «Opciones por persona», que apaga opciones a alguien concreto.
 */
export function Settings() {
  const [params, setParams] = useState<Record<string, OrgParameter>>({}), [personas, setPersonas] = useState<AppUser[]>([]);
  const [seccion, setSeccion] = useState<Seccion>('funciones'), [error, setError] = useState('');
  const [guardando, setGuardando] = useState(''), [guardado, setGuardado] = useState('');
  useEffect(() => {
    api.parameters().then(list => setParams(Object.fromEntries(list.map(p => [p.clave, p])))).catch(e => setError(errorText(e)));
    api.users(new URLSearchParams({ pagina: '0', tamano: '100' }))
      .then(r => setPersonas(r.contenido.filter(u => u.estado === 'ACTIVO').sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'))))
      .catch(e => setError(errorText(e)));
  }, []);

  async function guardar(marca: string, accion: () => Promise<OrgParameter>) {
    setGuardando(marca); setError(''); setGuardado('');
    try {
      const p = await accion();
      setParams(all => ({ ...all, [p.clave]: p })); setGuardado(marca);
      window.setTimeout(() => setGuardado(s => s === marca ? '' : s), 1800);
    } catch (e) { setError(errorText(e)); } finally { setGuardando(''); }
  }
  const cambiar = (clave: string, valor: string) => guardar(clave, async () => {
    const p = await api.setParameter(clave, valor);
    // La temática se ve al instante en este panel; los demás la toman al recargar
    if (clave === 'TEMATICA') window.dispatchEvent(new CustomEvent('iqc-tematica', { detail: p.valor }));
    return p;
  });
  const cambiarPersona = (clave: string, usuarioId: string, habilitado: boolean) =>
    guardar(`${clave}:${usuarioId}`, () => api.setParameterForPerson(clave, usuarioId, habilitado));
  const nombre = (id: string) => personas.find(p => p.id === id)?.nombre ?? 'una persona inactiva';
  const apagadasPorPersona = Object.values(params).reduce((t, p) => t + (p.desactivadoPara?.length ?? 0), 0);

  /** Lista de interruptores de un grupo, con a quién se le apagó cada opción */
  const interruptores = (g: GrupoOpciones) => <ul className="switch-list">{g.opciones.map(o => {
    const p = params[o.clave], on = p?.valor === 'SI', fuera = p?.desactivadoPara ?? [];
    return <Interruptor key={o.clave} titulo={o.titulo} detalle={o.detalle} encendido={on}
      ocupado={!p || guardando === o.clave} guardado={guardado === o.clave} alCambiar={() => cambiar(o.clave, on ? 'NO' : 'SI')}
      nota={<>
        {on && fuera.length > 0 && <small className="nota-excepcion">Apagada para {fuera.map(nombre).join(', ')}.{' '}
          <button type="button" className="enlace-texto" onClick={() => setSeccion('personas')}>Cambiar por persona</button></small>}
        {p && !p.porDefecto && p.actualizadoEn && <small>Cambiado el {stamp(p.actualizadoEn)}</small>}
      </>} />;
  })}</ul>;
  const firma = params.POLITICA_FIRMA_CERTIFICADOS, def = SECCIONES[seccion];

  return <div className="screen-body">
    <div className="page-heading"><div><h1>Configuración</h1>
      <p>Lo que cambies aquí vale para toda la organización. Cada cambio queda en la bitácora.</p></div></div>
    {error && <div className="notice" role="alert"><AlertTriangle size={16} aria-hidden /> {error}</div>}

    <div className="config">
      <nav className="config-indice" aria-label="Secciones de configuración">{INDICE.map(([titulo, lista]) => <div key={titulo}>
        <span>{titulo}</span>
        {lista.map(s => { const { titulo: t, icono: Icono } = SECCIONES[s];
          return <button key={s} type="button" aria-current={seccion === s ? 'page' : undefined} onClick={() => setSeccion(s)}>
            <Icono size={17} aria-hidden /><span>{t}</span>
            {s === 'personas' && apagadasPorPersona > 0 && <small className="tabular" title="Opciones apagadas a alguna persona">{apagadasPorPersona}</small>}
          </button>; })}
      </div>)}</nav>

      <section className="config-contenido" aria-labelledby="config-titulo">
        <header className="config-cabecera"><h2 id="config-titulo">{def.titulo}</h2><p>{def.resumen}</p></header>
        {seccion === 'funciones' && <div className="panel">{interruptores(grupo('General'))}</div>}
        {seccion === 'registro' && <div className="config-dos">
          {['Registro de certificados empresariales', 'Registro de certificados personales'].map(t => { const g = grupo(t);
            return <div className="panel" key={t}><div className="panel-title"><div><h3>{t.replace('Registro de certificados ', 'Certificados ')}</h3><p>{g.descripcion}</p></div></div>{interruptores(g)}</div>; })}
        </div>}
        {seccion === 'comercial' && params.COTIZACION_DIAS_SIN_RESPUESTA && <div className="panel config-firma">
          <label>Avisar de una cotización sin respuesta a los
            <select value={params.COTIZACION_DIAS_SIN_RESPUESTA.valor} disabled={guardando === 'COTIZACION_DIAS_SIN_RESPUESTA'} onChange={e => cambiar('COTIZACION_DIAS_SIN_RESPUESTA', e.target.value)}>
              {params.COTIZACION_DIAS_SIN_RESPUESTA.admitidos.map(v => <option key={v} value={v}>{v} días de enviada</option>)}</select></label>
          <small>Se crea una tarea «Recordatorio de cotización» a su responsable, una sola vez. Si el cliente responde antes, no se crea; si responde después, se cierra sola.</small>
        </div>}
        {seccion === 'administracion' && <div className="panel">
          <p className="config-nota">{grupo('Administración y logística').descripcion}</p>{interruptores(grupo('Administración y logística'))}</div>}
        {seccion === 'firma' && firma && <div className="panel config-firma">
          <label>Política de firma<select value={firma.valor} disabled={guardando === 'POLITICA_FIRMA_CERTIFICADOS'} onChange={e => cambiar('POLITICA_FIRMA_CERTIFICADOS', e.target.value)}>
            {firma.admitidos.map(v => <option key={v} value={v}>{FIRMA[v] || v}</option>)}</select></label>
          {!firma.porDefecto && firma.actualizadoEn && <small>Cambiado el {stamp(firma.actualizadoEn)}</small>}
        </div>}
        {seccion === 'personas' && <OpcionesPorPersona personas={personas} params={params} guardando={guardando} guardado={guardado} alCambiar={cambiarPersona} />}
        {seccion === 'tematica' && <TematicaTemporada valor={params.TEMATICA?.valor} ocupado={guardando === 'TEMATICA'} alElegir={v => cambiar('TEMATICA', v)} />}
      </section>
    </div>
  </div>;
}
