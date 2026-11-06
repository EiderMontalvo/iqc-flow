package pe.com.iqc.flow.auditorias.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**norma auditada en el expediente con la edición usada (se emitirá un certificado por norma)*/
@Embeddable
public record NormaExpediente(
    @Column(name = "norma_codigo", length = 20) String normaCodigo,
    @Column(name = "edicion", length = 20) String edicion) {
}
