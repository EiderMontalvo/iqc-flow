# IQC Flow — proyecto inicial (Windows 10, sin Docker)

Proyecto base de monolito modular: Java 21, Spring Boot, React/TypeScript, PostgreSQL local y Flyway.

**Estado:** estructura inicial. No incluye autenticación real, carga de certificados ni integración con producción.
No se ha verificado todavía una compilación completa en esta sesión.

## Requisitos en Windows 10

- JDK 21 (`java -version`)
- Maven 3.9+ (`mvn -version`)
- Node.js y npm (`node -v`, `npm -v`)
- PostgreSQL instalado como servicio en Windows (`localhost:5432`)

## 1. Preparar la base local

En SQL Shell (psql), conectado como `postgres`, ejecutar:

```sql
CREATE ROLE iqc_dev LOGIN PASSWORD 'CAMBIAR_POR_CLAVE_LOCAL_SEGURA';
CREATE DATABASE iqc_flow OWNER iqc_dev;
```

Si el usuario o la base ya existen, no ejecutes esos CREATE nuevamente.
Este procedimiento se aplica **solo en PostgreSQL local**, nunca en MariaDB de producción.

## 2. Iniciar el backend

Abrir PowerShell desde la raíz del proyecto:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/iqc_flow"
$env:DB_USERNAME = "iqc_dev"
$env:DB_PASSWORD = "CAMBIAR_POR_CLAVE_LOCAL_SEGURA"
cd iqc-flow-api
mvn spring-boot:run
```

Spring Boot inicia en `http://localhost:8080`.
Flyway crea las tablas iniciales y Hibernate las valida.

Comprobar desde otra consola:
```powershell
Invoke-RestMethod http://localhost:8080/api/v1/publico/estado
```

## 3. Iniciar el frontend

En otro PowerShell, desde la raíz:
```powershell
cd iqc-flow-web
npm install
npm run dev
```

Abrir `http://localhost:5173`; pulsar «Comprobar conexión».

## Seguridad y próximos pasos

El backend solo permite el endpoint de estado y `/actuator/health` sin autenticación.
Los demás endpoints quedan denegados por ahora. No se crean usuarios ni contraseñas predeterminados.
La base MariaDB existente y el bucket de Google Cloud Storage permanecen intactos.

Próxima fase: identidad y permisos, entidades de clientes y certificados, tests y API documentada.
El módulo técnico `ia` se conserva para integrar la inteligencia artificial más adelante;
el nombre del producto es exclusivamente **IQC Flow**.
