/**
 *Portal público de consulta de certificados (Incremento 8b, ADR-041/043): lee únicamente las bases públicas de
 *consulta (Perú y LATAM) con usuarios de solo lectura. No conoce la base interna ni otros módulos
 */
@org.springframework.modulith.ApplicationModule(displayName = "Consulta pública")
package pe.com.iqc.flow.consultas;
