package co.gov.epq.sigca.auditoria;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LogAuditoriaRepository extends JpaRepository<LogAuditoria, Long> {

    @Query("""
            select l from LogAuditoria l
            where (:operacion is null or l.operacion = :operacion)
              and (:entidad is null or l.entidad = :entidad)
              and (:entidadId is null or l.entidadId = :entidadId)
              and (:funcionarioId is null or l.funcionarioId = :funcionarioId)
              and (:usuarioId is null or l.usuarioId = :usuarioId)
              and (:resultado is null or l.resultado = :resultado)
              and l.fecha >= :desde and l.fecha < :hasta
            order by l.id desc
            """)
    Page<LogAuditoria> buscar(@Param("operacion") OperacionAuditoria operacion, @Param("entidad") String entidad,
            @Param("entidadId") Long entidadId, @Param("funcionarioId") Long funcionarioId, @Param("usuarioId") Long usuarioId,
            @Param("resultado") ResultadoAuditoria resultado, @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta, Pageable pageable);
}
