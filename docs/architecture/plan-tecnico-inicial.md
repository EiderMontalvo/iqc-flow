# IQC Flow — Plan técnico de inicialización

**Fecha:** 2 de noviembre de 2026
**Estado:** propuesta pendiente de aprobación
**Alcance de este documento:** arquitectura, límites de módulos, modelo de datos inicial, diseño de los motores de seguimientos y automatizaciones, estrategia de integración con Laravel, estrategia de IA, roadmap y plan del primer incremento.

---

## 1. Confirmación de decisiones base

| Aspecto | Decisión |
|---|---|
| Nombre oficial | **IQC Flow** (nunca "IQC Flow AI") |
| Backend | `iqc-flow-api` — Java 21, Spring Boot 4.1.x, Maven |
| Frontend | `iqc-flow-web` — React 19 + TypeScript + Vite |
| Base de datos | PostgreSQL (exclusiva de IQC Flow) + Flyway |
| Arquitectura | Monolito modular por dominios, sin microservicios |
| Desarrollo local | Windows 10, sin Docker; ejecución con Maven y npm |
| Integración Laravel | Sustitución progresiva; MariaDB solo lectura, nunca escritura dual |
| IA | Incorporación progresiva: primero solo lectura, luego propuestas confirmadas |
| Prioridad funcional | Motor de seguimientos y automatizaciones |
| Paquete raíz | `pe.com.iqc.flow` |
| Idioma | **Español** en módulos Java, clases, tablas, columnas, endpoints, interfaz y documentación (ADR-009). Se mantienen en inglés las capas internas (`api`, `application`, `domain`, `infrastructure`), los nombres impuestos por los frameworks (`SecurityFilterChain`, `@RestController`, `Repository`…) y los nombres de las carpetas `iqc-flow-api` e `iqc-flow-web` |

### 1.1 Nomenclatura de módulos

| Antes (prompt §17) | Módulo oficial |
|---|---|
| identity | `identidad` |
| organizations | `organizaciones` |
| clients | `clientes` |
| commercial | `comercial` |
| followups | `seguimientos` |
| workflows | `automatizaciones` |
| audits | `auditorias` |
| auditors | `auditores` |
| certifications | `certificaciones` |
| documents | `documentos` |
| meetings | `reuniones` |
| notifications | `notificaciones` |
| integrations | `integraciones` |
| ai | `ia` |
| reports | `reportes` |
| shared | `compartido` |

> **Nota:** las secciones 3 a 12 de este documento se redactaron antes de adoptar ADR-009 y todavía usan nombres en inglés para módulos, tablas y endpoints. Deben leerse con la tabla anterior. Los nombres definitivos de tablas y columnas se fijarán en español al reescribir `V1` (Incremento 1), por ejemplo `usuarios`, `roles`, `permisos`, `bitacora`, `clientes`, `contactos_cliente`, `tareas_seguimiento`, `actividades`, `reglas_automatizacion`, `temporizadores_automatizacion`, `ejecuciones_automatizacion`, `eventos_negocio` y `notificaciones`. Los endpoints seguirán el mismo criterio: `/api/v1/clientes`, `/api/v1/seguimientos/tareas`, `/api/v1/auth/...` → `/api/v1/autenticacion/...`.

---

## 2. Diagnóstico del repositorio actual

Revisado el 2 de noviembre de 2026.

### Lo que existe

- `iqc-flow-api/`: `pom.xml` con Spring Boot 4.1.2, `IqcFlowApplication`, `SecurityConfiguration` (todo denegado salvo `/api/v1/public/status` y `/actuator/health`), `ApiStatusController`, `application.yml`, `V1__init.sql` y un test vacío.
- Paquetes vacíos (`.gitkeep`): `ai`, `audits`, `certifications`, `commercial`, `documents`, `meetings`, `organizations`.
- `iqc-flow-web/`: Vite + React 19 con una sola pantalla que consulta el estado de la API.
- `docs/architecture/DECISIONS.md` con ADR-001 a ADR-008.

### Verificaciones realizadas

| Verificación | Resultado |
|---|---|
| `java -version` | Temurin 21.0.12 ✔ |
| `mvn` en el PATH | **No disponible.** Solo existe el Maven 3.9.15 incluido en NetBeans |
| `node` / `npm` | 24.18.0 / 11.16.0 ✔ |
| `git` | 2.55.0 ✔ — pero **el proyecto no es un repositorio git** |
| PostgreSQL | **No instalado** (sin servicio, sin `psql`, puerto 5432 libre) |
| `mvn compile` (con el Maven de NetBeans) | **Compila correctamente** |
| Tests | El único test está vacío; no verifica nada |

### Problemas detectados

1. **Flyway no se ejecutaría.** En Spring Boot 4 la autoconfiguración de Flyway está en el módulo `spring-boot-flyway` (starter `spring-boot-starter-flyway`). El `pom.xml` solo incluye `flyway-core`, así que las migraciones no correrían. Como aún no hay entidades JPA, `ddl-auto: validate` tampoco fallaría: la aplicación arrancaría sin tablas sin dar ningún aviso.
2. **`index.html` usa el título "IQC Flow AI"**, que contradice la nomenclatura oficial.
3. **`V1__init.sql` mezcla conceptos:** la tabla `organizations` tiene `tax_id` y `legacy_id`, que corresponden a clientes y no a la organización usuaria (IQC Perú). `app_users.organization_id` admite nulos, faltan los campos de 2FA, bloqueo y `updated_at`, y `audit_events` no tiene origen (manual/IA/automatización), resultado, motivo, valores anteriores y nuevos ni identificador de correlación.
4. Faltan Maven Wrapper, OpenAPI, manejo centralizado de errores, logging con correlación, la verificación de límites entre módulos y las carpetas `scripts/` e `infrastructure/`.
5. No están en el repositorio el informe de viabilidad ni los dos Excel (`PROCESO COMERCIAL.xlsx` y `CUADRO DE CONTROL DE OPERACIONES 2026 - IQC PERU - INDIA.xlsx`).

Como PostgreSQL no está instalado, **`V1__init.sql` nunca se ha aplicado en ninguna base**. Se propone reescribirlo en lugar de corregirlo con un `V2` (requiere aprobación; ver §12). A partir del primer `flyway migrate` real, ninguna migración aplicada se volverá a modificar.

---

## 3. Arquitectura propuesta

### 3.1 Vista general

```text
                ┌──────────────────────────┐        ┌──────────────────────────┐
 Personal IQC → │ iqc-flow-web (React/TS)  │        │ consultas.iqcperu.com    │ ← Público / QR
                │ app.iqcperu.com          │        │ (Laravel hoy; IQC Flow   │
                └────────────┬─────────────┘        │  en Fase 5, mismas URLs) │
                             │ HTTPS /api/v1        └──────────────────────────┘
                ┌────────────▼──────────────────────────────────────────────┐
                │ iqc-flow-api — monolito modular (Spring Boot)             │
                │                                                           │
                │  identity · organizations · clients · commercial          │
                │  followups · workflows · notifications · audits · auditors│
                │  meetings · documents · certifications · reports · ai     │
                │  integrations (adaptadores) · shared (núcleo técnico)     │
                └───┬──────────────┬───────────────┬──────────────┬─────────┘
                    │              │               │              │
              PostgreSQL     Google Workspace   Proveedor IA   MariaDB legado
              (iqc_flow)     (Calendar, Gmail)  (Tool Calling) (solo lectura, Fase 5)
                             Google Cloud Storage
```

### 3.2 Estructura interna de cada módulo

Se aplican capas cuando aportan valor, sin ceremonia innecesaria:

```text
pe.com.iqc.flow.<modulo>
├── api/              Controladores REST, DTOs de entrada/salida, mapeos
├── application/      Casos de uso / servicios de aplicación, transacciones, autorización
├── domain/           Entidades, value objects, reglas de negocio, eventos de dominio
├── infrastructure/   Repositorios JPA, adaptadores externos, configuración
└── <Modulo>Facade    (opcional) Interfaz pública que pueden usar otros módulos
```

Reglas:

- Un módulo **solo** puede usar de otro su fachada pública o sus eventos. Nunca sus repositorios ni sus entidades JPA.
- No se crean relaciones JPA (`@ManyToOne`) entre módulos: se referencia por identificador (UUID). Dentro de un módulo sí se usan.
- Los SDK externos (Google, IA, GCS) solo aparecen en `infrastructure` o en `integrations`, detrás de interfaces (puertos) definidas por el módulo que las necesita.
- La autorización se valida en la capa `application` (con `@PreAuthorize` más comprobaciones por recurso), no solo en los controladores.

### 3.3 Dependencias entre módulos (acíclicas)

```text
shared  ←  organizations  ←  identity  ←  notifications  ←  followups  ←  workflows
                                 ↑                              ↑             ↑ (SPI)
                              clients  ←───── commercial ───────┴─────────────┤
                                 ↑                                            │
                              auditors ← audits ← meetings                    │
                                           ↑                                  │
                                     certifications ──────────────────────────┘
                     documents ← (commercial, audits, certifications)
                     reports, ai → leen de todos mediante sus fachadas (capa superior)
```

Puntos clave para evitar ciclos:

- **`followups` no conoce a los demás módulos.** Una tarea apunta a su origen mediante una referencia genérica `subject_type` + `subject_id` (por ejemplo `CLIENT`, `OPPORTUNITY`, `QUOTATION`, `AUDIT`). Los demás módulos usan `followups`, nunca al revés.
- **`workflows` define interfaces SPI** (`SubjectStateProvider`, `EventTypeCatalog`) que implementan `commercial`, `audits` y `certifications`. Es inversión de dependencias: el motor evalúa condiciones sin depender de los módulos de negocio.
- Los límites se verifican automáticamente con un test de **Spring Modulith** (`ApplicationModules.of(IqcFlowApplication.class).verify()`), que solo se añade con alcance `test`. Si alguien crea una dependencia circular o accede a internals de otro módulo, el build falla.

### 3.4 Convenciones técnicas transversales

| Tema | Decisión |
|---|---|
| Identificadores | UUID v7 generados por Hibernate 7 (`@UuidGenerator(style = VERSION_7)`), ordenables en el tiempo y buenos para índices |
| Fechas | `timestamptz` en UTC; presentación y cálculo de "hoy" en `America/Lima` mediante un bean `java.time.Clock` (inyectable en tests) |
| Concurrencia | Bloqueo optimista (`version`) en entidades editables |
| Multiorganización | `organization_id NOT NULL` en todas las tablas de negocio; filtrado obligatorio en servicios. Solo existirá IQC Perú al inicio |
| Borrado | `archived_at` separado de cualquier estado de negocio (lección del legado: `estado` ≠ estado del certificado) |
| Errores | `ProblemDetail` (RFC 9457) con `code`, `correlationId` y errores de validación por campo |
| API | `/api/v1/...`, paginación `page`/`size` (máximo 100), orden permitido por lista blanca |
| Documentación API | springdoc-openapi (versión compatible con Boot 4), Swagger UI solo en perfiles `dev` y `staging` |
| Logging | SLF4J + Logback; `X-Correlation-Id` propagado mediante MDC; formato JSON en producción |
| Perfiles | `dev`, `test`, `staging`, `prod`; secretos solo por variables de entorno |
| Build | Maven Wrapper (`mvnw.cmd`), de modo que solo haga falta el JDK |

### 3.5 Autenticación (propuesta)

- **Sesión de servidor con cookie** `HttpOnly`, `Secure` y `SameSite=Lax`, más token CSRF para el SPA. Es más sencilla y segura que un JWT guardado en el navegador para una aplicación interna: permite revocación inmediata y no expone tokens a JavaScript.
- Contraseñas con `DelegatingPasswordEncoder` (bcrypt o argon2), bloqueo temporal tras intentos fallidos y rate limiting en el login.
- **2FA TOTP obligatorio para usuarios MASTER y administradores.** Los campos se crean en el Incremento 1 y la funcionalidad se implementa antes del primer despliegue en producción.
- Sin usuarios ni contraseñas por defecto: el primer usuario `MASTER` se crea con el arranque inicial `CreacionMasterInicial`, que lee el correo y la contraseña inicial de variables de entorno y obliga a cambiarla en el primer acceso.
- Recomendación de despliegue: servir la API bajo `app.iqcperu.com/api` mediante el proxy de Apache, sin CORS. `api.iqcperu.com` queda reservado para integraciones y portales futuros.

---

## 4. Límites de cada módulo

| Módulo | Responsabilidad | No hace |
|---|---|---|
| `shared` | Núcleo técnico: errores, paginación, `Clock`, correlación, auditoría base, tipos comunes (`SubjectRef`, `Money`) | Lógica de negocio |
| `organizations` | Organización usuaria (tenant), configuración general, zona horaria, calendario laboral | Clientes |
| `identity` | Usuarios internos, roles, permisos, autenticación, 2FA, sesiones, bitácora de seguridad | Datos de clientes |
| `clients` | Empresas cliente, identificación fiscal (RUC y otros), direcciones/sedes, contactos, historial de interacciones | Cotizaciones, tareas |
| `followups` | Tareas de seguimiento, actividades (llamadas, correos, reuniones), responsables, vencimientos, reprogramación, escalamiento, centro de seguimientos | Decidir *cuándo* crear tareas automáticas |
| `workflows` | Motor Evento → Condición → Acción: reglas, temporizadores, ejecuciones, reintentos, idempotencia | Lógica específica de cada dominio |
| `notifications` | Notificaciones internas y correo, preferencias, deduplicación, reintentos | Redactar contenido con IA |
| `commercial` | Oportunidades, cotizaciones, tarifarios, contratos, pagos | Programar auditorías |
| `auditors` | Auditores, competencias por norma, disponibilidad | Asignaciones concretas |
| `audits` | Programas de auditoría, etapas 1 y 2, vigilancias, renovaciones, asignaciones, evidencias | Emitir certificados |
| `meetings` | Reuniones, agendas, participantes, actas, acuerdos → tareas | Llamar directamente a la API de Google |
| `documents` | Plantillas DOCX, versiones, mapeo de campos, generación DOCX/PDF/XLSX, almacenamiento e integridad (hash) | Decidir el contenido oficial |
| `certifications` | Certificados empresariales y personales, estados, historial, QR, validación pública, compatibilidad con el legado | Decisiones automáticas de certificación |
| `integrations` | Adaptadores: Google Calendar/Meet/Gmail, GCS, correo SMTP, MariaDB legado (solo lectura) | Reglas de negocio |
| `ai` | Asistente: orquestación, catálogo de herramientas autorizadas, propuestas de cambio, registro de interacciones y costos | SQL libre; acciones sin confirmación |
| `reports` | Indicadores calculados en backend y exportaciones | Modificar datos |

---

## 5. Modelo conceptual de datos

### 5.1 Vista global (todas las fases)

```text
Organization ─┬─ User ── Role ── Permission
              │
              ├─ Client ─┬─ Contact
              │          ├─ Address / Site
              │          ├─ Opportunity ── Quotation (versiones) ── Contract ── PaymentSchedule ── Installment
              │          ├─ CertificationCycle ─┬─ Audit (Stage1, Stage2, Surveillance1, Surveillance2, Recert, Extraordinary)
              │          │                      │    └─ AuditAssignment ── Auditor ── Competence(Standard)
              │          │                      └─ Certificate ── CertificateStatusHistory ── QrCode ── StoredDocument
              │          └─ Activity (llamada, correo, reunión, nota)
              │
              ├─ FollowUpTask (subject: Client | Opportunity | Quotation | Contract | Audit | Certificate …)
              ├─ Meeting ── Participant ── Agreement → FollowUpTask
              ├─ AutomationRule ── AutomationTimer ── AutomationExecution
              ├─ BusinessEvent (bandeja de eventos persistida)
              ├─ Notification
              ├─ DocumentTemplate ── TemplateVersion ── GeneratedDocument
              ├─ AiInteraction ── AiActionProposal
              └─ AuditLog
Catálogos: Standard (ISO 9001:2015, ISO 14001:2015…), Scheme (reglas de ciclo por esquema), TaskType, Holiday
```

### 5.2 Tablas del Incremento 1 (identidad y bitácora)

```text
organizations      id, code, name, country_code, time_zone ('America/Lima'), status,
                   created_at, updated_at, version
app_users          id, organization_id NOT NULL, email (único por organización, sin distinguir mayúsculas),
                   display_name, password_hash, status (ACTIVE/DISABLED/LOCKED),
                   must_change_password, mfa_enabled, mfa_secret_encrypted,
                   failed_login_count, locked_until, last_login_at, password_changed_at,
                   created_at, updated_at, version
roles              id, organization_id, code, name, description, system_role, created_at, updated_at
permissions        code (PK, p. ej. 'clients:write'), module, description
role_permissions   role_id, permission_code
user_roles         user_id, role_id, granted_by, granted_at
audit_log          id, organization_id, occurred_at, actor_user_id, actor_type (USER/SYSTEM/AUTOMATION/AI_ASSISTANT),
                   origin (WEB/API/SCHEDULER/AI/MIGRATION), action, entity_type, entity_id,
                   before_state jsonb, after_state jsonb, result (SUCCESS/DENIED/FAILED), reason,
                   correlation_id, ai_interaction_id, confirmed_by_user_id, ip_address, user_agent
```

`audit_log` es de solo inserción: la aplicación no tendrá permisos `UPDATE` ni `DELETE` sobre ella en producción.

**Roles iniciales** (precargados, editables después): `MASTER`, `ADMINISTRADOR`, `COMERCIAL`, `COORDINADOR_AUDITORIAS`, `RESPONSABLE_CERTIFICACIONES`.

**Permisos iniciales** (formato `recurso:acción[:alcance]`):

```text
users:read  users:manage  roles:manage  audit-log:read
clients:read  clients:read-all  clients:write  clients:archive
followups:read  followups:read-all  followups:write  followups:assign
automations:read  automations:manage
notifications:read
```

El sufijo `-all` diferencia "lo mío / lo asignado a mí" de "todo". Las restricciones por recurso (por ejemplo, un comercial solo edita los clientes que tiene asignados) se validan en el servicio de aplicación.

### 5.3 Tablas del Incremento 2 (clientes)

```text
clients            id, organization_id, code (legible: CLI-000123), legal_name, trade_name,
                   country_code, tax_id_type (RUC, NIT, RFC, OTHER), tax_id,
                   status (PROSPECT/ACTIVE/INACTIVE), owner_user_id, industry, website,
                   lead_source, notes, legacy_source, legacy_id,
                   created_by, created_at, updated_at, archived_at, version
                   UNIQUE (organization_id, country_code, tax_id_type, tax_id) WHERE archived_at IS NULL
client_addresses   id, client_id, type (FISCAL/SITE/CORRESPONDENCE), address_line, district, province,
                   region, country_code, is_primary
client_contacts    id, client_id, full_name, job_title, email, phone, contact_roles (LEGAL_REP, COMMERCIAL,
                   QUALITY, BILLING…), is_primary, active, notes
```

- El RUC peruano se valida con un validador específico (11 dígitos, prefijo y dígito verificador). Los demás países usan validadores intercambiables por `tax_id_type`.
- **Prospecto y cliente son la misma entidad** con distinto `status`. Así el dato se registra una sola vez y no hay que "convertir" un lead en cliente.
- `legacy_source` y `legacy_id` permiten vincular después los clientes de la tabla `Clientes` de MariaDB sin duplicarlos.

### 5.4 Tablas del Incremento 3 (seguimientos y notificaciones)

```text
followup_tasks         id, organization_id, title, description, task_type (catálogo),
                       subject_type, subject_id, client_id (desnormalizado para la ficha del cliente),
                       assignee_user_id, priority (LOW/NORMAL/HIGH/URGENT),
                       status (PENDING/IN_PROGRESS/WAITING/DONE/CANCELLED),
                       due_date, due_time (opcional), remind_at,
                       outcome (catálogo), outcome_notes, completed_at, completed_by,
                       escalation_level, last_escalated_at, reschedule_count,
                       origin (MANUAL/AUTOMATION/AI), automation_rule_id, dedup_key,
                       created_by, created_at, updated_at, version
                       UNIQUE (organization_id, dedup_key) WHERE status IN ('PENDING','IN_PROGRESS','WAITING')
followup_task_history  id, task_id, event (CREATED/ASSIGNED/RESCHEDULED/STATUS_CHANGED/ESCALATED/COMMENTED),
                       from_value jsonb, to_value jsonb, actor_user_id, actor_type, reason, occurred_at
activities             id, organization_id, client_id, subject_type, subject_id,
                       type (CALL/EMAIL/MEETING/WHATSAPP/VISIT/NOTE), direction (INBOUND/OUTBOUND),
                       occurred_at, summary, outcome, contact_id, followup_task_id,
                       origin, created_by, created_at
task_types             code, name, default_priority, default_assignee_role, active
notifications          id, organization_id, recipient_user_id, type, title, body,
                       subject_type, subject_id, channel (IN_APP/EMAIL), status (PENDING/SENT/FAILED/READ),
                       dedup_key UNIQUE, attempts, next_attempt_at, sent_at, read_at, created_at
```

`due_date` + `due_time` opcional (en lugar de un solo `timestamptz`) porque la mayoría de los seguimientos son "para el jueves", no "para el jueves a las 10:00". Una tarea está vencida si su fecha ya pasó en hora de Lima.

### 5.5 Tablas del Incremento 4 (motor de automatizaciones)

```text
business_events        id, organization_id, event_type ('quotation.sent'), subject_type, subject_id,
                       occurred_at, payload jsonb, actor_user_id, correlation_id,
                       status (PENDING/PROCESSED/FAILED), attempts, processed_at
automation_rules       id, organization_id, code, name, description, trigger_event_type,
                       conditions jsonb, delay_amount, delay_unit (HOURS/CALENDAR_DAYS/BUSINESS_DAYS),
                       recheck_conditions_on_fire, actions jsonb, enabled, paused_reason,
                       rule_version, created_by, updated_by, created_at, updated_at, version
automation_timers      id, rule_id, rule_version, business_event_id, subject_type, subject_id,
                       fire_at, status (SCHEDULED/PROCESSING/DONE/CANCELLED/FAILED),
                       attempts, next_attempt_at, locked_until, idempotency_key UNIQUE, last_error
automation_executions  id, rule_id, rule_version, timer_id, business_event_id, started_at, finished_at,
                       outcome (ACTIONS_EXECUTED/CONDITION_NOT_MET/DUPLICATE_SKIPPED/FAILED),
                       actions_result jsonb, error_message
holidays               organization_id, date, description   (calendario laboral para días hábiles)
```

---

## 6. Flujo principal de negocio

```text
 Prospecto ─► Oportunidad ─► Cotización enviada ─┬─► Aceptada ─► Contrato ─► Firma ─► Pago inicial
   │              │               │              │                                     │
   │              │       (sin respuesta N días) └─► Rechazada / Cerrada               ▼
   │              │        → tarea de seguimiento                       Programa de auditoría
   │              │        → borrador de correo (IA, opcional)          Etapa 1 ─► Etapa 2
   │              │        → el comercial revisa y envía                        │
   │              │                                                Informe ─► Revisión técnica
   │              │                                                             │
   │              │                                        Decisión de certificación (HUMANA)
   │              │                                                             │
   │              │                                                    Certificado emitido
   │              │                                                             │
   │              │              Ciclo calculado según reglas del esquema ◄─────┘
   │              │              Vigilancia 1 ─► Vigilancia 2 ─► Renovación ─► nuevo ciclo
   ▼              ▼
 Cada paso emite un BusinessEvent → el motor crea tareas, recordatorios y escalamientos
```

---

## 7. Diseño del motor de seguimientos (`followups`)

### 7.1 Responsabilidades

1. Crear tareas (manualmente, desde otro módulo, desde una automatización o desde una propuesta de IA confirmada).
2. Asignar, reasignar, reprogramar, completar con resultado y cancelar.
3. Mantener el historial completo de cada tarea (`followup_task_history`).
4. Registrar actividades (llamadas, correos, reuniones) vinculadas o no a una tarea.
5. Calcular las vistas del **Centro de seguimientos**.
6. Publicar eventos (`followup.created`, `followup.completed`, `followup.overdue`, `subject.stale`) para que el motor de automatizaciones decida escalamientos.

### 7.2 Vistas del Centro de seguimientos (API)

| Vista | Criterio |
|---|---|
| Mis tareas de hoy | Asignadas a mí, abiertas, `due_date = hoy (Lima)` |
| Atrasadas | Abiertas, `due_date < hoy` |
| Próximos seguimientos | Abiertas, `due_date` entre mañana y +N días |
| Pendientes de confirmación | Estado `WAITING` (esperando respuesta de terceros) |
| Procesos sin movimiento | Sujetos sin actividad registrada en X días (X configurable por tipo) |

Filtros: responsable, cliente, tipo, estado, prioridad, rango de fechas y origen. Un usuario sin `followups:read-all` solo ve sus tareas y las de los clientes que tiene asignados.

### 7.3 Reglas importantes

- **Completar exige un resultado** (`outcome`): contactado, sin respuesta, reprogramado, aceptado, rechazado, etc. El catálogo se puede configurar.
- **Reprogramar exige un motivo** y queda en el historial. `reschedule_count` permite detectar tareas que se posponen indefinidamente.
- Completar una tarea con ciertos resultados puede proponer la siguiente (por ejemplo, "sin respuesta" → nuevo seguimiento en N días). Esa propuesta proviene de una regla del motor, no de código fijo.
- Una tarea automática nunca se duplica: `dedup_key` es único mientras la tarea esté abierta.

### 7.4 Barrido de vencimientos

Un proceso programado (cada 15 minutos, en horario configurable) detecta tareas que acaban de vencer y publica `followup.overdue` una sola vez por tarea y nivel de escalamiento. El motor de automatizaciones decide qué hacer (notificar al responsable, escalar al supervisor, etc.).

---

## 8. Diseño del motor de automatizaciones (`workflows`)

### 8.1 Modelo Evento → Condición → Acción

```text
  Módulo de negocio                workflows
  ─────────────────                ───────────────────────────────────────────────────────────
  CotizacionService.enviar()
    ├─ guarda la cotización ─┐
    └─ inserta BusinessEvent ┘ (MISMA transacción: outbox)
                                    │
                    EventDispatcher (cada 30 s, FOR UPDATE SKIP LOCKED)
                                    │ busca reglas activas con trigger = 'quotation.sent'
                                    ▼
                    ¿La regla tiene retardo? ── no ──► evalúa condición ► ejecuta acciones
                                    │ sí
                                    ▼
                    AutomationTimer(fire_at = enviado + 5 días hábiles, idempotency_key)
                                    │
                    TimerProcessor (cada 60 s, FOR UPDATE SKIP LOCKED)
                                    │ vuelve a evaluar la condición con el estado ACTUAL:
                                    │ SubjectStateProvider("QUOTATION", id) → {status: "SENT", ...}
                                    ▼
                    ¿Sigue en SENT? ── no ──► execution = CONDITION_NOT_MET (no molesta a nadie)
                                    │ sí
                                    ▼
                    Acciones: CREATE_TASK, NOTIFY, PREPARE_EMAIL_DRAFT …
                    → automation_executions + audit_log (actor_type = AUTOMATION)
```

### 8.2 Ejemplo de regla (configuración, no código)

```json
{
  "code": "QUOTE_NO_RESPONSE_REMINDER",
  "name": "Recordatorio de cotización sin respuesta",
  "triggerEventType": "quotation.sent",
  "delay": { "amount": 5, "unit": "BUSINESS_DAYS" },
  "recheckConditionsOnFire": true,
  "conditions": { "all": [ { "field": "status", "op": "eq", "value": "SENT" } ] },
  "actions": [
    { "type": "CREATE_TASK", "taskType": "QUOTE_FOLLOW_UP", "assignee": "SUBJECT_OWNER",
      "priority": "HIGH", "dueInDays": 0, "title": "Dar seguimiento a la cotización {{subject.code}}" },
    { "type": "NOTIFY", "recipient": "SUBJECT_OWNER", "channel": "IN_APP" }
  ]
}
```

Los plazos (3, 5 o 7 días) son datos de la regla y se editan desde la administración, sin desplegar código.

### 8.3 Garantías técnicas

| Requisito | Cómo se cumple |
|---|---|
| Persistencia | Eventos, temporizadores y ejecuciones en PostgreSQL |
| Seguro ante reinicios | Nada vive solo en memoria; al reiniciar, los procesadores retoman lo pendiente. `locked_until` libera trabajos de un proceso caído |
| Varias instancias | `SELECT … FOR UPDATE SKIP LOCKED` evita que dos procesos tomen el mismo trabajo |
| Idempotencia | `idempotency_key = regla:versión:evento:sujeto` con restricción única; acciones con `dedup_key` |
| Sin notificaciones duplicadas | `notifications.dedup_key` único |
| Reintentos | Backoff exponencial (1 min, 5 min, 30 min, 2 h…) hasta un máximo configurable; luego `FAILED` + alerta a los usuarios MASTER |
| Pausar reglas | `enabled = false` / `paused_reason`; los temporizadores pendientes se cancelan o se conservan según se elija al pausar |
| Historial | `automation_executions` + `audit_log` |
| Zona horaria | `Clock` en `America/Lima`; días hábiles con la tabla `holidays` |
| Seguridad de condiciones | DSL declarativo limitado (`eq`, `ne`, `in`, `lt`, `gt`, `exists`, `all`, `any`) sobre campos expuestos explícitamente por cada `SubjectStateProvider`. Sin SQL ni scripts |
| Cambios de reglas | `rule_version` se guarda en cada temporizador y ejecución, de modo que siempre se sabe qué versión actuó |

### 8.4 Por qué no una herramienta externa todavía

Spring Scheduling con tablas propias cubre el volumen previsible (cientos de clientes, miles de tareas al año) sin añadir infraestructura en el VPS. Si más adelante se necesitan flujos largos con pasos humanos complejos, se evaluarán JobRunr o Temporal; el diseño por puertos permite migrar sin reescribir las reglas.

---

## 9. Estrategia de integración con Laravel

| Etapa | Qué ocurre | Escrituras |
|---|---|---|
| 0 (ahora) | Análisis de un **volcado** de `iqc_prod_db` restaurado en un MariaDB local o de staging. Nunca contra producción | Ninguna |
| 1 | Importador de solo lectura (`integrations.legacy`) → tablas `legacy_*` en PostgreSQL con datos crudos + hash por fila → transformación a modelo IQC Flow conservando `legacy_id` | Solo en PostgreSQL |
| 2 | Validador nuevo en staging; comparación automática código por código y QR por QR contra el sistema actual; informe de reconciliación | Solo en PostgreSQL |
| 3 | Consulta pública migrada **con las mismas rutas** (`POST /verifyCertificate`, `POST /showQR/{idqr}`, `GET /personal-pdf/{token}`, `/{encriptacionID}`) | Laravel sigue emitiendo |
| 4 | Emisión transferida a IQC Flow tras la aprobación; MariaDB pasa a solo lectura | Solo IQC Flow |
| 5 | Archivo del sistema anterior | — |

Principios:

- Usuario de MariaDB con `SELECT` únicamente, preferiblemente sobre una réplica o un respaldo restaurado.
- Estados separados en IQC Flow: `administrative_status` (Anulado/Activo/En proceso/Suspendido, tal como está registrado), `record_visibility` (equivalente al `estado` de borrado lógico) y `temporal_validity` (calculada: vigente o expirado según las fechas). El sistema **muestra** que un certificado "Activo" está expirado, pero no cambia su estado oficial.
- Las anomalías detectadas (51 QR huérfanos, 13 códigos duplicados visibles, 106 activos expirados, 8 sin URL) se clasifican en el informe de reconciliación y **no se corrigen automáticamente**.
- Cada importación es repetible (idempotente por `legacy_id` + hash) y tiene reversión: las tablas destino se pueden vaciar y recargar sin afectar el origen.

---

## 10. Estrategia de incorporación de IA

| Etapa | Cuándo | Capacidades |
|---|---|---|
| A. Solo lectura | Cuando existan las APIs de clientes y seguimientos (después del Incremento 3) | Herramientas: `search_clients`, `get_client_summary`, `list_followups`, `list_overdue_followups`, `get_dashboard_counts`. Respuestas estructuradas (tablas, fichas) |
| B. Propuestas supervisadas | Fase 3 | `propose_followup_task`, `propose_reschedule`, `draft_email`. Cada propuesta se guarda en `ai_action_proposals`, se muestra como vista previa y solo se ejecuta cuando el usuario la confirma, mediante el mismo servicio que la acción manual |
| C. Documentos y reuniones | Fases 3 y 4 | Detección de datos faltantes, borradores de agenda y acta, resúmenes de expedientes |
| D. Análisis | Fase 6 | Explicación de indicadores calculados por `reports` |

Controles:

- Cada herramienta es un método Java que llama a un servicio de aplicación **con la identidad y los permisos del usuario**. La IA no puede ver ni hacer más que el usuario.
- Nunca SQL generado por el modelo. Nunca credenciales en el frontend.
- Minimización: las herramientas devuelven solo los campos necesarios; DNI y datos personales se enmascaran salvo permiso explícito.
- Lo que proviene de la base de datos o de documentos se trata como *dato* y no como instrucción (mitigación de prompt injection); las acciones de escritura siempre requieren confirmación humana.
- La IA nunca emite, anula, aprueba ni modifica certificados.
- `ai_interactions`: usuario, herramientas invocadas, tokens, costo estimado, latencia y resultado. Cuotas por usuario y por día.
- El proveedor se accede mediante un puerto (`LanguageModelClient`) en `ai.infrastructure`, de modo que se puede cambiar sin tocar las herramientas. El proveedor y el modelo concretos son una decisión pendiente (§12).

---

## 11. Roadmap

### 11.1 Incrementos del backend (Fases 1 y 2)

| # | Incremento | Contenido principal | Depende de |
|---|---|---|---|
| **1** | **Base ejecutable y segura** | Git, Maven Wrapper, PostgreSQL local, Flyway corregido, V1 reescrito, identidad (usuarios, roles, permisos, login por sesión), bitácora, errores, OpenAPI, correlación, tests de integración reales, verificación de módulos | — |
| 2 | Clientes | Clientes, contactos, direcciones, validación de RUC, búsqueda, asignación de responsable, auditoría de cambios | 1 |
| 3 | Seguimientos | Tareas, actividades, historial, Centro de seguimientos, barrido de vencidos, notificaciones internas | 2 |
| 4 | Motor de automatizaciones v1 | Outbox de eventos, reglas, temporizadores, ejecuciones, primeras reglas (escalar tarea vencida; cliente sin movimiento) | 3 |
| 5 | Web v1 | Login, clientes, Centro de seguimientos, dashboard básico, cliente API tipado generado desde OpenAPI | 1–4 (avanza en paralelo desde el 2) |
| 6 | Asistente IA de solo lectura | Etapa A de §10 | 3, 5 |

### 11.2 Fases siguientes

- **Fase 3 — Comercial:** oportunidades, cotizaciones con versiones y tarifario, **generación DOCX desde plantillas** (primera plantilla: cotización), regla automática de seguimiento de cotización, contratos, pagos, correo saliente.
- **Fase 4 — Auditorías y reuniones:** auditores, programación, ciclos por esquema, vigilancias, calendario, Google Calendar/Meet, plan de auditoría DOCX.
- **Fase 5 — Certificaciones y migración** (línea paralela desde la Fase 3, sin adelantar el corte): ver §9.
- **Fase 6 — IA avanzada.** **Fase 7 — Portales externos.**

### 11.3 Criterios de aceptación del MVP

El MVP comprende las Fases 1 y 2, el núcleo de la Fase 3 y el asistente de solo lectura. Se acepta cuando:

1. Un usuario interno inicia sesión; los usuarios MASTER y administradores con 2FA.
2. Ningún endpoint de negocio responde sin autenticación ni sin el permiso correspondiente (verificado con tests de autorización por endpoint).
3. Se registra un cliente con RUC válido una sola vez; un RUC duplicado se rechaza.
4. Se registran contactos, actividades y tareas de seguimiento con responsable, prioridad y fecha.
5. El Centro de seguimientos muestra correctamente "hoy", "atrasadas", "próximas" y "sin movimiento" en hora de Lima.
6. Una cotización marcada como enviada y sin respuesta genera **automáticamente** una tarea al cumplirse el plazo configurado; si fue respondida antes, no se genera nada.
7. Reiniciar el servidor no pierde ni duplica temporizadores, tareas ni notificaciones (verificado con test).
8. Una tarea vencida se notifica y se escala según la regla configurada.
9. Se genera una cotización DOCX a partir de una plantilla oficial sin alterar su formato.
10. El asistente responde "¿qué seguimientos tengo atrasados?" y "¿qué cotizaciones no han tenido seguimiento?" con datos reales y respetando los permisos del usuario.
11. Toda modificación queda en la bitácora con actor, origen y correlación.
12. La documentación OpenAPI, el README y las instrucciones para Windows 10 permiten levantar el sistema desde cero.

---

## 12. Plan detallado del Incremento 1 — "Base ejecutable y segura"

### 12.1 Tareas

1. **Repositorio:** `git init`, `.gitignore` revisado y commit inicial del estado actual (para conservar el punto de partida) antes de cualquier cambio.
2. **Build:** añadir Maven Wrapper (`mvnw.cmd`) para no depender del Maven de NetBeans.
3. **Dependencias** (cada una justificada):
   - `spring-boot-starter-flyway` — corrige el defecto de Flyway en Boot 4.
   - `springdoc-openapi-starter-webmvc-ui` (línea compatible con Boot 4) — OpenAPI/Swagger.
   - `spring-modulith-starter-test` (**solo test**) — verificación de límites entre módulos.
   - `spring-security-test` (test).
   - No se añaden Lombok, MapStruct ni Testcontainers (este último requiere Docker).
4. **PostgreSQL local:** instalación nativa en Windows (se recomienda la misma versión mayor que se usará en producción), bases `iqc_flow` (desarrollo) e `iqc_flow_test` (tests), usuario `iqc_dev`. Script `scripts/db/create-local-databases.sql`.
5. **Migraciones:** reescribir `V1__init.sql` → `V1__identity_and_audit.sql` según §5.2; `V2__seed_roles_and_permissions.sql` con los roles y permisos iniciales (sin usuarios).
6. **`shared`:** `GlobalExceptionHandler` (ProblemDetail), `CorrelationIdFilter`, bean `Clock` (America/Lima), `PageResponse`, `AuditLogService` y tipos `ActorType`/`Origin`.
7. **`organizations`:** entidad, repositorio y fachada de lectura; organización IQC Perú creada por el arranque inicial.
8. **`identity`:**
   - Entidades `User`, `Role`, `Permission`; `UserDetailsService` que carga permisos como *authorities*.
   - `SecurityConfiguration` con sesión, CSRF para SPA, `@EnableMethodSecurity`, denegar por defecto y rate limiting básico en el login.
   - Endpoints: `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`, `GET /api/v1/auth/me`, `POST /api/v1/auth/change-password`; `GET/POST/PATCH /api/v1/users`, `GET /api/v1/roles`, `PUT /api/v1/users/{id}/roles`.
   - Bloqueo tras N intentos fallidos; bitácora de inicios de sesión exitosos y fallidos.
   - Arranque inicial del primer usuario MASTER (`IQC_MASTER_INICIAL=true` y variables `IQC_MASTER_*`, cambio de contraseña obligatorio).
9. **Frontend (mínimo):** corregir el título a "IQC Flow"; nada más en este incremento.
10. **Documentación:** README con instrucciones de Windows 10, `docs/architecture/adr/` (migrar ADR-001…008 a archivos individuales y añadir los nuevos), `docs/api/http/identity.http` con ejemplos.

### 12.2 Pruebas

| Tipo | Qué verifica |
|---|---|
| Unitarias | Reglas de contraseña, bloqueo, mapeo de permisos |
| Integración (PostgreSQL real `iqc_flow_test`) | Flyway aplica todas las migraciones; Hibernate valida el esquema; login y logout; `/me` |
| Autorización | Cada endpoint: 401 sin sesión, 403 sin permiso, 2xx con permiso |
| Bitácora | Crear usuario y cambiar roles generan `audit_log` con actor y correlación |
| Arquitectura | `ApplicationModules.verify()` sin ciclos ni accesos indebidos |

### 12.3 Criterios de aceptación del Incremento 1

- `mvnw.cmd verify` pasa en Windows 10 con PostgreSQL local, sin Docker.
- `mvnw.cmd spring-boot:run` arranca, Flyway crea el esquema y Swagger UI muestra los endpoints.
- No existe ningún usuario ni contraseña por defecto en el código ni en las migraciones.
- El arranque inicial crea el usuario MASTER y el inicio de sesión funciona desde un archivo `.http`.
- Un usuario sin permisos no puede listar usuarios (403), y el intento queda en la bitácora.

---

## 13. Decisiones pendientes

| # | Decisión | Propuesta por defecto |
|---|---|---|
| D1 | Reescribir `V1__init.sql` (nunca aplicado) en lugar de agregar `V2` correctivo | **Resuelta:** reescrito (Incremento 1) |
| D2 | Instalar PostgreSQL en Windows; versión mayor alineada con producción | **Resuelta:** PostgreSQL 18 |
| D3 | Sesión con cookie vs JWT | **Resuelta:** sesión con cookie + CSRF (ADR-010) |
| D4 | API bajo `app.iqcperu.com/api` (sin CORS) vs `api.iqcperu.com` | `app…/api` ahora; `api.` para integraciones futuras |
| D5 | Plazos en días hábiles o calendario; feriados de Perú | Configurable por regla; días hábiles por defecto |
| D6 | Destinatario de los escalamientos (supervisor directo, administrador, rol) | Configurable por regla; administrador por defecto |
| D7 | ¿Los comerciales ven todos los clientes o solo los asignados? | **Resuelta:** ven todos, editan los suyos (ADR-016) |
| D8 | Importar clientes existentes de MariaDB (desde un volcado) en el Incremento 2 | **Resuelta:** más adelante, desde un volcado (ADR-019) |
| D9 | Proveedor y modelo de IA | Se decidirá antes del Incremento 6, tras evaluar costo, calidad en español y tratamiento de datos |
| D10 | Biblioteca de UI del frontend | Se decidirá en el Incremento 5 (nunca Bootstrap CSS) |
| D11 | Generación de PDF fiel a DOCX (por ejemplo, LibreOffice headless en el servidor) | Se evaluará en la Fase 3 |

### Insumos que faltan

- Informe de viabilidad técnica (copiar a `docs/legacy/`).
- `PROCESO COMERCIAL.xlsx` y `CUADRO DE CONTROL DE OPERACIONES 2026 - IQC PERU - INDIA.xlsx`. Son necesarios antes de diseñar las reglas concretas de la Fase 3 y los tipos de tarea definitivos.
- Plantillas DOCX oficiales (cotización, contrato y plan de auditoría).
- Reglas de ciclo por esquema/norma (plazos de vigilancias y renovación aplicables a IQC).
