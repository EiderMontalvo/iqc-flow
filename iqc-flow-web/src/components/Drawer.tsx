import { useEffect, useRef, type ReactNode } from 'react';
import { X } from 'lucide-react';

/** Panel lateral con foco protegido: Escape o el fondo lo cierran y el foco vuelve a donde estaba */
export function Drawer({ title, subtitle, onClose, wide, vista, children }: {
  title: ReactNode; subtitle?: ReactNode; onClose: () => void; wide?: boolean;
  /** Panel a la derecha (p. ej. el PDF): el cajón pasa a espacio de trabajo dividido, cada lado con su desplazamiento */
  vista?: ReactNode; children: ReactNode;
}) {
  const panel = useRef<HTMLElement>(null);
  const close = useRef(onClose);
  close.current = onClose;
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    panel.current?.focus();
    const key = (event: KeyboardEvent) => { if (event.key === 'Escape') close.current(); };
    window.addEventListener('keydown', key);
    document.body.classList.add('drawer-open');
    return () => { window.removeEventListener('keydown', key); document.body.classList.remove('drawer-open'); previous?.focus?.(); };
  }, []);
  return <div className="dialog-backdrop" onMouseDown={event => { if (event.target === event.currentTarget) onClose(); }}>
    <section ref={panel} tabIndex={-1} className={`dialog ${vista ? 'dialog-espacio' : wide ? 'dialog-wide' : ''}`} role="dialog" aria-modal="true" aria-labelledby="drawer-title">
      <header className="dialog-header">
        <div><h2 id="drawer-title">{title}</h2>{subtitle && <p className="dialog-subtitle">{subtitle}</p>}</div>
        <button type="button" className="icon-button" onClick={onClose} aria-label="Cerrar"><X size={18} aria-hidden /></button>
      </header>
      {vista ? <div className="espacio-trabajo"><div className="espacio-formulario">{children}</div><aside className="espacio-vista" aria-label="Vista previa">{vista}</aside></div>
        : children}
    </section>
  </div>;
}
