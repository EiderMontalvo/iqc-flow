# Panel IQC Flow (iqc-flow-web)

Panel interno de IQC Perú e IQC LATAM para certificados, clientes y seguimientos. Lo usa el personal de IQC, sobre todo en computadora de oficina. Usa la API Spring Boot (`iqc-flow-api`) con sesión por cookie y CSRF.

Comparte el sistema de diseño con el portal público de consultas: [`../iqc-flow-diseno`](../iqc-flow-diseno/README.md).

## Espacios por rol

Cada rol entra a su propio espacio, con su inicio, solo las secciones de su área y sus gráficos. Está definido en `src/app/App.tsx` (`SPACES`).

| Rol | Espacio | Inicio | Gráficos del inicio |
|---|---|---|---|
| MASTER, ADMINISTRADOR | Dirección | Hoy | Vencimientos por mes · emitidos · vigentes por norma · cartera · seguimientos por prioridad |
| CERTIFICADOS | Certificados | Resumen | Vencimientos · emitidos · personales · vigentes por norma · por acreditación |
| RESPONSABLE_CERTIFICACIONES | Jefatura técnica | Resumen | (igual que Certificados) |
| COORDINADOR_AUDITORIAS | Operaciones | Hoy | Vencimientos · seguimientos abiertos por tipo · por prioridad · completados por semana |
| COMERCIAL | Comercial | Hoy | Cartera · clientes nuevos por mes · vencimientos · seguimientos por prioridad |
| ADMINISTRACION_LOGISTICA | Administración | Hoy | Emitidos · vencimientos · vigentes por acreditación |

**De dónde salen los datos:**
- Vienen de `GET /api/v1/indicadores`. La API solo entrega cada sección si el usuario tiene el permiso de esa área (`certificados:leer`, `seguimientos:leer` o `clientes:leer`).
- **Seguimientos:** sin `seguimientos:leer-todos`, se cuentan solo los propios.
- **Clientes nuevos:** no cuentan los importados del panel anterior.
- **Qué gráficos ve cada espacio:** se configura en `src/features/indicators/Indicators.tsx` (`POR_VISTA`).

**Qué forma tiene cada gráfico:** columnas para los vencimientos por mes, línea con área para las tendencias (emitidos, personales, clientes nuevos, completados), barras ordenadas para comparar categorías, barra segmentada para la cartera y medidores para las prioridades. El criterio está en `DESIGN.md` (Gráficos).

## Armazón

- **Disposición fija:** la ventana no se desplaza; solo lo hace el contenido (`#contenido`), así el carril y la cabecera quedan siempre quietos.
- **Carril negro** con las secciones del espacio de cada rol. En escritorio se oculta y se vuelve a mostrar con el botón de la cabecera (se recuerda en el navegador).
- **Cabecera** (`src/app/cabecera/`): saludo según la hora con el nombre, fecha y espacio; campana de **notificaciones** con el número sin leer (ya no están en el carril) y menú de **perfil** (datos de la cuenta, apariencia Claro / Oscuro / Sistema, cambio de contraseña y cerrar sesión). El cajón del perfil se monta en `document.body`. En el celular es la barra negra con el menú y el título de la pantalla.
- **Tarjetas de cifra** (`components/cifras/TarjetaCifra.tsx`): todas con el mismo fondo blanco y la cifra en tinta de texto; el tono solo tiñe el ícono. La seleccionada lleva borde azul. En Hoy y Seguimientos llevan a la vista correspondiente; en Empresariales, Personales y Clientes funcionan como filtros.
- **Gráficos:** al pulsar una tarjeta se abre su detalle (gráfico grande, resumen y tabla con participación y variación). Las fechas se escriben con el mes y el año completo cuando cambia («Nov / 2026»).

- **Apariencia:** Claro por defecto; Oscuro y Sistema se eligen en el menú de perfil y se recuerdan en el navegador.
- **Olvidé mi contraseña** (acceso): mientras no hay correo configurado, la solicitud (`POST /autenticacion/olvido-contrasena`, pública, limitada por IP y con la misma respuesta exista o no la cuenta) avisa en Notificaciones a quienes tienen `usuarios:gestionar`. Ellos asignan una contraseña temporal en Usuarios → Editar (`POST /usuarios/{id}/contrasena-temporal`); al ingresar, el sistema obliga a cambiarla. Cuando haya correo, se puede sumar el enlace por correo.
- **Temática de temporada:** MASTER la elige en Configuración (parámetro `TEMATICA`): Ninguna, Automática (según el calendario de Perú: Navidad 1–26 dic, Año Nuevo 27 dic–6 ene, Fiestas Patrias 15–31 jul, Día de la Madre y del Padre la semana de su domingo) o una fija. Fechas: Año Nuevo, San Valentín (7–14 feb), Día de la Mujer (1–8 mar), Día de la Madre, Día del Padre, Fiestas Patrias (con la bandera del Perú, `app/tematica/iconosPropios.tsx`), Primavera (20–23 set), Halloween (24–31 oct) y Navidad. Para agregar una: su clave en `TEMATICA` (API), su entrada en `TEMATICAS` y, si es automática, su rango en `tematicaDeFecha`. Pone el papel en blanco con íconos de la fecha detrás del contenido (`app/tematica/`) y un saludo en la cabecera, para todos.

## Certificados: espacio de trabajo con el PDF

Registrar, corregir y ver un certificado abre un cajón dividido: los datos a la izquierda y el PDF a la derecha (`components/pdf/VistaPdf.tsx`, `Drawer` con `vista`). El PDF se ve antes de guardar, se puede arrastrar al visor (en modo Automatizado se lee al soltarlo) y, con varias normas, cada una tiene su pestaña. En el detalle se ven la versión vigente, las anteriores y la nueva antes de subirla. Los documentos de la API se muestran desde una copia local (`api.documentBlob`) porque la API responde con `X-Frame-Options: DENY`. En celulares Android el navegador no muestra PDF dentro de la página: queda el botón para abrirlo en otra pestaña.

## Cotizaciones

Primer paso del proceso comercial (`docs/analisis/PROCESO COMERCIAL.xlsx`): pantalla `features/quotes/` (permiso `cotizaciones:leer`; crear y cambiar con `cotizaciones:gestionar`, que tienen Comercial, Administrador y MASTER; Administración y logística la consulta).
- **Flujo:** borrador → enviada (fecha y destinatarios) → aceptada o rechazada (con motivo); también anular o duplicar. Solo se edita el borrador; para corregir una enviada se duplica.
- **Número correlativo** `COT-AAAA-0001` por organización y año (tabla `correlativos`, reutilizable para contratos y cartas).
- **Montos:** una línea por norma (o servicio) con días auditor y monto sin impuesto; con tarifa por día el monto se propone solo (días × tarifa) y se puede cambiar. Moneda e impuesto del país del cliente (catálogo de países: PEN e IGV 18 % en Perú).
- **Alerta sin respuesta:** si una enviada no tiene respuesta en N días (Configuración → Comercial; 7 por defecto) se crea una tarea «Recordatorio de cotización» a su responsable, una sola vez; si el cliente responde o se anula, la tarea se cierra sola.
- **Cifras de la pantalla:** borradores, enviadas sin respuesta, aceptadas del mes y conversión (aceptadas sobre respondidas, 12 meses).
- **Varios roles:** quien tiene Comercial y Administración ve un solo espacio que une las secciones de ambos.
- **Partir de la solicitud del cliente** (`NuevaCotizacion.tsx`, `PanelSolicitud.tsx`): se suelta el correo guardado (.eml de Gmail «Descargar mensaje» u Outlook web; .msg de Outlook), un PDF, un Word (.docx) o texto, o se pega el correo. Del correo se leen remitente, fecha, asunto, cuerpo y el texto de los adjuntos PDF/Word/correo; en un reenvío, el contacto es el remitente original (el último «De:»), no quien reenvió. La API devuelve el texto leído (`texto`) y el panel lo muestra a la derecha cuando no es PDF. Con todo eso, y `POST /api/v1/cotizaciones/analisis-solicitud` devuelve una propuesta: empresa (RUC + SUNAT, y si ya es cliente), contacto, tipo de servicio, normas del catálogo, sedes, trabajadores, puntos clave y lo que falta pedir al cliente (con botón Copiar). El formulario se abre ya completado y a la derecha queda la solicitud original para comparar. Si la empresa no es cliente, «Registrar como prospecto y usarla». Nada se guarda hasta pulsar «Crear cotización».
- **IA:** sin clave funciona con reglas (expresiones regulares + SUNAT). Con `ANTHROPIC_API_KEY` en `/etc/iqc-flow/api.env` y la opción `IA_HABILITADA` activa para esa persona (Configuración → Funciones / Por persona), la API pide además a Claude (`claude-opus-5-5`, salida estructurada) un resumen y los puntos clave. El texto de la solicitud se envía a Anthropic.

Pendiente del proceso comercial: contrato desde la cotización aceptada, pagos, panel comercial con todos los indicadores y el documento Word/PDF de la cotización con envío por correo.

## Datos de SUNAT para completar formularios

La API consulta el padrón de SUNAT (`GET /api/v1/padron/ruc/{ruc}` y `/padron/dni/{dni}`, módulo `padron`, con caché de 12 h y tiempo límite de 8 s; el navegador no puede llamarlo directo porque SUNAT no tiene CORS). Se usa en:
- **Clientes → Nuevo cliente:** el formulario empieza por el RUC; el botón azul «Buscar» con la lupa (o Enter) completa la razón social y la dirección fiscal (dirección, distrito, provincia, departamento), editables. Al crear el cliente, esa dirección se guarda como su dirección FISCAL. Al editar solo se actualiza la razón social.
- **Registrar certificado personal:** con un DNI de Perú que no está en IQC Flow, se completan los nombres («Nombres Apellidos», partículas en minúscula). Hay que revisar tildes: SUNAT los da en mayúsculas.
- **Registrar certificado empresarial:** si se busca el titular por un RUC que no es cliente, se muestra qué empresa es según SUNAT.

No es una API oficial de SUNAT: si cambia o se cae, los formularios siguen funcionando a mano y la API responde «SUNAT no respondió».

## Configuración de MASTER

Pantalla `features/settings/` (permiso `organizacion:configurar`), organizada en secciones con un índice a la izquierda: **Funciones** (funciones generales, registro de certificados, Administración y logística, firma), **Personas** (opciones por persona) y **Apariencia** (temática de temporada). Las opciones se aplican en dos niveles:

- **Toda la organización:** cada interruptor enciende o apaga la opción para todos. Si a alguien se le apagó, la fila lo dice («Apagada para …»).
- **Por persona:** se elige a alguien y se le apagan las opciones que no debe usar, sin tocar al resto. Una opción vale para una persona solo si está encendida en las dos vistas; si está apagada para toda la organización, aquí aparece bloqueada.

Se pueden apagar por persona la IA, las automatizaciones y cada modo de registro (Manual, Automatizado, Desde plantilla) de certificados empresariales y personales. Las herramientas de Administración son del rol: a una persona se le apagan desde sus modos. API: `PUT /api/v1/organizacion/parametros/{clave}/personas/{usuarioId}` con `{"habilitado": false}`; cada cambio queda en la bitácora (`PARAMETRO_PERSONA_CAMBIADO`).

## Estructura

```
src/
├── app/App.tsx            Sesión, espacios por rol, carril de navegación y pantallas
│   └── cabecera/          Cabecera (saludo, notificaciones, perfil) y PerfilDialogo
├── lib/                   api.ts (contrato con la API), format.ts
├── components/            BrandMark, SimboloIqc, Drawer, Pagination, SearchField…
│   ├── cifras/            TarjetaCifra, FilaCifras
│   └── charts/            GraficoColumnas, GraficoArea, GraficoBarras, BarraSegmentada, ListaMedidores, PanelGrafico, escala.ts
├── features/              Una carpeta por área: auth, overview, indicators, certificates, personal, clients, tasks, settings…
└── styles/                tokens.css (trae el sistema compartido) · base · shell · components · screens · certificates · charts
```

## Desarrollo

```bash
npm install
npm run dev      # http://localhost:5173 — /api se reenvía a la API en :8080
```

Entra por `http://localhost:5173`. En desarrollo el proxy de Vite quita la cabecera `Origin`, porque la API solo admite los orígenes de producción.

## Compilar y publicar

```bash
VITE_API_URL=https://api.iqcperu.com npm run build    # → dist/ (zip para BanaHosting: /home/iqc/iqc-flow-dist/panel.zip)
```

El demo interno, protegido con contraseña de Apache, se compila con `VITE_API_URL=` vacío, porque la API está en el mismo dominio.

## Diseño

- **Claro, oscuro o Sistema:** se elige en el carril y se guarda en `localStorage`.
- **Carril y acceso:** en el negro de marca, con el símbolo IQC.
- **Área de trabajo:** en papel.
- **Tokens:** todos los colores vienen del sistema compartido. Un color nuevo se agrega ahí, para claro y oscuro, no en `src/styles`.
- **Gráficos:** siguen la skill de visualización. Una serie por gráfico, con tooltip al pasar el mouse o con el teclado y vista de tabla en cada uno.
- **Detalle de diseño:** en `DESIGN.md`.
