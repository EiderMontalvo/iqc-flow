-- Datos que lleva el certificado de IQC Global (QF-12) y que antes no se guardaban: el sector del titular (subdivisión y
-- clase, p. ej. «46» y «4610»; puede haber varias separadas por coma) y, por norma, los procesos no aplicables («8.3» o «NA»)
-- y los anexos. Solo se usan con el esquema IQC_GLOBAL; en los demás certificados quedan nulos.
ALTER TABLE certificados
  ADD COLUMN subdivision           VARCHAR(40),
  ADD COLUMN clase                 VARCHAR(40),
  ADD COLUMN procesos_no_aplicables VARCHAR(60),
  ADD COLUMN anexos                VARCHAR(200),
  ADD CONSTRAINT ck_certificados_subdivision CHECK (subdivision IS NULL OR subdivision ~ '^[0-9]{1,6}([[:space:]]*,[[:space:]]*[0-9]{1,6})*$'),
  ADD CONSTRAINT ck_certificados_clase CHECK (clase IS NULL OR clase ~ '^[0-9]{1,6}([[:space:]]*,[[:space:]]*[0-9]{1,6})*$'),
  ADD CONSTRAINT ck_certificados_procesos CHECK (procesos_no_aplicables IS NULL
    OR procesos_no_aplicables ~ '^(NA|[0-9]+(\.[0-9]+)*([[:space:]]*[,;][[:space:]]*[0-9]+(\.[0-9]+)*)*)$');
