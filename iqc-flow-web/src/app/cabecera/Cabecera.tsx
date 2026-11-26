import { useEffect, useRef, useState, type ComponentType } from 'react';
import { createPortal } from 'react-dom';
import { Bell, ChevronDown, LogOut, Menu, Monitor, Moon, PanelLeftClose, PanelLeftOpen, Sun, UserRound, type LucideProps } from 'lucide-react';
import type { CurrentUser } from '../../lib/api';
import { initials } from '../../lib/format';
import { PerfilDialogo } from './PerfilDialogo';
import type { Tematica } from '../tematica/tematicas';
import { BotonIcono } from '../../components/ui/Boton';

const fechaLarga = new Intl.DateTimeFormat('es-PE', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
const saludo = (hora: number) => hora < 12 ? 'Buenos días' : hora < 19 ? 'Buenas tardes' : 'Buenas noches';
const primerNombre = (nombre: string) => nombre.trim().split(/\s+/)[0] || nombre;

export type Apariencia = 'system' | 'dark' | 'light';
const APARIENCIAS: Array<[Apariencia, string, ComponentType<LucideProps>]> = [['light', 'Claro', Sun], ['dark', 'Oscuro', Moon], ['system', 'Sistema', Monitor]];

interface Props {
  user: CurrentUser; espacio: string; titulo: string; noLeidas: number; verNotificaciones: boolean;
  /** Temática de temporada vigente: su saludo completa la frase del de la hora */
  temporada: Tematica | null;
  apariencia: Apariencia; cambiarApariencia: (a: Apariencia) => void;
  /** Escritorio: el carril lateral está oculto */
  carrilOculto: boolean; alternarCarril: () => void;
  abrirMenu: () => void; irANotificaciones: () => void; cerrarSesion: () => void;
}

/**
 * Cabecera del área de trabajo: saludo con el nombre y la fecha, notificaciones y menú de perfil.
 * En el celular es la barra negra superior con el botón del menú y el título de la pantalla.
 */
export function Cabecera({ user, espacio, titulo, noLeidas, verNotificaciones, temporada, apariencia, cambiarApariencia, carrilOculto, alternarCarril, abrirMenu, irANotificaciones, cerrarSesion }: Props) {
  const [abierto, setAbierto] = useState(false), [perfil, setPerfil] = useState(false);
  const menu = useRef<HTMLDivElement>(null), ahora = new Date();
  useEffect(() => {
    if (!abierto) return;
    const fuera = (e: PointerEvent) => { if (!menu.current?.contains(e.target as Node)) setAbierto(false); };
    const escape = (e: KeyboardEvent) => { if (e.key === 'Escape') setAbierto(false); };
    document.addEventListener('pointerdown', fuera); document.addEventListener('keydown', escape);
    return () => { document.removeEventListener('pointerdown', fuera); document.removeEventListener('keydown', escape); };
  }, [abierto]);
  const fecha = fechaLarga.format(ahora);

  return <header className="cabecera">
    <BotonIcono etiqueta="Abrir menú" icono={Menu} tamano={20} className="menu-button" onClick={abrirMenu} />
    <button className="cabecera-boton alternar-carril" onClick={alternarCarril} aria-pressed={carrilOculto}
      aria-label={carrilOculto ? 'Mostrar menú lateral' : 'Ocultar menú lateral'} title={carrilOculto ? 'Mostrar menú lateral' : 'Ocultar menú lateral'}>
      {carrilOculto ? <PanelLeftOpen size={19} aria-hidden /> : <PanelLeftClose size={19} aria-hidden />}
    </button>
    <div className="cabecera-saludo">
      {/* En temporada, una sola frase: «Buenos días, Ana. ¡Feliz Navidad!» */}
      <strong>{saludo(ahora.getHours())}, {primerNombre(user.nombre)}{temporada ? <>. <span className="cabecera-temporada">{temporada.saludo}</span></> : null}</strong>
      <span><span className="cabecera-fecha">{fecha.charAt(0).toUpperCase() + fecha.slice(1)}</span> · {espacio}</span>
    </div>
    <span className="cabecera-titulo">{titulo}</span>
    <div className="cabecera-acciones">
      {verNotificaciones && <button className="cabecera-boton" onClick={irANotificaciones}
        aria-label={noLeidas ? `Notificaciones: ${noLeidas} sin leer` : 'Notificaciones'} title="Notificaciones">
        <Bell size={19} aria-hidden />{noLeidas > 0 && <span className="cabecera-insignia tabular" aria-hidden>{noLeidas > 99 ? '99+' : noLeidas}</span>}
      </button>}
      <div className="cabecera-perfil" ref={menu}>
        <button className="cabecera-cuenta" onClick={() => setAbierto(!abierto)} aria-expanded={abierto} aria-haspopup="menu">
          <span className="avatar-cabecera" aria-hidden>{initials(user.nombre)}</span>
          <span className="cabecera-cuenta-nombre">{user.nombre}</span>
          <ChevronDown size={16} aria-hidden />
        </button>
        {abierto && <div className="menu-perfil" role="menu">
          <div className="menu-perfil-quien"><strong>{user.nombre}</strong><span>{user.correo}</span><span>{espacio} · {user.organizacion.nombre}</span></div>
          <div className="menu-perfil-apariencia" role="radiogroup" aria-label="Apariencia"><span>Apariencia</span>
            <div>{APARIENCIAS.map(([clave, nombre, Icono]) => <button key={clave} type="button" role="radio" aria-checked={apariencia === clave}
              className={apariencia === clave ? 'elegida' : ''} onClick={() => cambiarApariencia(clave)}><Icono size={15} aria-hidden />{nombre}</button>)}</div></div>
          <button role="menuitem" onClick={() => { setAbierto(false); setPerfil(true); }}><UserRound size={16} aria-hidden /> Mi perfil, firma y contraseña</button>
          <button role="menuitem" className="peligro" onClick={() => { setAbierto(false); cerrarSesion(); }}><LogOut size={16} aria-hidden /> Cerrar sesión</button>
        </div>}
      </div>
    </div>
    {/* Fuera de la cabecera: el cajón cubre toda la ventana */}
    {perfil && createPortal(<PerfilDialogo user={user} espacio={espacio} cerrar={() => setPerfil(false)} />, document.body)}
  </header>;
}
