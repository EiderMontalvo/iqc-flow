---
version: 1
slug: "src-main-tsx"
primary_target: "src/main.tsx"
related_targets: ["src/app/App.tsx"]
---

# Panel IQC Flow (app shell y todas las pantallas)

Mode: Operate. Personal interno de IQC en escritorio de oficina; móvil para consultas rápidas.
Tarea: encontrar, registrar y vigilar certificados, clientes y seguimientos; ver qué vence y qué falló; cada rol entra a su inicio con gráficos de su área.

## Direction contract

THESIS: El panel es la parte interna del mismo sistema visual que el portal de consultas: el negro de la marca IQC enmarca el trabajo y el contenido se lee como el documento oficial (papel, tinta, sello). Rechaza el dashboard genérico de tarjetas grises con un acento suelto y el carril azul marino del panel anterior.
OWN-WORLD: Sistema compartido iqc-flow-diseno. Carril y acceso en negro #000 con el símbolo IQC trazado en línea de degradado (#d90435 → #9002ae → #6701e0 → #0019d2), degradado solo en líneas. Área de trabajo en papel #f2f4f7 con superficies blancas y tinta #1a1a2e (claro) o superficies grafito (oscuro). Acción azul IQC #1a33e0/#0019d2. Estados con las tintas del sello (ok, alerta, mal, neutro). Public Sans en todo; Source Serif 4 600 solo para el nombre del titular. Cifras tabulares. Toast oscuro para errores.
STORY: Cada rol entra a su espacio, ve en sus gráficos lo que vence, lo que se emitió y lo que tiene pendiente, filtra o abre el detalle y actúa sin perder el contexto.
FIRST VIEWPORT: Carril negro a la izquierda (logo, espacio del rol, navegación, cuenta); cabecera con título y acción principal; fila de cifras clave; gráficos del rol (vencimientos por mes, emisiones, seguimientos o cartera según el espacio) y la lista de lo urgente.
FORM: seed: none (pinned) — «tenemos que ir avanzando con el mismo diseño y estilos en el panel web de administración», «los estilos deben ser como un sistema completo», «pon gráficos en cada rol».
FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
