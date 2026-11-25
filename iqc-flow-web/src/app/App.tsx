import { useEffect, useState, type ComponentType } from 'react';
import {
  BadgeCheck, Bell, Building2, FileSignature, Globe2, LayoutDashboard, LayoutGrid, ListTodo,
  SlidersHorizontal, UserRound, Users as UsersIcon, X, type LucideProps,
} from 'lucide-react';
import { api, type CurrentUser, type TaskView } from '../lib/api';
import { errorText } from '../lib/format';
import { Cabecera, type Apariencia } from './cabecera/Cabecera';
import { FondoTematico } from './tematica/FondoTematico';
import { TEMATICAS, tematicaActiva } from './tematica/tematicas';
import { BrandMark } from '../components/BrandMark';
import { SimboloIqc } from '../components/SimboloIqc';
import type { VistaIndicadores } from '../features/indicators/Indicators';
import { Login } from '../features/auth/Login';
import { PasswordChange } from '../features/auth/PasswordChange';
import { Overview } from '../features/overview/Overview';
import { CertificatesHome, type CertificatesTarget } from '../features/certificates/CertificatesHome';
import { Certificates } from '../features/certificates/Certificates';
import { PersonalCertificates } from '../features/personal/PersonalCertificates';
import { Clients } from '../features/clients/Clients';
import { Tasks } from '../features/tasks/Tasks';
import { Brands } from '../features/brands/Brands';
import { Users } from '../features/users/Users';
import { Notices } from '../features/notices/Notices';
import { Settings } from '../features/settings/Settings';
import { Cotizaciones } from '../features/quotes/Cotizaciones';

type Screen = 'overview' | 'cert-home' | 'business' | 'personal' | 'clients' | 'quotes' | 'tasks' | 'brands' | 'users' | 'notices' | 'settings';
type ThemePreference = Apariencia;
type Params = { country?: string; validity?: string; view?: string };

interface ScreenDef { label: string; icon: ComponentType<LucideProps>; permission?: string }
const SCREENS: Record<Screen, ScreenDef> = {
  overview: { label: 'Hoy', icon: LayoutDashboard },
  'cert-home': { label: 'Resumen', icon: LayoutGrid, permission: 'certificados:leer' },
  business: { label: 'Empresariales', icon: BadgeCheck, permission: 'certificados:leer' },
  personal: { label: 'Personales', icon: UserRound, permission: 'certificados:leer' },
  clients: { label: 'Clientes', icon: Building2, permission: 'clientes:leer' },
  quotes: { label: 'Cotizaciones', icon: FileSignature, permission: 'cotizaciones:leer' },
  tasks: { label: 'Seguimientos', icon: ListTodo, permission: 'seguimientos:leer' },
  notices: { label: 'Notificaciones', icon: Bell },
  brands: { label: 'Marcas y portal', icon: Globe2, permission: 'certificados:leer' },
  users: { label: 'Usuarios', icon: UsersIcon, permission: 'usuarios:leer' },
  settings: { label: 'Configuración', icon: SlidersHorizontal, permission: 'organizacion:configurar' },
};

/** Cada rol trabaja en su propio espacio: su inicio y solo las secciones de su área */
interface Space { name: string; key: VistaIndicadores; home: Screen; groups: Array<[string, Screen[]]> }
const SPACES: Array<{ roles: string[]; space: Space }> = [
  { roles: ['MASTER', 'ADMINISTRADOR'], space: { name: 'Dirección', key: 'direccion', home: 'overview', groups: [
    ['General', ['overview']], ['Certificados', ['cert-home', 'business', 'personal']],
    ['Gestión', ['clients', 'quotes', 'tasks']], ['Configuración', ['settings', 'brands', 'users']]] } },
  { roles: ['CERTIFICADOS'], space: { name: 'Certificados', key: 'certificados', home: 'cert-home', groups: [
    ['Certificados', ['cert-home', 'business', 'personal']], ['Configuración', ['brands']]] } },
  { roles: ['RESPONSABLE_CERTIFICACIONES'], space: { name: 'Jefatura técnica', key: 'certificados', home: 'cert-home', groups: [
    ['Certificados', ['cert-home', 'business', 'personal']], ['Trabajo', ['tasks']], ['Configuración', ['brands']]] } },
  { roles: ['COORDINADOR_AUDITORIAS'], space: { name: 'Operaciones', key: 'operaciones', home: 'overview', groups: [
    ['Trabajo', ['overview', 'tasks', 'clients']], ['Certificados', ['business']]] } },
  { roles: ['COMERCIAL'], space: { name: 'Comercial', key: 'comercial', home: 'overview', groups: [
    ['Trabajo', ['overview', 'clients', 'quotes', 'tasks']], ['Consulta', ['business']]] } },
  { roles: ['ADMINISTRACION_LOGISTICA'], space: { name: 'Administración', key: 'administracion', home: 'overview', groups: [
    ['Trabajo', ['overview', 'quotes']], ['Certificados', ['business']]] } },
];
const FALLBACK: Space = { name: 'IQC Flow', key: 'direccion', home: 'overview', groups: [['Trabajo', ['overview']]] };
/**
 * Espacio de trabajo según los roles. Quien tiene varios roles (p. ej. una sola persona lleva Comercial y
 * Administración) ve un espacio que une sus secciones, sin repetir ninguna; Dirección ya lo ve todo.
 */
function spaceFor(roles: string[]): Space {
  const propios = SPACES.filter(s => s.roles.some(r => roles.includes(r))).map(s => s.space);
  if (!propios.length) return FALLBACK;
  if (propios.length === 1 || propios[0].key === 'direccion') return propios[0];
  const groups: Array<[string, Screen[]]> = [], vistas = new Set<Screen>();
  for (const s of propios) for (const [titulo, lista] of s.groups) {
    const nuevas = lista.filter(x => !vistas.has(x));
    nuevas.forEach(x => vistas.add(x));
    if (!nuevas.length) continue;
    const g = groups.find(([t]) => t === titulo);
    if (g) g[1].push(...nuevas); else groups.push([titulo, [...nuevas]]);
  }
  return { ...propios[0], name: propios.map(s => s.name).join(' y '), groups };
}

function preferredTheme(): ThemePreference {
  // Claro por defecto; Oscuro y Sistema solo si la persona los eligió
  try { const saved = localStorage.getItem('iqc-flow-theme'); return saved === 'dark' || saved === 'system' ? saved : 'light'; }
  catch { return 'light'; }
}

export function App() {
  const [user, setUser] = useState<CurrentUser | null>(null), [loading, setLoading] = useState(true);
  const [screen, setScreen] = useState<Screen | null>(null), [params, setParams] = useState<Params>({}), [visit, setVisit] = useState(0);
  const [menuOpen, setMenuOpen] = useState(false), [theme, setTheme] = useState<ThemePreference>(preferredTheme);
  const [unread, setUnread] = useState(0), [tematica, setTematica] = useState<string | undefined>();
  const [carrilOculto, setCarrilOculto] = useState(() => { try { return localStorage.getItem('iqc-flow-carril') === 'oculto'; } catch { return false; } });
  function alternarCarril() {
    setCarrilOculto(v => { try { if (v) localStorage.removeItem('iqc-flow-carril'); else localStorage.setItem('iqc-flow-carril', 'oculto'); } catch { /* solo esta sesión */ } return !v; });
  }
  useEffect(() => {
    const media = window.matchMedia('(prefers-color-scheme: dark)');
    const apply = () => { document.documentElement.dataset.theme = theme === 'system' ? (media.matches ? 'dark' : 'light') : theme; };
    apply();
    media.addEventListener('change', apply);
    return () => media.removeEventListener('change', apply);
  }, [theme]);
  useEffect(() => { api.me().then(setUser).catch(() => {}).finally(() => setLoading(false)); }, []);
  useEffect(() => {
    if (!user || user.debeCambiarContrasena) return;
    let active = true;
    const poll = () => api.unreadNotices().then(r => { if (active) setUnread(r.noLeidas); }).catch(() => {});
    poll();
    const timer = window.setInterval(poll, 60_000);
    return () => { active = false; window.clearInterval(timer); };
  }, [user, screen]);
  // Temática de temporada elegida por MASTER (vale para todos). Configuración avisa con un evento al cambiarla
  useEffect(() => {
    if (!user || user.debeCambiarContrasena) return;
    api.parameters().then(ps => setTematica(ps.find(p => p.clave === 'TEMATICA')?.valor)).catch(() => {});
    const alCambiar = (e: Event) => setTematica((e as CustomEvent<string>).detail);
    window.addEventListener('iqc-tematica', alCambiar);
    return () => window.removeEventListener('iqc-tematica', alCambiar);
  }, [user]);
  function changeTheme(value: ThemePreference) {
    setTheme(value);
    try { if (value === 'light') localStorage.removeItem('iqc-flow-theme'); else localStorage.setItem('iqc-flow-theme', value); } catch { /* solo esta sesión */ }
  }
  async function logout() {
    try { await api.logout(); setUser(null); setScreen(null); }
    catch (e) { window.alert(errorText(e)); }
  }
  if (loading) return <main className="center-screen" aria-busy="true"><BrandMark alto={64} /></main>;
  if (!user) return <Login onLogin={u => { setUser(u); setScreen(null); }} />;
  if (user.debeCambiarContrasena) return <PasswordChange user={user} onDone={setUser} />;

  const can = (permission?: string) => !permission || user.permisos.includes(permission);
  const space = spaceFor(user.roles);
  const allowed = (s: Screen) => can(SCREENS[s].permission);
  const groups = space.groups.map(([title, list]) => [title, list.filter(allowed)] as [string, Screen[]]).filter(([, list]) => list.length);
  // Notificaciones no está en el carril: se abre desde la campana de la cabecera
  const reachable = new Set<Screen>([...groups.flatMap(([, list]) => list), 'notices']);
  const home = allowed(space.home) ? space.home : groups[0]?.[1][0] ?? 'notices';
  const current: Screen = screen && reachable.has(screen) ? screen : home;
  const navigate = (target: Screen, next: Params = {}) => {
    setScreen(target); setParams(next); setVisit(n => n + 1); setMenuOpen(false);
    document.getElementById('contenido')?.scrollTo({ top: 0 });
  };
  const goCertificates = (target: CertificatesTarget | 'brands') => target === 'brands' ? navigate('brands')
    : navigate(target.screen, { country: target.country, validity: target.validity });

  const temporada = tematicaActiva(tematica);
  return <div className={`app-shell ${carrilOculto ? 'carril-oculto' : ''}`} data-tematica={temporada ?? undefined}>
    <aside className={`sidebar ${menuOpen ? 'open' : ''}`} aria-label="Navegación">
      <SimboloIqc />
      <div className="sidebar-head"><button className="sidebar-brand" onClick={() => navigate(home)} aria-label="Ir al inicio"><BrandMark alto={64} /></button>
        <button className="icon-button mobile-close" onClick={() => setMenuOpen(false)} aria-label="Cerrar menú"><X size={18} aria-hidden /></button></div>
      <div className="sidebar-scroll">
      <div className="space-name" title="Espacio de trabajo"><strong>{space.name}</strong></div>
      <nav>{groups.map(([title, list]) => <div className="nav-group" key={title}><span className="nav-heading">{title}</span>{list.map(target => {
        const { label, icon: Icon } = SCREENS[target];
        return <button key={target} className={current === target ? 'active' : ''} aria-current={current === target ? 'page' : undefined} onClick={() => navigate(target)}>
          <Icon size={18} aria-hidden /><span>{label}</span>
        </button>;
      })}</div>)}</nav>
      </div>
    </aside>
    <div className="app-main">
      {temporada && <FondoTematico clave={temporada} />}
      <Cabecera temporada={temporada ? TEMATICAS[temporada] : null} user={user} espacio={space.name} titulo={SCREENS[current].label} noLeidas={unread} verNotificaciones
        apariencia={theme} cambiarApariencia={changeTheme} carrilOculto={carrilOculto} alternarCarril={alternarCarril}
        abrirMenu={() => setMenuOpen(true)} irANotificaciones={() => navigate('notices')} cerrarSesion={logout} />
      <main id="contenido" key={`${current}-${visit}`}>
        {current === 'overview' ? <Overview canClients={can('clientes:leer')} canTasks={can('seguimientos:leer')} canCertificates={can('certificados:leer')}
            vista={space.key} go={(s, v) => navigate(s === 'certificates' ? 'business' : s, { view: v })} />
          : current === 'cert-home' ? <CertificatesHome canManage={can('certificados:gestionar')} go={goCertificates} />
          : current === 'business' ? <Certificates canManage={can('certificados:gestionar')} initialCountry={params.country} initialValidity={params.validity} />
          : current === 'personal' ? <PersonalCertificates canManage={can('certificados:gestionar')} initialCountry={params.country} />
          : current === 'clients' ? <Clients canWrite={can('clientes:escribir')} canArchive={can('clientes:archivar')} canReadActivities={can('seguimientos:leer')} canWriteActivities={can('seguimientos:escribir')} />
          : current === 'quotes' ? <Cotizaciones puedeGestionar={can('cotizaciones:gestionar')} puedeCrearClientes={can('clientes:escribir')} />
          : current === 'tasks' ? <Tasks canWrite={can('seguimientos:escribir')} canReadAll={can('seguimientos:leer-todos')} initialView={params.view as TaskView | undefined} />
          : current === 'brands' ? <Brands canConfigure={can('organizacion:configurar')} canPublish={can('certificados:gestionar')} />
          : current === 'settings' ? <Settings />
          : current === 'users' ? <Users canManage={can('usuarios:gestionar')} canAssign={can('roles:asignar')} />
          : <Notices />}
      </main>
    </div>
    {menuOpen && <button className="menu-scrim" aria-label="Cerrar menú" onClick={() => setMenuOpen(false)} />}
  </div>;
}
