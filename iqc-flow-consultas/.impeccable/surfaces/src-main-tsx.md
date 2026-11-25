---
version: 1
slug: "src-main-tsx"
primary_target: "src/main.tsx"
related_targets: ["src/style.css"]
---

# Portal público de consultas

Scope: portal de validación (formulario, resultado por QR, estados de error). Mode: Operate.
Audiencia: público general; mitad llega por QR en celular, mitad por formulario en computadora.
Tarea: saber en segundos si el certificado es auténtico y vigente; abrir el PDF oficial.
Restricciones: colores IQC #1a1a2e / #ffffff / #00192d, logo IQC, QR impresos intactos, sin datos sin documento+código.

## Direction contract

THESIS: La respuesta se lee como un documento de seguridad sellado: el estado del certificado es un sello estampado, no una etiqueta de color. Rechaza el «hero con foto de stock y tarjeta de vidrio» del portal anterior y el formulario gris de trámite.

OWN-WORLD: Papel de seguridad. Banda superior azul IQC #00192d con guilloche de líneas finas (rosetas y ondas SVG trazadas, no gradientes) y una línea de microtexto «IQC · VALIDACIÓN OFICIAL». Fondo papel blanco azulado muy leve; hoja de resultado blanca con marco doble fino. Tinta: navy para texto, verde sello para vigente, rojo sello para vencido/anulado, ámbar para suspendido, pizarra para reemplazado/sin fecha. Public Sans para la interfaz; Source Serif 4 seminegrita solo para el nombre del titular.

STORY: El visitante entiende que es la consulta oficial de IQC, ingresa documento y código (o llega por QR), ve el sello con el estado y la fecha «válido hasta», revisa titular, norma y acreditación, abre o imprime.

FIRST VIEWPORT: Banda navy con guilloche: logo IQC a la izquierda y nombre del portal; dentro de la banda, título «Validación de certificados», una línea de explicación y el formulario (documento, código, Verificar) en una fila en escritorio y apilado en móvil. Debajo, en papel, una guía breve de dónde están los datos en el certificado. Con resultado: la hoja sellada ocupa el centro, sello de 112px a la izquierda del titular.

FORM: Papel de seguridad, pick card (candidato 2 de mi lista), seed:e09fe0ee971b2c09. Firma: sello circular SVG con texto en anillo que se estampa una vez (escala + desenfoque → nítido) al llegar el resultado.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
