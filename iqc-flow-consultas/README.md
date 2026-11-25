# Portal de consultas IQC (iqc-flow-consultas)

Portal público donde cualquier persona comprueba si un certificado emitido por IQC es auténtico y está vigente.
Sirve para los dos tipos de certificado que emite IQC:

- **Empresariales:** sistemas de gestión ISO (9001, 14001, 45001, 37001…). Se consultan con el RUC de la empresa y el código del certificado.
- **Personales:** cursos y formación. Se consultan con el DNI o la cédula de la persona y el código de verificación.

Es el reemplazo del portal Laravel anterior (`/home/iqc/consultas`) y forma parte de IQC Flow:

| Pieza | Qué hace |
|---|---|
| `iqc-flow-api` | API Spring Boot. Publica los certificados en las bases públicas y expone `/api/v1/publico/consultas/{portal}` |
| `iqc-flow-web` | Panel interno donde IQC registra y gestiona los certificados |
| **`iqc-flow-consultas`** | Este portal: sitio estático que solo lee la API pública |

Hay **dos compilaciones** del mismo código:

| Portal | Dominio | Marca | Archivo de entorno |
|---|---|---|---|
| `PE` | consultas.iqcperu.com | IQC Perú | `.env.pe` |
| `LATAM` | consultas.iqc-latam.com | IQC LATAM (Ecuador, Colombia, Panamá) | `.env.latam` |

---

## Cómo funciona

### Rutas

El portal no usa enrutador: tiene dos rutas, resueltas en `src/App.tsx`.

| Ruta | Vista | Origen típico |
|---|---|---|
| `/` | `vistas/Verificacion.tsx`: formulario a pantalla completa | Alguien escribe el documento y el código, casi siempre en computadora |
| `/<token>` | `vistas/VistaQr.tsx`: muestra directamente el certificado | Escaneo del QR impreso en el certificado, casi siempre en el celular |
| `/personal-pdf/<token>` | Ninguna: Apache redirige a la API | URL impresa en los PDF de certificados personales antiguos |

Los QR y las URL ya impresas **no pueden romperse**. Por eso el `.htaccess` envía a `index.html` toda ruta que no sea un archivo.

### Flujo de una verificación

1. La persona escribe el documento y el código. No elige el tipo: la API reconoce si el código es empresarial o personal.
2. Mientras se consulta, la portada no cambia y el botón muestra «Consultando…».
3. **Si encuentra el certificado,** la cabecera se compacta y el certificado aparece debajo: sello y veredicto, datos, PDF embebido (solo en pantallas anchas) y constancia imprimible.
4. **Si falla,** la portada se queda (o vuelve) a pantalla completa y un toast explica qué pasó y qué hacer.

### Estados que se muestran

`src/lib/situaciones.ts` traduce cada estado a su palabra, color y frase.

| Tipo | Estado o situación | Se muestra como |
|---|---|---|
| Empresarial | `VIGENTE` | Vigente, «Válido hasta el …» |
| Empresarial | `VENCIDO` | Vencido, «Expiró el …» |
| Empresarial | `SIN_FECHA` | Registrado |
| Empresarial | `SUSPENDIDO` | Suspendido |
| Empresarial | `RETIRADO` | Retirado |
| Empresarial | `CANCELADO` | Anulado |
| Empresarial | `REEMPLAZADO` | Reemplazado |
| Personal | `EMITIDO` | Válido, «Emitido el …» |
| Personal | `ANULADO` | Anulado |

- La API calcula la situación de los empresariales a partir del estado y la fecha de expiración.
- Por decisión de producto **no se muestra la fecha de anulación**.
- Un estado desconocido se muestra tal cual, en gris, en lugar de romper la página.

---

## Estructura del código

```
src/
├── main.tsx                 Punto de entrada: título de la pestaña y montaje de React
├── App.tsx                  Elige la vista según la ruta (portada o QR)
├── config/
│   └── portal.ts            Portal (PE/LATAM), URL de la API, marca, textos del formulario, límites
├── api/
│   ├── tipos.ts             Contrato de la API pública (Empresarial, Personal, Resultado)
│   └── consultas.ts         verificar(), porQr(), URL de los PDF y ErrorConsulta (mensajes para el usuario)
├── lib/                     Funciones puras, sin React
│   ├── fechas.ts            Formato de fechas en español
│   ├── situaciones.ts       Estado → palabra, color y frase del veredicto
│   ├── seguridad.ts         Validación de redirecciones de QR y lectura segura de la ruta
│   ├── formulario.ts        Mensajes de validación del navegador en español
│   └── roseta.ts            Curva de seguridad del sello
├── hooks/
│   └── usePantallaAncha.ts  Decide si se embebe el PDF (solo ≥ 900 px)
├── componentes/
│   ├── FormularioVerificacion.tsx
│   ├── estructura/          Banda (cabecera), Fondo (símbolo IQC)
│   ├── certificado/         Hoja, Sello, Dato/TextoLargo, Resultados (hoja empresarial y personal)
│   └── avisos/              Toast (errores del formulario) y Aviso (errores dentro del contenido)
├── vistas/
│   ├── Verificacion.tsx     Portada y resultado de la verificación manual
│   └── VistaQr.tsx          Resultado de un QR
└── estilos/                 CSS por responsabilidad; index.css los importa en orden de cascada.
                             tokens.css trae el sistema de diseño compartido (../iqc-flow-diseno)
```

**Criterios de la estructura:**
- Las **dependencias van en un solo sentido**: `vistas → componentes → lib/api/config`. `lib/` no conoce React; `api/` no conoce componentes.
- **Todo lo que cambia entre Perú y LATAM** está en `config/portal.ts`. Ningún componente pregunta por el portal.
- **Los textos de error** para el usuario se escriben en `api/consultas.ts`, como `ErrorConsulta(titulo, detalle)`.
- **CSS:** el orden de `estilos/index.css` importa. `responsive.css`, `movimiento.css` e `impresion.css` van al final porque sobrescriben a los demás.

---

## Desarrollo

Requisitos: Node 20 o superior.

```bash
npm install
npm run dev        # http://localhost:5174; /api se reenvía a la API local en :8080 (vite.config.ts)
```

Para probar contra la API de prueba del servidor, basta con que el servicio `iqc-flow-prueba` esté corriendo en el puerto 8080.

**Datos de prueba** de la base pública de prueba:

| Caso | Documento | Código |
|---|---|---|
| Empresarial vigente | 20447882923 | PER/OHSMS/JAS-C0084/0805 |
| Empresarial suspendido | 20552260679 | PER/IMS-QI/JAS–C0113/1351 |
| Personal emitido | 43813319 | IQC/PE/ABMS322 |
| Inexistente (toast) | 20100000001 | PE-XXX-1 |
| QR | `/fdc7efd5-ac04-46ff-a5ae-a4e839395034` | — |

### Variables de entorno

| Variable | Valores | Uso |
|---|---|---|
| `VITE_PORTAL` | `PE` o `LATAM` | Marca, textos y portal de la API |
| `VITE_API_URL` | p. ej. `https://api.iqcperu.com` | Origen de la API. Vacío: mismo dominio (desarrollo y demo) |

---

## Compilar y publicar

```bash
npm run build:pe       # → dist-pe/
npm run build:latam    # → dist-latam/
```

Cada carpeta es un sitio estático completo: `index.html`, `assets/`, logos y un **`.htaccess` generado** por el plugin de `vite.config.ts`. Ese `.htaccess` contiene:

- la redirección a HTTPS;
- la redirección `/personal-pdf/<token>` hacia la API;
- el *fallback* de QR (`/<token>` → `index.html`);
- las cabeceras de seguridad (ver abajo).

**Publicación en BanaHosting:** se sube el contenido de `dist-pe/` a la raíz de consultas.iqcperu.com y el de `dist-latam/` a la de consultas.iqc-latam.com, incluido el `.htaccess`, que es un archivo oculto. Los zip listos para subir se generan en `/home/iqc/iqc-flow-dist/consultas-pe.zip` y `consultas-latam.zip`.

**Demo interno:** existe una copia en `https://iqcflow-demo.iqcperu.com/consultas/`. Se compila con la API en el mismo dominio y desde una subcarpeta:

```bash
VITE_API_URL= npx vite build --mode pe --base /consultas/ --outDir <carpeta>
```

**Después de publicar:**

```bash
curl -I https://consultas.iqcperu.com/   # deben aparecer Content-Security-Policy, X-Frame-Options, Strict-Transport-Security
```

Además conviene abrir un QR real y hacer una verificación con PDF.

---

## Seguridad

| Medida | Dónde |
|---|---|
| Límite de 60 consultas por minuto e IP; responde 429. Usa la IP real detrás de Apache: falsificar `X-Forwarded-For` no lo burla | API: `LimitadorConsultas`, `forward-headers-strategy: native` |
| Sin datos internos: el portal solo lee las bases públicas, que no contienen expedientes ni contactos | API: `ServicioConsultaPublica` |
| No se revela nada sin documento **y** código correctos | API |
| El PDF personal solo se entrega mientras el certificado siga emitido | API |
| El PDF puede embeberse solo desde los portales oficiales (`frame-ancestors`) | API: `ConfiguracionSeguridad` |
| CSP: el sitio solo carga sus propios archivos y solo se conecta con la API | `.htaccess` (vite.config.ts) |
| `X-Frame-Options: DENY` y `frame-ancestors 'none'`: nadie puede enmarcar el portal | `.htaccess` |
| HTTPS forzado y HSTS, `nosniff`, `Referrer-Policy`, `Permissions-Policy`, `COOP` | `.htaccess` |
| Un QR antiguo solo reenvía a `consultas.iqcperu.com` o `consultas.iqc-latam.com` | `lib/seguridad.ts` |
| Una ruta mal codificada (`/%E0`) no rompe la página | `lib/seguridad.ts` |
| Sin `dangerouslySetInnerHTML`, sin mapas de código en producción, `npm audit` sin vulnerabilidades | Código |

**Si la API cambia de dominio,** basta con recompilar: la CSP se arma con `VITE_API_URL`.

**Los PDF de certificados importados** del sistema anterior se sirven desde `storage.googleapis.com`. Por eso está permitido en `frame-src`.

---

## Diseño

- **Sistema compartido:** fuentes, colores, escalas y el símbolo IQC vienen de [`../iqc-flow-diseno`](../iqc-flow-diseno/README.md), el mismo sistema que usa el panel interno. El portal no define colores propios.
- **Dirección visual:** cabecera negra con el símbolo de IQC trazado en línea fina con el degradado de la marca. Los colores, la tipografía y las reglas están en [`DESIGN.md`](DESIGN.md).
- **Contexto de producto:** usuarios, propósito y compromisos de marca en [`PRODUCT.md`](PRODUCT.md).

**Reglas que no deben romperse:**

1. **La portada no tiene desplazamiento.** Se verificó en 1920×950, 1536×730, 1440×760, 1440×900, 1366×657, 1280×720, 1280×600, 1280×540, 1024×640, 1024×520, 768×900, 412×780, 390×664, 390×844, 375×667, 375×560, 360×640 y 320×568. Al cambiar textos o tamaños hay que volver a medir: `document.documentElement.scrollHeight` debe ser igual a la altura de la ventana.
2. **Debe quedar claro que el portal sirve para certificados empresariales y personales.** Lo dicen el antetítulo, la bajada y la etiqueta «RUC o DNI del titular».
3. **Respetar «reducir movimiento».** Cada animación nueva debe detenerse en `estilos/movimiento.css`, con un selector de **igual o mayor especificidad** que el que la activa.
4. **Accesibilidad:**
   - etiquetas visibles en los campos;
   - foco visible;
   - el toast se anuncia (`role="status"`);
   - el sello tiene texto alternativo;
   - lo decorativo lleva `aria-hidden`.
