# IQC Flow

Plataforma interna de IQC Perú para centralizar el ciclo comercial, operativo y de certificación,
con automatización de seguimientos, reuniones y documentos.

Monolito modular: Java 21 + Spring Boot 4.1 (`iqc-flow-api`), React + TypeScript (`iqc-flow-web`),
PostgreSQL 18 y Flyway. Desarrollo en Windows 10, sin Docker.

**Estado:**
- **Incremento 1 (base ejecutable y segura):** identidad (usuarios, roles, permisos), inicio de sesión con
  cookie y CSRF, bitácora inmutable, manejo de errores, OpenAPI y pruebas contra PostgreSQL real.
- **Incremento 2 (clientes):** prospectos y clientes con RUC validado, contactos, direcciones y sedes,
  responsable comercial, búsqueda sin tildes, archivo y restauración.
- **Incremento 3 (seguimientos):** tareas con responsable y fecha límite, historial, actividades con
  clientes, Centro de seguimientos (hoy, atrasadas, próximas, en espera, clientes sin movimiento) y avisos
  internos de tareas vencidas o asignadas.
- **Multipaís:** clientes de Perú y Ecuador (RUC ecuatoriano validado), direcciones con niveles según el
  país y catálogos de países (moneda e impuesto), esquemas de acreditación (INACAL, SAE, IQC Global) y
  normas con su código de sistema y ediciones.
- **Incremento 5 (expedientes):** expediente de certificación (cliente, esquema, tipo CI/RC/AMP, varias normas
  con edición, sedes, aliado comercial), flujo con India o INACAL/SAE, auditorías con equipo auditor, ciclo
  automático de vigilancias con tareas de seguimiento, auditores y aliados comerciales.
- **Incremento 6 (decisiones y certificados):** control automático de imparcialidad y declaraciones de
  conflicto de interés, decisiones DDC-PRO-003 con vía del Comité de Imparcialidad, certificados por norma con
  código generado, checklist y firma configurable (Gerencia, o Gerencia y Jefe Técnico), certificados de India,
  entrega bloqueada por pagos y encuesta, plazos de suspensión y documentos adjuntos.
- Siguiente: motor de automatizaciones y módulo comercial (Fase 3). Ver el plan en
  [docs/architecture/plan-tecnico-inicial.md](docs/architecture/plan-tecnico-inicial.md).

```text
iqc-flow/
├── iqc-flow-api/     Backend Spring Boot (módulos en pe.com.iqc.flow.*)
├── iqc-flow-web/     Frontend React + Vite
├── docs/             Arquitectura, decisiones (ADR) y ejemplos HTTP
└── scripts/          Scripts de apoyo (base de datos local)
```

## Requisitos en Windows 10

| Herramienta | Comprobación |
|---|---|
| JDK 21 | `java -version` |
| PostgreSQL 18 como servicio de Windows | `Get-Service postgresql-x64-18` |
| Node.js y npm | `node -v`, `npm -v` |

Maven **no** necesita instalarse: el proyecto incluye Maven Wrapper (`mvnw.cmd`).

## 1. Preparar la base de datos local

Una sola vez, desde la raíz del repositorio. Crea el usuario `iqc_dev` y las bases `iqc_flow` (desarrollo)
e `iqc_flow_test` (pruebas). Pedirá la contraseña del usuario `postgres`.

```powershell
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -U postgres -d postgres `
  -v clave="ELIGE_UNA_CLAVE_PARA_IQC_DEV" -f scripts\db\crear-bases-locales.sql
```

Luego copia `iqc-flow-api\config\local.example.yml` como `iqc-flow-api\config\local.yml` (git lo ignora)
y coloca esa clave en `iqc.local.clave-bd`. Como alternativa, define `DB_PASSWORD` en el entorno.

Este procedimiento es **solo para PostgreSQL local**. Nunca se ejecuta contra MariaDB ni contra producción.

## 2. Iniciar el backend

```powershell
cd iqc-flow-api
.\mvnw.cmd spring-boot:run
```

- API: `http://localhost:8080` — Flyway aplica las migraciones al arrancar.
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Estado: `Invoke-RestMethod http://localhost:8080/api/v1/publico/estado`

### Crear el primer usuario MASTER (una sola vez)

No existen usuarios ni contraseñas por defecto. El primer usuario `MASTER` se crea con el arranque inicial:

```powershell
cd iqc-flow-api
$env:IQC_MASTER_INICIAL = "true"
$env:IQC_MASTER_CORREO = "tu.correo@iqcperu.com"
$env:IQC_MASTER_NOMBRE = "Tu Nombre"
$env:IQC_MASTER_CONTRASENA = "Una-Frase-Temporal-Larga"
.\mvnw.cmd spring-boot:run
```

- Crea la organización IQC Perú y el usuario con el rol `MASTER`. Queda registrado en la bitácora.
- La contraseña debe tener al menos 12 caracteres. Se exige cambiarla en el primer inicio de sesión.
- Si ya existe un usuario `MASTER`, no hace nada.
- Después cierra esa consola o elimina las variables: `Remove-Item Env:IQC_MASTER_*`.

## 3. Probar la API

Con la extensión **REST Client** de VS Code (`humao.rest-client`):

1. Copia `docs/api/http/ejemplo.env` como `docs/api/http/.env` (git lo ignora) y completa tu correo y contraseñas.
2. Abre [docs/api/http/identidad.http](docs/api/http/identidad.http) y ejecuta las solicitudes en orden:
   CSRF → inicio de sesión → CSRF → cambio de contraseña → usuarios, roles y bitácora.
3. Para clientes, usa [docs/api/http/clientes.http](docs/api/http/clientes.http): verificación de RUC,
   registro, activación, búsqueda, contactos, direcciones, historial y archivo.
4. Para seguimientos, usa [docs/api/http/seguimientos.http](docs/api/http/seguimientos.http): tareas, vistas
   del centro, completar con siguiente tarea, reprogramar, actividades y notificaciones.

### Autenticación

| Paso | Detalle |
|---|---|
| Token CSRF | `GET /api/v1/autenticacion/csrf` emite la cookie `XSRF-TOKEN`; su valor se envía en la cabecera `X-XSRF-TOKEN` en todo `POST`, `PUT`, `PATCH` y `DELETE` |
| Inicio de sesión | `POST /api/v1/autenticacion/inicio-sesion` (formulario `correo`, `contrasena`) → 204 y cookie `IQCFLOWSESSION` |
| Cierre de sesión | `POST /api/v1/autenticacion/cierre-sesion` → 204 |
| Bloqueo | 5 intentos fallidos bloquean la cuenta 15 minutos; 20 fallos por IP en 15 minutos devuelven 429 |

### Endpoints del Incremento 1

| Método y ruta | Permiso |
|---|---|
| `GET /api/v1/publico/estado` | público |
| `GET /api/v1/autenticacion/yo` | sesión |
| `POST /api/v1/autenticacion/cambio-contrasena` | sesión |
| `GET /api/v1/usuarios`, `GET /api/v1/usuarios/{id}`, `GET /api/v1/roles` | `usuarios:leer` |
| `POST /api/v1/usuarios`, `PATCH /api/v1/usuarios/{id}` | `usuarios:gestionar` |
| `PUT /api/v1/usuarios/{id}/roles` | `roles:asignar` |
| `GET /api/v1/bitacora` | `bitacora:leer` |

### Endpoints del Incremento 2 (clientes)

| Método y ruta | Permiso |
|---|---|
| `GET /api/v1/clientes` (texto, estado, responsableId, incluirArchivados) | `clientes:leer` |
| `GET /api/v1/clientes/{id}`, `GET /api/v1/clientes/verificacion-documento` | `clientes:leer` |
| `POST /api/v1/clientes` | `clientes:escribir` (otro responsable: también `clientes:reasignar`) |
| `PATCH /api/v1/clientes/{id}` | `clientes:escribir` y ser el responsable, o `clientes:escribir-todos` |
| `POST\|PUT\|DELETE /api/v1/clientes/{id}/contactos[/{contactoId}]` | igual que editar el cliente |
| `POST\|PUT\|DELETE /api/v1/clientes/{id}/direcciones[/{direccionId}]` | igual que editar el cliente |
| `PUT /api/v1/clientes/{id}/responsable` | `clientes:reasignar` |
| `POST /api/v1/clientes/{id}/archivo`, `POST /api/v1/clientes/{id}/restauracion` | `clientes:archivar` |

Reglas principales: el RUC se valida con el dígito verificador de SUNAT; no puede haber dos clientes vigentes con el
mismo documento; un cliente `ACTIVO` necesita documento; solo hay una dirección `FISCAL` y un contacto principal;
la bitácora guarda el correo y el teléfono de los contactos enmascarados.

### Endpoints del Incremento 3 (seguimientos y notificaciones)

| Método y ruta | Permiso |
|---|---|
| `GET /api/v1/seguimientos/tareas` (vista, mias, responsableId, clienteId, tipo, estado, prioridad, dias) | `seguimientos:leer` |
| `GET /api/v1/seguimientos/tareas/{id}`, `GET /api/v1/seguimientos/tipos-tarea` | `seguimientos:leer` |
| `POST /api/v1/seguimientos/tareas` | `seguimientos:escribir` (para otra persona: `seguimientos:asignar`) |
| `PATCH /api/v1/seguimientos/tareas/{id}` | ser el responsable, o `seguimientos:asignar` / `seguimientos:leer-todos` |
| `POST /api/v1/seguimientos/tareas/{id}/completado`, `/reprogramacion`, `/cancelacion` | igual que editar |
| `PUT /api/v1/seguimientos/tareas/{id}/responsable` | `seguimientos:asignar` |
| `GET /api/v1/seguimientos/resumen`, `GET /api/v1/seguimientos/clientes-sin-movimiento` | `seguimientos:leer` |
| `POST /api/v1/seguimientos/actividades`, `GET /api/v1/seguimientos/actividades?clienteId=` | `escribir` / `leer` |
| `GET /api/v1/notificaciones`, `/contador`, `POST /{id}/lectura`, `POST /lectura` | sesión (solo las propias) |

Vistas: `ABIERTAS` (por defecto), `HOY`, `ATRASADAS`, `PROXIMAS` (de mañana a `dias`), `EN_ESPERA`, `CERRADAS`,
`TODAS`. Un usuario ve sus tareas y las de los clientes a su cargo; con `seguimientos:leer-todos`, todas.
Completar exige un resultado; reprogramar y cancelar exigen un motivo. Cada 15 minutos un proceso avisa una sola
vez al responsable de cada tarea vencida (`iqc.seguimientos.revision-vencidas`).

Los errores siguen RFC 9457 (`application/problem+json`) con `codigo` estable e `idCorrelacion`.

### Multipaís y catálogos

| Método y ruta | Permiso |
|---|---|
| `GET /api/v1/catalogos/paises` | sesión |
| `GET /api/v1/catalogos/esquemas-acreditacion` | sesión |
| `GET /api/v1/catalogos/normas` | sesión |

- **RUC:** Perú, 11 dígitos con dígito verificador SUNAT obligatorio. Ecuador, 13 dígitos con provincia, tipo de
  contribuyente y establecimiento obligatorios; el dígito verificador del SRI solo genera una advertencia en
  `verificacion-documento` (campo `advertencias`).
- **Direcciones:** los campos `departamento`, `provincia` y `distrito` corresponden en Ecuador a provincia, cantón y
  parroquia; `ubigeo` admite el código DPA de 6 dígitos. Las etiquetas por país están en `/catalogos/paises`.
- **Normas:** código de sistema 01–05 del procedimiento DDC-PRO-003 (parte E del código del certificado) y la
  abreviatura del cuadro de control (`9`, `14`, `45`, `27`, `37`).

### Endpoints del Incremento 5 (expedientes, auditorías, auditores)

| Método y ruta | Permiso |
|---|---|
| `GET /api/v1/expedientes` (texto, clienteId, esquema, estado, tipoServicio, responsableId), `GET /{id}` | `expedientes:leer` |
| `GET /api/v1/expedientes/resumen`, `/vigilancias?vista=PROXIMAS\|VENCIDAS&dias=`, `/auditorias-sin-equipo` | `expedientes:leer` |
| `POST /api/v1/expedientes`, `PATCH /{id}`, `POST /{id}/transicion`, `POST /{id}/certificacion` | `expedientes:gestionar` |
| `POST /api/v1/expedientes/{id}/auditorias`, `PATCH /{id}/auditorias/{auditoriaId}`, `PUT …/equipo` | `expedientes:gestionar` |
| `GET /api/v1/auditores`, `GET /api/v1/aliados-comerciales` | `auditores:leer` |
| `POST\|PUT /api/v1/auditores[/{id}]`, `POST\|PUT /api/v1/aliados-comerciales[/{id}]` | `auditores:gestionar` |

- **Flujo según el esquema:**
  - IQC Global (decide India): `SOLICITUD_ENVIADA → PROPOSAL_RECIBIDO → PROPOSAL_FIRMADO → PAQUETE_FASE1_RECIBIDO →
    PAQUETE_FASE1_ENVIADO → PAQUETE_FASE2_RECIBIDO → PAQUETE_FASE2_ENVIADO → (OBSERVADO ↔ reenvío) →
    CERTIFICADO_RECIBIDO → TRADUCIDO → ENTREGADO`.
  - INACAL / SAE: `EN_PLANIFICACION → ETAPA1_REALIZADA → ETAPA2_REALIZADA → EXPEDIENTE_VERIFICADO → EN_DECISION →
    EN_EMISION (o RECHAZADO) → EMITIDO → ENTREGADO`. Desde el Incremento 6, los cuatro últimos los asigna el
    sistema (decisión, firma y entrega), no `POST /transicion`.
  - Una recertificación o ampliación puede omitir la Etapa 1. Cancelar exige un motivo.
- **Ciclo:** `POST /{id}/certificacion` registra la fecha de otorgamiento (solo con el certificado emitido o
  recibido) y crea las vigilancias 1 y 2 (antes de los aniversarios), la expiración a los 3 años y tareas
  automáticas «Programar vigilancia» (60 días antes) y «Preparar recertificación» (120 días antes), sin duplicados.
- **Equipo:** cada auditor debe poder cumplir el rol asignado (líder, auditor, experto técnico, observador, en
  entrenamiento) y hay un solo líder. La respuesta avisa, sin bloquear, si falta un líder o si un auditor no tiene
  competencia registrada en alguna norma del expediente.

### Endpoints del Incremento 6 (imparcialidad, decisiones, certificados y entrega)

| Método y ruta | Permiso |
|---|---|
| `POST /api/v1/documentos?categoria=` (multipart, campo `archivo`), `GET /{id}`, `GET /{id}/contenido` | `documentos:subir` / `expedientes:leer` |
| `GET\|PUT /api/v1/organizacion/parametros[/{clave}]` | autenticado / `organizacion:configurar` |
| `GET\|PUT /api/v1/expedientes/{id}/auditorias/{a}/declaraciones[/{auditorId}]` | `expedientes:leer` / el propio auditor o `expedientes:gestionar` |
| `GET /{id}/decisores`, `GET /{id}/decisiones`, `GET /{id}/certificados`, `GET /{id}/condiciones-entrega` | `expedientes:leer` |
| `POST /api/v1/expedientes/{id}/decisiones` | `decisiones:registrar` |
| `PATCH /{id}/certificados/{c}`, `POST …/revision`, `POST /{id}/certificados/externos`, `PUT …/traduccion` | `certificados:gestionar` |
| `POST /{id}/certificados/{c}/firma` (`GERENCIA` o `JEFE_TECNICO`) | `certificados:firmar-gerencia` / `certificados:firmar-jefe-tecnico` |
| `PUT /{id}/condiciones-entrega`, `POST /{id}/entrega` | `entregas:gestionar` |

- **Imparcialidad:** nadie vinculado a un auditor que participó en una auditoría del cliente puede decidir; la
  alternativa es la vía `COMITE_IMPARCIALIDAD` con el acta adjunta. Cada miembro del equipo declara conflicto de
  interés antes de ejecutar la auditoría (el auditor desde su usuario, o el coordinador con la declaración firmada).
- **Certificados:** la decisión favorable genera un borrador por norma con el código `PE-INA|SAE-…` → checklist
  de emisión → firma según `POLITICA_FIRMA_CERTIFICADOS` (`FIRMA_GERENCIA` por defecto) → emitido `V.01 - fecha`.
  En IQC Global se registra el código que envía India.
- **Entrega:** bloqueada hasta registrar pagos conformes y la encuesta de satisfacción aplicada.
- **Suspensión:** exige el Anexo 1 adjunto y crea las tareas de restauración (6 meses) y retiro (1 año), que se
  cancelan solas al restaurar.
- Los documentos se guardan en `iqc.documentos.directorio` (variable `IQC_DOCUMENTOS_DIR`, por defecto
  `./datos/documentos`, ignorado por git). Respáldalo junto con la base de datos.
- Ejemplos: `docs/api/http/certificacion.http`.

### Roles iniciales

| Rol | Permisos |
|---|---|
| `MASTER` | todos (incluye firmar como Gerencia y `organizacion:configurar`) |
| `ADMINISTRADOR` | `usuarios:leer`, `bitacora:leer`, todos los de `clientes:*`, `seguimientos:*`, `expedientes:*` y `auditores:*`, `certificados:gestionar`, `certificados:firmar-gerencia`, `entregas:gestionar`, `documentos:subir` |
| `COMERCIAL` | `clientes:leer`, `clientes:escribir` (ve todos los clientes; edita los suyos), `seguimientos:leer`, `seguimientos:escribir`, `expedientes:leer`, `auditores:leer` |
| `COORDINADOR_AUDITORIAS` (Jefe de Operaciones) | `clientes:leer`, `seguimientos:leer`, `seguimientos:escribir`, `seguimientos:asignar`, `expedientes:*`, `auditores:*`, `documentos:subir` |
| `RESPONSABLE_CERTIFICACIONES` (Jefe Técnico) | `clientes:leer`, `seguimientos:leer`, `seguimientos:escribir`, `expedientes:leer`, `auditores:leer`, `decisiones:registrar`, `certificados:gestionar`, `certificados:firmar-jefe-tecnico`, `documentos:subir` |
| `ADMINISTRACION_LOGISTICA` | `expedientes:leer`, `entregas:gestionar` (conformidad de pagos, encuesta y entrega) |

## 4. Pruebas

```powershell
cd iqc-flow-api
.\mvnw.cmd test     # unitarias y verificación de límites entre módulos (sin base de datos)
.\mvnw.cmd verify   # además, pruebas de integración *IT contra iqc_flow_test
```

Las pruebas de integración usan PostgreSQL real; borran y recrean sus datos en cada prueba.

## 5. Iniciar el frontend

```powershell
cd iqc-flow-web
npm install
npm run dev
```

Abre `http://localhost:5173` y pulsa «Comprobar conexión». La interfaz de inicio de sesión llega en el Incremento 5.

## Migraciones

| Versión | Contenido |
|---|---|
| `V1__identidad_y_bitacora.sql` | organizaciones, usuarios, roles, permisos y bitácora (con trigger de solo inserción) |
| `V2__roles_y_permisos_iniciales.sql` | 5 roles del sistema y 4 permisos; no crea usuarios |
| `V3__clientes.sql` | clientes (código `CLI-000001`), contactos, direcciones y 5 permisos `clientes:*` |
| `V4__seguimientos_y_notificaciones.sql` | tipos de tarea, tareas, historial, actividades, notificaciones y 4 permisos `seguimientos:*` |
| `V5__catalogos_multipais.sql` | países (PE, EC), esquemas de acreditación (INACAL, SAE, IQC Global), normas y ediciones |
| `V6__expedientes_y_auditorias.sql` | auditores y competencias, aliados comerciales, expedientes (normas, sedes, historial), auditorías y equipo auditor; IQC Global: decide India |
| `V7__decisiones_y_certificados.sql` | documentos, parámetros de la organización, declaraciones de conflicto, decisiones, certificados, condiciones de entrega, rol `ADMINISTRACION_LOGISTICA` y 7 permisos |

Una migración aplicada nunca se modifica: los cambios se hacen con una nueva versión.

## Seguridad

- Todo lo no declarado está cerrado; los permisos se validan en el backend con `@PreAuthorize`.
- En cada solicitud se recargan los permisos del usuario: deshabilitar a alguien corta su sesión al instante.
- Secretos solo por variables de entorno o `config/local.yml` (ignorado por git).
- La base MariaDB de producción y Google Cloud Storage no se tocan en esta etapa.
