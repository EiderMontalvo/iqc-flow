import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { api, type ClientSummary, type Country, type Quote, type QuoteService, type Scheme, type Standard } from '../../lib/api';
import { errorText } from '../../lib/format';
import { BuscadorCliente } from '../../components/BuscadorCliente';
import { SERVICIOS, dinero } from './textos';

type Linea = { clave: number; norma: string; edicion: string; descripcion: string; dias: string; monto: string; montoTocado: boolean };
const lineaVacia = (clave: number): Linea => ({ clave, norma: '', edicion: '', descripcion: '', dias: '', monto: '', montoTocado: false });
const num = (t: string) => { const n = Number(t.replace(',', '.')); return Number.isFinite(n) ? n : 0; };

/** Datos propuestos (p. ej. por el análisis de la solicitud del cliente) para empezar el formulario ya lleno */
export interface PropuestaCotizacion {
  contacto?: string; correo?: string; servicio?: QuoteService; alcance?: string; sedes?: number; detalleSedes?: string;
  normas?: Array<{ codigo: string; nombre: string; edicion: string | null }>; notas?: string;
}

/**
 * Formulario de cotización (crear o editar un borrador). Por cada norma: días auditor y monto; si hay tarifa por día,
 * el monto se propone solo (días × tarifa) hasta que se escriba a mano. El total se calcula al vuelo con el impuesto
 * del país del cliente; la API lo vuelve a calcular al guardar.
 */
export function FormularioCotizacion({ inicial, propuesta, cliente: clienteExterno, alGuardar, alCancelar }: {
  inicial?: Quote; propuesta?: PropuestaCotizacion;
  /** Cliente elegido o creado fuera del formulario (p. ej. desde el análisis de la solicitud) */
  cliente?: ClientSummary | null;
  alGuardar: (q: Quote) => void; alCancelar: () => void;
}) {
  const [normas, setNormas] = useState<Standard[]>([]), [esquemas, setEsquemas] = useState<Scheme[]>([]), [paises, setPaises] = useState<Country[]>([]);
  useEffect(() => { api.standards().then(setNormas).catch(() => {}); api.schemes().then(setEsquemas).catch(() => {}); api.countries().then(setPaises).catch(() => {}); }, []);
  const [cliente, setCliente] = useState<ClientSummary | null>(inicial ? {
    id: inicial.cliente.id, codigo: '', razonSocial: inicial.cliente.razonSocial, nombreComercial: null, tipoDocumento: inicial.cliente.tipoDocumento,
    numeroDocumento: inicial.cliente.numeroDocumento, estado: 'ACTIVO', codigoPais: inicial.cliente.codigoPais || 'PE',
    responsable: inicial.responsable, archivado: false, actualizadoEn: '' } : clienteExterno ?? null);
  useEffect(() => { if (clienteExterno) setCliente(clienteExterno); }, [clienteExterno]);
  const [contacto, setContacto] = useState(inicial?.contactoNombre || propuesta?.contacto || ''), [correo, setCorreo] = useState(inicial?.contactoCorreo || propuesta?.correo || '');
  const [servicio, setServicio] = useState<QuoteService>(inicial?.tipoServicio || propuesta?.servicio || 'CERTIFICACION_INICIAL'), [esquema, setEsquema] = useState(inicial?.esquemaCodigo || '');
  const [alcance, setAlcance] = useState(inicial?.alcance || propuesta?.alcance || ''), [sedes, setSedes] = useState(String(inicial?.numeroSedes ?? propuesta?.sedes ?? 1)), [detalleSedes, setDetalleSedes] = useState(inicial?.sedesDetalle || propuesta?.detalleSedes || '');
  const [tarifa, setTarifa] = useState(inicial?.tarifaDia != null ? String(inicial.tarifaDia) : '');
  const [validez, setValidez] = useState(String(inicial?.validezDias ?? 30)), [condiciones, setCondiciones] = useState(inicial?.condicionesPago || ''), [notas, setNotas] = useState(inicial?.notas || propuesta?.notas || '');
  const [lineas, setLineas] = useState<Linea[]>(inicial ? inicial.items.map((i, k) => ({ clave: k + 1, norma: i.normaCodigo || '', edicion: i.edicion || '',
    descripcion: i.descripcion, dias: String(i.diasAuditor), monto: String(i.monto), montoTocado: true }))
    : propuesta?.normas?.length ? propuesta.normas.map((n, k) => ({ ...lineaVacia(k + 1), norma: n.codigo, edicion: n.edicion || '',
      descripcion: `${SERVICIOS[propuesta.servicio || 'CERTIFICACION_INICIAL']} ${n.nombre}${n.edicion ? `:${n.edicion}` : ''}` }))
    : [lineaVacia(1)]);
  const [ocupado, setOcupado] = useState(false), [error, setError] = useState('');

  const pais = paises.find(p => p.codigo === (cliente?.codigoPais || 'PE'));
  const moneda = pais?.moneda || 'PEN', tasa = pais?.tasaImpuesto ?? 18, impuestoNombre = pais?.nombreImpuesto || 'IGV';
  const subtotal = useMemo(() => lineas.reduce((t, l) => t + num(l.monto), 0), [lineas]);
  const impuesto = Math.round(subtotal * tasa) / 100, dias = lineas.reduce((t, l) => t + num(l.dias), 0);
  const ediciones = (codigo: string) => normas.find(n => n.codigo === codigo)?.ediciones || [];
  const designacion = (codigo: string, edicion: string) => ediciones(codigo).find(e => e.edicion === edicion)?.designacion;

  function cambiar(clave: number, cambio: Partial<Linea>) {
    setLineas(todas => todas.map(l => {
      if (l.clave !== clave) return l;
      const n = { ...l, ...cambio };
      // Descripción automática a partir del servicio y la norma, mientras no se haya escrito otra
      if ((cambio.norma !== undefined || cambio.edicion !== undefined) && (!l.descripcion || l.descripcion === descripcionDe(l))) n.descripcion = descripcionDe(n);
      // Monto propuesto con la tarifa por día, hasta que se escriba a mano
      if (cambio.dias !== undefined && !n.montoTocado && num(tarifa) > 0) n.monto = (num(n.dias) * num(tarifa)).toFixed(2);
      return n;
    }));
  }
  const descripcionDe = (l: Pick<Linea, 'norma' | 'edicion'>) => l.norma ? `${SERVICIOS[servicio]} ${designacion(l.norma, l.edicion) || normas.find(n => n.codigo === l.norma)?.nombreCorto || ''}`.trim() : '';
  function aplicarTarifa(valor: string) {
    setTarifa(valor);
    if (num(valor) > 0) setLineas(todas => todas.map(l => l.montoTocado || !l.dias ? l : { ...l, monto: (num(l.dias) * num(valor)).toFixed(2) }));
  }

  async function guardar(e: FormEvent) {
    e.preventDefault();
    if (!cliente) return setError('Elige el cliente de la lista de coincidencias.');
    setOcupado(true); setError('');
    const cuerpo = {
      clienteId: cliente.id, contactoNombre: contacto, contactoCorreo: correo || null, esquemaCodigo: esquema || null, tipoServicio: servicio,
      alcance, numeroSedes: Number(sedes) || 1, sedesDetalle: detalleSedes, tarifaDia: tarifa ? num(tarifa) : null, validezDias: Number(validez) || 30,
      condicionesPago: condiciones, notas,
      items: lineas.map(l => ({ normaCodigo: l.norma || null, edicion: l.edicion || null, descripcion: l.descripcion.trim() || 'Servicio', diasAuditor: num(l.dias), monto: num(l.monto) })),
      ...(inicial ? { version: inicial.version } : {}),
    };
    try { alGuardar(inicial ? await api.updateQuote(inicial.id, cuerpo) : await api.createQuote(cuerpo)); }
    catch (err) { setError(errorText(err)); } finally { setOcupado(false); }
  }

  return <form className="client-form" onSubmit={guardar}>
    <fieldset><legend>Cliente</legend>
      <BuscadorCliente value={cliente} onChange={setCliente} />
      <div className="form-grid">
        <label>Contacto<input value={contacto} onChange={e => setContacto(e.target.value)} maxLength={200} placeholder="A quién va dirigida" /></label>
        <label>Correo del contacto<input type="email" value={correo} onChange={e => setCorreo(e.target.value)} maxLength={320} /></label>
      </div>
    </fieldset>

    <fieldset><legend>Servicio</legend>
      <div className="form-grid">
        <label>Tipo de servicio<select value={servicio} onChange={e => setServicio(e.target.value as QuoteService)}>
          {Object.entries(SERVICIOS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></label>
        <label>Acreditación<select value={esquema} onChange={e => setEsquema(e.target.value)}>
          <option value="">Sin definir</option>{esquemas.map(s => <option key={s.codigo} value={s.codigo}>{s.nombre}</option>)}</select></label>
      </div>
      <label>Alcance<textarea rows={3} maxLength={4000} value={alcance} onChange={e => setAlcance(e.target.value)} placeholder="Actividades que cubrirá la certificación" /></label>
      <div className="form-grid corto-largo">
        <label>Sedes<input type="number" min={1} max={999} value={sedes} onChange={e => setSedes(e.target.value)} /></label>
        <label>Detalle de sedes<input maxLength={1000} value={detalleSedes} onChange={e => setDetalleSedes(e.target.value)} placeholder="Ej.: oficina en Lima y planta en Arequipa" /></label>
      </div>
    </fieldset>

    <fieldset><legend>Normas y montos</legend>
      <div className="form-grid largo-corto">
        <span className="field-help">Cada norma con sus días auditor y su monto sin {impuestoNombre}. Con una tarifa por día, el monto se calcula solo; puedes cambiarlo.</span>
        <label>Tarifa por día<input inputMode="decimal" value={tarifa} onChange={e => aplicarTarifa(e.target.value)} placeholder="Opcional" /></label>
      </div>
      <div className="cot-lineas">{lineas.map((l, i) => <div className="cot-linea" key={l.clave}>
        <label>Norma<select value={l.norma} onChange={e => { const ed = ediciones(e.target.value); cambiar(l.clave, { norma: e.target.value, edicion: (ed.find(x => x.vigente) || ed[0])?.edicion || '' }); }}>
          <option value="">Otro servicio</option>{normas.map(n => <option key={n.codigo} value={n.codigo}>{n.nombreCorto}</option>)}</select></label>
        <label>Edición<select value={l.edicion} disabled={!l.norma} onChange={e => cambiar(l.clave, { edicion: e.target.value })}>
          <option value="">—</option>{ediciones(l.norma).map(ed => <option key={ed.edicion} value={ed.edicion}>{ed.edicion}{ed.vigente ? '' : ' (anterior)'}</option>)}</select></label>
        <label>Días auditor<input inputMode="decimal" value={l.dias} onChange={e => cambiar(l.clave, { dias: e.target.value })} placeholder="0" /></label>
        <label>Monto<input inputMode="decimal" required value={l.monto} onChange={e => cambiar(l.clave, { monto: e.target.value, montoTocado: true })} placeholder="0.00" /></label>
        <label className="cot-linea-desc">Descripción<input required maxLength={500} value={l.descripcion} onChange={e => cambiar(l.clave, { descripcion: e.target.value })} placeholder="Ej.: Certificación ISO 9001:2015" /></label>
        {lineas.length > 1 && <button type="button" className="icon-button danger" onClick={() => setLineas(t => t.filter(x => x.clave !== l.clave))} aria-label={`Quitar línea ${i + 1}`}><Trash2 size={16} aria-hidden /></button>}
      </div>)}</div>
      <button type="button" className="secondary" onClick={() => setLineas(t => [...t, lineaVacia(Date.now())])}><Plus size={16} aria-hidden /> Agregar norma o servicio</button>
      <dl className="cot-totales" aria-live="polite">
        <div><dt>Días auditor</dt><dd className="tabular">{dias.toLocaleString('es-PE')}</dd></div>
        <div><dt>Subtotal</dt><dd className="tabular">{dinero(subtotal, moneda)}</dd></div>
        <div><dt>{impuestoNombre} {tasa} %</dt><dd className="tabular">{dinero(impuesto, moneda)}</dd></div>
        <div className="total"><dt>Total</dt><dd className="tabular">{dinero(subtotal + impuesto, moneda)}</dd></div>
      </dl>
    </fieldset>

    <fieldset><legend>Condiciones</legend>
      <div className="form-grid corto-largo">
        <label>Validez (días)<input type="number" min={1} max={365} value={validez} onChange={e => setValidez(e.target.value)} /></label>
        <label>Condiciones de pago<input maxLength={2000} value={condiciones} onChange={e => setCondiciones(e.target.value)} placeholder="Ej.: 50 % al firmar el contrato y 50 % antes de la etapa 2" /></label>
      </div>
      <label>Notas internas<textarea rows={3} maxLength={4000} value={notas} onChange={e => setNotas(e.target.value)} /></label>
    </fieldset>

    {error && <p className="form-error" role="alert">{error}</p>}
    <div className="form-actions"><button type="button" className="secondary" onClick={alCancelar}>Cancelar</button>
      <button className="primary" disabled={ocupado}>{ocupado ? 'Guardando…' : inicial ? 'Guardar cambios' : 'Crear cotización'}</button></div>
  </form>;
}
