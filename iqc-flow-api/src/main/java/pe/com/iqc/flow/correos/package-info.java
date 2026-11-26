/**
 *Correo saliente: envía mensajes por SMTP con la cuenta que configura el servidor (iqc.correo.*, variables IQC_CORREO_*).
 *Sin usuario y clave configurados no envía nada y lo dice con un error claro. No depende de otros módulos de negocio
 */
@ApplicationModule(displayName = "Correos")
package pe.com.iqc.flow.correos;

import org.springframework.modulith.ApplicationModule;
