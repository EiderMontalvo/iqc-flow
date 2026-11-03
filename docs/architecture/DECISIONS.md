# Decisiones iniciales

- ADR-001: Monolito modular en Java 21 + Spring Boot 4.1.2.
- ADR-002: PostgreSQL exclusivo de IQC Flow; MariaDB actual no se toca.
- ADR-003: Mantener `consultas.iqcperu.com` y rutas QR históricas cuando llegue la fase de migración.
- ADR-004: La IA no ejecutará SQL arbitrario ni acciones oficiales sin autorización.
- ADR-005: Los PDFs existentes permanecen en GCS hasta que se diseñe una migración verificada.
- ADR-006: Los límites de módulos se implementarán con paquetes y casos de uso; no hay microservicios en esta etapa.

- ADR-007: Nombre comercial IQC Flow; el módulo interno `ai` no modifica la marca.
- ADR-008: Desarrollo en Windows 10 con PostgreSQL nativo; sin Docker ni Compose.
- ADR-009: Nomenclatura en español para módulos Java (`identidad`, `clientes`, `seguimientos`, `automatizaciones`, `compartido`…), clases, tablas, columnas y endpoints. Se mantienen en inglés las capas internas (`api`, `application`, `domain`, `infrastructure`), los nombres impuestos por los frameworks y las carpetas `iqc-flow-api` e `iqc-flow-web`. El módulo técnico `ai` pasa a llamarse `ia`.
- ADR-010: Autenticación por sesión de servidor (cookie `IQCFLOWSESSION`, HttpOnly, SameSite=Lax) con token CSRF en cookie `XSRF-TOKEN` y cabecera `X-XSRF-TOKEN`, en lugar de JWT. Permite revocar sesiones al instante y no expone tokens a JavaScript. Las sesiones viven en memoria; antes de producción se evaluará persistirlas (Spring Session JDBC).
- ADR-011: Los permisos se recalculan desde la base de datos en cada solicitud autenticada. Un usuario deshabilitado pierde la sesión de inmediato y los cambios de rol aplican sin volver a iniciar sesión. Costo: una consulta por clave primaria por solicitud.
- ADR-012: La bitácora es de solo inserción, garantizado por un trigger de PostgreSQL. Las denegaciones y los fallos se registran en una transacción independiente para que no se pierdan si la operación se revierte.
- ADR-013: Pruebas de integración contra PostgreSQL real (`iqc_flow_test`) con Maven Failsafe (`*IT`), sin Testcontainers porque requiere Docker. Los límites entre módulos se verifican con Spring Modulith en una prueba unitaria.
- ADR-014: El rol de máximo privilegio se llama `MASTER`. El primer usuario `MASTER` se crea con el arranque inicial `CreacionMasterInicial` (variables `IQC_MASTER_*`); no hay credenciales por defecto. El frontend no usará Bootstrap CSS.
- ADR-015: Los identificadores son UUID v7 generados por Hibernate, y el correo de los usuarios es único en todo el sistema y se guarda en minúsculas.
- ADR-016 (D7): Con `clientes:leer` se ven todos los clientes de la organización; se edita un cliente solo si se es su responsable o se tiene `clientes:escribir-todos`. Así se evita duplicar prospectos y se mantiene la propiedad de cada cartera.
- ADR-017: Prospecto y cliente son la misma entidad con distinto estado. Un cliente `ACTIVO` exige documento fiscal, que es único entre los clientes vigentes de la organización. Los clientes se archivan en lugar de eliminarse.
- ADR-018: Los datos personales de los contactos (correo y teléfono) se guardan enmascarados en la bitácora inmutable, para poder atender una solicitud de supresión (Ley 29733) sin alterar el historial.
- ADR-019 (D8): La importación de los clientes de Laravel se hará más adelante, desde un volcado y nunca contra producción. La tabla `clientes` ya tiene `fuente_legado` e `id_legado` para vincularlos.
- ADR-020: Los DTO de solicitud usan tipos envoltorio (`Boolean`, `Long`) en lugar de primitivos, porque Jackson 3 (Spring Boot 4) rechaza un primitivo ausente en el JSON.
- ADR-021: El módulo `identidad` expone `DirectorioUsuarios` como API pública para otros módulos; `clientes` lo usa para validar responsables sin depender de los internos de identidad.
