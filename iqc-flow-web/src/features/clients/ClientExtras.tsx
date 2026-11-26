import { Plus } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { api, type Address, type ClientDetail, type Contact } from '../../lib/api';
import { ClientActivity } from './ClientActivity';
import { Boton } from '../../components/ui/Boton';
import { errorText } from '../../lib/format';

const roles = ['GERENCIA', 'CALIDAD', 'COMERCIAL', 'FACTURACION', 'OPERACIONES', 'REPRESENTANTE_LEGAL', 'OTRO'];
const addressTypes: Array<Address['tipo']> = ['FISCAL', 'SEDE', 'CORRESPONDENCIA'];

function ContactForm({ initial, onSave, onCancel, busy, error }: {
  initial?: Contact; onSave: (data: object) => void; onCancel: () => void; busy: boolean; error: string;
}) {
  const [name, setName] = useState(initial?.nombreCompleto || '');
  const [selectedRoles, setSelectedRoles] = useState(initial?.roles || []);
  const [position, setPosition] = useState(initial?.cargo || '');
  const [email, setEmail] = useState(initial?.correo || '');
  const [phone, setPhone] = useState(initial?.telefono || '');
  const [principal, setPrincipal] = useState(initial?.esPrincipal || false);
  function submit(event: FormEvent) {
    event.preventDefault();
    onSave({ nombreCompleto: name.trim(), cargo: position.trim(), correo: email.trim(),
      telefono: phone.trim(), roles: selectedRoles, esPrincipal: principal, activo: initial?.activo ?? true,
      notas: initial?.notas || '', ...(initial ? { version: initial.version } : {}) });
  }
  return <form className="extras-form" onSubmit={submit}>
    <div className="extras-form-head"><h4>{initial ? 'Editar contacto' : 'Nuevo contacto'}</h4><Boton type="button" variante="texto" onClick={onCancel}>Cerrar</Boton></div>
    <div className="form-grid">
      <label className="full">Nombre completo<input value={name} onChange={event => setName(event.target.value)} required maxLength={200} /></label>
      <label>Cargo<input value={position} onChange={event => setPosition(event.target.value)} maxLength={120} /></label>
      <fieldset className="contact-roles full"><legend>Roles del contacto</legend>{roles.map(value => <label key={value}><input type="checkbox" checked={selectedRoles.includes(value)} onChange={() => setSelectedRoles(current => current.includes(value) ? current.filter(item => item !== value) : [...current, value])} />{value.replaceAll('_', ' ')}</label>)}</fieldset>
      <label>Correo<input type="email" value={email} onChange={event => setEmail(event.target.value)} maxLength={320} /></label>
      <label>Teléfono<input type="tel" value={phone} onChange={event => setPhone(event.target.value)} pattern="[0-9+()\-\s]{6,40}" title="Entre 6 y 40 caracteres: números, espacios, +, paréntesis o guiones" /></label>
      <label className="inline-check full"><input type="checkbox" checked={principal} onChange={event => setPrincipal(event.target.checked)} /> Contacto principal</label>
    </div>
    {error && <p className="form-error" role="alert">{error}</p>}
    <div className="form-actions"><Boton type="button" variante="secundario" onClick={onCancel}>Cancelar</Boton><Boton type="submit" variante="primario" disabled={busy}>{busy ? 'Guardando…' : 'Guardar contacto'}</Boton></div>
  </form>;
}

function AddressForm({ initial, onSave, onCancel, busy, error }: {
  initial?: Address; onSave: (data: object) => void; onCancel: () => void; busy: boolean; error: string;
}) {
  const [type, setType] = useState<Address['tipo']>(initial?.tipo || 'SEDE');
  const [name, setName] = useState(initial?.nombre || '');
  const [line, setLine] = useState(initial?.direccion || '');
  const [district, setDistrict] = useState(initial?.distrito || '');
  const [province, setProvince] = useState(initial?.provincia || '');
  const [department, setDepartment] = useState(initial?.departamento || '');
  const [ubigeo, setUbigeo] = useState(initial?.ubigeo || '');
  function submit(event: FormEvent) {
    event.preventDefault();
    onSave({ tipo: type, nombre: name.trim(), direccion: line.trim(), distrito: district.trim(),
      provincia: province.trim(), departamento: department.trim(), codigoPais: initial?.codigoPais || 'PE',
      ubigeo: ubigeo.trim() || null, referencia: initial?.referencia || '',
      ...(initial ? { version: initial.version } : {}) });
  }
  return <form className="extras-form" onSubmit={submit}>
    <div className="extras-form-head"><h4>{initial ? 'Editar dirección' : 'Nueva dirección'}</h4><Boton type="button" variante="texto" onClick={onCancel}>Cerrar</Boton></div>
    <div className="form-grid">
      <label>Tipo<select value={type} onChange={event => setType(event.target.value as Address['tipo'])}>{addressTypes.map(value => <option key={value} value={value}>{value}</option>)}</select></label>
      <label>Nombre de sede (opcional)<input value={name} onChange={event => setName(event.target.value)} maxLength={120} /></label>
      <label className="full">Dirección<input value={line} onChange={event => setLine(event.target.value)} required maxLength={300} /></label>
      <label>Distrito<input value={district} onChange={event => setDistrict(event.target.value)} maxLength={100} /></label>
      <label>Provincia<input value={province} onChange={event => setProvince(event.target.value)} maxLength={100} /></label>
      <label>Departamento<input value={department} onChange={event => setDepartment(event.target.value)} maxLength={100} /></label>
      <label>Ubigeo (opcional)<input value={ubigeo} onChange={event => setUbigeo(event.target.value)} inputMode="numeric" pattern="[0-9]{6}" maxLength={6} /></label>
    </div>
    {error && <p className="form-error" role="alert">{error}</p>}
    <div className="form-actions"><Boton type="button" variante="secundario" onClick={onCancel}>Cancelar</Boton><Boton type="submit" variante="primario" disabled={busy}>{busy ? 'Guardando…' : 'Guardar dirección'}</Boton></div>
  </form>;
}

export function ClientExtras({ detail, onChanged, canReadActivities, canWriteActivities }: {
  detail: ClientDetail; onChanged: (detail: ClientDetail) => void;
  canReadActivities: boolean; canWriteActivities: boolean;
}) {
  const [kind, setKind] = useState<'contact' | 'address' | null>(null);
  const [contact, setContact] = useState<Contact | null>(null);
  const [address, setAddress] = useState<Address | null>(null);
  const [busy, setBusy] = useState(false), [error, setError] = useState('');
  const editable = detail.puedeEditar && !detail.archivado;
  const close = () => { setKind(null); setContact(null); setAddress(null); setError(''); };
  async function reload() { onChanged(await api.client(detail.id)); close(); }
  async function saveContact(data: object) {
    setBusy(true); setError('');
    try {
      if (contact) await api.updateContact(detail.id, contact.id, data);
      else await api.addContact(detail.id, data);
      await reload();
    } catch (e) { setError(errorText(e)); } finally { setBusy(false); }
  }
  async function saveAddress(data: object) {
    setBusy(true); setError('');
    try {
      if (address) await api.updateAddress(detail.id, address.id, data);
      else await api.addAddress(detail.id, data);
      await reload();
    } catch (e) { setError(errorText(e)); } finally { setBusy(false); }
  }
  async function remove(item: Contact | Address, itemKind: 'contact' | 'address') {
    if (!window.confirm(`¿Eliminar ${itemKind === 'contact' ? 'este contacto' : 'esta dirección'}?`)) return;
    setBusy(true); setError('');
    try {
      if (itemKind === 'contact') await api.deleteContact(detail.id, item.id);
      else await api.deleteAddress(detail.id, item.id);
      await reload();
    } catch (e) { setError(errorText(e)); } finally { setBusy(false); }
  }
  return <>
    <div className="extras-section-head"><h3>Contactos</h3>{editable && <Boton variante="texto" onClick={() => { close(); setKind('contact'); }}><Plus size={14} aria-hidden /> Agregar contacto</Boton>}</div>
    {detail.contactos.length ? detail.contactos.map(item => <div className="detail-item extras-item" key={item.id}>
      <div><strong>{item.nombreCompleto}{item.esPrincipal ? ' · Principal' : ''}</strong>
        <span>{[item.cargo, item.correo, item.telefono].filter(Boolean).join(' · ')}</span></div>
      {editable && <div className="extras-actions"><Boton variante="texto" onClick={() => { setContact(item); setAddress(null); setKind('contact'); setError(''); }}>Editar</Boton><Boton variante="texto" className="danger" disabled={busy} onClick={() => remove(item, 'contact')}>Eliminar</Boton></div>}
    </div>) : <p className="detail-empty">Sin contactos registrados.</p>}
    {kind === 'contact' && <ContactForm key={contact?.id || 'new'} initial={contact || undefined} onSave={saveContact} onCancel={close} busy={busy} error={error} />}
    <div className="extras-section-head"><h3>Direcciones</h3>{editable && <Boton variante="texto" onClick={() => { close(); setKind('address'); }}><Plus size={14} aria-hidden /> Agregar dirección</Boton>}</div>
    {detail.direcciones.length ? detail.direcciones.map(item => <div className="detail-item extras-item" key={item.id}>
      <div><strong>{item.nombre || item.tipo}</strong><span>{[item.direccion, item.distrito, item.provincia].filter(Boolean).join(' · ')}</span></div>
      {editable && <div className="extras-actions"><Boton variante="texto" onClick={() => { setAddress(item); setContact(null); setKind('address'); setError(''); }}>Editar</Boton><Boton variante="texto" className="danger" disabled={busy} onClick={() => remove(item, 'address')}>Eliminar</Boton></div>}
    </div>) : <p className="detail-empty">Sin direcciones registradas.</p>}
    {kind === 'address' && <AddressForm key={address?.id || 'new'} initial={address || undefined} onSave={saveAddress} onCancel={close} busy={busy} error={error} />}
    {canReadActivities && <ClientActivity clientId={detail.id} canWrite={canWriteActivities} />}
    {!kind && error && <p className="form-error" role="alert">{error}</p>}
  </>;
}
