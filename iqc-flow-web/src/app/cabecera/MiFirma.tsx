import { useEffect, useRef, useState, type FormEvent } from 'react';
import { CheckCircle2, ImageUp } from 'lucide-react';
import { api, type MySignature } from '../../lib/api';
import { errorText, stamp } from '../../lib/format';
import { Boton } from '../../components/ui/Boton';

/**
 * Firma de la persona para las cartas: imagen (PNG o JPEG), nombre con el que firma y cargo. Por seguridad la imagen
 * guardada no se vuelve a mostrar ni se puede descargar: solo la usa IQC Flow al emitir una carta.
 */
export function MiFirma() {
  const [firma, setFirma] = useState<MySignature | null>(null), [archivo, setArchivo] = useState<File | null>(null);
  const [vista, setVista] = useState<string | null>(null), [nombre, setNombre] = useState(''), [cargo, setCargo] = useState('');
  const [error, setError] = useState(''), [listo, setListo] = useState(''), [ocupado, setOcupado] = useState(false);
  const entrada = useRef<HTMLInputElement>(null);

  useEffect(() => {
    api.mySignature().then(f => { setFirma(f); setNombre(f.nombreMostrado || ''); setCargo(f.cargo || ''); }).catch(e => setError(errorText(e)));
  }, []);
  useEffect(() => {
    if (!archivo) { setVista(null); return; }
    const url = URL.createObjectURL(archivo); setVista(url);
    return () => URL.revokeObjectURL(url);
  }, [archivo]);

  async function guardar(e: FormEvent) {
    e.preventDefault(); setError(''); setListo('');
    if (!archivo && !firma?.tieneFirma) return setError('Elige la imagen de tu firma.');
    const datos = new FormData();
    if (archivo) datos.append('archivo', archivo);
    datos.append('cargo', cargo.trim());
    if (nombre.trim()) datos.append('nombreMostrado', nombre.trim());
    setOcupado(true);
    try { const f = await api.saveMySignature(datos); setFirma(f); setArchivo(null); if (entrada.current) entrada.current.value = ''; setListo('Firma guardada.'); }
    catch (err) { setError(errorText(err)); } finally { setOcupado(false); }
  }
  async function quitar() {
    setOcupado(true); setError(''); setListo('');
    try { await api.deleteMySignature(); setFirma(f => f && { ...f, tieneFirma: false, actualizadoEn: null }); setListo('Firma eliminada.'); }
    catch (err) { setError(errorText(err)); } finally { setOcupado(false); }
  }

  return <form className="client-form mi-firma" onSubmit={guardar}>
    <h3>Mi firma</h3>
    <p className="field-help">{firma?.tieneFirma
      ? `Tienes una firma guardada (actualizada el ${stamp(firma.actualizadoEn)}). Se usa solo al emitir cartas y nadie puede descargarla.`
      : 'Súbela para poder firmar cartas. Mejor en PNG con fondo transparente o blanco, solo el trazo y el sello.'}</p>
    <div className="form-grid">
      <div className="full mi-firma-elegir">
        <input ref={entrada} id="mi-firma-archivo" type="file" accept="image/png,image/jpeg" className="sr-only" onChange={e => setArchivo(e.target.files?.[0] || null)} />
        <Boton type="button" variante="secundario" icono={ImageUp} onClick={() => entrada.current?.click()}>
          {firma?.tieneFirma ? 'Cambiar la imagen' : 'Elegir la imagen de la firma'}</Boton>
        {archivo && <span className="field-help">{archivo.name}: se guardará al pulsar «Guardar firma».</span>}
      </div>
      {vista && <img className="mi-firma-vista full" src={vista} alt="Vista previa de la firma elegida" />}
      <label>Nombre con el que firmas<input value={nombre} maxLength={160} placeholder={firma?.nombreUsuario || ''} onChange={e => setNombre(e.target.value)} /></label>
      <label>Cargo<input value={cargo} maxLength={120} placeholder="Gerente General" onChange={e => setCargo(e.target.value)} required /></label>
    </div>
    {error && <p className="form-error" role="alert">{error}</p>}
    {listo && <p className="form-success" role="status"><CheckCircle2 size={14} aria-hidden /> {listo}</p>}
    <div className="form-actions">
      {firma?.tieneFirma && <Boton type="button" variante="secundario" onClick={quitar} disabled={ocupado}>Quitar mi firma</Boton>}
      <Boton type="submit" variante="primario" cargando={ocupado}>Guardar firma</Boton>
    </div>
  </form>;
}
