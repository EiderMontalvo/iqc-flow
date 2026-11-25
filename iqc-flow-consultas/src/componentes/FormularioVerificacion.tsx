import { useState, type FormEvent } from 'react';
import { LoaderCircle, Search } from 'lucide-react';
import { CAMPOS, LIMITES } from '../config/portal';
import { limpiarValidacion, validarEnEspanol } from '../lib/formulario';

interface Props {
  cargando: boolean;
  /** Recibe el documento y el código tal como se escribieron; `api/consultas.ts` los normaliza */
  alEnviar: (documento: string, codigo: string) => void;
}

/** Documento del titular + código del certificado. Sirve para certificados empresariales y personales */
export function FormularioVerificacion({ cargando, alEnviar }: Props) {
  const [documento, setDocumento] = useState(''), [codigo, setCodigo] = useState('');
  const enviar = (e: FormEvent) => { e.preventDefault(); alEnviar(documento, codigo); };

  return <form className="buscador" onSubmit={enviar}>
    <label><span>{CAMPOS.documento}</span>
      <input value={documento} onChange={e => setDocumento(e.target.value)} onInvalid={validarEnEspanol} onInput={limpiarValidacion}
        required minLength={LIMITES.documento.min} maxLength={LIMITES.documento.max} autoComplete="off" inputMode="text"
        placeholder={CAMPOS.ejemploDocumento} />
    </label>
    <label><span>{CAMPOS.codigo}</span>
      <input value={codigo} onChange={e => setCodigo(e.target.value)} onInvalid={validarEnEspanol} onInput={limpiarValidacion}
        required minLength={LIMITES.codigo.min} maxLength={LIMITES.codigo.max} autoComplete="off" autoCapitalize="characters"
        spellCheck={false} placeholder={CAMPOS.ejemploCodigo} />
    </label>
    <button className="boton verificar" disabled={cargando}>
      {cargando ? <LoaderCircle size={18} className="gira" aria-hidden /> : <Search size={18} aria-hidden />} {cargando ? 'Consultando…' : 'Verificar'}
    </button>
  </form>;
}
