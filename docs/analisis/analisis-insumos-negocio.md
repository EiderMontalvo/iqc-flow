# Análisis de los insumos de negocio de IQC Perú

**Fecha:** 3 de noviembre de 2026
**Documentos analizados** (en `docs/`; git los ignora porque contienen datos reales de clientes):

| Documento | Contenido |
|---|---|
| `PROCESO COMERCIAL.xlsx` | Requerimientos del área comercial: actividades, funcionalidades e indicadores |
| `CUADRO DE CONTROL DE OPERACIONES 2026 - IQC PERU - INDIA.xlsx` | Control operativo de certificaciones y vigilancias |
| `Carta de CERTIFICACIÓN … .docx` | Carta de aprobación/recertificación (plantilla en uso) |
| `Carta de Continuidad … 2da vigilancia.pdf` | Carta de continuidad tras una vigilancia |
| `Carta de notificación de suspensión … .pdf` | Carta de suspensión por falta de pago |

Este documento describe estructura, reglas y hallazgos. **No reproduce datos de clientes.**

---

## 1. `PROCESO COMERCIAL.xlsx`: requerimientos del área comercial

No contiene datos. Son tres hojas con lo que el área comercial espera del sistema.

### 1.1 Actividades del proceso (Hoja1)

| # | Actividad | Lo que se necesita | Dónde encaja en IQC Flow |
|---|---|---|---|
| 1 | Registrar clientes / solicitud de información | **Analizar la solicitud y crear con ella cotización y contrato** | Clientes ✔ (Inc. 2) + IA documental (extraer datos de la solicitud) |
| 2 | Cotización + contrato + requisitos | Generar cotizaciones según normas ISO, alcance, sedes, servicios, **días auditor** | Comercial (Fase 3) + documentos |
| 3 | Envío de cotizaciones | Registrar fecha de envío y destinatarios; **alertas de cotizaciones sin respuesta** | Seguimientos + motor de automatizaciones |
| 4 | Seguimiento comercial | Recordatorios, respuestas del cliente, servicios contratados, aceptadas/rechazadas | Seguimientos (Inc. 3) |
| 5 | Programación de auditorías | Fechas, auditores, días de auditoría | Auditorías (Fase 4) |
| 6 | Seguimiento de pagos | Anticipos, saldos, facturas, alertas de facturas pendientes, **validar condiciones de pago antes de continuar etapas** | Pagos (Fase 3) + regla de bloqueo por pago |
| 7 | Coordinación de auditorías | Iniciales, seguimientos (vigilancias) y recertificaciones | Auditorías (Fase 4) |
| 8 | Entrega de certificados | Coordinar emisión y entrega | Certificaciones (Fase 5) |
| 9 | Renovación de servicios | Identificar clientes próximos a vigilancia o recertificación | Automatización del ciclo |
| 10–13 | Cartas de notificación, **suspensión**, **felicitación**; correos comerciales | Comunicaciones generadas desde plantillas | Documentos (plantillas DOCX) |

### 1.2 Funcionalidades esperadas (Hoja2)

| # | Funcionalidad | Estado |
|---|---|---|
| 1 | Acceso con usuario y contraseña | ✔ Incremento 1 |
| 2 | Perfiles y permisos según responsabilidades | ✔ Incremento 1 |
| 3 | Buscador de clientes por RUC o razón social | ✔ Incremento 2 |
| 4 | Base de datos centralizada | ✔ (PostgreSQL) |
| 5 | Generación y descarga de documentos PDF y Word | Fase 3 |
| 6 | **Numeración automática y correlativa de documentos** | Fase 3 (nuevo requisito explícito) |
| 7 | Historial de modificaciones y versiones | ✔ bitácora; versiones de documentos en Fase 3 |
| 8 | Alertas y notificaciones automáticas | Inc. 3–4 |
| 9 | Panel de control comercial (dashboard) | Inc. 5 |
| 10 | Reportes de ventas, cotizaciones y servicios contratados | Reportes |
| 11 | Exportación a Excel | Reportes |
| 12 | Integración con correo electrónico | Integraciones |
| 13 | Respaldo y seguridad | Despliegue |
| 14 | Trazabilidad de cada operación comercial | ✔ bitácora |

### 1.3 Indicadores pedidos (Hoja3)

"Visualizar indicadores sin tener que elaborar Excel manualmente":

1. Cotizaciones emitidas por mes.
2. Cotizaciones aceptadas y rechazadas.
3. Porcentaje de conversión comercial.
4. Total de ventas mensuales.
5. Clientes nuevos y recurrentes.
6. Servicios contratados por norma ISO.
7. Pagos pendientes.
8. Servicios pendientes de programación.
9. Auditorías próximas a vencer.
10. Clientes próximos a recertificación.

---

## 2. `CUADRO DE CONTROL DE OPERACIONES 2026`: el proceso real de certificación

### 2.1 Estructura

| Hoja | Uso | Filas con datos |
|---|---|---|
| `Certificación` | Servicios de certificación bajo el esquema **IQC Global** (marcas JAS-ANZ / IAF), gestionados con India | ≈ 82 servicios |
| `Vigilancias` | Registro de comunicación para vigilancias | ≈ 37 |
| `INACAL` | Misma estructura para el esquema acreditado por INACAL (Perú) | vacía (solo encabezados y fórmulas) |
| `SAE` | Misma estructura para el esquema acreditado por SAE (Ecuador) | vacía |

**Hallazgo clave:** el negocio se organiza por **esquema de acreditación** (IQC Global/JAS-ANZ, INACAL, SAE). Cada esquema puede tener sus propias reglas de ciclo, marcas y plantillas, lo que confirma que los ciclos no deben programarse fijos en el código.

### 2.2 Campos de un servicio de certificación (hoja `Certificación`)

| Columna | Significado (interpretado) | Cómo se modelará |
|---|---|---|
| ÍTEM | Correlativo interno | Código del servicio |
| RUC, EMPRESAS | Cliente | Referencia a `clientes` (Inc. 2) |
| TIPO | `CI`, `C`, `RC`, `AMP` | Tipo de servicio (**por confirmar**, ver §5) |
| NORMAS | Códigos abreviados: `9`, `14`, `45`, `27`, combinados como `9-14-45` o `9. 45` | Catálogo de normas con versión (ISO 9001:2015, 14001:2015, 45001:2018, 27001:2022…) |
| Cant Cert | Número de certificados | Derivado de las normas |
| SPONSOR | Iniciales (4 valores distintos) | **Por confirmar** (¿comercial?, ¿aliado que refiere?) |
| stage I / stage II | **Días auditor** por etapa (decimales: 0.5, 1.5, 2.5…) | Días auditor por etapa |
| Scope | Alcance del certificado | Alcance |
| Audit Team stage 1 / 2 | Equipo auditor por etapa (nombres en texto libre) | Asignaciones de auditores (Fase 4) |
| T.E | 3–4 personas recurrentes | **Por confirmar** (¿revisor técnico / experto técnico?) |
| stage 1/2 inicio y fin | Fechas de cada etapa | Auditorías Etapa 1 y Etapa 2 |
| Fecha emisión de CERTIFICADO | Fecha de emisión | Certificaciones |
| FECHA AUDITORÍA (REAL) | Fecha real ejecutada | Auditoría (fecha real ≠ planificada) |
| ESTADO (INDIA) | `PAQUETE RECIBIDO` → `PAQUETE ENVIADO A INDIA` → `CERTIFICADO` (lista desplegable); también `CONCLUIDO` / `EN PROCESO` | **Estado del expediente** con flujo hacia India |
| IQC GLOBAL / JAS ANZ / IAF | Marcas de acreditación aplicables | Esquema y marcas del certificado |
| COTIZACIÓN | Monto sin IGV (S/) | Cotización (Fase 3) |
| DETALLE DE PAGO | `FACTURADO` y otros valores | Estado de facturación |
| MONTO + IGV | Fórmula `=COTIZACIÓN × 1.18` | Cálculo en backend con IGV configurable |
| PAGOS | Porcentaje pagado (0 %, 100 %) | Cronograma de pagos (Fase 3) |
| ESTADO ENTREGA CERTIFICADO | `VIRTUAL`, `FÍSICO` y anotaciones libres ("falta profesional", "esperar a …") | Entrega del certificado + tareas de seguimiento |

### 2.3 Vigilancias (hoja `Vigilancias`)

| Campo | Valores observados |
|---|---|
| Vigilancia | `1ERA VIG`, `2DA VIG`, combinaciones `1ERA VIG + AMP` (ampliación), `1ERA VIG + CHANGE ADDRESS` (cambio de dirección) |
| Días auditor | Texto libre (`3 DIAS`, `1.5`) |
| Auditor, T.E | Nombres en texto libre |
| Fecha | Fechas y rangos en texto (`12-14/02/2026`) |
| Estado (lista) | `PAQUETE RECIBIDO`, `PAQUETE TERMINADO`, `ENVIADO A INDIA`, `CONTINÚA CERTIFICACIÓN` |
| Carta de certificación | `SOLICITAR CARTA A INDIA` → `SE ENVIÓ CARTA` (resaltados con formato condicional) |
| Columnas M–X | Vínculos a un libro externo **`# Certificados`** que no se entregó (enlaces rotos) |

**Flujo operativo deducido de una vigilancia:**

```text
Vigilancia programada → auditoría ejecutada → paquete recibido (informe del auditor)
→ paquete terminado → enviado a India → continúa certificación → solicitar carta a India
→ se envía carta de continuidad al cliente
```

Cada flecha es hoy un cambio manual de color o de texto en el Excel. En IQC Flow cada paso será un estado con responsable, fecha y una tarea de seguimiento automática si se demora.

### 2.4 Problemas de calidad de datos (a resolver antes de cualquier importación)

| Problema | Ejemplo | Consecuencia |
|---|---|---|
| RUC ausente en la mayoría de filas | ≈ 10 de 82 servicios tienen RUC | No se puede vincular con clientes sin revisión manual |
| Columnas de pago y estado casi vacías | Estado, cotización y pagos solo en ≈ 10 filas | El Excel se usa sobre todo para programar auditorías |
| Normas en formatos distintos | `9-14-45`, `9. 45`, `9. 14` | Requiere normalizar a un catálogo |
| Fechas en formato estadounidense y como texto | `12/29/25`, `12-14/02/2026` | Riesgo de interpretar mal día y mes |
| Nombres sin normalizar | Mismo revisor escrito de 3 formas | Imposible contar la carga por persona |
| Días auditor como texto | `3 DIAS`, `1.5 DÍAS` | No se pueden sumar |
| Fórmulas y formatos con `#REF!` | Formato condicional en INACAL y SAE | Alertas visuales que ya no funcionan |
| Libro externo `# Certificados` no disponible | Columnas M–X de Vigilancias | Información de certificados fuera del alcance de este análisis |

---

## 3. Cartas: candidatas directas a plantillas automáticas

Las tres cartas comparten el membrete y la estructura. Sus campos variables salen de datos que IQC Flow tendrá:

| Carta | Disparador | Campos variables |
|---|---|---|
| Aprobación / certificación (o recertificación) | Decisión de certificación favorable | Razón social, RUC del cliente, tipo de servicio (certificación / recertificación), normas con versión, alcance, fecha, firmante |
| Continuidad tras vigilancia | Vigilancia concluida con continuidad | Razón social, número de vigilancia (1ra/2da), normas con versión, fecha, firmante |
| Notificación de suspensión | Decisión de suspensión (p. ej. falta de pago) | Razón social, normas con nombre del sistema de gestión, motivo, fecha, firmante |
| Felicitación, notificación general (pedidas en Hoja1) | Por definir | Por definir |

**Datos institucionales** (membrete, RUC de IQC, dirección fiscal, oficina, correos, firmante) deben ser **configuración**, no texto fijo. Hoy hay variaciones entre cartas:

- dos correos distintos (`administracion@…` y `gerencia@…`) y dos dominios (`.com` y `.net`);
- la dirección del membrete (oficina en Huancayo) es distinta de la dirección fiscal (Lima) citada en el cuerpo;
- **una carta cita "ISO 37001:2015", versión que no existe** (la norma antisoborno es ISO 37001:2016). Un catálogo de normas con su versión oficial evita este tipo de error.

Las cartas de suspensión y la emisión de certificados son **decisiones oficiales**: el sistema preparará el borrador desde la plantilla, pero solo el personal autorizado lo aprueba y emite.

---

## 4. Impacto en el diseño de IQC Flow

1. **Nueva entidad central: el expediente o servicio de certificación.** Reúne cliente, esquema, tipo de servicio, normas, días auditor por etapa, alcance, equipo auditor, revisor técnico, fechas planificadas y reales, estado del paquete hacia India, cotización, pagos y entrega. Es lo que hoy es cada fila del cuadro de control, y será el "sujeto" principal de los seguimientos y automatizaciones.
2. **Catálogos configurables:**
   - normas con código corto y versión (`9` → ISO 9001:2015);
   - esquemas de acreditación (IQC Global/JAS-ANZ/IAF, INACAL, SAE) con sus marcas y reglas de ciclo;
   - tipos de servicio;
   - estados del paquete;
   - IGV vigente.
3. **Flujo de estados con India** como máquina de estados explícita, con tareas automáticas cuando un paquete lleva demasiado tiempo en un estado.
4. **Regla de pago como condición del motor de automatizaciones:** "validar condiciones de pago antes de continuar determinadas etapas" (por ejemplo, no emitir carta o certificado con saldo pendiente sin autorización).
5. **Numeración correlativa de documentos** por tipo (cotizaciones, contratos, cartas), sin saltos ni duplicados.
6. **Indicadores de la Hoja3** como alcance concreto del módulo de reportes.
7. **IA documental:** "analizar la solicitud y crear con ella cotización y contrato" es un caso de uso claro para la IA. La IA propone los datos y la persona los confirma.
8. **Importación del cuadro de control:** por la calidad de los datos, no conviene una importación automática directa. Conviene un importador con informe de revisión (filas sin RUC, normas no reconocidas, fechas ambiguas) para que operaciones corrija antes de cargar.

---

## 5. Preguntas para IQC Perú (glosario por confirmar)

1. **TIPO:** ¿qué significan exactamente `CI`, `C`, `RC` y `AMP`? (Interpretación provisional: `CI` certificación inicial, `RC` recertificación, `AMP` ampliación; `C` sin aclarar.)
2. **SPONSOR:** ¿quiénes son (iniciales)? ¿Comercial responsable, aliado o consultor que refiere al cliente? ¿Recibe comisión?
3. **T.E:** ¿revisor técnico, experto técnico o evaluador? ¿Es quien revisa el informe antes de enviar el paquete?
4. **Flujo con India:** ¿IQC Global (India) toma la decisión de certificación y emite el certificado? ¿Qué contiene exactamente el "paquete"?
5. **DETALLE DE PAGO:** significado de los valores distintos de `FACTURADO`.
6. **Ciclo de vigilancias:** ¿la 1ra vigilancia ocurre a los 12 meses de la decisión de certificación y la 2da a los 24, con recertificación antes del vencimiento a 3 años? ¿Es igual en IQC Global, INACAL y SAE?
7. **Libro `# Certificados`:** ¿es el registro de certificados del panel Laravel o un Excel aparte? ¿Podemos recibirlo?
8. **Firmante y numeración de cartas:** ¿siempre firma la Gerencia General? ¿Las cartas llevan número correlativo?
9. **Datos institucionales:** ¿cuál es el correo oficial para cartas y cuál la dirección del membrete?
