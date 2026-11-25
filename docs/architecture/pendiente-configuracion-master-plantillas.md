# Pendiente: configuración global de MASTER, modos de registro, plantillas y correo

**Pedido del usuario:** 20 de noviembre de 2026. **Estado al 21/11/2026:** pasos 1 a 3 hechos y probados; 4 y 5 pendientes.

| Paso | Estado |
|---|---|
| 1. V12, MASTER con todo, evento `ParametroCambiado` | ✔ Hecho (`PermisosPorParametros` sincroniza MASTER al arrancar y aplica los parámetros de Administración) |
| 2. Lector de PDF local | ✔ Hecho: `AnalizadorTextoCertificado` (3 formatos, avisos de campos faltantes y fechas incoherentes), `POST /api/v1/certificados/lectura`, `GET /api/v1/certificados/herramientas`; puerto `LectorIaCertificados` sin proveedor |
| 3. Configuración y pestañas de modo | ✔ Hecho: pantalla Configuración (MASTER) y modos Manual / Automatizado / Desde plantilla en los registros empresarial y personal |
| 4. Plantillas Word y generación de PDF | En curso: **LibreOffice 7.1 instalado el 25/11/2026** (aprobado por el usuario). Cartas listas en `plantillas/cartas/` con la firma anclada al pie de la última página (ver `plantillas/README.md`). Falta el generador en la API, la pantalla Plantillas y subir la firma de cada persona. La pestaña «Desde plantilla» ya existe y registra sin PDF |
| 5. Correo | Pendiente: requiere credenciales. Qué cuenta usar y qué debe crear IQC (buzón `cotizaciones@` en Google Workspace, contraseñas de aplicación): ver [proceso-comercial-cotizaciones.md](proceso-comercial-cotizaciones.md), sección 4 |
| 3b. Opciones por persona (pedido del 24/11/2026) | ✔ Hecho: V15 `parametros_desactivados_usuario`, `ParametrosOrganizacion.activoPara` y `cambiarParaPersona`, vista «Por persona» en Configuración; IT en `LecturaYConfiguracionIT` |

## 1. Lo que se pidió

1. **MASTER tiene y puede hacer todos los módulos.** Recibe todos los permisos, también los que se agreguen después.
2. **Interruptores generales que solo cambia MASTER y que valen para todos:**
   - encender o apagar la IA para toda la organización;
   - encender o apagar las automatizaciones;
   - más opciones del mismo tipo a medida que se agreguen módulos.
3. **Modos de registro de certificados**, configurables por separado para **empresariales** y **personales**:

   | Modo | Flujo |
   |---|---|
   | Manual | Se sube el PDF y los datos se completan a mano |
   | Automatizado | Se sube el PDF, el sistema lee sus campos y completa el formulario; la persona revisa y confirma |
   | Plantilla | Se completa el formulario y el sistema genera el PDF desde una plantilla |

   El formulario muestra solo los modos habilitados.
4. **Administración** (rol `ADMINISTRACION_LOGISTICA`):
   - tiene autocompletado desde PDF y generación de PDF desde el formulario, habilitados por defecto. Solo MASTER puede desactivarlos;
   - gestiona **varias plantillas** (certificados, cartas, correos);
   - envía documentos y certificados **por correo**.

## 2. Decisiones tomadas con el usuario

| Tema | Decisión |
|---|---|
| Lectura del PDF (modo automatizado) | **Local primero, IA de respaldo.** Reglas sobre el texto del PDF; la IA solo se usa si el formato no se reconoce y la IA está encendida |
| Proveedor de IA | **Decidir después.** El puerto de IA queda preparado y apagado (`IA_HABILITADA = NO`) |
| Plantillas | **Word (.docx) con campos** `{{razonSocial}}`, `{{codigo}}`, `{{norma}}`, `{{qr}}`…, convertidas a PDF con LibreOffice sin interfaz |
| Correo | **Las dos opciones:** cuentas de Google Workspace y cuentas del servidor. MASTER configura las cuentas de envío y cada plantilla de correo elige una |

## 3. Formatos de PDF reales analizados (muestra del 20/11/2026)

Los tres formatos tienen texto extraíble, así que la lectura local es viable.

| Formato | Marcas en el texto |
|---|---|
| INACAL / SAE (`DDC-FRM-015`) | `Certificado No.: PE-…`, `ISO 37001: 2025`, `Alcance:`, `dd/mm/aaaa – V.01`, `Ciclo de certificación Nro. N`. Las 5 fechas van en formato largo (`16 de Octubre del 2026`) en este orden: otorgamiento, emisión, vigilancia 1, vigilancia 2, expiración. Después vienen el titular en mayúsculas y la dirección |
| India / IQC Global (`QF-12`) | `Número de certificado : PER/…`, titular y dirección después de la banda `EMS IQC…`, `ISO 14001:2015`, `Subdivisión`, `Clase`, `Fecha de emisión inicial : dd.mm.aaaa`, `Fecha de 1era vigilancia`, `Fecha de 2da vigilancia`, `Certificado válido hasta` |
| Personal | `Código de verificación: IQC/PE/…`, `DNI: n`, el nombre en la línea siguiente, el curso, la norma, el subtítulo, `DURACIÓN … horas`, `MODALIDAD`, `FECHAS DEL CURSO dd al dd de mes de aaaa`, `Entrenador: …`, `Fecha de emisión: dd de mes de aaaa` |

## 4. Diseño acordado

- **Parámetros de la organización** (`ParametroOrganizacion`, ADR-034), **ya agregados al enum:**
  - `IA_HABILITADA`, `AUTOMATIZACIONES_HABILITADAS`;
  - `CERT_EMPRESARIAL_MODO_MANUAL`, `CERT_EMPRESARIAL_MODO_AUTOMATIZADO`, `CERT_EMPRESARIAL_MODO_PLANTILLA`;
  - `CERT_PERSONAL_MODO_MANUAL`, `CERT_PERSONAL_MODO_AUTOMATIZADO`, `CERT_PERSONAL_MODO_PLANTILLA`;
  - `ADMINISTRACION_AUTOCOMPLETADO`, `ADMINISTRACION_GENERACION_PDF`.

  La tabla `parametros_organizacion` no restringe las claves, así que no hace falta migración para estos.
- **Permisos nuevos (migración V12):**
  - `certificados:autocompletar`, `certificados:generar-pdf`, `plantillas:gestionar`, `correos:enviar` y `correos:configurar`;
  - MASTER recibe **todos** los permisos;
  - Administración recibe `certificados:leer`, `certificados:gestionar`, `documentos:subir`, `clientes:leer`, `plantillas:gestionar`, `correos:enviar`, `certificados:autocompletar` y `certificados:generar-pdf`.
- **Apagar herramientas de Administración:** `ParametrosOrganizacion.cambiar` publicará un evento `ParametroCambiado`. El módulo `identidad` lo escucha y quita o devuelve `certificados:autocompletar` o `certificados:generar-pdf` al rol `ADMINISTRACION_LOGISTICA`. Como los permisos se recalculan en cada petición (ADR-011), el cambio es inmediato y el frontend solo mira los permisos.
- **Lector de PDF:**
  - PDFBox en el backend;
  - `POST /api/v1/certificados/lectura?tipo=EMPRESARIAL|PERSONAL&documentoId=` devuelve los campos detectados, el formato reconocido y la confianza de cada campo;
  - la IA queda como respaldo detrás de un puerto, sin proveedor todavía.
- **Plantillas Word:**
  - tabla `plantillas` con tipo (`CERTIFICADO_EMPRESARIAL`, `CERTIFICADO_PERSONAL`, `CARTA`, `CORREO`), nombre, marca o país opcional, documento .docx y si está activa;
  - relleno con Apache POI, juntando los fragmentos de texto en que Word parte los marcadores; el campo `{{qr}}` se reemplaza por la imagen del QR;
  - conversión a PDF con `soffice --headless`;
  - en el modo plantilla, primero se registra el certificado y después se genera su PDF con el código y el QR reales.
- **Correo:**
  - tabla de cuentas de envío (SMTP, con la contraseña cifrada con una clave de entorno);
  - plantillas de correo con asunto y cuerpo con campos;
  - envío con adjuntos y registro de cada envío;
  - para el SMTP del servidor, el servicio necesita `Group=mail` (bloqueo de SMTP de cPanel; mismo patrón que impservicesac).
- **Frontend:**
  - pantalla de **Configuración** (MASTER) con los interruptores;
  - pestañas de modo (Manual, Automatizado, Plantilla) en los formularios de registro empresarial y personal;
  - pantalla de **Plantillas**;
  - **Correo:** cuentas, plantillas y botón «Enviar por correo» en el detalle de cada certificado.

## 5. Pendiente de aprobación

- **Instalar LibreOffice Writer en el servidor** (`dnf install libreoffice-writer libreoffice-langpack-es` y fuentes Liberation y DejaVu), necesario para convertir las plantillas Word a PDF. El agente no lo instaló porque instalar paquetes en el servidor de producción necesita aprobación explícita del usuario.
- **Credenciales de correo:** una contraseña de aplicación de Google Workspace y una cuenta de correo del cPanel, cuando se configuren las cuentas de envío.

## 6. Orden de trabajo propuesto para continuar

1. V12 (permisos y MASTER con todo), el evento `ParametroCambiado` y su escucha en `identidad`, y pruebas.
2. Lector de PDF local (los tres formatos) con pruebas sobre textos de ejemplo ficticios.
3. Pantalla de Configuración y pestañas de modo en los formularios (Manual y Automatizado).
4. Plantillas Word y generación de PDF (requiere LibreOffice).
5. Cuentas de envío, plantillas de correo y envío.
