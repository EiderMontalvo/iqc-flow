import { RAIZ } from './config/portal';
import { tokenDeRuta } from './lib/seguridad';
import { Verificacion } from './vistas/Verificacion';
import { VistaQr } from './vistas/VistaQr';

/**
 * Dos rutas, sin enrutador: la portada (`/`) y cualquier otra ruta, que es el identificador de un QR (`/<token>`).
 * Apache sirve `index.html` para todas ellas (el `.htaccess` lo genera vite.config.ts).
 */
export function App() {
  const token = tokenDeRuta(window.location.pathname, RAIZ);
  return <div className="pagina">
    {token ? <VistaQr token={token} /> : <Verificacion />}
  </div>;
}
