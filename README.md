# IQC Flow

Plataforma interna de IQC Perú para centralizar el ciclo comercial, operativo y de certificación,
con automatización de seguimientos, reuniones y documentos.

Monolito modular: Java 21 + Spring Boot 4.1 (`iqc-flow-api`), React + TypeScript (`iqc-flow-web`),
PostgreSQL 18 y Flyway. Desarrollo en Windows 10, sin Docker.

**Estado — Incremento 1 (base ejecutable y segura):** identidad (usuarios, roles, permisos), inicio de
sesión con cookie y CSRF, bitácora inmutable, manejo de errores, OpenAPI y pruebas contra PostgreSQL real.
Aún no hay módulos de negocio (clientes, seguimientos…). Ver el plan en
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

Los errores siguen RFC 9457 (`application/problem+json`) con `codigo` estable e `idCorrelacion`.

### Roles iniciales

| Rol | Permisos en el Incremento 1 |
|---|---|
| `MASTER` | todos |
| `ADMINISTRADOR` | `usuarios:leer`, `bitacora:leer` |
| `COMERCIAL`, `COORDINADOR_AUDITORIAS`, `RESPONSABLE_CERTIFICACIONES` | ninguno todavía; cada incremento añade los suyos |

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

Una migración aplicada nunca se modifica: los cambios se hacen con una nueva versión.

## Seguridad

- Todo lo no declarado está cerrado; los permisos se validan en el backend con `@PreAuthorize`.
- En cada solicitud se recargan los permisos del usuario: deshabilitar a alguien corta su sesión al instante.
- Secretos solo por variables de entorno o `config/local.yml` (ignorado por git).
- La base MariaDB de producción y Google Cloud Storage no se tocan en esta etapa.
