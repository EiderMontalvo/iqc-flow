# Plantillas de IQC Flow

Aquí se guardan las **plantillas base** de los documentos y correos que genera IQC Flow: certificados, cotizaciones,
contratos, cartas y correos. Son el punto de partida. Más adelante se suben y se mantienen desde la pantalla
**Plantillas** del panel (permiso `plantillas:gestionar`), que guarda cada versión en la tabla `plantillas`. Esta carpeta
es la copia de referencia versionada en git.

> **Estado (25/11/2026):**
> - **Certificados (empresarial y personal):** ya se generan en PDF desde IQC Flow, en el modo «Desde plantilla». No usan Word:
>   se dibujan con PDFBox sobre los fondos de `iqc-flow-api/src/main/resources/certificados/`. Los PDF de referencia
>   (con datos reales de clientes, fuera de git) están en `certificados/*/_originales/`.
> - **Cotización y contrato:** los Word de IQC (GCO-FRM-003, 004, 005 y 008) ya están en `cotizaciones/` y `contratos/`, pero aún
>   **sin campos** `{{…}}`. Falta marcarlos y generar el PDF con LibreOffice (instalado en el servidor desde el 25/11/2026).
> - **Cartas:** cuatro plantillas en `cartas/`, con la firma siempre al pie de la última página. **Se emiten desde el panel**
>   (pantalla **Cartas**, desde el 26/11/2026) con vista previa en vivo, número `CAR-AAAA-0001` y la firma que cada persona sube en su perfil.
> - Los correos de `correos/comerciales/` son borradores para revisar.
> - El detalle del plan: [docs/architecture/pendiente-configuracion-master-plantillas.md](../docs/architecture/pendiente-configuracion-master-plantillas.md).

## Carpetas

| Carpeta | Qué va | Formato |
|---|---|---|
| `certificados/empresariales/` | Certificado de sistema de gestión DDC-FRM-015. Hoy solo guarda los PDF de referencia en `_originales/` | PDF de referencia |
| `certificados/personales/` | Certificado de curso o capacitación. Hoy solo guarda los PDF de referencia en `_originales/` | PDF de referencia |
| `cotizaciones/` | Propuesta económica que se envía al cliente | Word `.docx` |
| `contratos/` | Contrato de servicios de certificación | Word `.docx` |
| `cartas/` | Cartas: otorgamiento, suspensión, retiro, programación de auditoría, cobranza… | Word `.docx` |
| `correos/comerciales/` | Correos del proceso comercial | Texto `.txt` |
| `correos/certificados/` | Correos de entrega de certificados y avisos al cliente | Texto `.txt` |

## Cómo se nombra un archivo

`<tipo>-<variante>[-<pais>].docx`, en minúsculas y sin espacios ni tildes. Ejemplos:

- `certificado-empresarial-inacal-pe.docx`, `certificado-empresarial-global.docx`;
- `certificado-personal-curso.docx`;
- `cotizacion-certificacion.docx`;
- `contrato-certificacion-pe.docx`;
- `carta-otorgamiento.docx`, `carta-suspension.docx`.

Sin sufijo de país, la plantilla vale para todas las marcas. Con sufijo (`-pe`, `-ec`, `-co`, `-pa`), solo para la marca
de ese país.

## Cómo se escriben los campos

- **En Word:** se escribe el campo entre llaves dobles, por ejemplo `{{razonSocial}}`, con **el mismo formato en todo el campo**.
  - Si se pone negrita solo en una parte, Word divide el texto por dentro y el campo deja de reconocerse.
  - Un campo sin dato queda vacío, no se muestra `{{…}}`.
- **Tablas que se repiten**, por ejemplo una fila por norma en la cotización: la fila lleva los campos de la línea
  (`{{item.descripcion}}`, `{{item.monto}}`) y se repite sola por cada elemento.
- **QR y logo:** se pone una imagen de muestra cuyo *texto alternativo* sea `{{qr}}` o `{{logo}}`. Se reemplaza conservando su tamaño.
- **Fechas** en formato peruano (`24/11/2026`). Para escribirlas en letras se usa el sufijo `Texto`, por ejemplo `{{fechaEmisionTexto}}` → «24 de noviembre de 2026».
- **Montos** con separador de miles y dos decimales. `{{totalTexto}}` da el monto en letras («seis mil soles con 00/100»).
- **Correos (.txt):** la primera línea es `Asunto: …`, luego una línea en blanco y luego el cuerpo. Usan los mismos campos.

## Campos disponibles

### Comunes a todas las plantillas (marca del país)

| Campo | Contenido |
|---|---|
| `{{marcaNombre}}` | Nombre visible de la marca (IQC Perú, IQC Ecuador…) |
| `{{marcaSitioWeb}}`, `{{marcaCorreo}}`, `{{marcaTelefono}}`, `{{marcaDireccion}}` | Membrete de la marca |
| `{{logo}}` | Logo de la marca (imagen) |
| `{{fechaHoy}}`, `{{fechaHoyTexto}}` | Fecha de generación |
| `{{usuarioNombre}}`, `{{usuarioCargo}}` | Persona que genera o envía el documento |

### Cliente

| Campo | Contenido |
|---|---|
| `{{razonSocial}}` | Razón social |
| `{{tipoDocumento}}`, `{{numeroDocumento}}` | RUC u otro documento |
| `{{direccionFiscal}}`, `{{distrito}}`, `{{provincia}}`, `{{departamento}}`, `{{pais}}` | Dirección fiscal |
| `{{contactoNombre}}`, `{{contactoCargo}}`, `{{contactoCorreo}}`, `{{contactoTelefono}}` | Persona de contacto |

### Certificado empresarial

| Campo | Contenido |
|---|---|
| `{{codigo}}` | Código del certificado |
| `{{norma}}`, `{{normaNombre}}`, `{{edicion}}` | Norma (ISO 9001), su nombre largo y edición (2015) |
| `{{esquema}}` | Esquema o acreditación (INACAL, IQC Global…) |
| `{{alcance}}` | Alcance certificado |
| `{{sedes}}` | Sedes cubiertas (una por línea) |
| `{{fechaOtorgamiento}}`, `{{fechaEmision}}`, `{{fechaExpiracion}}` | Fechas, también con sufijo `Texto` |
| `{{ciclo}}`, `{{revisionAlcance}}`, `{{versionDocumento}}` | Ciclo, revisión del alcance y versión |
| `{{firmaGerencia}}`, `{{firmaJefeTecnico}}` | Firmas (imagen) de quienes aprobaron |
| `{{qr}}`, `{{urlConsulta}}` | QR de verificación y la dirección que contiene |

### Certificado personal

| Campo | Contenido |
|---|---|
| `{{personaNombre}}`, `{{personaDocumento}}` | Participante |
| `{{codigo}}` | Código del certificado |
| `{{curso}}`, `{{subtitulo}}`, `{{descripcion}}` | Curso y textos |
| `{{normaReferencia}}`, `{{normaAnio}}` | Norma de referencia |
| `{{duracionHoras}}`, `{{modalidad}}` | Horas y modalidad |
| `{{fechaInicio}}`, `{{fechaFin}}`, `{{fechaEmision}}` | Fechas |
| `{{entrenador}}` | Instructor |
| `{{qr}}`, `{{urlConsulta}}` | QR de verificación |

### Cotización

| Campo | Contenido |
|---|---|
| `{{numero}}` | `COT-2026-0001` |
| `{{tipoServicio}}` | Certificación inicial, recertificación, ampliación, vigilancia… |
| `{{normas}}` | Normas de la cotización en una línea («ISO 9001:2015 e ISO 45001:2018») |
| `{{fechaEnvio}}` | Fecha en que se marcó como enviada |
| `{{alcance}}`, `{{numeroSedes}}`, `{{sedesDetalle}}` | Alcance y sedes |
| `{{item.norma}}`, `{{item.edicion}}`, `{{item.descripcion}}`, `{{item.diasAuditor}}`, `{{item.monto}}` | Una fila por línea |
| `{{diasAuditor}}` | Total de días auditor |
| `{{moneda}}`, `{{subtotal}}`, `{{nombreImpuesto}}`, `{{tasaImpuesto}}`, `{{impuesto}}`, `{{total}}`, `{{totalTexto}}` | Montos (PEN, IGV 18 % en Perú) |
| `{{validezDias}}`, `{{fechaVencimiento}}` | Validez de la propuesta |
| `{{condicionesPago}}` | Condiciones de pago |
| `{{responsableNombre}}`, `{{responsableCorreo}}`, `{{responsableTelefono}}` | Responsable comercial |

### Contrato

Lleva los campos del cliente y de la cotización de la que nace, más `{{numeroContrato}}` (`CON-2026-0001`),
`{{fechaContrato}}` y el representante legal del cliente (`{{representanteNombre}}`, `{{representanteDocumento}}`).
La lista final se cierra cuando se construya el módulo de contratos.

### Cartas

| Campo | Contenido |
|---|---|
| `{{ciudad}}`, `{{fechaTexto}}` | Lugar y fecha de la carta («Lima, 25 de noviembre de 2026») |
| `{{asunto}}` | Asunto (en la carta base) |
| `{{marcaRazonSocial}}`, `{{marcaRuc}}`, `{{marcaDireccionFiscal}}` | Datos legales de la marca (IQC PERÚ S.A.C.) |
| `{{normas}}`, `{{normasSinEdicion}}` | «ISO 45001:2018 e ISO 14001:2015» / «ISO 45001 e ISO 14001» |
| `{{norma.codigo}}`, `{{norma.nombre}}` | Párrafo que se repite por norma (lista con viñetas) |
| `{{tipoServicio}}` | En minúscula, dentro de la frase: «recertificación» |
| `{{etapa}}` | «2da Vigilancia», «Recertificación»… |
| `{{sistema}}` | «Sistema Integrado de Gestión» con varias normas; «Sistema de Gestión» con una |
| `{{alcance}}` | Alcance certificado |
| `{{firma}}` | Imagen de la firma de quien firma (texto alternativo de la imagen) |
| `{{firmanteNombre}}`, `{{firmanteCargo}}` | Nombre y cargo de quien firma |

## Cartas: la firma siempre al pie de la última página

| Archivo | Carta |
|---|---|
| `cartas/carta-base.docx` | **Carta libre** en el panel y punto de partida para cartas nuevas: membrete y cierre listos; el cuerpo es `{{cuerpo}}` |
| `cartas/carta-certificacion.docx` | Aprobación o recertificación del sistema de gestión, con alcance |
| `cartas/carta-continuidad.docx` | Continuidad de las certificaciones tras una vigilancia (`{{etapa}}`) |
| `cartas/carta-suspension-pago.docx` | Suspensión por falta de pago, con la lista de normas |

**Todas comparten el mismo diseño:**
- **Membrete** en el encabezado, que se repite en cada página: `{{logo}}` y los datos de la marca, con una línea azul debajo.
- **Cierre** («Atentamente», la firma `{{firma}}` con el logo a la derecha, el nombre, el cargo y la empresa): es un
  **marco anclado al margen inferior**.
  - Queda **siempre al pie de la última página**, sea la carta de 5 líneas o de 2 hojas.
  - El texto del cuerpo nunca se le monta encima ni pasa a su lado. Si el cuerpo no cabe, el cierre pasa a la hoja
    siguiente junto con el final del texto.
  - Probado con LibreOffice con cartas de 1 y 2 páginas: el cierre cae en la misma posición en todas.
- **La firma es dinámica:** la imagen de la plantilla es solo una muestra («Firma»). Al generar la carta, IQC Flow pone
  la firma guardada de la persona que firma, con su nombre y cargo.
  - La firma real no se guarda en el repositorio. Cada persona sube la suya en el panel: menú con su nombre → **«Mi perfil, firma y contraseña»**.
  - Por seguridad, la imagen guardada no se puede descargar: solo la usa IQC Flow al emitir una carta.

**Dónde se hacen las cartas:** en el panel, pantalla **Cartas** → «Nueva carta».
1. Se elige el tipo (certificación, continuidad, suspensión por falta de pago o carta libre) y el cliente.
2. Se marcan los certificados que menciona: las normas y ediciones salen de ellos. Se completan la etapa, el alcance o el
   texto, según el tipo, y quién firma.
3. A la derecha se ve la carta armada mientras se escribe.
4. «Emitir carta» le da su número (`CAR-2026-0001`) y guarda el PDF. Desde la lista se vuelve a abrir, se descarga el PDF
   y, para corregirla a mano, el Word.

**`{{cuerpo}}`** (solo en la carta libre): un párrafo cuyo único texto es `{{cuerpo}}` se convierte en lo que escriba la
persona. Una línea en blanco empieza un párrafo nuevo y un salto simple es un salto de línea.

**Para agregar un tipo de carta nuevo** (por ejemplo, retiro):
1. Copia `cartas/carta-base.docx` con su nuevo nombre, por ejemplo `carta-retiro.docx`, y escribe el cuerpo con los
   campos de la lista. **No borres ni muevas el bloque de firma.**
   - Si al editar en Word el cierre aparece en un recuadro, es el marco: déjalo como está.
2. Revísalo con la vista previa (abajo).
3. Para que aparezca en el panel hay que agregarlo en `TipoCarta` (API) y en la migración de `cartas.tipo`; pídeselo al
   equipo técnico. Las plantillas de `cartas/` viajan dentro del JAR al compilar (`pom.xml`).

**Tus cartas originales** (las que subiste el 24/11) quedaron en `cartas/_originales/` como referencia. Esa carpeta no
se sube al repositorio porque tiene datos reales de clientes.

## Vista previa (antes de usar una plantilla)

En el servidor, desde esta carpeta:

```bash
python3 _herramientas/vista_previa.py cartas/carta-continuidad.docx _herramientas/datos-ejemplo.json /tmp/vista
```

- Llena la plantilla con los datos ficticios de `datos-ejemplo.json` y la convierte a PDF con LibreOffice.
- Avisa de los campos que no tienen dato de ejemplo.
- Requiere `pip install python-docx`.

`_herramientas/armar_cartas.py` vuelve a generar las cuatro cartas desde código, con el mismo membrete y cierre. Solo
hace falta si se cambia el diseño común.

## Cómo agregar o cambiar una plantilla

1. Se copia el archivo en la carpeta que corresponde, con el nombre según la convención.
2. Se revisa que cada campo esté escrito igual que en las tablas de arriba.
3. Cuando exista la pantalla **Plantillas**, se sube desde ahí y se prueba con «Vista previa» antes de activarla. La
   versión anterior queda guardada.
