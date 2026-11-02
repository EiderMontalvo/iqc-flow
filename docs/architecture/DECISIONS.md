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
