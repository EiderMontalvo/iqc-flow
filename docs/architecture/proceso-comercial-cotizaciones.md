# Proceso comercial: cotizaciones y solicitudes de clientes

**Origen:** `docs/analisis/PROCESO COMERCIAL.xlsx`. **Iniciado:** 24/11/2026. **Estado al 24/11/2026:** cotizaciones y
análisis de la solicitud hechos, probados y desplegados en la demo. **La configuración de los correos está pendiente**
(sección 4). Decisión de arquitectura: ADR-045 en [DECISIONS.md](DECISIONS.md).

| Parte del proceso | Estado |
|---|---|
| 1. Cotizaciones: registro, numeración, flujo y alerta sin respuesta | ✔ Hecho (V16) |
| 2. Analizar la solicitud del cliente para crear la cotización | ✔ Hecho, con reglas + SUNAT; la IA está lista pero apagada (sección 3.3) |
| 3. Leer el correo guardado del cliente (.eml / .msg) | ✔ Hecho |
| 4. **Correo entrante:** buzón que IQC Flow lee solo | ⏳ **Pendiente: falta crear el buzón y sus credenciales** (sección 4.1) |
| 5. **Correo saliente:** enviar la cotización al cliente | ⏳ **Pendiente: falta la cuenta de envío y el documento** (sección 4.2) |
| 6. Documento Word/PDF de la cotización desde plantilla | ⏳ Pendiente: requiere aprobar LibreOffice (ver [pendiente-configuracion-master-plantillas.md](pendiente-configuracion-master-plantillas.md)) |
| 7. Contrato desde la cotización aceptada | ⏳ Siguiente paso |
| 8. Pagos: anticipo y saldo, facturas, alertas, bloqueo de etapas | ⏳ Pendiente |
| 9. Panel comercial con los indicadores de la hoja 3 y exportación a Excel | ⏳ Pendiente (hoy hay 4 cifras en la pantalla) |

### Puntos del Excel (hoja de requerimientos) y su estado

| Punto del Excel | Estado |
|---|---|
| Cotizaciones según normas ISO, alcance, sedes, servicios y días auditor | ✔ Hecho |
| Envío: registrar fecha y destinatarios | ✔ Hecho (a mano, con «Marcar como enviada») |
| Alerta de cotizaciones pendientes de respuesta | ✔ Hecho (tarea a los N días) |
| Registrar si el cliente aceptó o rechazó | ✔ Hecho (con el motivo del rechazo) |
| Numeración automática y correlativa | ✔ Hecho (`COT-2026-0001`) |
| Historial de cambios y trazabilidad | ✔ Bitácora de cada paso |
| Analizar la solicitud del cliente y crear con ella la cotización y el contrato | ◐ La cotización ✔ (correo .eml/.msg, PDF, Word o texto; reglas + SUNAT; IA lista pero apagada). El contrato llega con el contrato |
| Cotización + contrato + requisitos | ◐ Falta el contrato y la lista de requisitos o documentos que se piden al cliente |
| Generar y descargar el documento en PDF y Word | ✘ Falta: necesita la plantilla de IQC y, para pasar de Word a PDF, LibreOffice |
| Integración con correo electrónico | ✘ Falta: necesita el buzón y la cuenta de envío (sección 4). Ya se puede soltar el correo guardado |
| Correos comerciales con plantillas para distintas situaciones | ✘ Falta: envío de la cotización, recordatorio sin respuesta, agradecimiento por aceptar… (depende de la cuenta de envío) |
| Seguimiento comercial: registro de servicios contratados | ◐ Llega con el contrato |
| Reportes de cotizaciones y servicios contratados, y exportación a Excel | ✘ Falta la exportación y los indicadores comerciales: emitidas por mes, aceptadas y rechazadas, conversión y ventas |

---

## 1. Cotizaciones

### 1.1 Qué hace

- **Flujo de estados:** `BORRADOR` → `ENVIADA` (con fecha y destinatarios) → `ACEPTADA` o `RECHAZADA` (con motivo).
  - Se puede **anular** con motivo o **duplicar**.
  - Solo se edita el borrador. Para corregir una cotización ya enviada, se duplica.
- **Número correlativo** `COT-AAAA-0001` por organización y año.
  - Lo genera la tabla `correlativos` con un `INSERT … ON CONFLICT … RETURNING` atómico (`Correlativos.siguienteCodigo`).
  - La misma tabla servirá para contratos (`CON`) y cartas.
- **Montos:**
  - Una línea por norma o servicio, con días auditor y monto sin impuesto.
  - Con tarifa por día, el panel propone el monto (días × tarifa) y se puede cambiar.
  - Moneda e impuesto salen del país del cliente: PEN e IGV 18 % en Perú.
  - Aún no se sabe cómo calcula IQC sus precios, por eso el monto es manual.
- **Alerta sin respuesta:**
  - `DetectorCotizacionesSinRespuesta` revisa cada hora.
  - Si una cotización enviada no tiene respuesta en N días, crea una tarea `RECORDATORIO_COTIZACION` para su responsable, una sola vez.
  - N se configura en `COTIZACION_DIAS_SIN_RESPUESTA` (Configuración → Comercial; 7 por defecto).
  - Si el cliente responde o la cotización se anula, la tarea se cierra sola.
- **Cifras de la pantalla:** borradores, enviadas sin respuesta, aceptadas del mes y conversión (aceptadas sobre respondidas, últimos 12 meses).
- **Permisos:**
  - `cotizaciones:leer` para Comercial, Administrador y Administración y logística;
  - `cotizaciones:gestionar` para Comercial y Administrador;
  - MASTER tiene ambos.
  - En IQC, Comercial y Administración los hace una sola persona. El panel une los espacios de quien tiene varios roles.

### 1.2 Dónde está

| Capa | Ubicación |
|---|---|
| Migración | `iqc-flow-api/src/main/resources/db/migration/V16__cotizaciones.sql` (`correlativos`, `cotizaciones`, `cotizaciones_items`, permisos) |
| Dominio | `cotizaciones/domain`: `Cotizacion`, `ItemCotizacion`, `EstadoCotizacion`, `TipoServicioCotizacion` |
| Servicio | `cotizaciones/application/ServicioCotizaciones`, `DtosCotizaciones` |
| API | `cotizaciones/api/CotizacionesController` |
| Panel | `iqc-flow-web/src/features/quotes/` (`Cotizaciones`, `NuevaCotizacion`, `FormularioCotizacion`, `DetalleCotizacion`, `PanelSolicitud`) |
| Pruebas | `CotizacionesIT`, `AnalisisSolicitudIT`, `LectorSolicitudTest` |
| Ejemplos HTTP | [docs/api/http/cotizaciones.http](../api/http/cotizaciones.http) |

### 1.3 Endpoints (`/api/v1/cotizaciones`)

| Método y ruta | Permiso | Qué hace |
|---|---|---|
| `GET /` (`estado`, `sinRespuesta`, `texto`, `clienteId`, `pagina`, `tamano`) | `cotizaciones:leer` | Lista paginada |
| `GET /resumen` | `cotizaciones:leer` | Cifras de la pantalla |
| `GET /{id}` | `cotizaciones:leer` | Detalle |
| `POST /` | `cotizaciones:gestionar` | Crea en borrador y le asigna número |
| `PUT /{id}` | `cotizaciones:gestionar` | Edita el borrador (con `version`) |
| `POST /{id}/envio` | `cotizaciones:gestionar` | Marca como enviada (fecha, destinatarios) |
| `POST /{id}/respuesta` | `cotizaciones:gestionar` | Aceptada o rechazada (con motivo) |
| `POST /{id}/anulacion` | `cotizaciones:gestionar` | Anula con motivo |
| `POST /{id}/duplicado` | `cotizaciones:gestionar` | Copia como borrador nuevo |
| `POST /analisis-solicitud` (multipart: `archivo` y/o `texto`; 15 MB) | `cotizaciones:gestionar` | Propuesta a partir de la solicitud (sección 2). No guarda nada |

Endpoints relacionados: `GET /api/v1/padron/ruc/{n}` y `GET /api/v1/padron/dni/{n}` consultan SUNAT. Tienen caché de 12 h y
un tiempo de espera de 8 s.

---

## 2. Crear la cotización a partir de la solicitud del cliente

### 2.1 Cómo se usa

1. En **Cotizaciones → Nueva cotización**, se suelta el archivo o se pega el texto. Puede ser:
   - el correo guardado: en Gmail, ⋮ → «Descargar mensaje» (.eml); en Outlook, arrastrar el correo al escritorio (.msg);
   - un PDF o un Word (.docx);
   - el texto pegado.
2. Con **Analizar solicitud** se abre el formulario ya completado y, a la derecha:
   - los puntos clave;
   - lo que falta pedir al cliente, con un botón **Copiar**;
   - la empresa según SUNAT, con «Registrar como prospecto y usarla» si aún no es cliente;
   - el documento o el texto del correo, para comparar.
3. La persona revisa y pulsa **Crear cotización**. **Nada se guarda antes de ese paso.** El análisis solo deja una
   entrada `SOLICITUD_ANALIZADA` en la bitácora.

### 2.2 Qué se lee

| Dato | Cómo |
|---|---|
| Texto | `LectorSolicitud` reconoce el tipo por los primeros bytes, no por la extensión. Lee PDF con PDFBox (20 páginas), Word .docx con POI, .eml con Jakarta Mail (angus-mail) y .msg con POI HSMF (poi-scratchpad). Corta a 60 000 caracteres |
| Correo | Remitente, fecha (hora de Lima), asunto y cuerpo (texto plano o, si no hay, HTML sin etiquetas). Lee hasta 6 adjuntos PDF, Word, texto o correo reenviado como adjunto (2 niveles). Omite las imágenes |
| RUC, correo, teléfono, normas ISO, trabajadores, sedes, tipo de servicio | Reglas en `AnalisisLocalSolicitud` |
| Contacto | Se busca en este orden: (1) el **último** «Correo de / De / From: Nombre <correo>», que en un reenvío es el cliente original y no quien reenvió; (2) «Contacto: Nombre»; (3) el nombre bajo la despedida («Saludos cordiales, …») |
| Empresa | Con el RUC: SUNAT (razón social y dirección) y si ya es cliente |
| Normas | Se traducen al catálogo con su edición. Las que no están en el catálogo generan un aviso |

### 2.3 La IA

- **Dónde va:** dentro del paso «Analizar solicitud», no como módulo aparte en el carril. Un asistente en una
  esquina, para preguntas sobre los datos, queda para más adelante.
- **Qué agrega cuando está encendida:** resumen, puntos clave, faltantes, alcance, detalle de sedes, plazo, y cargo y
  teléfono del contacto. Las reglas y SUNAT siempre corren igual; si la IA falla, se usan solo ellas y se avisa.
- **Proveedor:**
  - Claude, con el SDK oficial `com.anthropic:anthropic-java`, modelo `claude-opus-5-5`, configurable con `iqc.ia.modelo`.
  - Usa salida estructurada.
  - El texto del cliente va dentro de `<solicitud>`, con la instrucción de tratarlo como datos y no como órdenes.
  - Adaptador: `ia/infrastructure/AnalistaSolicitudesClaude`, que implementa el puerto `cotizaciones/AnalistaIaSolicitudes`.
- **Estado: apagada.** Para encenderla hacen falta:
  1. el **OK del usuario** para enviar a Anthropic el texto de las solicitudes de los clientes;
  2. una **clave de API de Anthropic**: `ANTHROPIC_API_KEY=...` en `/etc/iqc-flow/api.env` y reiniciar `iqc-flow-prueba`;
  3. encender `IA_HABILITADA` en Configuración, para todos o por persona.

  Sin clave, `disponible()` es `false` y no se llama a nadie.

---

## 3. Verificación (24/11/2026)

- **API:**
  - suite completa: 71 pruebas unitarias y 128 de integración, todas pasan;
  - después del lector de correos: `LectorSolicitudTest` 4/4, `AnalisisSolicitudIT` 3/3 y `ModulosTest`.
- **Panel:**
  - probado con Playwright con una solicitud pegada y con un .eml reenviado ficticio;
  - contacto, correo, normas, servicio, trabajadores y sedes salen completados;
  - sin errores de consola.
- **Desplegado:** JAR en `/home/iqc/iqc-flow-run` (anterior en `iqc-flow-api.jar.bak-20261124f`), panel en la demo y
  `iqc-flow-dist/panel.zip`.
- **Dato de prueba en la demo:** cotización `COT-2026-0001` (111 DESIGN S.A.C., borrador).

---

## 4. ⏳ Pendiente: configuración de los correos

### 4.0 Situación actual del correo de IQC

- **Recibir:** el dominio `iqcperu.com` tiene sus registros MX en **Google Workspace** (`aspmx.l.google.com`). Todo
  correo dirigido a `@iqcperu.com` llega a Google.
- **Enviar:** el SPF autoriza a Google, a los servidores de BanaHosting y a MailChannels
  (`include:_spf.google.com`, `include:relay.mailchannels.net` y dos IP). El correo de BanaHosting (webmail) **solo
  sirve para enviar**: no recibe correo externo de `@iqcperu.com`.

### 4.1 Correo entrante: «Solicitudes recibidas»

**Objetivo:** que las solicitudes lleguen solas a IQC Flow, sin descargar ni pegar nada.

**Lo que tiene que hacer IQC (pendiente):**

1. Crear en Google Workspace un **buzón dedicado**, por ejemplo `cotizaciones@iqcperu.com`.
   - Ocupa una licencia de Workspace.
   - No conviene usar un buzón personal: el sistema podría leer todo su contenido.
   - Un Grupo de Google no sirve, porque no se lee por IMAP.
2. En esa cuenta: activar la **verificación en 2 pasos** y crear una **contraseña de aplicación**
   (Cuenta de Google → Seguridad → Contraseñas de aplicaciones).
   - Comprobar en la consola de administración que IMAP esté permitido.
3. Entregar la contraseña de aplicación al equipo técnico, que la guarda en `/etc/iqc-flow/api.env` y no en el código
   ni en el repositorio.
4. Opcional: en los buzones donde hoy llegan solicitudes, crear un filtro de Gmail que las reenvíe a `cotizaciones@`.

**Lo que se construirá cuando exista el buzón:**

- **Lectura:** por IMAP (`imap.gmail.com:993`, SSL) con Jakarta Mail, que ya está incluido.
  - Cada pocos minutos, con un bloqueo de PostgreSQL para que solo lea una instancia.
  - Solo lee los correos nuevos (por UID) y los marca con una etiqueta, sin borrarlos.
- **Almacenamiento:** tabla `solicitudes_recibidas` con remitente, asunto, fecha, `message_id` (para no duplicar),
  estado (`NUEVA`, `COTIZADA`, `DESCARTADA`), el .eml guardado como documento y el resultado del análisis.
- **Pantalla:** **Cotizaciones → Solicitudes recibidas**, con «Crear cotización» (abre el mismo formulario completado de
  la sección 2), «Descartar» y el enlace a la cotización creada.
  - Permite medir el tiempo de respuesta, un indicador de la hoja 3.
- **Configuración** (variables propuestas):
  - `IQC_CORREO_ENTRADA_USUARIO` y `IQC_CORREO_ENTRADA_CONTRASENA`;
  - `IQC_CORREO_ENTRADA_HOST`, por defecto `imap.gmail.com`;
  - `IQC_CORREO_ENTRADA_INTERVALO`.
  - Sin usuario, la lectura queda apagada.
- **Seguridad:**
  - los adjuntos se tratan como datos no confiables: solo se extrae texto, no se ejecuta ni se abre nada más;
  - límite de tamaño;
  - el texto va a la IA solo si está encendida (sección 2.3).
- **Alternativa no elegida:** Gmail API con cuenta de servicio y delegación de dominio. Es más robusta, pero exige
  configurar la consola de Google Cloud y de Workspace. Se puede migrar después sin cambiar la pantalla.

### 4.2 Correo saliente: enviar la cotización

**Lo que hay que decidir y configurar (pendiente):**

1. **Desde qué cuenta se envía.** Se recomienda Google Workspace (`smtp.gmail.com:587`, con una contraseña de
   aplicación), porque el MX del dominio está en Google y así se entrega mejor.
   - Puede ser el mismo `cotizaciones@`.
   - La alternativa es el SMTP de BanaHosting, que el SPF ya autoriza.
2. **El documento de la cotización** (Word/PDF desde una plantilla de IQC). Requiere aprobar la instalación de
   LibreOffice en el servidor, que usa unos 200–400 MB de RAM mientras convierte y nada en reposo.
3. **Qué hace IQC Flow al enviar:**
   - genera el PDF y lo envía al contacto con copia al responsable;
   - pasa la cotización a `ENVIADA` con fecha y destinatarios;
   - guarda el correo enviado en la bitácora.

   Hoy el envío se registra a mano en «Marcar como enviada».

La misma cuenta servirá para el enlace de «¿Olvidaste tu contraseña?», que hoy avisa a quienes gestionan usuarios porque
aún no hay correo configurado.

---

## 5. Orden siguiente propuesto

1. Contrato desde la cotización aceptada (correlativo `CON-AAAA-0001`, datos copiados de la cotización).
2. Correo entrante (4.1), apenas exista el buzón.
3. Pagos: anticipo y saldo, facturas, alertas y bloqueo de etapas.
4. Documento de la cotización y envío por correo (4.2), cuando estén LibreOffice y la cuenta de envío.
5. Panel comercial con los indicadores de la hoja 3 y exportación a Excel.
