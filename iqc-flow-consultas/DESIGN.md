---
name: Consulta oficial de certificados IQC
description: Portal público de validación de certificados IQC. Cabecera negra con el símbolo IQC en línea de degradado; resultado como documento oficial sobre papel.
colors:
  negro: "#000000"
  azul: "#0019d2"
  azul-hover: "#0013a3"
  azul-vivo: "#1a33e0"
  azul-vivo-hover: "#1428bd"
  degradado-rojo: "#d90435"
  degradado-magenta: "#9002ae"
  degradado-violeta: "#6701e0"
  degradado-azul: "#0019d2"
  navy: "#00192d"
  tinta: "#1a1a2e"
  blanco: "#ffffff"
  papel: "#f2f4f7"
  linea: "#d5dbe4"
  linea-suave: "#e6eaf0"
  texto-2: "#434b5e"
  etiqueta: "#59627a"
  foco-banda: "#8f9bff"
  cabecera-bajada: "#b8bfcd"
  cabecera-antetitulo: "#c4c9d6"
  cabecera-etiqueta: "#e6e9f0"
  cabecera-enlace: "#d5d9e2"
  cabecera-marcador: "#8a92a5"
  campo-fondo: "rgb(8 10 14 / .72)"
  toast-fondo: "rgb(18 19 26 / .96)"
  toast-texto: "#e6e8ee"
  toast-detalle: "#aab1c0"
  toast-icono: "#ff7a8a"
  luz: "#c9b6ff"
  luz-resplandor: "#8f6bff"
  seleccion: "#cdd3ff"
  mal-borde: "#efc3bf"
  ok: "#0d6b44"
  ok-fondo: "#e8f4ee"
  mal: "#b42318"
  mal-fondo: "#fbeceb"
  alerta: "#9a5800"
  alerta-fondo: "#fbf1e1"
  neutro: "#4a5568"
  neutro-fondo: "#eef1f5"
typography:
  display:
    fontFamily: "Public Sans, system-ui, -apple-system, Segoe UI, sans-serif"
    fontSize: "clamp(2.1rem, 1.3rem + 3vw, 3.4rem)"
    fontWeight: 700
    lineHeight: 1.05
    letterSpacing: "-0.025em"
  antetitulo:
    fontFamily: "Public Sans, system-ui, sans-serif"
    fontSize: "13px"
    fontWeight: 700
    letterSpacing: "0.14em"
  headline:
    fontFamily: "Source Serif 4, Georgia, serif"
    fontSize: "clamp(1.45rem, 1.1rem + 1.3vw, 2rem)"
    fontWeight: 600
    lineHeight: 1.18
    letterSpacing: "-0.01em"
  veredicto:
    fontFamily: "Public Sans, system-ui, sans-serif"
    fontSize: "1.25rem"
    fontWeight: 700
    letterSpacing: "0.06em"
  display-compacto:
    fontFamily: "Public Sans, system-ui, sans-serif"
    fontSize: "clamp(1.75rem, 1.2rem + 2.2vw, 2.6rem)"
    fontWeight: 700
    lineHeight: 1.12
  display-bajo:
    fontFamily: "Public Sans, system-ui, sans-serif"
    fontSize: "clamp(2rem, 1.4rem + 2vw, 2.75rem)"
    fontWeight: 700
  display-movil-bajo:
    fontFamily: "Public Sans, system-ui, sans-serif"
    fontSize: "1.8rem"
    fontWeight: 700
  antetitulo-movil:
    fontFamily: "Public Sans, system-ui, sans-serif"
    fontSize: "11px"
    fontWeight: 700
  lead-bajo:
    fontFamily: "Public Sans, system-ui, sans-serif"
    fontSize: "1.0625rem"
    fontWeight: 400
  label-campo:
    fontFamily: "Public Sans, system-ui, sans-serif"
    fontSize: "14px"
    fontWeight: 600
  toast:
    fontFamily: "Public Sans, system-ui, sans-serif"
    fontSize: ".875rem"
    fontWeight: 400
    lineHeight: 1.45
  lead:
    fontFamily: "Public Sans, system-ui, sans-serif"
    fontSize: "1.125rem"
    fontWeight: 400
    lineHeight: 1.55
  body:
    fontFamily: "Public Sans, system-ui, -apple-system, Segoe UI, sans-serif"
    fontSize: "16px"
    fontWeight: 400
    lineHeight: 1.55
  dato:
    fontFamily: "Public Sans, system-ui, sans-serif"
    fontSize: "16px"
    fontWeight: 500
    lineHeight: 1.55
  label:
    fontFamily: "Public Sans, system-ui, sans-serif"
    fontSize: "13px"
    fontWeight: 600
  codigo:
    fontFamily: "Public Sans, system-ui, sans-serif"
    fontWeight: 600
    letterSpacing: "0.02em"
    fontFeature: "\"tnum\" 1, \"zero\" 1"
rounded:
  trazo: "3px"
  hoja: "4px"
  control: "6px"
  toast: "8px"
  sello: "50%"
spacing:
  "1": "4px"
  "2": "8px"
  "3": "12px"
  "4": "16px"
  "5": "20px"
  "6": "24px"
  "7": "28px"
  "8": "32px"
  "10": "40px"
  "12": "48px"
  "16": "64px"
components:
  button-verificar:
    backgroundColor: "{colors.azul-vivo}"
    textColor: "{colors.blanco}"
    rounded: "{rounded.control}"
    padding: "0 28px"
    height: "52px"
  button-verificar-hover:
    backgroundColor: "{colors.azul-vivo-hover}"
    textColor: "{colors.blanco}"
  button-primary:
    backgroundColor: "{colors.azul}"
    textColor: "{colors.blanco}"
    rounded: "{rounded.control}"
    padding: "0 20px"
    height: "48px"
  button-primary-hover:
    backgroundColor: "{colors.azul-hover}"
    textColor: "{colors.blanco}"
  button-secondary:
    backgroundColor: "{colors.blanco}"
    textColor: "{colors.tinta}"
    rounded: "{rounded.control}"
    padding: "0 20px"
    height: "48px"
  button-nueva:
    backgroundColor: "transparent"
    textColor: "{colors.blanco}"
    rounded: "{rounded.control}"
    padding: "0 20px"
    height: "48px"
  input-buscador:
    backgroundColor: "{colors.campo-fondo}"
    textColor: "{colors.blanco}"
    rounded: "{rounded.control}"
    padding: "0 16px"
    height: "52px"
  banda:
    backgroundColor: "{colors.negro}"
    textColor: "{colors.blanco}"
    padding: "20px 24px 48px"
  toast:
    backgroundColor: "{colors.toast-fondo}"
    textColor: "{colors.toast-texto}"
    rounded: "{rounded.toast}"
    padding: "12px 10px 12px 14px"
  hoja-resultado:
    backgroundColor: "{colors.blanco}"
    textColor: "{colors.tinta}"
    rounded: "{rounded.hoja}"
    padding: "32px"
  sello:
    textColor: "{colors.ok}"
    rounded: "{rounded.sello}"
    size: "120px"
  aviso:
    backgroundColor: "{colors.mal-fondo}"
    textColor: "{colors.mal}"
    rounded: "{rounded.control}"
    padding: "16px 18px"
---

# Design System: Consulta oficial de certificados IQC

## Overview

**Creative North Star: "El símbolo y el sello"**

El portal tiene dos mundos que se tocan en una línea:

- **Arriba, la cabecera de la marca:** negra como la web iqcperu.com, con el símbolo de IQC dibujado en grande, en línea fina con el degradado del logo.
- **Abajo, el documento:** el resultado aparece como una hoja oficial sobre papel claro, con un sello estampado en la tinta de su estado.

Pasan de uno a otro sin franjas ni filetes: la franja de microtexto en carrusel (parecía publicidad) y luego el filete de degradado al pie de la cabecera se retiraron el 23/11/2026.

La portada ocupa una sola pantalla, sin desplazamiento y sin tarjeta: antetítulo, título, bajada y formulario van directamente sobre el negro. Al verificar, la misma cabecera se compacta en una barra y el certificado pasa a ser lo principal.

La autoridad sale de la marca y del documento, no de adornos: ni fotos de stock, ni efectos de luz difusa, ni tarjetas flotantes. El movimiento es poco y con intención:

- el símbolo se dibuja una vez al cargar;
- una luz tenue recorre su anillo;
- el contenido entra en secuencia;
- el sello se estampa al llegar el resultado.

**Key Characteristics:**
- Cabecera negra a pantalla completa con el símbolo IQC (SVG trazado del logo): relleno al 7 % y contorno de degradado.
- Antetítulo en versalitas con un filete sólido de 32 × 3 px en el azul IQC vivo (#1a33e0, el del botón «Verificar»): «Certificados empresariales y personales». El filete separa el antetítulo de la bajada; sin él se leía como texto corrido.
- Formulario único (RUC o DNI + código) sobre el negro, con campos oscuros translúcidos y el botón en azul IQC vivo.
- Toast oscuro en la esquina para los errores; nunca un bloque claro sobre la cabecera.
- Hoja de resultado con marco doble y sello SVG estampado.
- Public Sans para la interfaz; Source Serif 4 600 solo para el nombre del titular.

## Colors

La paleta se reparte en tres familias:

- **Marca sobre negro:** negro, azul IQC y el degradado del logo.
- **Documento:** papel, líneas y tinta.
- **Estado:** cuatro tintas de sello.

### Primary
- **Negro** (negro): fondo de la cabecera y del cuerpo de la página en la portada. Viene de la cabecera de iqcperu.com.
- **Azul IQC vivo** (azul-vivo): botón «Verificar». Es el único relleno de color sólido sobre la cabecera. En hover pasa a azul-vivo-hover.
- **Azul IQC** (azul): acción principal de la hoja («Ver certificado en PDF»), enlaces y foco sobre papel.
- **Degradado del logo** (rojo → magenta → violeta → azul): solo el contorno del símbolo del fondo (el filete del antetítulo pasó a azul sólido el 23/11/2026). Solo en líneas, nunca como relleno de superficies.

### Secondary (tintas de sello)
- **Verde sello** (ok) sobre ok-fondo: vigente o personal válido.
- **Rojo sello** (mal) sobre mal-fondo: vencido, retirado o anulado.
- **Ámbar sello** (alerta) sobre alerta-fondo: suspendido.
- **Pizarra sello** (neutro) sobre neutro-fondo: reemplazado, sin fecha o estado desconocido.

### Neutral
- **Tinta IQC** (tinta) y **Blanco** (blanco): texto sobre papel y texto sobre negro. Son colores obligatorios de la marca.
- **Navy IQC** (navy): filete de la cabecera en la versión impresa.
- **Papel, línea, línea suave, texto-2, etiqueta:** la zona de resultados.
- **Tonos de cabecera:** textos atenuados sobre negro, todos con contraste AA o superior.
  - cabecera-bajada: la bajada;
  - cabecera-antetitulo: el antetítulo;
  - cabecera-etiqueta: las etiquetas de los campos;
  - cabecera-enlace: el enlace a la web;
  - cabecera-marcador: el texto de ejemplo de los campos.

### Named Rules
**La Regla del Degradado en Línea.** El degradado del logo solo aparece como línea: contorno, filete o borde. Rellenar fondos, botones o textos con él lo convierte en decoración publicitaria.

**La Regla de la Tinta de Sello.** Los colores de estado solo tiñen el sello y el veredicto. El estado se estampa, no se pinta.

**La Regla del Negro sin Tarjeta.** Sobre la cabecera no hay superficies claras ni tarjetas. Los avisos de error van en un toast oscuro.

## Typography

**Display Font:** Public Sans (con system-ui, -apple-system, Segoe UI, sans-serif)
**Body Font:** Public Sans
**Headline Font:** Source Serif 4, peso 600 únicamente (con Georgia, serif)

Ambas fuentes se sirven desde el propio sitio en woff2, con subconjuntos latín y latín extendido.

### Hierarchy
- **Display** (700; clamp(2.1rem, 1.3rem + 3vw, 3.4rem); interlineado 1.05; −0.025em): «Validación de certificados». Máximo 16ch en pantallas altas y una sola línea en laptops bajas.
- **Antetítulo** (700; 13px; 0.14em; mayúsculas): en el celular baja a 11px y 0.05em para caber en una línea.
- **Lead** (400; 1.125rem): la bajada, máximo 52ch. Se oculta en pantallas muy bajas para que no aparezca desplazamiento.
- **Headline** (Source Serif 4 600): nombre del titular en la hoja.
- **Veredicto** (700; 1.25rem; 0.06em; mayúsculas): la palabra de estado en la tinta del sello.
- **Label** (600; 13–14px), **Dato** (500; 16px), **Código** (600; cifras tabulares y cero con barra).

### Named Rules
**La Regla de la Firma Única.** Source Serif 4 se usa solo para el nombre del titular.

**La Regla de la Cifra Tabular.** Los códigos y documentos van en cifras tabulares, para cotejarlos carácter por carácter con el papel.

## Layout

Columna de 1040 px de ancho máximo, con márgenes laterales de 24 px (16 px en el celular).

- **Portada** (`.banda.inicio`):
  - ocupa toda la altura disponible;
  - la barra (logo de 92 px y enlace a la web) va arriba;
  - el bloque de título y formulario queda centrado en vertical, con 880 px de ancho máximo.
- **Símbolo:**
  - en pantallas anchas va centrado al 56 % del ancho, con 118 % de la altura de la cabecera, detrás del contenido;
  - en el celular va desplazado a la derecha, al 92 % de altura;
  - en la barra compacta solo asoma una parte, a la derecha.
- **Formulario:**
  - en pantallas anchas, tres columnas (documento 1fr, código 1.35fr, botón);
  - por debajo de 860 px, dos columnas con el botón abajo;
  - por debajo de 560 px, una columna.
- **Sin desplazamiento:** hay ajustes por altura (`max-height` 760, 720 y 560 px) que reducen el logo, los márgenes y la bajada. La lista de tamaños verificados está en el README.
- **Hoja:** cabecera con el sello y el texto, y datos en una grilla de 4 columnas (2 en tableta y celular).
- **Toast:**
  - en pantallas anchas, fijo arriba a la derecha, con 380 px de ancho máximo;
  - en el celular, abajo, de borde a borde.

## Elevation & Depth

La cabecera es plana: su profundidad viene del símbolo y de un resplandor violeta muy tenue (16 %) detrás de él.

Solo dos objetos proyectan sombra:
- **Hoja apoyada** (`0 1px 2px rgb(0 25 45 / .06), 0 12px 32px -12px rgb(0 25 45 / .22)`): la hoja de resultado.
- **Toast** (`0 12px 32px -8px rgb(0 0 0 / .7)`, con un desenfoque de 8 px del fondo): flota sobre la cabecera.

## Shapes

Esquinas casi rectas:
- 4 px en la hoja;
- 6 px en campos, botones y avisos;
- 8 px en el toast.

El círculo es exclusivo del sello. El símbolo IQC conserva sus ángulos de 45° y sus esquinas en inglete.

## Components

### Símbolo IQC (componente firma)
SVG trazado del logo, en `componentes/estructura/Fondo.tsx`. Está formado por tres piezas: el anillo octogonal abierto, la pieza de cierre y el marcador central.

- **Capas:**
  - relleno con el degradado al 7 %;
  - contorno de 0,55 unidades con el degradado;
  - una línea de luz (#c9b6ff con resplandor) que recorre el anillo.
- **Movimiento en la portada:**
  - el contorno se dibuja una vez en 2,4 s;
  - el relleno aparece después;
  - la luz da una vuelta cada 11 s.
- **En la barra compacta:** queda quieto.
- **Con «reducir movimiento»:** todo queda quieto y la luz no se muestra.

### Botones
- **Verificar:** azul vivo, 52 px de alto y texto 700. Al presionarlo muestra un indicador giratorio y «Consultando…».
- **Primario de la hoja:** azul IQC («Ver certificado en PDF»). Hay uno solo por hoja.
- **Secundario:** blanco con borde («Imprimir constancia»).
- **Nueva consulta:** transparente con borde blanco al 45 %, sobre la cabecera.
- **Transiciones:** 180 ms; al presionar el botón baja 1 px.
- **Foco:** contorno de 2 px. Sobre negro se usa foco-banda.

### Campos
- **Forma:** 52 px de alto, fondo casi negro translúcido y borde blanco al 18 %. El ejemplo de relleno usa cabecera-marcador.
- **Foco:** borde foco-banda con un anillo de 3 px al 25 %.
- **Validación:** la del navegador, con mensajes en español.

### Antetítulo
Filete sólido de 32 × 3 px (16 px en el celular) en azul IQC vivo, seguido de «CERTIFICADOS EMPRESARIALES Y PERSONALES». Es lo primero que se lee en la portada y existe para que nadie piense que el portal es solo para empresas. Historial del 23/11/2026: el filete de degradado se cambió a azul sólido a pedido del usuario; sin filete se leía como texto corrido; las cápsulas y la frase en azul se descartaron.

### Toast
Fondo oscuro casi opaco, borde blanco al 10 % e ícono de alerta en toast-icono.

- **Contenido:** título en blanco 600 («Certificado no encontrado») y detalle debajo en toast-detalle.
- **Cierre:** con la X o solo a los 8 s.
- **Accesibilidad:** `role="status"`.

### Hoja de resultado y sello
- **Hoja:** marco doble (borde de 1 px y un `outline` hacia adentro a 8 px).
- **Sello:** SVG de 120 px con anillo de texto, roseta de seguridad e ícono, girado −4°. Se estampa en 620 ms al aparecer.
- **PDF:** se embebe solo desde 900 px de ancho.
- **Constancia:** «Consultado el … en el registro oficial…».

### Aviso
Bloque claro en tonos de rojo, usado solo dentro de la zona de resultados (por ejemplo, un QR inexistente). Nunca va sobre la cabecera.

### Sin franjas
La cabecera termina en el negro, sin franja de microtexto ni filete de degradado al pie: el usuario los retiró el 23/11/2026 (parecían publicidad). **No volver a poner franjas ni texto en movimiento.**

## Do's and Don'ts

### Do:
- **Do** mantener la portada en una sola pantalla y volver a medir al cambiar textos o tamaños.
- **Do** decir explícitamente que el portal sirve para certificados empresariales **y** personales.
- **Do** usar el degradado solo en líneas.
- **Do** detener cada animación nueva en `estilos/movimiento.css`, con un selector de igual o mayor especificidad.
- **Do** comunicar el estado con el sello y con la palabra del veredicto: el color nunca va solo.
- **Do** reservar Source Serif 4 para el nombre del titular.

### Don't:
- **Don't** volver a usar fotos de stock ni las imágenes de iqcperu.com como fondo: el usuario las descartó por repetidas y poco serias.
- **Don't** poner una tarjeta o un bloque claro sobre la cabecera, ni para el formulario ni para los errores.
- **Don't** usar efectos tipo aurora, brillos difusos ni redes de partículas: el usuario los descartó por perder seriedad.
- **Don't** mostrar la fecha de anulación de un certificado.
- **Don't** rellenar la hoja, los botones o los fondos con las tintas de estado.
