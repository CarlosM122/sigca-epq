package co.gov.epq.sigca.pqr;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PqrAdjuntoRepository extends JpaRepository<PqrAdjunto, Long> {

    /** Metadatos sin cargar el contenido (BLOB). */
    @Query("""
            select new co.gov.epq.sigca.pqr.PqrAdjuntoMetadatos(a.id, a.tipo, a.nombreArchivo, a.tipoMime,
                   a.tamanoBytes, a.cargadoEn)
            from PqrAdjunto a where a.pqrId = :pqrId order by a.id""")
    List<PqrAdjuntoMetadatos> metadatos(Long pqrId);

    long countByPqrIdAndTipo(Long pqrId, TipoAdjunto tipo);

    boolean existsByPqrIdAndHashSha256(Long pqrId, String hashSha256);

    Optional<PqrAdjunto> findByIdAndPqrId(Long id, Long pqrId);
}
