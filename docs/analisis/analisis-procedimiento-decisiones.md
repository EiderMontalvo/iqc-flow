# Análisis del procedimiento DDC-PRO-003: decisiones de certificación (v.11)

**Fecha:** 6 de noviembre de 2026
**Fuente:** *Procedimiento de decisiones de certificación* DDC-PRO-003, versión 11, publicado el 29/05/2026, basado en ISO/IEC 17021-1:2015.
**Uso:** base para diseñar en IQC Flow el expediente de certificación, las decisiones, la emisión de certificados y su entrega.

---

## 1. Flujo del procedimiento

```text
Auditor líder entrega el informe
  → Jefe de Operaciones verifica que el expediente esté completo (formatos, omisiones)
  → Revisión técnica y decisión: Jefe Técnico
       (si tiene conflicto de interés → Comité de Imparcialidad + Experto Técnico de Imparcialidad)
  → Registro de la decisión (DDC-FMR-008) y control general de decisiones (DDC-FRM-002)
  → Si es favorable: orden de emisión (DDC-FRM-007)
  → Borrador del certificado → checklist de emisión (DDC-MAN-001) → correcciones
  → Firma (Gerente General o Jefe Técnico) y emisión; control general de certificados (PDA-FRM-010)
  → Entrega, solo si los pagos y documentos administrativos están conformes y se aplicó la encuesta de satisfacción
  → Entrega digital o física; el cliente confirma la recepción
```

## 2. Reglas que IQC Flow debe aplicar

### 2.1 Imparcialidad (segregación de funciones)

- **Nadie que haya participado en la auditoría de ese cliente puede revisar, recomendar ni decidir**, sea cual sea su rol (auditor líder, auditor, coauditor, observador, experto técnico, acompañante técnico, personal en entrenamiento, evaluador testificado), su fase (Etapa 1, Etapa 2, vigilancia, renovación, especiales) o su nivel de participación.
- Si el Jefe Técnico tiene conflicto, el expediente pasa **obligatoriamente** al **Comité de Imparcialidad**. Un **Experto Técnico de Imparcialidad**, independiente, emite una recomendación técnica, pero **no decide**.
- La designación del experto (Coordinador SIG con el Jefe de Operaciones) y la verificación de competencia del comité se registran en el expediente **antes** de la decisión.

**En el sistema:** al registrar una decisión, IQC Flow verificará automáticamente con las asignaciones de auditores del expediente si quien decide participó en alguna auditoría del cliente. Si participó, bloqueará la decisión y la derivará al comité. Es un control que hoy depende de la memoria de las personas.

### 2.2 Tipos de decisión y clasificación

- **Tipos:** otorgamiento, rechazo, mantenimiento, renovación, ampliación, reducción, suspensión, restauración, retiro, transferencia y cancelación voluntaria.
- **Clasificación de la revisión:** `CONFORME`, `OBSERVADO`, `ACEPTABLE_CON_OBSERVACIONES`.
- **Información mínima según el tipo:**
  - otorgamiento: informes de Etapa 1 y 2, no conformidades y acciones correctivas, objetivos logrados, recomendación del equipo auditor;
  - renovación: resultados de las vigilancias del ciclo, quejas, fechas de expiración y acciones correctivas de las no conformidades mayores.
- **Antes de suspender o retirar:** el Anexo 1 (checklist de suspensión) es obligatorio.

### 2.3 Fechas y ciclo de certificación

| Regla | Cómo se modela |
|---|---|
| La fecha de emisión es igual o posterior a la fecha de la decisión | Validación |
| Ciclo de 3 años; las vigilancias 1 y 2 se programan antes de los aniversarios de la fecha de otorgamiento (ejemplo del certificado: 26/08/2024 → 26/08/2025, 26/08/2026; expiración 26/08/2027) | Cálculo automático de hitos y tareas |
| Si la renovación termina antes de la expiración, la nueva expiración puede basarse en la anterior | Regla de renovación |
| Tras la expiración, se puede restaurar dentro de 6 meses si se completó la renovación; si no, hace falta al menos una Etapa 2 | Plazo con tarea automática |
| La suspensión no detiene la vigencia del certificado | El estado administrativo y la vigencia temporal son conceptos separados (ya previsto) |
| Retiro si una suspensión no se resuelve en 1 año (o 2 meses antes del vencimiento, si queda menos) | Regla del motor de automatizaciones |
| Suspensión voluntaria: la restauración debe pedirse por escrito, dentro de los 6 meses siguientes al vencimiento anual de la vigilancia | Plazo con tarea automática |
| Restaurar después de superado el ciclo exige una auditoría Etapa 1 | Validación |

### 2.4 Requisitos para entregar

La entrega exige pagos conformes **y** la encuesta de satisfacción aplicada. Esto confirma la regla de pago detectada en `PROCESO COMERCIAL.xlsx`: será una condición bloqueante, que solo una persona autorizada puede liberar y que queda registrada en la bitácora.

## 3. Código único del certificado

Formato: `PE-INA-00120210111` = **A** `PE` · **B** `INA` · **C** `001` · **D** `2021` · **E** `01` · **F** `1` · **G** `1`.

| Parte | Significado | Origen en IQC Flow |
|---|---|---|
| A | País del **organismo de certificación** (no del cliente): `PE` | Configuración de la organización |
| B | Organismo de acreditación: `INA` (INACAL) o `SAE` | Esquema del expediente |
| C | Tres primeros dígitos del número de contrato de certificación | Contrato (Fase 3) |
| D | Año de la **decisión** de otorgamiento (no el del contrato) | Decisión |
| E | Sistema de gestión: 01 ISO 37001, 02 ISO 9001, 03 ISO 14001, 04 ISO 27001, 05 ISO 45001 | Catálogo de normas (código por norma) |
| F | Revisión del alcance (sube con cada ampliación o reducción) | Historial del certificado |
| G | Ciclo de certificación (sube con cada renovación) | Ciclo |

- **Un certificado por norma:** un cliente con ISO 9001, 14001 y 45001 recibe tres certificados (lo que en el cuadro de control es "Cant Cert").
- **El sistema generará el código** y validará que sea único, en lugar de componerlo a mano.

Contenido obligatorio del certificado (ISO/IEC 17021-1, 8.2.2):

- nombre y ubicación del cliente, y sedes si es multisitio;
- fechas de otorgamiento, emisión, vigilancias 1 y 2 y expiración;
- código único;
- norma con su edición;
- alcance sin actividades excluidas;
- datos y marca del organismo, sello INACAL o SAE;
- QR de verificación;
- versión del documento (`V.01 - fecha`) para distinguir revisiones;
- firma.

## 4. Implicaciones multipaís

- **Los certificados de empresas ecuatorianas los emite IQC Perú S.A.C.** bajo acreditación **SAE**: el código empieza con `PE` porque indica el país del organismo, y `SAE` aparece en la parte B. Por tanto **no hace falta una segunda organización emisora**: basta con el país del cliente y el esquema de acreditación del expediente.
- El esquema determina el sello, las reglas y el prefijo del código. El país del cliente determina la validación de su documento (RUC de Perú o de Ecuador), el formato de dirección y, en la Fase 3, la moneda y el impuesto.

## 5. Roles del procedimiento frente a los roles de IQC Flow

| Rol del procedimiento | Rol actual | Observación |
|---|---|---|
| Jefe Técnico (decide, firma, gestiona la emisión y el control de certificados) | `RESPONSABLE_CERTIFICACIONES` | Equivalente |
| Jefe de Operaciones (verifica el expediente, remite la documentación) | `COORDINADOR_AUDITORIAS` | Equivalente |
| Gerente General (firma, alta dirección) | `MASTER` / `ADMINISTRADOR` | — |
| Comité de Imparcialidad | — | Nuevo rol, con permiso solo para decisiones derivadas |
| Experto Técnico de Imparcialidad | — | Nuevo rol: recomienda y no decide |
| Coordinador SIG | — | Designa al experto; podría ser un permiso dentro de ADMINISTRADOR |
| Analista de logística / Administración | — | Entrega y verificación de pagos |

## 6. Inconsistencias encontradas en el documento

1. **ISO/IEC 27001:2014** en el código 04: no existe esa edición internacional (son 2013 y 2022). Probablemente se refiere a la NTP-ISO/IEC 27001:2014, adopción peruana de la versión 2013. La transición internacional a la edición 2022 venció en octubre de 2025, así que conviene confirmar qué edición se audita hoy.
2. **ISO 37001:2025** en el código 01, mientras que la carta de suspensión revisada cita "ISO 37001:2015". El catálogo de normas debe admitir **varias ediciones de una misma norma**: el código 01 identifica la norma y la edición queda registrada en cada certificado.
3. **4.1.3.2.4.2 (auditoría con notificación a corto plazo)** repite en su primer párrafo el texto de 4.1.3.2.4.1 (ampliación de alcance).
4. **4.1.3.2.5.4 (reducción del alcance)** empieza con "La decisión de restaurar…", texto copiado del apartado anterior.
5. La **página 19** tiene una línea de texto sin sentido debajo de la tabla del código.
6. El texto alterna los códigos **DDC-FMR-008** y **DDC-FRM-008** para el mismo formato.

## 7. Preguntas del glosario resueltas y pendientes

| Pregunta (análisis de insumos §5) | Estado |
|---|---|
| 4. ¿Quién decide la certificación? | **Resuelta para INACAL y SAE:** IQC Perú, por medio del Jefe Técnico. **Pendiente para IQC Global (India / JAS-ANZ)**: ¿decide India? |
| 6. Ciclo de vigilancias | **Resuelta:** 3 años; vigilancias antes de los aniversarios 1 y 2 de la fecha de otorgamiento |
| 8. Firmante de cartas y certificados | **Resuelta para certificados:** Gerente General o Jefe Técnico. Las comunicaciones de decisión las notifica el Jefe Técnico |
| 3. T.E del cuadro de control | Sigue pendiente: ¿es el experto técnico del equipo auditor o el revisor técnico? |
| 1, 2, 5, 7, 9 | Siguen pendientes |
