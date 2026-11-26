import type { AnchorHTMLAttributes, ButtonHTMLAttributes, ComponentPropsWithRef, ComponentType } from 'react';
import type { LucideProps } from 'lucide-react';
import { LoaderCircle } from 'lucide-react';

/**
 * Botones del panel: un solo componente por vocabulario visual, con los estilos de `components.css` (tokens, sin colores
 * sueltos). `primario` es la acción principal de la pantalla; `secundario`, las demás; `texto` es un enlace con forma de botón;
 * `peligro` rellena de rojo lo irreversible. Por defecto es `type="button"`: el que guarda un formulario debe declarar
 * `type="submit"`, así un botón nuevo nunca envía un formulario por accidente.
 */
type Variante = 'primario' | 'secundario' | 'texto' | 'peligro';

const CLASE: Record<Variante, string> = {
  primario: 'primary', secundario: 'secondary', texto: 'text-button', peligro: 'primary danger-fill',
};

/** Con `ref` (React 19 lo recibe como un prop más): sirve para llevar el foco a un botón concreto, p. ej. «Cancelar» al abrir una confirmación */
interface BotonProps extends ComponentPropsWithRef<'button'> {
  variante?: Variante;
  /** Más compacto (filtros, barras de herramientas) */
  pequeno?: boolean;
  /** Ocupa todo el ancho (formularios de acceso) */
  ancho?: boolean;
  /** Muestra el giro y bloquea el botón mientras la acción está en curso */
  cargando?: boolean;
  icono?: ComponentType<LucideProps>;
}

export function Boton({ variante = 'primario', pequeno, ancho, cargando, icono: Icono, className, disabled, type = 'button', children, ...resto }: BotonProps) {
  const clase = [CLASE[variante], pequeno && 'small', ancho && 'wide', className].filter(Boolean).join(' ');
  return <button {...resto} type={type} className={clase} disabled={disabled || cargando} aria-busy={cargando || undefined}>
    {cargando ? <LoaderCircle className="spin" size={16} aria-hidden /> : Icono && <Icono size={16} aria-hidden />}{children}
  </button>;
}

interface BotonIconoProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'aria-label'> {
  /** Obligatorio: un botón sin texto necesita un nombre para el lector de pantalla y el tooltip */
  etiqueta: string;
  icono: ComponentType<LucideProps>;
  peligro?: boolean;
  tamano?: number;
}

export function BotonIcono({ etiqueta, icono: Icono, peligro, tamano = 18, className, ...resto }: BotonIconoProps) {
  const clase = ['icon-button', peligro && 'danger', className].filter(Boolean).join(' ');
  return <button {...resto} type={resto.type ?? 'button'} className={clase} aria-label={etiqueta} title={etiqueta}><Icono size={tamano} aria-hidden /></button>;
}

/** Un enlace (descarga, otra pestaña) con la misma forma y el mismo ícono que un botón: para lo que navega en vez de actuar */
export function BotonEnlace({ variante = 'secundario', pequeno, icono: Icono, className, children, ...resto }: Omit<AnchorHTMLAttributes<HTMLAnchorElement>, 'className'> & {
  variante?: Exclude<Variante, 'peligro'>; pequeno?: boolean; icono?: ComponentType<LucideProps>; className?: string;
}) {
  const clase = [CLASE[variante], pequeno && 'small', className].filter(Boolean).join(' ');
  return <a {...resto} className={clase}>{Icono && <Icono size={16} aria-hidden />}{children}</a>;
}
