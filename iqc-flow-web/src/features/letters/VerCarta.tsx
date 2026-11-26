import { useEffect, useState } from 'react';
import { Download, FileDown } from 'lucide-react';
import { api, type LetterSummary } from '../../lib/api';
import { day, errorText, stamp } from '../../lib/format';
import { Drawer } from '../../components/Drawer';
import { VistaPdf } from '../../components/pdf/VistaPdf';
import { Boton } from '../../components/ui/Boton';

/** Una carta emitida: su PDF a la derecha y, a la izquierda, sus datos y las descargas (el Word, para corregirla a mano) */
export function VerCarta({ carta, puedeDescargarWord, alCerrar }: { carta: LetterSummary; puedeDescargarWord: boolean; alCerrar: () => void }) {
  const [pdf, setPdf] = useState<File | null>(null), [error, setError] = useState(''), [bajando, setBajando] = useState('');
  useEffect(() => {
    let vigente = true;
    api.letterFile(carta.id, 'pdf').then(b => { if (vigente) setPdf(new File([b], `${carta.numero}.pdf`, { type: 'application/pdf' })); })
      .catch(e => { if (vigente) setError(errorText(e)); });
    return () => { vigente = false; };
  }, [carta.id, carta.numero]);

  async function descargar(formato: 'pdf' | 'word') {
    setBajando(formato); setError('');
    try {
      const b = formato === 'pdf' && pdf ? pdf : await api.letterFile(carta.id, formato);
      const url = URL.createObjectURL(b), a = document.createElement('a');
      a.href = url; a.download = `${carta.numero}.${formato === 'pdf' ? 'pdf' : 'docx'}`; a.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (e) { setError(errorText(e)); } finally { setBajando(''); }
  }

  return <Drawer onClose={alCerrar} title={<span className="mono">{carta.numero}</span>} subtitle={carta.asunto}
    vista={<VistaPdf fuente={pdf ? { tipo: 'archivo', archivo: pdf } : null} titulo="Carta emitida" vacio={error || 'Cargando la carta…'} />}>
    <div className="client-form">
    <dl className="ficha-datos">
      <div><dt>Cliente</dt><dd>{carta.cliente.nombre || '—'}</dd></div>
      <div><dt>Tipo</dt><dd>{carta.tipoNombre}</dd></div>
      <div><dt>Fecha de la carta</dt><dd>{day(carta.fecha)}</dd></div>
      <div><dt>Firma</dt><dd>{carta.firmante || '—'}</dd></div>
      <div><dt>Emitida</dt><dd>{stamp(carta.creadoEn)}{carta.creadoPor ? ` · ${carta.creadoPor}` : ''}</dd></div>
    </dl>
    {error && <p className="form-error" role="alert">{error}</p>}
    <div className="form-actions">
      {puedeDescargarWord && <Boton variante="secundario" icono={FileDown} cargando={bajando === 'word'} onClick={() => descargar('word')}>Descargar Word</Boton>}
      <Boton variante="primario" icono={Download} cargando={bajando === 'pdf'} disabled={!pdf} onClick={() => descargar('pdf')}>Descargar PDF</Boton>
    </div>
    {puedeDescargarWord && <p className="field-help">El Word sirve para corregir la carta a mano. Si la corriges, guarda el PDF que envíes al cliente.</p>}
    </div>
  </Drawer>;
}
