---
name: IQC Flow (panel interno)
description: Panel interno de IQC Perú e IQC LATAM. Carril y acceso en negro de marca con el símbolo IQC en línea de degradado; trabajo sobre papel, con tintas de sello para los estados. Tokens del sistema compartido iqc-flow-diseno.
colors:
  negro-marca: "#000000"
  azul-iqc: "#0019d2"
  azul-vivo: "#1a33e0"
  azul-vivo-hover: "#1428bd"
  degradado-rojo: "#d90435"
  degradado-magenta: "#9002ae"
  degradado-violeta: "#6701e0"
  degradado-azul: "#0019d2"
  tinta: "#1a1a2e"
  blanco: "#ffffff"
  papel: "#f2f4f7"
  superficie: "#ffffff"
  superficie-2: "#f7f8fb"
  superficie-hundida: "#eceef3"
  borde: "#d5dbe4"
  borde-suave: "#e6eaf0"
  borde-fuerte: "#c2c9d6"
  texto: "#1a1a2e"
  texto-suave: "#343a4d"
  texto-2: "#434b5e"
  texto-3: "#59627a"
  accion: "#1a33e0"
  accion-hover: "#1428bd"
  accion-suave: "#eceffe"
  enlace: "#0019d2"
  ok: "#0d6b44"
  ok-suave: "#e8f4ee"
  alerta: "#9a5800"
  alerta-suave: "#fbf1e1"
  mal: "#b42318"
  mal-suave: "#fbeceb"
  pausa: "#a0480a"
  pausa-suave: "#fdecdc"
  neutro: "#4a5568"
  neutro-suave: "#eef1f5"
  senal: "#9002ae"
  cromo-texto-2: "#a3abbd"
  cromo-hover: "rgb(255 255 255 / .07)"
  cromo-activo: "rgb(255 255 255 / .11)"
  cromo-borde: "rgb(255 255 255 / .1)"
  foco-cromo: "#8f9bff"
  aviso-oscuro: "rgb(18 19 26 / .96)"
  grafico: "#1a33e0"
  grafico-hover: "#0019d2"
  rejilla: "#e6eaf0"
  grafito-fondo: "#0b0c10"
  grafito-superficie: "#14151c"
  grafito-superficie-2: "#1a1b23"
  grafito-borde: "#2a2c38"
  grafito-texto: "#f2f3f7"
  grafito-texto-2: "#aeb2c2"
  grafito-accion: "#8f9bff"
  grafito-grafico: "#6b7bff"
  grafito-ok: "#56d2a4"
  grafito-alerta: "#f1b54f"
  grafito-mal: "#ff8395"
typography:
  display-acceso:
    fontFamily: "Public Sans, system-ui, -apple-system, Segoe UI, sans-serif"
    fontSize: "clamp(2.4rem, 1.6rem + 3vw, 3.6rem)"
    fontWeight: 700
    lineHeight: 1.04
    letterSpacing: "-0.025em"
  headline:
    fontFamily: "Public Sans, system-ui, -apple-system, Segoe UI, sans-serif"
    fontSize: "1.75rem"
    fontWeight: 700
    lineHeight: 1.15
    letterSpacing: "-0.02em"
  title:
    fontFamily: "Public Sans, system-ui, -apple-system, Segoe UI, sans-serif"
    fontSize: "1.1875rem"
    fontWeight: 650
    lineHeight: 1.3
    letterSpacing: "-0.01em"
  panel-title:
    fontFamily: "Public Sans, system-ui, -apple-system, Segoe UI, sans-serif"
    fontSize: "1rem"
    fontWeight: 650
    lineHeight: 1.3
    letterSpacing: "-0.01em"
  cifra:
    fontFamily: "Public Sans, system-ui, -apple-system, Segoe UI, sans-serif"
    fontSize: "1.4375rem"
    fontWeight: 650
    lineHeight: 1.2
    fontFeature: "tnum"
  body:
    fontFamily: "Public Sans, system-ui, -apple-system, Segoe UI, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: 1.5
  body-sm:
    fontFamily: "Public Sans, system-ui, -apple-system, Segoe UI, sans-serif"
    fontSize: "0.8125rem"
    fontWeight: 400
    lineHeight: 1.5
  label:
    fontFamily: "Public Sans, system-ui, -apple-system, Segoe UI, sans-serif"
    fontSize: "0.75rem"
    fontWeight: 600
    lineHeight: 1.4
    letterSpacing: "0.05em"
  codigo:
    fontFamily: "Public Sans, system-ui, -apple-system, Segoe UI, sans-serif"
    fontSize: "0.92em"
    fontWeight: 500
    letterSpacing: "0.02em"
    fontFeature: "zero"
  titular:
    fontFamily: "Source Serif 4, Georgia, serif"
    fontSize: "1.1875rem"
    fontWeight: 600
    lineHeight: 1.3
    letterSpacing: "-0.01em"
rounded:
  barra: "4px"
  sm: "6px"
  md: "8px"
  lg: "10px"
  pildora: "999px"
spacing:
  e1: "4px"
  e2: "8px"
  e3: "12px"
  e4: "16px"
  e5: "20px"
  e6: "24px"
  e8: "32px"
  e10: "40px"
  e12: "48px"
components:
  button-primary:
    backgroundColor: "{colors.accion}"
    textColor: "{colors.blanco}"
    typography: "{typography.body}"
    rounded: "{rounded.sm}"
    padding: "0 16px"
    height: "38px"
  button-primary-hover:
    backgroundColor: "{colors.accion-hover}"
    textColor: "{colors.blanco}"
  button-secondary:
    backgroundColor: "{colors.superficie}"
    textColor: "{colors.texto}"
    rounded: "{rounded.sm}"
    padding: "0 16px"
    height: "38px"
  button-secondary-hover:
    backgroundColor: "{colors.superficie-2}"
  button-acceso:
    backgroundColor: "{colors.azul-vivo}"
    textColor: "{colors.blanco}"
    rounded: "{rounded.sm}"
    height: "48px"
  button-acceso-hover:
    backgroundColor: "{colors.azul-vivo-hover}"
  input:
    backgroundColor: "{colors.superficie}"
    textColor: "{colors.texto}"
    typography: "{typography.body}"
    rounded: "{rounded.sm}"
    padding: "7px 12px"
    height: "38px"
  nav-item:
    backgroundColor: "{colors.negro-marca}"
    textColor: "{colors.cromo-texto-2}"
    rounded: "{rounded.sm}"
    padding: "0 12px"
    height: "38px"
  nav-item-hover:
    backgroundColor: "{colors.cromo-hover}"
    textColor: "{colors.blanco}"
  nav-item-active:
    backgroundColor: "{colors.cromo-activo}"
    textColor: "{colors.blanco}"
  panel:
    backgroundColor: "{colors.superficie}"
    textColor: "{colors.texto}"
    rounded: "{rounded.lg}"
    padding: "20px"
  estado-vigente:
    backgroundColor: "{colors.ok-suave}"
    textColor: "{colors.ok}"
    typography: "{typography.label}"
    rounded: "{rounded.pildora}"
    padding: "2px 8px"
  estado-por-vencer:
    backgroundColor: "{colors.alerta-suave}"
    textColor: "{colors.alerta}"
    rounded: "{rounded.pildora}"
    padding: "2px 8px"
  estado-vencido:
    backgroundColor: "{colors.mal-suave}"
    textColor: "{colors.mal}"
    rounded: "{rounded.pildora}"
    padding: "2px 8px"
  estado-suspendido:
    backgroundColor: "{colors.pausa-suave}"
    textColor: "{colors.pausa}"
    rounded: "{rounded.pildora}"
    padding: "2px 8px"
  estado-neutro:
    backgroundColor: "{colors.neutro-suave}"
    textColor: "{colors.neutro}"
    rounded: "{rounded.pildora}"
    padding: "2px 8px"
  grafico-tooltip:
    backgroundColor: "{colors.negro-marca}"
    textColor: "{colors.blanco}"
    rounded: "{rounded.sm}"
    padding: "6px 10px"
  aviso-sobre-negro:
    backgroundColor: "{colors.aviso-oscuro}"
    textColor: "#e6e8ee"
    rounded: "{rounded.sm}"
    padding: "12px"
---

# Design System: IQC Flow (panel interno)

## Overview

**Creative North Star: "El marco negro y el expediente"**

El panel es la parte interna del mismo sistema visual que el portal de consultas. El negro de la marca IQC enmarca el trabajo: el carril de navegación, la pantalla de acceso y la carga inicial son negros como la cabecera de iqcperu.com, con el símbolo IQC dibujado en línea fina con el degradado del logo. Dentro del marco, el trabajo se lee como un expediente: papel gris muy claro, superficies blancas con borde fino, tinta casi negra y, para cada estado, la tinta de un sello.

Es una herramienta de uso diario (modo Operate): escala tipográfica fija, densidad de tabla, filas de cifras y gráficos de una sola serie. No hay hero ni composición de página de presentación salvo en el acceso. Cada rol entra a su espacio («Hoy» de Dirección, Certificados, Gestión…) y ve primero lo urgente y después sus gráficos. Tiene tres apariencias, Claro, Oscuro y Sistema: en oscuro el papel pasa a grafito y el carril sigue negro.

Todos los colores, fuentes, tamaños, espacios, radios, sombras y curvas vienen del paquete compartido `iqc-flow-diseno` (`css/tokens.css`). El panel no define colores propios: solo el ancho del carril (256 px). Lo que el usuario descartó no vuelve: fotos de stock, auroras y brillos difusos, redes de partículas, el carril azul marino del panel anterior y el acento magenta.

**Key Characteristics:**
- Carril negro de marca con filete superior de degradado de 2 px y el símbolo IQC recortado al pie, en línea.
- Acceso a pantalla completa en negro, con el símbolo trazado en grande y el formulario directamente sobre el negro, sin tarjeta.
- Área de trabajo en papel con paneles blancos de borde fino y radio de 10 px; planos, sin sombra.
- Public Sans en todo; Source Serif 4 600 solo para el nombre del titular del certificado.
- Códigos de certificado con cero con barra; cifras tabulares en tablas, contadores y gráficos.
- Estados con las tintas del sello (ok, alerta, mal, pausa, neutro), siempre con su palabra.
- Gráficos de una serie en azul IQC, columnas de 24 px como máximo, solo el máximo rotulado, tooltip negro y vista de tabla.

## Colors

La paleta se reparte en tres familias: la marca sobre negro (negro, azul IQC y el degradado del logo), el documento (papel, superficies, bordes y tinta) y el estado (cinco tintas de sello con su fondo suave). Todo color nuevo se agrega en `iqc-flow-diseno/css/tokens.css`, en claro y en oscuro, nunca en el panel.

### Primary
- **Azul IQC vivo** (`accion`, `azul-vivo`): botón principal, casillas y switches activos, enlaces de acción («Ver todos»), insignia de conteo del carril y botón del acceso sobre negro. En oscuro pasa al **Azul lavanda** (`grafito-accion`) con texto grafito encima.
- **Azul IQC** (`azul-iqc`, `enlace`): enlaces de texto y el estado de hover de la serie de los gráficos.
- **Azul del gráfico** (`grafico`): la única serie de cada gráfico. En oscuro, `grafito-grafico`. Ambos validados con la skill de visualización contra su superficie.

### Secondary
- **Tintas del sello**: **Verde vigente** (`ok`), **Ámbar por vencer** (`alerta`), **Rojo vencido** (`mal`: vencido, retirado, cancelado, atrasado, error), **Naranja suspendido** (`pausa`) y **Gris archivo** (`neutro`: sin fecha, reemplazado, archivado). Cada una con su `-suave` para fondos de etiqueta, avisos y filas marcadas; en oscuro, versiones claras sobre un fondo al 13 %.
- **Señal de hoy** (`senal`): la marca vertical de «hoy» en la barra del ciclo de 3 años del certificado. Es el único uso del magenta del degradado como color de relleno.

### Tertiary
- **Degradado del logo** (`degradado-rojo` → `degradado-magenta` → `degradado-violeta` → `degradado-azul`, a 0 / 38 / 66 / 100 %): solo como línea. El filete de 2 px arriba del carril, del acceso y de la barra móvil, y el contorno del símbolo IQC.

### Neutral
- **Negro de marca** (`negro-marca`, `--cromo`): carril, acceso, carga inicial y tooltip de los gráficos. Igual en claro y en oscuro.
- **Texto del carril** (`cromo-texto-2`): encabezados de grupo, iconos en reposo, correo de la cuenta. Hover y activo del carril son veladuras blancas (`cromo-hover` al 7 %, `cromo-activo` al 11 %); el foco sobre negro es `foco-cromo`.
- **Papel** (`papel`, `--fondo`): fondo del área de trabajo. En oscuro, **Grafito** (`grafito-fondo`).
- **Superficie** (`superficie`) para paneles, tablas y cajones; `superficie-2` para barras de herramientas, hovers de fila, cajas de detalle; `superficie-hundida` para pistas de barras, campos deshabilitados y esqueletos.
- **Bordes**: `borde` en paneles y filas, `borde-suave`/`rejilla` en la rejilla de gráficos, `borde-fuerte` en campos y botones secundarios.
- **Tinta** (`texto`) y sus escalones `texto-suave`, `texto-2` (secundario) y `texto-3` (etiquetas, ejes y ayudas). Todos con contraste ≥ 4.5:1 en ambos temas, medido.

### Named Rules
**The Paquete Compartido Rule.** El panel no inventa colores. Si falta uno, se agrega en `iqc-flow-diseno` con su valor claro y su valor oscuro, y lo heredan panel y portal.

**The Degradado en Línea Rule.** El degradado del logo solo dibuja líneas: filetes de 2 px y el contorno del símbolo. Nunca rellena botones, fondos, barras ni texto. La única excepción es el relleno interior del propio símbolo, una veladura del 6–7 % bajo su contorno.

**The Tinta con Palabra Rule.** Un estado se dice con su tinta y con su palabra («Vigente», «Quedan 11 días»). El color nunca va solo, y las tintas de estado no rellenan botones ni paneles, salvo el botón destructivo de confirmación.

## Typography

**Display Font:** Public Sans (variable 100–900, servida desde el propio sitio; con system-ui de respaldo)
**Body Font:** Public Sans
**Documento:** Source Serif 4 600 (con Georgia de respaldo)

**Character:** Una sola sans pública, sobria y legible en densidad, para toda la interfaz; la serif aparece solo donde el panel muestra lo que está inscrito en el certificado: el nombre del titular.

### Hierarchy
- **Display del acceso** (700, `clamp(2.4rem, 1.6rem + 3vw, 3.6rem)`, 1.04): solo el título de la pantalla de acceso, sobre negro. Es el único tamaño fluido del panel.
- **Headline** (700, 1.75rem, 1.15): título de cada pantalla («Hoy», «Certificados»). Baja a 1.4375rem por debajo de 960 px.
- **Title** (650, 1.1875rem, 1.3): títulos de cajón y de formularios.
- **Panel title** (650, 1rem): título de cada panel y gráfico, con su línea de detalle en `body-sm` y `texto-2` debajo.
- **Cifra** (650, 1.4375rem, tabular): contadores de la franja «Hoy», de seguimientos y la cifra en línea de un gráfico de una sola categoría.
- **Body** (400, 0.875rem, 1.5): texto de interfaz, celdas de tabla, navegación. Notas largas hasta 72ch.
- **Body small** (0.8125rem): metadatos, ayudas, avisos, filtros.
- **Label** (600, 0.75rem, 0.05em, mayúsculas): cabeceras de tabla y rótulos de dato dentro de la caja de detalle (y los `legend` de formulario a 0.8125rem). Son rótulos de columna o de campo, no antetítulos.
- **Código** (500, 0.92em, 0.02em, cero con barra): códigos de certificado y URL del QR, para cotejarlos con el papel impreso.
- **Titular** (Source Serif 4 600, 1.1875rem en el detalle; a tamaño de fila en las listas): nombre del titular en el detalle del certificado, en «Certificados por vencer» y en «Últimos personales».

### Named Rules
**The Serif del Titular Rule.** Source Serif 4 se usa solo para el nombre del titular. Ni títulos, ni cifras, ni rótulos.

**The Cifra Tabular Rule.** Toda cifra que se compara en columna (tablas, `dd`, contadores, ejes, tooltips, días restantes) usa cifras tabulares; los códigos usan cero con barra.

**The Sin Antetítulo Rule.** Ningún título lleva un rótulo pequeño encima. El contexto va debajo, como línea de detalle.

## Layout

Rejilla de espaciado de 4 px (`e1` 4 → `e12` 48). Armazón de dos columnas: carril de 256 px fijo a la altura de la ventana y área de trabajo con un máximo de 1400 px, centrada, con 32 px de margen lateral y 48 px al pie.

Cada pantalla abre con su cabecera (título y línea de contexto a la izquierda, acciones a la derecha, alineadas abajo) y 24 px de separación. En «Hoy», una franja de cuatro cifras en una sola caja dividida por bordes, después la rejilla de vista general (1.6fr para la lista urgente y una columna lateral ≥ 280 px) y debajo los gráficos del rol en dos columnas; un gráfico ancho ocupa ambas. Entre paneles, 20 px. Los paneles llevan 20 px de relleno.

Respuesta por anchos:
- **≤ 1080 px:** la vista general y los gráficos pasan a una columna.
- **≤ 960 px:** el carril se vuelve un cajón lateral (máx. 300 px, 86vw) sobre un velo, y aparece una barra superior negra de 56 px con filete de degradado de 2 px abajo.
- **≤ 860 px:** el acceso apila texto y formulario.
- **≤ 760 px:** las tablas se sustituyen por listas de filas, los formularios y cajas de detalle van a una columna, las franjas de cifras a 2 × 2.

### Named Rules
**The Urgente Primero Rule.** En el inicio de cada rol, lo que pide acción (atrasadas, por vencer) va antes que los gráficos; los gráficos sin datos van al final y compactos.

## Elevation & Depth

Plano por defecto. Los paneles, tablas y tarjetas de tipo se separan del papel por el contraste de superficie y un borde de 1 px, sin sombra. La sombra aparece solo en lo que flota sobre otra cosa: el tooltip de los gráficos y el buscador desplegable (`--sombra-flotante`), y el cajón de detalle y el carril móvil (`--sombra-lateral`), siempre con el velo detrás. En oscuro las mismas sombras pasan a negro puro.

### Shadow Vocabulary
- **Flotante** (`box-shadow: 0 8px 24px -4px rgb(0 25 45 / .16)`): tooltip de gráfico, resultados del buscador de clientes.
- **Lateral** (`box-shadow: -12px 0 32px rgb(0 25 45 / .16)`): cajón de detalle y formularios, carril abierto en móvil.
- **Velo** (`rgb(5 6 12 / .48)`): fondo detrás de cajón y carril móvil.

### Named Rules
**The Plano en Reposo Rule.** Nada en el área de trabajo tiene sombra en reposo. Si un elemento no flota, se separa con borde.

## Shapes

Esquinas suaves y pequeñas: 6 px en botones, campos, iconos y filas interactivas; 8 px en cajas internas (detalle, ciclo, QR, formularios en línea); 10 px en paneles, listas y la franja de cifras. Píldora (999 px) para etiquetas de estado, filtros de vigencia, insignias y el switch. Las barras de datos tienen la punta redondeada a 4 px y la base recta, creciendo desde el eje. Las pistas finas (vence en, composición, ciclo) son de 6–10 px de alto con radio a la mitad.

El símbolo IQC (anillo octogonal abierto, pieza de cierre y marcador central) es la forma firma: un trazado SVG compartido (`simbolo.ts`) que siempre va sobre negro.

## Components

### Símbolo IQC y carril (componente firma)
- **Carril:** negro de marca, 256 px, filete de degradado de 2 px arriba. Logo blanco, nombre del espacio del rol en 1rem 650, grupos de navegación con encabezado en `label` sin mayúsculas, selector de apariencia (Claro / Oscuro / Sistema) y la cuenta al pie con borde superior `cromo-borde`.
- **Símbolo:** asoma recortado al pie del carril (300 px, contorno al 40 %, relleno al 6 %) y en grande detrás del acceso (contorno al 55 %, relleno al 7 %). Solo en el acceso se dibuja una vez al cargar (2.4 s, curva `--salida`); el panel de trabajo no se anima. Siempre `aria-hidden`.

### Navegación
- **Elemento:** 38 px de alto, radio 6 px, texto 0.875rem 500 en gris claro con icono en `cromo-texto-2`.
- **Hover:** veladura blanca al 7 % y texto blanco. **Activo:** veladura al 11 %, texto blanco 600 e icono en `foco-cromo`.
- **Foco:** contorno en `foco-cromo`. **Insignia:** píldora azul IQC vivo con texto blanco.
- **Móvil:** cajón lateral con velo; barra superior negra con el título y botón de menú.

### Buttons
- **Shape:** esquinas suaves (6 px), 38 px de alto, 0 16 px de relleno; variante pequeña de 30 px.
- **Primary:** azul IQC vivo con texto blanco, 600. Hover a `accion-hover`; al presionar baja 1 px.
- **Secondary:** superficie blanca, borde `borde-fuerte`, tinta. Hover a `superficie-2` con borde `texto-3`.
- **Texto:** sin fondo, en `accion`, subrayado al pasar; en `mal` si es destructivo.
- **Icono:** 34 × 34, transparente, hover en `superficie-hundida`.
- **Sobre negro (acceso):** 48 px de alto, azul IQC vivo, texto 1rem.

### Chips
- **Etiqueta de estado:** solo la palabra en la tinta del estado (`--ok`, `--alerta`, `--mal`, `--pausa`; neutros en `texto-2`), 600, sin punto ni píldora (26/11/2026).
- **Filtro de vigencia:** píldora de 40 px con borde `borde-fuerte` y la cifra en la tinta de su estado; seleccionado se invierte a tinta con texto de superficie.

### Cards / Containers
- **Color del ícono de una cifra:** rojo, ámbar y verde solo para estados (atrasado, por vencer, vigente); todas las demás cifras en el azul de IQC (`tono-accion`). No hay ícono gris: se leía como «sin color» (26/11/2026).
- **Íconos:** van en las tarjetas de cifra (`TarjetaCifra`), en el carril y en los botones. **Los títulos de panel (`.panel-title h2`) no llevan ícono** (26/11/2026): con uno solo con ícono la pantalla se ve dispareja.
- **Corner Style:** 10 px.
- **Background:** `superficie` sobre `papel`; cajas internas en `superficie-2` con radio 8 px.
- **Shadow Strategy:** ninguna (ver Plano en Reposo).
- **Border:** 1 px `borde`. Panel de alerta con borde mezclado al 45 % de `mal` y fondo `mal-suave`.
- **Internal Padding:** 20 px (`e5`).
- **Misma fila, misma altura:** las rejillas de paneles (gráficos, Configuración, tarjetas de tipo del Resumen) estiran sus celdas; las tarjetas de tipo comparten estructura (título y cifra arriba, datos abajo).

### Inputs / Fields
- **Style:** superficie blanca, borde 1 px `borde-fuerte`, radio 6 px, 38 px de alto; rótulo encima en 0.8125rem 600.
- **Focus:** borde `accion` y anillo de 3 px `foco-anillo`.
- **Error / Disabled:** errores en caja `mal-suave` con icono; deshabilitado en `superficie-hundida` con texto `texto-3`.
- **Sobre negro (acceso):** campo oscuro translúcido (`rgb(8 10 14 / .72)`), borde blanco al 18 %, 48 px; foco en `foco-cromo`.

### Tablas
- **Color (26/11/2026):** encabezado en franja `accion-suave` con texto `enlace`; filas pares con tinte azul al 55 % de `accion-suave`; fila bajo el mouse en azul al 9 %; el identificador que abre el detalle (código del certificado) en `enlace`. Los tintes de estado (por vencer, vencido) mandan sobre el alterno. Los códigos largos solo se parten después de cada «/».
Cabecera fija en `label` con `texto-2`, celdas de 12 × 16 px con borde inferior, fila clicable con hover en `superficie-2` y foco en `accion-suave`. Filas por vencer y vencidas se tiñen con su `-suave` a media intensidad. Acciones de fila aparecen al pasar o al enfocar (siempre visibles en pantallas táctiles). Por debajo de 760 px se cambian por lista de filas.

### Cajón de detalle
Panel lateral derecho de 600 px (920 px el ancho), cabecera fija con título y subtítulo, entrada de 220 ms desde 24 px. El detalle del certificado lleva caja de datos con rótulos en `label`, el titular en serif, el ciclo de 3 años con la marca de hoy y el QR en caja propia.

### Vence en
Días que le quedan a un certificado dentro de la ventana de 90 días de «por vencer». Texto en 0.8125rem 650 tabular («Quedan 11 días», «Vence hoy», «Venció hace N días») y una pista de 6 px que se acorta según se acerca la fecha. Tinta `alerta` y, en los últimos 30 días, `mal`. Sin fecha: «Sin fecha» en `texto-3`.

### Gráficos
La forma la decide la pregunta (skill de visualización de datos), no la costumbre:

| Pregunta | Forma | Gráficos |
|---|---|---|
| ¿Cuánto cae en cada periodo futuro? | Columnas | Vencimientos por mes |
| ¿Cómo evoluciona? | Línea de 2 px con velo al 10 % | Emitidos, personales, clientes nuevos, completados por semana |
| ¿Cómo se compara cada categoría? | Barras horizontales ordenadas | Vigentes por norma y por acreditación, seguimientos por tipo |
| ¿Cómo se reparte un total? | Barra segmentada alta (28 px) con leyenda | Cartera por estado |
| ¿Qué parte del total pesa cada nivel? | Lista de medidores con tinta de severidad | Seguimientos por prioridad |

- **Serie:** una por gráfico, en `grafico`; hover/foco en `grafico-hover`.
- **Línea con área:** línea de 2 px con uniones redondeadas y velo de la misma tinta al 10 %; se rotulan solo el máximo y el último periodo; una línea vertical de 1 px sigue al puntero y se ajusta al periodo más cercano; con foco, las flechas recorren los periodos.
- **Barra segmentada:** colores categóricos en orden fijo (`serie-1` azul IQC, `serie-2` naranja, `serie-3` verde agua), validados en claro y oscuro (CVD ΔE ≥ 9); 2 px de aire entre bloques, radio 4 px, porcentaje dentro si el bloque lo admite (blanco sobre azul, casi negro sobre los demás) y leyenda con nombre y cantidad.
- **Medidores:** el relleno lleva la severidad (Urgente `mal`, Alta `alerta`, Normal `grafico`, Baja `neutro`) y la pista es el paso suave de la misma tinta; nombre, cantidad y porcentaje siempre en texto.
- **Columnas:** 24 px de grosor como máximo, punta redondeada 4 px y base recta; rejilla horizontal de 1 px en `rejilla`; ejes en 11 px `texto-3` tabulares; solo el valor máximo rotulado, en `texto` 650.
- **Barras horizontales:** ordenadas, 14 px de alto, nombre a la izquierda y cifra a la derecha en tinta, nunca en el color de la serie.
- **Tooltip:** negro de marca, texto blanco, cifra 1rem 700 y periodo en `cromo-texto-2`; responde a mouse y teclado.
- **Detalle:** la tarjeta entera (o su botón de ampliar) abre un cajón con el gráfico grande, cifras de resumen y la tabla con cada valor; reemplaza al botón de vista de tabla.
- **Fechas del eje:** mes abreviado y, debajo, el año completo en el primer periodo y cuando cambia; nunca «nov 26», que se leía como un día.
- **Una sola categoría con datos:** en lugar del gráfico, una cifra en línea («450 abiertos, todos de tipo…»); el panel pasa a compacto. No aplica a los medidores de prioridad: ver Urgente en cero también informa.
- **Sin datos:** el panel va al final, compacto, con una frase que explica por qué está vacío.
- **Distribución:** dos columnas con `align-items: stretch`, así los paneles de una misma fila miden lo mismo; van de dos en dos, en el orden de importancia del rol. Si quedan impares, uno va solo a todo el ancho al final: uno de barras, medidores o barra segmentada, nunca uno de columnas o de área por mes (con columnas de 24 px como máximo, a todo el ancho quedan huecos de más de 60 px entre ellas; desde el 26/11/2026). Los compactos (sin datos o con una sola cifra) van al final y a todo el ancho, sin dejar huecos.

### Formularios: distribución de campos
- **Cada fila se llena de lado a lado:** nunca queda un campo solo con un hueco al lado. Un campo corto va con uno largo (`corto-largo` / `largo-corto`: Horas + Modalidad, Norma + Año), varias fechas se acomodan al ancho (`fechas`, 150 px mínimo cada una) y tres datos de una dirección van en tres columnas (`three`).
- **Separación pareja:** 16 px entre filas y entre grupos (`fieldset` y `.campos` son rejillas con ese espacio); 24 px entre secciones. Las etiquetas van encima, alineadas arriba, y la ayuda debajo del campo.
- **Sin recuadros ni selectores especiales:** el RUC va suelto (etiqueta, campo y botón azul «Buscar») y el estado del cliente es una lista desplegable; el usuario rechazó el recuadro y el selector de botones (24/11).
- **Orden:** primero lo que identifica (RUC con su botón «Buscar», luego razón social), después lo descriptivo y al final las notas.

### Espacio de trabajo con PDF
Registrar, corregir y ver un certificado (empresarial o personal) usan el cajón dividido (`Drawer` con `vista`): datos a la izquierda (440–600 px, con las acciones fijas al pie) y el PDF a la derecha sobre `superficie-hundida`. Cada lado se desplaza por su cuenta. Por debajo de 1100 px pasa a una columna con el PDF debajo, a 78 vh.
- **Visor** (`components/pdf/VistaPdf.tsx`): visor del navegador sobre una copia `blob:` (la API no se deja enmarcar), sin columna de miniaturas y ajustado al ancho. Barra con nombre, tamaño y qué versión es; abrir en otra pestaña y «Cambiar».
- **Estados:** vacío (zona punteada: arrastrar o hacer clic), arrastrando (borde azul y «Suelta el PDF»), cargando, error, leyendo (capa sobre el visor) y PDF de la plataforma anterior (Google Cloud Storage: se muestra desde su enlace en el marco; no se puede descargar desde el panel porque el almacenamiento no tiene CORS).
- **Varias normas:** una pestaña por norma con un punto verde si ya tiene PDF.
- **Antes de guardar:** el PDF nuevo (registro o nueva versión) se ve antes de subirlo; en el detalle, cada versión anterior se puede ver en el visor.
- **Elegir PDF:** botón propio en español (`ElegirPdf`); nunca el control nativo «Choose File».
- **Ficha del certificado** (columna de datos del detalle): bloques separados por filetes, sin cajas grises anidadas, en este orden: estado y marca → titular (serif) con su documento → datos clave en dos columnas → ciclo → consulta pública (QR de 112 px en línea con el enlace, copiar y descargar) → alcance (6 líneas y «Ver alcance completo») → documento PDF → marca. El personal sigue el mismo orden (persona, curso y datos, descripción, consulta, PDF).
- **Estado de un certificado:** la palabra en su tinta («Vigente» en verde), sin punto ni pastilla, igual que el estado en Clientes, Seguimientos y Correos.

### Aviso sobre negro
En el acceso, los errores van en una caja oscura casi opaca (`aviso-oscuro`, borde blanco al 10 %, icono rosado), nunca en un bloque claro. En el área de trabajo, los avisos son cajas suaves en la tinta de su estado (`alerta-suave`, `mal-suave`, `ok-suave`).

## Do's and Don'ts

### Do:
- **Do** tomar todos los colores, tamaños, radios, sombras y curvas de `iqc-flow-diseno`; un color nuevo se agrega allí en claro y en oscuro.
- **Do** mantener carril, acceso y carga inicial en negro de marca, con el filete de degradado de 2 px arriba.
- **Do** usar el degradado solo en líneas: filetes y contorno del símbolo.
- **Do** separar paneles con borde de 1 px y radio de 10 px, sin sombra.
- **Do** comunicar cada estado con su tinta de sello y su palabra.
- **Do** escribir los códigos de certificado con `.mono` (cero con barra) y las cifras comparables como tabulares.
- **Do** reservar Source Serif 4 600 para el nombre del titular.
- **Do** hacer cada gráfico de una sola serie, con columnas ≤ 24 px, rejilla de 1 px, solo el máximo rotulado, tooltip negro y vista de tabla.
- **Do** detener toda animación con «reducir movimiento».

### Don't:
- **Don't** volver a usar fotos de stock como fondo: el usuario las descartó.
- **Don't** usar auroras, brillos difusos ni redes de partículas.
- **Don't** volver al carril azul marino ni al acento magenta del panel anterior.
- **Don't** poner tarjetas ni bloques claros sobre negro; los avisos sobre negro van en la caja oscura.
- **Don't** poner rótulos pequeños (antetítulos) encima de los títulos.
- **Don't** rellenar con degradado botones, fondos, barras ni texto.
- **Don't** pintar textos, cifras o etiquetas de un gráfico con el color de la serie, ni mezclar varias series de color en un gráfico.
- **Don't** dar sombra a un panel en reposo.

- **Escala de texto (26/11/2026):** `texto` #1a1a2e (17:1) para títulos y datos clave, `texto-2` #59627a (6,1:1) para subtítulos y datos secundarios, `texto-3` #646c80 (5,2:1) para códigos, etiquetas y ayudas. Antes `texto-2` era #434b5e y casi no se distinguía del principal. Vive en el sistema compartido (`iqc-flow-diseno/css/tokens.css`), así que también aplica al portal de consultas en su próxima compilación.
- **Temática de temporada (26/11/2026):** el fondo va detrás del contenido sin darle `z-index` a `#contenido` (si se le da, los cajones quedan por debajo de la cabecera); no se dibujan íconos en la zona del título (primera fila, columnas de la izquierda); el saludo es una sola frase: «Buenos días, Ana. ¡Feliz Navidad!».
- **Ficha de un certificado (26/11/2026):** las acciones del PDF (regenerar con la plantilla, reemplazar por otro) van en una fila de botones secundarios, sin recuadro; al elegir un PDF nuevo aparecen «Cancelar» y «Subir este PDF» y se revisa en el visor de la derecha. Las normas de los personales se muestran completas («ISO 27701:2025»).
- **Formularios con validación propia (26/11/2026):** `noValidate` y `components/ui/Validacion.tsx`. El título de cada sección va en azul IQC y los obligatorios llevan un asterisco rojo pegado a la etiqueta (sin leyenda); los opcionales lo dicen en la etiqueta («Correo (opcional)»). Al enviar, cada campo vacío (también con solo espacios) o mal escrito se marca en rojo con su mensaje debajo, se enfoca el primero y los mensajes se actualizan en vivo.
- **Limpieza al escribir (certificados personales, `features/personal/sanitizar.ts`):** número de documento según la regla que envía la API (`patron`: solo dígitos y con su largo máximo para DNI, CE y cédulas); nombres en formato título al salir del campo, como todos los guardados («Diego de la Cruz Huarancca»), solo letras, de 5 a 120 caracteres y al menos nombre y apellido; teléfono solo con dígitos y + ( ) -, de 6 a 15 dígitos; código en mayúsculas, sin espacios, y aviso si ya existe; norma de referencia elegida de la lista del catálogo (más «Otra norma…», que agrega «ISO» si falta); año de la norma solo con 4 dígitos.
- **Filtros de lista:** la búsqueda a la izquierda y los filtros alineados a la derecha.
- **Ficha desde una lista:** se abre al instante con los datos de la fila y se actualiza en segundo plano; nunca un cajón vacío con «Cargando…».
- **Temporada en cajones:** el fondo de íconos también va detrás de los formularios, más tenue (14 %), fijo mientras el formulario se desplaza.
- **Certificados empresariales (26/11/2026):** el registro usa la misma validación propia: titular obligatorio (marcado en el buscador), norma, edición (solo el año en la lista) y código en mayúsculas por línea, con aviso si el código ya existe con esa norma (unicidad código + norma, ADR-039) o se repite en el formulario; subdivisión y clase solo números y comas; procesos «NA» o numerales; alcance obligatorio (al menos 10 caracteres); fechas en orden (si fallan las escondidas, su bloque se abre solo); motivo obligatorio. La ficha se abre con los datos de la fila; la marca y la versión nueva del PDF son formularios sin recuadro (`.form-ficha`).
