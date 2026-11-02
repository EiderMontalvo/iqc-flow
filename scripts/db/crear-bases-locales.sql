-- Crea el usuario y las bases locales de IQC Flow (desarrollo y pruebas).
-- SOLO para PostgreSQL local. Nunca ejecutar contra servidores compartidos ni producción.
--
-- Uso (PowerShell, desde la raíz del repositorio):
--   & "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -U postgres -d postgres `
--     -v clave="CLAVE_PARA_IQC_DEV" -f scripts\db\crear-bases-locales.sql
--
-- Es idempotente: si el usuario o las bases ya existen, no los vuelve a crear.

\set ON_ERROR_STOP on

SELECT format('CREATE ROLE iqc_dev LOGIN PASSWORD %L', :'clave')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'iqc_dev')
\gexec

SELECT 'CREATE DATABASE iqc_flow OWNER iqc_dev ENCODING ''UTF8'''
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'iqc_flow')
\gexec

SELECT 'CREATE DATABASE iqc_flow_test OWNER iqc_dev ENCODING ''UTF8'''
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'iqc_flow_test')
\gexec

REVOKE CONNECT ON DATABASE iqc_flow FROM PUBLIC;
REVOKE CONNECT ON DATABASE iqc_flow_test FROM PUBLIC;
