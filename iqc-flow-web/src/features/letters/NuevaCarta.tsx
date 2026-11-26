import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { api, type ClientSummary, type LetterCertificate, type LetterRequest, type LetterSigner, type LetterSummary, type LetterType, type LetterTypeInfo } from '../../lib/api';
import { day, errorText, isoDay } from '../../lib/format';
import { useVistaPreviaEnVivo } from '../../lib/useVistaPreviaEnVivo';
import { Drawer } from '../../components/Drawer';
import { BuscadorCliente } from '../../components/BuscadorCliente';
import { VistaPdf } from '../../components/pdf/VistaPdf';
import { Boton } from '../../components/ui/Boton';
import { Campo } from '../../components/ui/Campo';
import { Seccion } from '../../components/ui/Seccion';

/** «Vigente hasta…», «Venció el…» o el estado (suspendido, retirado…) */
function situacion(c: LetterCertificate) {
  if (c.estado !== 'EMITIDO') return c.estado.charAt(0) + c.estado.slice(1).toLowerCase();
  if (c.fechaExpiracion && c.fechaExpiracion < isoDay(new Date())) return `Venció el ${day(c.fechaExpiracion)}`;
  return `Vigente hasta ${day(c.fechaExpiracion)}`;
}

const ETAPAS = ['1ra Vigilancia', '2da Vigilancia', 'Recertificación', 'Ampliación'];

/**
 * Redactar una carta: se elige el tipo, el cliente y sus certificados, y la carta se arma a la derecha con la plantilla
 * de IQC mientras se escribe. Al emitirla recibe su número y el PDF queda guardado.
 */
export function NuevaCarta({ alCerrar, alEmitir }: { alCerrar: () => void; alEmitir: (c: LetterSummary) => void }) {
  const [tipos, setTipos] = useState<LetterTypeInfo[]>([]), [firmantes, setFirmantes] = useState<LetterSigner[] | null>(null);
  const [tipo, setTipo] = useState<LetterType>('CONTINUIDAD'), [cliente, setCliente] = useState<ClientSummary | null>(null);
  const [certificados, setCertificados] = useState<LetterCertificate[] | null>(null), [elegidos, setElegidos] = useState<string[]>([]);
  const [tipoServicio, setTipoServicio] = useState('certificación'), [etapa, setEtapa] = useState(''), [alcance, setAlcance] = useState('');
  const [asunto, setAsunto] = useState(''), [cuerpo, setCuerpo] = useState('');
  const [fecha, setFecha] = useState(isoDay(new Date())), [ciudad, setCiudad] = useState('Lima'), [firmante, setFirmante] = useState('');
  const [error, setError] = useState(''), [ocupado, setOcupado] = useState(false);

  useEffect(() => {
    api.letterTypes().then(setTipos).catch(e => setError(errorText(e)));
    api.letterSigners().then(f => { setFirmantes(f); if (f.length === 1) setFirmante(f[0].usuarioId); }).catch(e => setError(errorText(e)));
  }, []);
  // Al cambiar de cliente se proponen sus certificados vigentes
  useEffect(() => {
    setCertificados(null); setElegidos([]);
    if (!cliente) return;
    let vigente = true;
    api.letterCertificates(cliente.id).then(c => {
      if (!vigente) return;
      // no se marca ninguno: un cliente suele tener varios ciclos y ediciones, y la carta habla de unos concretos
      setCertificados(c); setElegidos(c.length === 1 ? [c[0].id] : []);
    }).catch(e => { if (vigente) setError(errorText(e)); });
    return () => { vigente = false; };
  }, [cliente]);

  const info = tipos.find(t => t.codigo === tipo);
  const pedido: LetterRequest | null = useMemo(() => cliente ? {
    tipo, clienteId: cliente.id, certificadoIds: info?.requiereCertificados ? elegidos : [], fecha: fecha || null, ciudad: ciudad.trim() || null,
    firmanteId: firmante || null, tipoServicio, etapa: etapa.trim() || null, alcance: alcance.trim() || null,
    asunto: asunto.trim() || null, cuerpo: cuerpo.trim() || null,
  } : null, [tipo, cliente, info, elegidos, fecha, ciudad, firmante, tipoServicio, etapa, alcance, asunto, cuerpo]);
  const enVivo = useVistaPreviaEnVivo('/cartas/vista-previa', pedido, true, 900);
  const alcancePropuesto = certificados?.find(c => elegidos.includes(c.id) && c.alcance)?.alcance;

  async function emitir(e: FormEvent) {
    e.preventDefault();
    if (!pedido) return setError('Elige el cliente.');
    setOcupado(true); setError('');
    try { alEmitir(await api.issueLetter(pedido)); } catch (err) { setError(errorText(err)); } finally { setOcupado(false); }
  }
  const alternar = (id: string) => setElegidos(l => l.includes(id) ? l.filter(x => x !== id) : [...l, id]);

  const vista = <VistaPdf fuente={!enVivo.error && enVivo.archivo ? { tipo: 'archivo', archivo: enVivo.archivo } : null} titulo="Vista previa"
    ocupado={enVivo.ocupado ? 'Armando la carta…' : undefined} vacio={enVivo.error || 'Elige el cliente y aquí verás la carta con el membrete y la firma de IQC.'} />;

  return <Drawer onClose={alCerrar} title="Nueva carta" subtitle="Se arma con la plantilla de IQC; al emitirla recibe su número y queda guardada." vista={vista}>
    <form className="client-form" onSubmit={emitir}>
      <Seccion titulo="Carta">
        <Campo etiqueta="Tipo de carta" ayuda={info?.descripcion}>{a =>
          <select {...a} value={tipo} onChange={e => setTipo(e.target.value as LetterType)}>
            {tipos.map(t => <option key={t.codigo} value={t.codigo}>{t.nombre}</option>)}</select>}</Campo>
        <BuscadorCliente value={cliente} onChange={setCliente} />
      </Seccion>

      {info?.requiereCertificados && cliente && <Seccion titulo="Certificados que menciona" ayuda="Marca los que trata la carta: sus normas y ediciones van en el texto.">
        {certificados === null ? <p className="field-help">Buscando sus certificados…</p>
          : !certificados.length ? <p className="form-error">Este cliente no tiene certificados emitidos en IQC Flow.</p>
          : <fieldset className="lista-certificados"><legend className="sr-only">Certificados del cliente</legend>
            {certificados.map(c => <label key={c.id} className="check-row">
              <input type="checkbox" checked={elegidos.includes(c.id)} onChange={() => alternar(c.id)} />
              <span><strong>{c.norma}</strong> <span className="mono">{c.codigo}</span>
                <small>{situacion(c)}</small></span>
            </label>)}</fieldset>}
      </Seccion>}

      {tipo === 'CERTIFICACION' && <Seccion titulo="Contenido">
        <div className="form-grid">
          <Campo etiqueta="Servicio">{a => <select {...a} value={tipoServicio} onChange={e => setTipoServicio(e.target.value)}>
            <option value="certificación">Certificación</option><option value="recertificación">Recertificación</option></select>}</Campo>
        </div>
        <Campo etiqueta="Alcance" ayuda={alcancePropuesto && !alcance ? 'Vacío: se usa el alcance del certificado.' : undefined}>{a =>
          <textarea {...a} rows={4} maxLength={4000} value={alcance} placeholder={alcancePropuesto || 'Actividades que cubre la certificación'} onChange={e => setAlcance(e.target.value)} />}</Campo>
      </Seccion>}

      {tipo === 'CONTINUIDAD' && <Seccion titulo="Contenido">
        <div className="form-grid">
          <Campo etiqueta="Etapa auditada">{a => <>
            <input {...a} list="etapas-carta" value={etapa} maxLength={60} placeholder="2da Vigilancia" onChange={e => setEtapa(e.target.value)} required />
            <datalist id="etapas-carta">{ETAPAS.map(x => <option key={x} value={x} />)}</datalist></>}</Campo>
        </div>
      </Seccion>}

      {tipo === 'LIBRE' && <Seccion titulo="Contenido" ayuda="Deja una línea en blanco entre párrafos. El saludo, el membrete y la firma ya los pone la plantilla.">
        <Campo etiqueta="Asunto">{a => <input {...a} value={asunto} maxLength={300} onChange={e => setAsunto(e.target.value)} required />}</Campo>
        <Campo etiqueta="Texto de la carta">{a => <textarea {...a} rows={10} maxLength={20000} value={cuerpo} onChange={e => setCuerpo(e.target.value)} required />}</Campo>
      </Seccion>}

      <Seccion titulo="Fecha y firma">
        <div className="form-grid">
          <Campo etiqueta="Ciudad">{a => <input {...a} value={ciudad} maxLength={80} onChange={e => setCiudad(e.target.value)} />}</Campo>
          <Campo etiqueta="Fecha">{a => <input {...a} type="date" value={fecha} onChange={e => setFecha(e.target.value)} required />}</Campo>
        </div>
        <Campo etiqueta="Firma" ayuda={firmantes && !firmantes.length
          ? 'Nadie ha subido su firma todavía. Súbela desde tu perfil: menú con tu nombre → «Mi perfil, firma y contraseña».' : undefined}>{a =>
          <select {...a} value={firmante} onChange={e => setFirmante(e.target.value)} required>
            <option value="">Elige quién firma</option>
            {firmantes?.map(f => <option key={f.usuarioId} value={f.usuarioId}>{f.nombre} · {f.cargo}</option>)}</select>}</Campo>
      </Seccion>

      {error && <p className="form-error" role="alert">{error}</p>}
      <div className="form-actions"><Boton type="button" variante="secundario" onClick={alCerrar}>Cancelar</Boton>
        <Boton type="submit" variante="primario" cargando={ocupado} disabled={!cliente}>{ocupado ? 'Emitiendo…' : 'Emitir carta'}</Boton></div>
    </form>
  </Drawer>;
}
