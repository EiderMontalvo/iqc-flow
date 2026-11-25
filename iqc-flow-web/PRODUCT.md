# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Personal interno de IQC Perú (comercial, operaciones / coordinación de auditorías, jefe técnico de certificaciones,
administración y logística, gerencia). Trabajan sobre todo en computadora de escritorio, en la oficina; el móvil es
secundario y sirve para consultas rápidas. Confirmado por el usuario el 20/11/2026.

Su trabajo diario: registrar y consultar certificados, sus QR y PDF; mantener la cartera de clientes; llevar los
seguimientos (tareas, vigilancias, recertificaciones); gestionar expedientes, decisiones y entregas.

## Product Purpose

IQC Flow es la plataforma interna que centraliza el ciclo comercial, operativo y de certificación de IQC Perú y de las
marcas de IQC LATAM (Ecuador, Colombia y, próximamente, Panamá). Reemplaza al panel Laravel de certificados
(admin-22-33-44-55.iqcperu.com) y alimenta las consultas públicas donde los clientes validan sus certificados
(consultas.iqcperu.com y un portal LATAM único).

Éxito: el equipo encuentra cualquier certificado en segundos, registra sin errores, sabe qué vence y qué toca hacer hoy,
y lo publicado en la consulta pública es siempre correcto.

## Positioning

Lleva en un solo lugar lo que hoy vive repartido entre el panel anterior, Excel de control y correo: certificados con su
QR y PDF versionado, el expediente que los respalda (imparcialidad, decisión, firma, entrega) y los seguimientos que
genera cada ciclo, con publicación separada por país.

## Operating Context

- Esquemas de acreditación: INACAL, SAE e IQC Global (en IQC Global decide y emite India).
- Procedimiento DDC-PRO-003 para el código del certificado; normas ISO con edición (ISO 9001:2015, ISO 37001:2016…).
- Los QR impresos en certificados ya emitidos apuntan a consultas.iqcperu.com y no pueden romperse.
- Datos históricos importados del panel anterior (≈ 640 certificados, ≈ 290 clientes).

## Capabilities and Constraints

- Frontend React + TypeScript + Vite. Se alojará en BanaHosting; el backend (Spring Boot) corre en otro servidor
  (Contabo), por lo que la URL de la API debe ser configurable y la sesión funciona con cookie + CSRF entre dominios.
- Permisos por rol: cada pantalla y acción se muestra según los permisos del usuario; la API los vuelve a validar.
- Modos claro, oscuro y «Sistema».
- Idioma: español (Perú).

## Brand Commitments

- Colores de marca IQC, obligatorios: `#1a1a2e`, `#ffffff` y `#00192d` (indicados por el usuario el 20/11/2026). El
  color de acento queda a propuesta del diseño, derivado de esa base.
- Logo: el del panel actual (`logo-iqcperu.png`).
- Cada país tiene su marca (IQC Perú, IQC Ecuador, IQC Colombia, IQC Panamá); el panel es uno solo.

## Evidence on Hand

- Datos reales importados en la base de desarrollo; no inventar cifras ni clientes en la interfaz.

## Product Principles

1. Encontrar antes que navegar: la búsqueda y los filtros son la entrada principal.
2. Lo que vence o falla se ve primero (vigencias, tareas atrasadas, errores de publicación).
3. Nada público sin control: lo que llega a la consulta pública es exactamente lo emitido.
4. Cada cambio deja rastro (motivo y bitácora).
