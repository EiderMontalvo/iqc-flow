import React from 'react';
import ReactDOM from 'react-dom/client';
import './style.css';
function App() {
  const [status, setStatus] = React.useState('Sin comprobar');
  async function ping() {
    try { const response = await fetch('/api/v1/publico/estado'); if (!response.ok) throw new Error(); const data = await response.json(); setStatus(`${data.servicio}: ${data.estado}`); }
    catch { setStatus('API no disponible. Inicia Spring Boot y PostgreSQL.'); }
  }
  return <main><aside><div className="brand">IQC <span>Flow</span></div><nav><div className="selected">Panel principal</div><div>Clientes · Próximamente</div><div>Certificaciones · Próximamente</div><div>Auditorías · Próximamente</div><div>Asistente IA · Próximamente</div></nav></aside><section className="content"><p className="eyebrow">FASE 1 · NÚCLEO DE PLATAFORMA</p><h1>Bienvenido a IQC Flow</h1><p>Base de desarrollo aislada. No hay conexión con los certificados de producción.</p><div className="card"><h2>Estado de la API</h2><p>{status}</p><button onClick={ping}>Comprobar conexión</button></div><div className="grid"><div className="card"><h2>PostgreSQL</h2><p>Esquema inicial mediante Flyway</p></div><div className="card"><h2>Seguridad</h2><p>Acceso cerrado salvo el endpoint público de prueba</p></div></div></section></main>;
}
ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><App/></React.StrictMode>);
