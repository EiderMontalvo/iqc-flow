import { ChevronLeft, ChevronRight } from 'lucide-react';
import { number } from '../lib/format';
import { BotonIcono } from '../components/ui/Boton';

export function Pagination({ page, totalPages, total, noun, onPage }: {
  page: number; totalPages: number; total: number | null; noun: string; onPage: (page: number) => void;
}) {
  return <div className="pagination">
    <span>{total == null ? 'Cargando…' : `${number(total)} ${noun}`}</span>
    <div>
      <BotonIcono etiqueta="Página anterior" icono={ChevronLeft} tamano={16} type="button" disabled={page === 0} onClick={() => onPage(page - 1)} />
      <span className="tabular">{page + 1} / {Math.max(1, totalPages)}</span>
      <BotonIcono etiqueta="Página siguiente" icono={ChevronRight} tamano={16} type="button" disabled={total == null || page + 1 >= totalPages} onClick={() => onPage(page + 1)} />
    </div>
  </div>;
}
