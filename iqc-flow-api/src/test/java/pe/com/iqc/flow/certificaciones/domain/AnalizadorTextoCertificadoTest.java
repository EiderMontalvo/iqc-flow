package pe.com.iqc.flow.certificaciones.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import pe.com.iqc.flow.certificaciones.domain.AnalizadorTextoCertificado.Formato;

/**textos FICTICIOS con la estructura de los PDF reales (orden de posición de PDFBox); no contienen datos de clientes*/
class AnalizadorTextoCertificadoTest {

  @Test
  void leeUnCertificadoInacalSae() {
    var r = AnalizadorTextoCertificado.analizar("""
        CERTIFICADO
        En reconocimiento al Sistema de Gestión Antisoborno de:
        EMPRESA FICTICIA DEL SUR
        SOCIEDAD ANONIMA CERRADA - EFS S.A.C.
        AV. LOS EJEMPLOS NRO. 123 LIMA - LIMA - MIRAFLORES
        El cual se encuentra implementado y es conforme con los requisitos de la norma:
        ISO 37001: 2025
        Alcance:
        Diseño y venta de productos de prueba.
        Certificado No.: PE-INA-01220260111A
        Ciclo de certificación Nro. 1
        Fecha efectiva de otorgamiento: 16 de Octubre del 20 26
        Fecha de emisión: 16 de Octubre del 2026
        Auditoría de Seguimiento 1 programada antes de: *15 de Octubre del 2027
        Auditoría de Seguimiento 2 programada antes de: *15 de Octubre del 2028
        Fecha de expiración, la renovación debe ser programada antes de: **15 de Octubre del 2029
        DDC-FRM-015 – V.08
        """);
    assertThat(r.formato()).isEqualTo(Formato.INACAL_SAE);
    var e = r.empresarial();
    assertThat(e.codigo()).isEqualTo("PE-INA-01220260111A");
    assertThat(e.esquema()).isEqualTo("INACAL");
    assertThat(e.titular()).isEqualTo("EMPRESA FICTICIA DEL SUR SOCIEDAD ANONIMA CERRADA - EFS S.A.C.");
    assertThat(e.direccion()).startsWith("AV. LOS EJEMPLOS");
    assertThat(e.normaNombre()).isEqualTo("ISO 37001");
    assertThat(e.edicion()).isEqualTo("2025");
    assertThat(e.alcance()).isEqualTo("Diseño y venta de productos de prueba.");
    assertThat(e.ciclo()).isEqualTo(1);
    assertThat(e.fechaOtorgamiento()).isEqualTo(LocalDate.of(2026, 10, 16));
    assertThat(e.limiteVigilancia2()).isEqualTo(LocalDate.of(2028, 10, 15));
    assertThat(e.fechaExpiracion()).isEqualTo(LocalDate.of(2029, 10, 15));
    assertThat(r.advertencias()).isEmpty();
  }

  @Test
  void leeUnCertificadoDeIndiaConEtiquetasPartidas() {
    var r = AnalizadorTextoCertificado.analizar("""
        INTEGRATED QUALITY CERTIFICATION PRIVATE LIMITED
        hereby certifies that the Quality Management Systems of
        EMPRESA FICTICIA S.A.S.
        CALLE 1 # 2-3, BOGOTÁ D.C., COLOMBIA.
        ha sido evaluado y cumple con los
        Sistemas de Gestión de la Calidad
        ISO 9001:2015
        Alcance: Servicios de prueba.
        Subdivisión                            : 70, 59 Fecha de  emisi ó n   inicial    : 18.11.2026
        Clase                                  : 7000, 5910 Fec ha de 1era vi gilan c i a    : 17.11.2027
        Procesos no aplicables   : 8.3 Fecha de 2da vig i lanci a   : 17.11.2028
        Número de  c e r t i f i c a do : PER/QE/JAS-C0001/0001 Certificado válido hast a    : 17.11.2029
        Anexos    : NA
        QF-12 Rev 01 date 28.10.2025
        """);
    assertThat(r.formato()).isEqualTo(Formato.IQC_GLOBAL);
    var e = r.empresarial();
    assertThat(e.codigo()).isEqualTo("PER/QE/JAS-C0001/0001");
    assertThat(e.esquema()).isEqualTo("IQC_GLOBAL");
    assertThat(e.titular()).isEqualTo("EMPRESA FICTICIA S.A.S.");
    assertThat(e.normaNombre()).isEqualTo("ISO 9001");
    assertThat(e.alcance()).isEqualTo("Servicios de prueba.");
    assertThat(e.fechaEmision()).isEqualTo(LocalDate.of(2026, 11, 18));
    assertThat(e.limiteVigilancia1()).isEqualTo(LocalDate.of(2027, 11, 17));
    assertThat(e.fechaExpiracion()).isEqualTo(LocalDate.of(2029, 11, 17));
    assertThat(e.subdivision()).isEqualTo("70, 59");
    assertThat(e.procesosNoAplicables()).isEqualTo("8.3");
    assertThat(e.anexos()).isEqualTo("NA");
  }

  @Test
  void leeUnCertificadoPersonal() {
    var r = AnalizadorTextoCertificado.analizar("""
        CERTIFICADO
        CURSO DE AUDITOR INTERNO
        Por la presente se certifica que
        ANA PRUEBA FICTICIA
        DNI: 12345678
        ha completado con éxito la capacitación en
        ISO 37001:2016
        Sistema de Gestión Antisoborno
        Curso de interpretación
        DURACIÓN MODALIDAD FECHAS DEL CURSO
        16 horas Virtual 05 al 07 de julio de 2026
        Entrenador: Juan Ejemplo
        Fecha de emisión: 19 de julio de 2026
        Código de verificación: IQC/PE/ABM0001
        """);
    assertThat(r.formato()).isEqualTo(Formato.PERSONAL);
    var p = r.personal();
    assertThat(p.codigo()).isEqualTo("IQC/PE/ABM0001");
    assertThat(p.nombres()).isEqualTo("ANA PRUEBA FICTICIA");
    assertThat(p.tipoDocumento()).isEqualTo("DNI");
    assertThat(p.numeroDocumento()).isEqualTo("12345678");
    assertThat(p.curso()).isEqualTo("CURSO DE AUDITOR INTERNO");
    assertThat(p.normaReferencia()).isEqualTo("ISO 37001");
    assertThat(p.normaAnio()).isEqualTo("2016");
    assertThat(p.subtitulo()).isEqualTo("Sistema de Gestión Antisoborno");
    assertThat(p.duracionHoras()).isEqualByComparingTo(new BigDecimal("16"));
    assertThat(p.modalidad()).isEqualTo("Virtual");
    assertThat(p.fechaInicio()).isEqualTo(LocalDate.of(2026, 7, 5));
    assertThat(p.fechaFin()).isEqualTo(LocalDate.of(2026, 7, 7));
    assertThat(p.fechaEmision()).isEqualTo(LocalDate.of(2026, 7, 19));
    assertThat(p.entrenador()).isEqualTo("Juan Ejemplo");
  }

  @Test
  void leeElDocumentoConOSinDosPuntosSinConfundirloConLaPalabraCertificado() {
    var r = AnalizadorTextoCertificado.analizar("""
        CERTIFICADO
        CURSO DE AUDITOR LIDER
        Por la presente se certifica que
        Ana Prueba Ficticia
        DNI 87654321
        ISO 27701:2025
        Fecha de emisión: 19 de julio de 2026
        Código de verificación: IQC/PE/IIP0001
        """);
    assertThat(r.personal().tipoDocumento()).isEqualTo("DNI");
    assertThat(r.personal().numeroDocumento()).isEqualTo("87654321");
    assertThat(r.advertencias()).noneMatch(a -> a.contains("documento"));
  }

  @Test
  void leeLaPlantillaDeIndiaEnInglesConCodigoIasYFechasPorEtiqueta() {
    var r = AnalizadorTextoCertificado.analizar("""
        hereby certifies that the Quality Management Systems of
        EMPRESA FICTICIA S.A.C.
        AV. SIEMPRE VIVA 123 LIMA
        ha sido evaluado y cumple con los
        ISO 9001:2015
        Alcance: Servicios de prueba.
        Subdivision                            : 59, 70 Current issue date       : 20.08.2025
        Class                                  : 5921 Current expiry date    : 19.08.2028
        Process(es) not applicable   : None 1st Surveillance due    : 19.08.2026
        Certificate number                : PER/QMS/IAS–C0046/1668 2nd  Surveillance due : 19.08.2027
        QF-13A Rev 01 date 06.04.2024
        """);
    var e = r.empresarial();
    assertThat(e.codigo()).isEqualTo("PER/QMS/IAS–C0046/1668");
    assertThat(e.fechaEmision()).isEqualTo(LocalDate.of(2025, 8, 20));
    assertThat(e.limiteVigilancia1()).isEqualTo(LocalDate.of(2026, 8, 19));
    assertThat(e.limiteVigilancia2()).isEqualTo(LocalDate.of(2027, 8, 19));
    assertThat(e.fechaExpiracion()).isEqualTo(LocalDate.of(2028, 8, 19));
  }

  @Test
  void separaEmisionInicialYActualYLeeLaEtiquetaDeLaLineaAnterior() {
    var r = AnalizadorTextoCertificado.analizar("""
        EMPRESA FICTICIA S.A.C.
        ha sido evaluado y cumple con los
        ISO 45001:2018
        Subdivisión : 30 Fecha de emisión inicial : 02.04.2020
        Clase : 3011 Fecha de emisión actual
        : 29.01.2026
        Procesos no aplicables : NA Certificado válido hasta : 01.04.2029
        Número de Certificado : PER/QO/JAS-C0023/RC1/R1/1214
        QF-13A Rev 00 date 06.04.2024
        """);
    var e = r.empresarial();
    assertThat(e.titular()).isEqualTo("EMPRESA FICTICIA S.A.C.");
    assertThat(e.fechaOtorgamiento()).isEqualTo(LocalDate.of(2020, 4, 2));
    assertThat(e.fechaEmision()).isEqualTo(LocalDate.of(2026, 1, 29));
    assertThat(e.fechaExpiracion()).isEqualTo(LocalDate.of(2029, 4, 1));
  }

  @Test
  void convierteElCodigoInacalAntiguoYReconoceLaAmpliacion() {
    var r = AnalizadorTextoCertificado.analizar("""
        En reconocimiento al Sistema de Gestión Antisoborno de:
        EMPRESA X S.A.C.
        El cual se encuentra implementado
        ISO 37001:2016
        Certificado No.: PE02120230121
        Fecha efectiva de otorgamiento: 25 de Julio de l 2023
        Fecha efectiva de ampliación: 04 de Octubre del 2024
        Auditoría de Seguimiento 1 programada antes de: *24 de Julio  del 2024
        Auditoría de Seguimiento 2 programada antes de: *24 de Julio  del 2025
        Fecha de expiración, la renovación debe ser programada antes de: **24 de Julio  del 2026
        Acreditación INACAL
        DDC-FRM-015 – V.08
        """);
    var e = r.empresarial();
    assertThat(e.codigo()).isEqualTo("PE-INA-02120230121");
    assertThat(e.esquema()).isEqualTo("INACAL");
    assertThat(e.fechaOtorgamiento()).isEqualTo(LocalDate.of(2023, 7, 25));
    assertThat(e.fechaEmision()).isEqualTo(LocalDate.of(2024, 10, 4));
    assertThat(e.fechaExpiracion()).isEqualTo(LocalDate.of(2026, 7, 24));
    assertThat(r.advertencias()).anyMatch(a -> a.contains("formato antiguo")).anyMatch(a -> a.contains("ampliación"));
  }

  @Test
  void avisaFechasIncoherentesYFormatoDesconocido() {
    var r = AnalizadorTextoCertificado.analizar("""
        En reconocimiento al Sistema de Gestión de:
        EMPRESA X S.A.C.
        El cual se encuentra implementado
        ISO 9001:2015
        Certificado No.: PE-INA-00120260211
        Fecha efectiva de otorgamiento: 30 de Marzo del 2026
        Fecha de emisión: 29 de Marzo del 2026
        """);
    assertThat(r.advertencias()).anyMatch(a -> a.contains("emisión es anterior"));
    assertThat(r.advertencias()).anyMatch(a -> a.contains("5 fechas"));
    assertThat(AnalizadorTextoCertificado.analizar("Factura electrónica 0001-313").formato()).isEqualTo(Formato.DESCONOCIDO);
  }
}
