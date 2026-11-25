package pe.com.iqc.flow.certificaciones.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import pe.com.iqc.flow.compartido.domain.EntidadBase;

/**persona que recibe certificados de cursos o competencias; el documento es único por país y tipo*/
@Entity
@Table(name = "personas_certificadas")
public class PersonaCertificada extends EntidadBase {
  @Column(name = "organizacion_id", nullable = false, updatable = false)
  private UUID organizacionId;

  @Column(name = "codigo_pais", nullable = false, length = 2)
  private String codigoPais;

  @Column(name = "tipo_documento", nullable = false, length = 20)
  private String tipoDocumento;

  @Column(name = "numero_documento", nullable = false, length = 20)
  private String numeroDocumento;

  @Column(nullable = false)
  private String nombres;

  private String correo;

  @Column(length = 30)
  private String telefono;

  @Column(name = "fuente_legado", length = 40, updatable = false)
  private String fuenteLegado;

  @Column(name = "id_legado", length = 60, updatable = false)
  private String idLegado;

  protected PersonaCertificada() {
  }

  public PersonaCertificada(UUID organizacionId, DocumentoPersona documento, String nombres, String correo,
      String telefono, String fuenteLegado, String idLegado) {
    this.organizacionId = organizacionId;
    this.codigoPais = documento.pais();
    this.tipoDocumento = documento.tipo();
    this.numeroDocumento = documento.numero();
    this.nombres = nombres;
    this.correo = correo;
    this.telefono = telefono;
    this.fuenteLegado = fuenteLegado;
    this.idLegado = idLegado;
  }

  public void actualizarContacto(String nombres, String correo, String telefono) {
    this.nombres = nombres;
    this.correo = correo;
    this.telefono = telefono;
  }

  public UUID getOrganizacionId() { return organizacionId; }
  public String getCodigoPais() { return codigoPais; }
  public String getTipoDocumento() { return tipoDocumento; }
  public String getNumeroDocumento() { return numeroDocumento; }
  public String getNombres() { return nombres; }
  public String getCorreo() { return correo; }
  public String getTelefono() { return telefono; }
}
