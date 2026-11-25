package pe.com.iqc.flow.documentos.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**configuración del almacén de documentos (iqc.documentos.*)*/
@ConfigurationProperties("iqc.documentos")
public record PropiedadesDocumentos(
    /**carpeta raíz del almacén local; debe estar fuera del repositorio y con respaldo*/
    @DefaultValue("./datos/documentos") String directorio,
    @DefaultValue("15MB") DataSize tamanoMaximo,
    /**LOCAL (disco) o S3 (AWS S3 o compatible). Ver ADR-044*/
    @DefaultValue("LOCAL") String almacen,
    S3 s3) {

  /**bucket y región; endpoint solo para servicios compatibles (R2, Wasabi, MinIO). Sin claves se usa la cadena
   *estándar de AWS (variables AWS_*, perfil o rol de la instancia)*/
  public record S3(String bucket, @DefaultValue("us-east-1") String region, String endpoint,
      @DefaultValue("documentos/") String prefijo, String claveAcceso, String claveSecreta,
      @DefaultValue("false") boolean rutaEstilo) {
  }
}
