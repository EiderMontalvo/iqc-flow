package pe.com.iqc.flow.documentos.infrastructure;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

/**
 *almacén en S3 o compatible (ADR-044): varias instancias de la API comparten los documentos. Los objetos se cifran en
 *reposo y la ruta la genera el sistema; el bucket debe ser privado (los documentos se sirven siempre por la API)
 */
@Component
@ConditionalOnProperty(name = "iqc.documentos.almacen", havingValue = "S3")
class AlmacenArchivosS3 implements AlmacenArchivos, AutoCloseable {
  private final S3Client s3;
  private final String bucket;
  private final String prefijo;
  private final boolean cifrar;

  AlmacenArchivosS3(PropiedadesDocumentos propiedades) {
    PropiedadesDocumentos.S3 c = propiedades.s3();
    if (c == null || c.bucket() == null || c.bucket().isBlank()) {
      throw new IllegalStateException("iqc.documentos.almacen=S3 exige iqc.documentos.s3.bucket (IQC_S3_BUCKET)");
    }
    S3ClientBuilder constructor = S3Client.builder().httpClient(UrlConnectionHttpClient.create())
        .region(Region.of(c.region())).forcePathStyle(c.rutaEstilo());
    if (c.endpoint() != null && !c.endpoint().isBlank()) constructor.endpointOverride(URI.create(c.endpoint()));
    constructor.credentialsProvider(c.claveAcceso() != null && !c.claveAcceso().isBlank()
        ? StaticCredentialsProvider.create(AwsBasicCredentials.create(c.claveAcceso(), c.claveSecreta()))
        : DefaultCredentialsProvider.builder().build());
    this.s3 = constructor.build();
    this.bucket = c.bucket();
    this.prefijo = c.prefijo() == null ? "" : c.prefijo();
    // Los servicios compatibles no siempre aceptan la cabecera de cifrado del servidor de AWS
    this.cifrar = c.endpoint() == null || c.endpoint().isBlank();
  }

  @Override
  public void guardar(String ruta, byte[] contenido) throws IOException {
    PutObjectRequest.Builder solicitud = PutObjectRequest.builder().bucket(bucket).key(clave(ruta));
    if (cifrar) solicitud.serverSideEncryption(ServerSideEncryption.AES256);
    try {
      s3.putObject(solicitud.build(), RequestBody.fromBytes(contenido));
    } catch (SdkException e) {
      throw new IOException("No se pudo guardar el documento en S3", e);
    }
  }

  @Override
  public InputStream abrir(String ruta) throws IOException {
    try {
      return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(clave(ruta)).build());
    } catch (SdkException e) {
      throw new IOException("No se pudo leer el documento de S3", e);
    }
  }

  @Override
  public void eliminar(String ruta) throws IOException {
    try {
      s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(clave(ruta)).build());
    } catch (SdkException e) {
      throw new IOException("No se pudo eliminar el documento de S3", e);
    }
  }

  /**la ruta la genera el sistema; aun así se rechazan rutas absolutas o con «..»*/
  private String clave(String ruta) {
    if (ruta.startsWith("/") || ruta.contains("..")) throw new IllegalArgumentException("Ruta fuera del almacén de documentos");
    return prefijo + ruta;
  }

  @Override
  public void close() { s3.close(); }
}
