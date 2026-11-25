# Plantillas de IQC Flow

Aquí se guardan las **plantillas base** de los documentos y correos que genera IQC Flow: certificados, cotizaciones,
contratos, cartas y correos. Son el punto de partida. Más adelante se suben y se mantienen desde la pantalla
**Plantillas** del panel (permiso `plantillas:gestionar`), que guarda cada versión en la tabla `plantillas`. Esta carpeta
es la copia de referencia versionada en git.

> **Estado (25/11/2026):**
> - Hay cuatro cartas listas en `cartas/`, con la firma siempre al pie de la última página (ver «Cartas»).
> - Faltan las plantillas de certificados, cotización y contrato.
> - Los correos de `correos/comerciales/` son borradores para revisar.
> - LibreOffice ya está instalado en el servidor (25/11/2026) para convertir a PDF. El generador dentro de IQC Flow
>   (pantalla Plantillas y botón «Generar carta») está pendiente. Ver
>   [docs/architecture/pendiente-configuracion-master-plantillas.md](../docs/architecture/pendiente-configuracion-master-plantillas.md).

## Carpetas

| Carpeta | Qué va | Formato |
|---|---|---|
| `certificados/empresariales/` | Certificado de sistema de gestión (una plantilla por marca o país y por esquema si cambia el diseño) | Word `.docx` |
| `certificados/personales/` | Certificado de curso o capacitación | Word `.docx` |
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
| `cartas/carta-base.docx` | **Punto de partida para cartas nuevas**: membrete y cierre listos, cuerpo libre |
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
  - La firma real no se guarda en el repositorio.
  - Falta que el panel permita subir la firma de cada persona.

**Para hacer una carta nueva:**
1. Copia `cartas/carta-base.docx` con su nuevo nombre, por ejemplo `carta-retiro.docx`.
2. Cambia solo el cuerpo, entre «Presente.–» y la última frase. **No borres ni muevas el bloque de firma.**
   - Si al editar en Word el cierre aparece en un recuadro, es el marco: déjalo como está.
3. Revisa el resultado con la vista previa (abajo).

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
