import { ChevronLeft, ChevronRight } from 'lucide-react';
import { number } from '../lib/format';

export function Pagination({ page, totalPages, total, noun, onPage }: {
  page: number; totalPages: number; total: number | null; noun: string; onPage: (page: number) => void;
}) {
  return <div className="pagination">
    <span>{total == null ? 'Cargando…' : `${number(total)} ${noun}`}</span>
    <div>
      <button type="button" className="icon-button" disabled={page === 0} onClick={() => onPage(page - 1)} aria-label="Página anterior"><ChevronLeft size={16} aria-hidden /></button>
      <span className="tabular">{page + 1} / {Math.max(1, totalPages)}</span>
      <button type="button" className="icon-button" disabled={total == null || page + 1 >= totalPages} onClick={() => onPage(page + 1)} aria-label="Página siguiente"><ChevronRight size={16} aria-hidden /></button>
    </div>
  </div>;
}
