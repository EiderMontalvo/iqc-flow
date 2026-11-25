# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Público general: cualquier persona que necesita comprobar que un certificado emitido por IQC es auténtico y está
vigente (clientes de las empresas certificadas, entidades, licitaciones, auditores, empleadores) y los propios titulares
(empresas y personas) que buscan su certificado o su PDF. Confirmado por el usuario el 21/11/2026: «es público».

Llegan por dos vías en proporción similar (confirmado): escaneando el QR impreso en el certificado, casi siempre desde
el celular, o escribiendo el documento del titular y el código del certificado, a menudo en computadora.

## Product Purpose

Portal oficial de validación de certificados de IQC. Responde una sola pregunta con autoridad: «¿este certificado es
real y sigue vigente?», y entrega el PDF oficial cuando corresponde. Hay dos compilaciones del mismo portal:
IQC Perú (consultas.iqcperu.com) e IQC LATAM (consultas.iqc-latam.com, Ecuador, Colombia y Panamá).

Éxito: en segundos el visitante sabe si el certificado es válido, de quién es, qué norma cubre y hasta cuándo, sin dudas
sobre la autenticidad de la página.

## Positioning

La única fuente oficial: los datos vienen del registro de certificados de IQC, publicado desde IQC Flow. Cubre
certificados de sistemas de gestión (empresariales, acreditados por INACAL, SAE o IQC Global) y certificados personales
(cursos de formación) en un mismo lugar.

## Operating Context

- Entrada por QR: la URL es `/<identificador>`; los QR ya impresos no pueden romperse. Los QR antiguos de certificados
  LATAM impresos con la dirección de Perú se reenvían al portal LATAM.
- Entrada manual: documento del titular (RUC, DNI, cédula u otro) + código del certificado, tal como figuran en él.
- Situaciones de un certificado empresarial: vigente, vencido, sin fecha de expiración, suspendido, retirado, anulado,
  reemplazado. Personal: emitido o anulado.
- Los PDF de certificados personales ya impresos usan la ruta `/personal-pdf/<token>`.
- Límite de consultas por minuto (429) por protección.

## Capabilities and Constraints

- React + TypeScript + Vite, sitio estático alojado en BanaHosting (Apache, `.htaccess`); la API está en
  api.iqcperu.com. Sin sesión ni cookies.
- La consulta no requiere elegir tipo: el backend reconoce si el código es empresarial o personal.
- Idioma: español.

## Brand Commitments

- Colores IQC obligatorios: `#1a1a2e`, `#ffffff`, `#00192d`.
- Colores de la web iqcperu.com (indicados por el usuario el 21/11/2026 con una captura): cabecera negra `#000000`, azul
  IQC `#0019d2` y el degradado del logo `#d90435` → `#9002ae` → `#6701e0` → `#0019d2`.
- La portada de consulta no debe tener desplazamiento: todo el acceso cabe en una pantalla (pedido del 21/11/2026; el pie
  de página se retiró el 22/11/2026 y el enlace a la web pasó a la barra superior).
- Debe quedar claro que también se validan certificados personales, no solo de empresa.
- Logo IQC (`public/logo-iqc-horizontal.png`, blanco) y el símbolo IQC trazado en SVG como fondo de la cabecera
  (elegido el 22/11/2026 en lugar de fotografías).
- Marca por portal: «IQC Perú» o «IQC LATAM (Ecuador · Colombia · Panamá)»; cada certificado indica la marca que lo emitió
  (IQC Perú, IQC Ecuador, IQC Colombia, IQC Panamá).
- Del portal anterior no hay nada obligatorio que conservar (confirmado 21/11/2026).

## Evidence on Hand

- Datos reales en la base pública (≈ 640 certificados empresariales y ≈ 23 personales). No inventar cifras,
  clientes, sellos ni acreditaciones que no existan.

## Product Principles

1. La respuesta primero: vigente o no, visible antes que cualquier otro dato.
2. Autoridad sin adorno: debe parecer un registro oficial, no publicidad.
3. Funciona igual desde un QR en el celular que desde el formulario en computadora.
4. Nunca revelar datos sin documento y código correctos.

## Accessibility & Inclusion

Público amplio y no técnico; contraste AA, textos claros, funcionamiento completo en móvil.
