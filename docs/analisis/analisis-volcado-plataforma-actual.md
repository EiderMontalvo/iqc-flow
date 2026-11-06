# Análisis del volcado de la plataforma actual de certificaciones

**Fecha:** 6 de noviembre de 2026
**Fuente:** volcado de MariaDB 10.11 de la plataforma Laravel de certificaciones, exportado por phpMyAdmin con los datos de 7 tablas. El archivo se guarda fuera del repositorio porque contiene datos reales.
**Uso:** diseñar la importación del histórico a IQC Flow (ADR-019: desde un volcado, nunca contra producción).
**Este documento solo contiene cifras agregadas y formatos, no datos de clientes.**

---

## 1. Qué contiene

| Tabla | Filas | Contenido | Uso en IQC Flow |
|---|---|---|---|
| `Certificaciones` | 784 | un certificado por norma: código, norma, alcance, país, acreditación, fechas del ciclo, URL del PDF | certificados, expedientes históricos y vigilancias |
| `Clientes` | 337 | RUC, razón social, dirección, representante legal | clientes (`fuente_legado`, `id_legado`) |
| `generarQR` | 602 | identificador del QR (`idqr`) y valor cifrado (`encriptacionID`, 37 caracteres) por certificación | compatibilidad de los QR ya impresos (ADR-003) |
| `estados_certificados` | 4 | Anulado, Activo, En proceso, Suspendido | traducción de estados |
| `fondos` | 3 | plantillas de fondo del certificado (firma, sello y pie) | referencia para el futuro PDF (D11) |
| `personas`, `certificados_personales` | 1 y 1 | certificación de personas (cursos), recién iniciada | fuera del alcance por ahora |

No se exportaron los logs (28,6 MB), los usuarios, los tokens ni los intentos de inicio de sesión.

## 2. Hallazgos

### 2.1 Dos campos de estado

- **`estado` es el borrado lógico:** `2` = registro vigente y `1` = eliminado. Hay 133 certificaciones y 51 clientes con `estado = 1`, entre ellas casi todas las pruebas (`asdasd`, `prueba`, `1234`…) y copias repetidas.
- **`status` es el estado del certificado:**
  - `2`: 731 filas;
  - `1`: 27 filas, todas con fecha de anulación;
  - `4`: 25 filas;
  - `3`: 1 fila.

  Por las cifras, parece que 1 = Anulado, 2 = Activo, 3 = En proceso y 4 = Suspendido. **Falta confirmarlo**, porque `estados_certificados` no tiene una clave numérica.
- **La vigencia no está en el estado.** De los registros vigentes con `status = 2`, 497 siguen dentro de su fecha de expiración y 106 ya vencieron. Coincide con ADR-037: la vigencia y el estado administrativo son conceptos separados.

### 2.2 Códigos de certificado

| Formato | Filas aprox. | Esquema | Ejemplo de formato |
|---|---|---|---|
| `PE-INA-CCCAAAANNFG` | 310 | INACAL y SAE (formato actual DDC-PRO-003) | `PE-INA-07120230111` |
| `PE-SAE-CCCAAAANNFG` + letra | 32 | SAE: termina en una letra (`…0111A`) | **¿qué significa la letra?** |
| `PECCCAAAANNFG` (sin guiones) | 86 | formato antiguo, casi todo eliminado o de prueba | `PE00220220111` |
| `PER/<sistema>/JAS-C<n>/[RC1/][R1/]<n>` | ~350 | IQC Global (lo emite India) | `PER/IMS-QEO/JAS-C0062/0364` |

- **En IQC Global, un mismo código puede cubrir varias normas** (sistema integrado: `IMS`, `QEO`…). Por ejemplo, un código `PER/IMS/EO/…` aparece con ISO 45001 y con ISO 14001 del mismo cliente. **Esto choca con ADR-037**, que hoy exige un código único por certificado: para los certificados de India, la unicidad debe ser código + norma.
- Hay 33 códigos repetidos (87 filas). Casi todos son registros eliminados o pruebas, pero hay unos cuantos vigentes repetidos (el mismo código, cliente y fecha): parecen cargas duplicadas.
- Algunos códigos usan la raya `–` en lugar del guion `-`. Se conservan tal como están, para que la consulta por QR siga funcionando, y para buscar se comparan normalizados.

### 2.3 Normas y esquemas

- La norma es texto libre, con 23 variantes. Las conocidas se traducen al catálogo (`37001:2016`, `14001:2015`, `9001:2015`, `45001:2018`, `37001:2025`, `27001:2022`, `27001:2013`). Otras hay que limpiarlas:
  - variantes de escritura: `9001:2015A`, `" 37001:2016"` con espacio y `27001` sin año;
  - inexistentes: `37001:2020`;
  - desconocidas: **`16001:2024`** y **`4005-011/012/015`**. Estas últimas aparecen solo en registros de prueba o eliminados.
- La acreditación también es texto libre, con 6 variantes y 151 filas vacías. El esquema se puede deducir con seguridad del código:
  - `PE-INA` → INACAL;
  - `PE-SAE` → SAE;
  - `PER/…/JAS-C…` → IQC Global.
- Hay 10 certificados **"IAS ACCREDITED Y IQCGLOBAL"**: son de IQC Global, pero con otro organismo de acreditación (IAS en lugar de JAS-ANZ).
- El país está en cada certificación (752 Perú, 31 Ecuador, 1 vacío), no en el cliente.
- `clase` (4 dígitos) y `subdivision` (2 dígitos) parecen la clase y la división CIIU del alcance: sirven para el sector del cliente y la competencia del auditor.

### 2.4 Fechas

- `0000-00-00` se usa como "sin fecha", por ejemplo en 151 fechas de otorgamiento y 150 de cada vigilancia.
- En 656 certificados hay 36 meses entre la certificación y la expiración, que es el ciclo normal. En 36 hay 0 meses y en otros 90 aparecen plazos irregulares (1, 7, 24, 72 meses…): son errores de digitación o pruebas, y se reportan para revisión.
- `fechaSeguimientoUno` y `fechaSeguimientoDos` son las fechas límite de las vigilancias: pasarán a nuestras auditorías `VIGILANCIA_1` y `VIGILANCIA_2`.
- `fechaAmpliacion`, `fechaReduccion` y `FechaRenovacion` solo están llenas en unos 10 certificados.

### 2.5 Clientes

- 31 RUC repetidos y 34 razones sociales repetidas (comparadas sin espacios ni signos): **hay que fusionar clientes antes de importar**, porque IQC Flow exige un documento único por cliente activo (ADR-017).
- 7 clientes tienen `-` en lugar de RUC, 199 no tienen dirección y 118 no tienen representante legal.
- 64 clientes no tienen ninguna certificación (prospectos o registros abandonados).
- No hay certificaciones sin un cliente existente.

### 2.6 QR y PDF

- 776 PDF están en Google Cloud Storage (`storage.googleapis.com/documents-dlk44e/…`). Se mantienen allí (ADR-005) y se importa solo la URL.
- QR: 376 certificaciones no tienen QR, 160 tienen más de uno (reimpresiones) y 51 QR apuntan a certificaciones eliminadas. **Hay que conservar todos los `idqr` y `encriptacionID`**, porque hay QR impresos en certificados entregados.

## 3. Propuesta de importación (a aprobar)

1. **Importador local de una sola vez.** Lee el volcado (no la base de producción) y escribe en `iqc_flow`. Se puede ejecutar de nuevo sin duplicar nada (clave `fuente_legado` + `id_legado`). Antes de escribir, genera un **informe de calidad**.
2. **Clientes.** Se importan los registros con `estado = 2`.
   - Se fusionan los que tienen el mismo RUC; los duplicados por razón social se proponen para revisión manual.
   - El país sale de sus certificaciones.
   - Con un RUC válido, el cliente queda `ACTIVO`; sin RUC, o con uno inválido, queda como `PROSPECTO` con una observación.
3. **Certificados.** Se importan los registros con `estado = 2` y una norma reconocida, como certificados emitidos con su código original, su PDF y su estado (`status`). Las pruebas y los eliminados no se importan, pero quedan en el informe.
4. **Expedientes históricos.** Se agrupan los certificados por cliente, esquema y fecha de certificación (las normas auditadas juntas). Cada grupo crea un expediente `ENTREGADO` con su ciclo, sus vigilancias y las tareas automáticas de las vigilancias que aún no vencieron.
5. **QR.** Una tabla de equivalencias `idqr`/`encriptacionID` → certificado, para que la consulta pública (`consultas.iqcperu.com`) siga respondiendo a los QR ya impresos.
6. **Cambio de modelo necesario.** En IQC Global, la unicidad del código pasa a ser código + norma (por los certificados integrados). Más un campo para la URL del PDF de origen.

## 4. Respuestas y hallazgos en los PDF (6 de noviembre de 2026)

Con el permiso de IQC se revisaron los PDF públicos de una muestra de certificados.

1. **`status`:** confirmado por IQC: 1 = Anulado, 2 = Activo, 3 = En proceso y 4 = Suspendido.
2. **Letra final de los códigos SAE:** **marca la edición 2025 de ISO 37001.** El código de sistema (01) no distingue ediciones: 30 de los 32 códigos con `A` son 37001:2025, y el PDF de uno de ellos lo confirma. Los 2 códigos con `A` que figuran como 37001:2016 son errores del campo norma. **Hay que corregirlo en IQC Flow:** la edición necesita un sufijo de código (`37001:2025` → `A`).
3. **`16001:2024` y `4005-…`:** son pruebas. Sus PDF son un folleto de un curso de Certus y la proforma de una agencia de viajes. Se descartan.
4. **`solicitud` y `firma`:**
   - `solicitud` sigue el orden de las columnas de fechas: 1 = inicial, 2 = ampliación, 3 = reducción y 4 = renovación. El certificado de renovación revisado dice "Ciclo de certificación Nro. 2". **No es fiable** (37 códigos de India con `RC1` tienen `solicitud = 1`): en INACAL/SAE, la revisión y el ciclo se leen del propio código (partes F y G).
   - `firma` coincide con el esquema y la época: 1 = INACAL/SAE (Gerente General), y 0, 2 y 3 = plantillas de IQC Global. No se importa.
5. **QR:** la consulta pública es `https://consultas.iqcperu.com/<encriptacionID>`, donde `encriptacionID` es el `idqr` más un carácter. Se guardan los dos valores por certificado.
6. **Vigilancias:** se importan tal como están (fecha límite del sistema actual), sin marcarlas como realizadas. De los 499 certificados activos y vigentes, la vigilancia 1 ya pasó en 275 y la 2 en 83: aparecerán en la vista de vencidas para que el equipo las revise. Para no crear cientos de tareas de golpe, las tareas automáticas solo se generan para las vigilancias futuras.

### Correcciones al Incremento 6 que salen de los certificados reales

El certificado de renovación revisado (`PE-INA-01520220132`, emitido en 2025) muestra tres reglas:

- **El año del código no cambia en la renovación:** se mantiene el año del otorgamiento original (D = 2022), y solo sube el ciclo (G = 2).
- **La revisión del alcance se conserva entre ciclos:** F = 3.
- **La fecha efectiva de otorgamiento sigue siendo la original** (04/11/2022), y la nueva expiración continúa la anterior (03/11/2028).

Hoy, IQC Flow usa el año de la decisión de renovación: se corrige en el próximo incremento.

## 5. Estado de la importación (6 de noviembre de 2026)

- **Implementada** en el Incremento 7 (módulo `importacion`, ADR-038). Los comandos están en el README, en "Importar el histórico de la plataforma anterior".
- **Informe sobre el volcado real generado:**
  - 287 clientes (202 activos, 53 inactivos y 32 prospectos);
  - 637 certificados y 499 QR;
  - 523 expedientes históricos (396 vigentes);
  - 300 descartes y 32 advertencias.

  El informe está junto al volcado, fuera del repositorio.
- **Pendiente: no se ha aplicado todavía en ninguna base.** Antes de aplicar:
  1. Revisar los 3 códigos de IQC Global asignados a dos clientes distintos. Por ahora el importador conserva la carga más reciente.
  2. Revisar 1 posible cliente duplicado con distinto RUC.
  3. Revisar los 28 certificados con plazos irregulares entre la certificación y la expiración.
  4. Elegir el usuario responsable de lo importado (`--iqc.importacion-legado.responsable`).
  5. Ejecutar el modo `APLICAR` primero en la base de desarrollo `iqc_flow`, y en el futuro servidor solo cuando se defina el pase a producción.
