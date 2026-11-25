import { Search, X } from 'lucide-react';

export function SearchField({ value, onChange, placeholder, label }: { value: string; onChange: (value: string) => void; placeholder: string; label: string }) {
  return <label className="search-field">
    <Search size={16} aria-hidden />
    <span className="sr-only">{label}</span>
    <input type="search" value={value} onChange={e => onChange(e.target.value)} placeholder={placeholder} />
    {value && <button type="button" className="search-clear" onClick={() => onChange('')} aria-label="Borrar búsqueda"><X size={14} aria-hidden /></button>}
  </label>;
}
