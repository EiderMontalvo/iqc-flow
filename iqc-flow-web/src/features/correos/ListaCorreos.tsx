import { type KeyboardEvent } from 'react';
import { Inbox, Paperclip } from 'lucide-react';
import type { MailSummary, Page } from '../../lib/api';
import { relativa, stamp } from '../../lib/format';
import { Boton } from '../../components/ui/Boton';
import { EtiquetaEstado, EtiquetaTipo, nombreCuenta, quienEscribe } from './etiquetas';

/**
 * Lista de correos con el patrón de lista de opciones: una sola parada de tabulación, flechas (y Re Pág / Av Pág) para recorrerla y Enter
 * (o clic) para abrir. «Nuevo» es «sin tratar», no «no leído»: va con punto y texto en negrita, y los demás estados se escriben.
 */
export function ListaCorreos({ datos, cargando, error, seleccionId, hayBorrador, alAbrir, alReintentar, textoVacio, mostrarCuenta }: {
  datos: Page<MailSummary> | null; cargando: boolean; error: string; seleccionId: string | null; hayBorrador: (id: string) => boolean;
  alAbrir: (id: string) => void; alReintentar: () => void; textoVacio: string; mostrarCuenta: boolean;
}) {
  function teclas(e: KeyboardEvent<HTMLDivElement>) {
    const filas = Array.from(e.currentTarget.querySelectorAll<HTMLElement>('[role="option"]'));
    const i = filas.indexOf(document.activeElement as HTMLElement);
    const siguiente = e.key === 'ArrowDown' ? i + 1 : e.key === 'ArrowUp' ? i - 1 : e.key === 'PageDown' ? i + 5 : e.key === 'PageUp' ? i - 5
      : e.key === 'Home' ? 0 : e.key === 'End' ? filas.length - 1 : null;
    if (siguiente === null || !filas.length) return;
    e.preventDefault(); filas[Math.min(Math.max(siguiente, 0), filas.length - 1)].focus();
  }
  if (error) return <div className="notice" role="alert">{error} <Boton variante="texto" onClick={alReintentar}>Reintentar</Boton></div>;
  if (!datos) return <div className="skeleton-rows correos-esqueleto" aria-busy="true">{Array.from({ length: 7 }, (_, i) => <span key={i} />)}</div>;
  if (!datos.contenido.length) return <p className="empty-state"><Inbox size={20} aria-hidden />{textoVacio}</p>;
  // Una sola fila es la parada de tabulación: la abierta o, si no hay, la primera
  const parada = datos.contenido.find(m => m.id === seleccionId)?.id ?? datos.contenido[0].id;
  return <div className="correos-filas" role="listbox" aria-label="Correos" aria-busy={cargando} onKeyDown={teclas}>
    {datos.contenido.map(m => {
      const nuevo = m.estado === 'NUEVO';
      return <button key={m.id} type="button" role="option" aria-selected={m.id === seleccionId} tabIndex={m.id === parada ? 0 : -1}
        className={`correo-fila ${nuevo ? 'nuevo' : ''}`} onClick={() => alAbrir(m.id)}>
        <span className="correo-fila-cabeza">
          <span className="correo-remitente">{quienEscribe(m)}</span>
          <time dateTime={m.fecha} title={stamp(m.fecha)}>{relativa(m.fecha)}</time>
        </span>
        <span className="correo-asunto"><span className="correo-asunto-titulo">{m.asunto || '(sin asunto)'}</span>{m.extracto && <span className="correo-extracto"> — {m.extracto}</span>}</span>
        <span className="correo-fila-pie">
          <EtiquetaTipo tipo={m.tipo} /><EtiquetaEstado estado={m.estado} />
          {m.adjuntos > 0 && <span className="correo-adjunto" title={`${m.adjuntos} adjunto${m.adjuntos === 1 ? '' : 's'}`}><Paperclip size={13} aria-hidden /><span className="sr-only">{m.adjuntos} {m.adjuntos === 1 ? 'adjunto' : 'adjuntos'}</span><span aria-hidden>{m.adjuntos}</span></span>}
          {hayBorrador(m.id) && <span className="correo-borrador">Borrador</span>}
          {mostrarCuenta && <span className="correo-cuenta">{nombreCuenta(m.cuenta)}</span>}
        </span>
      </button>;
    })}
  </div>;
}
