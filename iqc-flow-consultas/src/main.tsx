import { StrictMode } from 'react';
import ReactDOM from 'react-dom/client';
import { App } from './App';
import { MARCA } from './config/portal';
import './estilos/index.css';

document.title = `Validación de certificados · ${MARCA.nombre}`;

ReactDOM.createRoot(document.getElementById('root')!).render(<StrictMode><App /></StrictMode>);
