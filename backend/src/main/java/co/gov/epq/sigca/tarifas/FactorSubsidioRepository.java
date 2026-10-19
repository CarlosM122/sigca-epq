package co.gov.epq.sigca.tarifas;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import co.gov.epq.sigca.contratos.TipoServicio;
import co.gov.epq.sigca.usuarios.UsoPredio;
import jakarta.persistence.LockModeType;

public interface FactorSubsidioRepository extends JpaRepository<FactorSubsidio, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select f from FactorSubsidio f
            where f.municipioId = :municipioId and f.tipoServicio = :tipo and f.uso = :uso
              and ((:estrato is null and f.estrato is null) or f.estrato = :estrato) and f.vigenteHasta is null
            """)
    Optional<FactorSubsidio> abierto(Long municipioId, TipoServicio tipo, UsoPredio uso, Integer estrato);

    @Query("""
            select count(f) > 0 from FactorSubsidio f
            where f.municipioId = :municipioId and f.tipoServicio = :tipo and f.uso = :uso
              and ((:estrato is null and f.estrato is null) or f.estrato = :estrato)
              and (:excluir is null or f.id <> :excluir)
              and (f.vigenteHasta is null or f.vigenteHasta >= :desde)
            """)
    boolean seSuperpone(Long municipioId, TipoServicio tipo, UsoPredio uso, Integer estrato, LocalDate desde, Long excluir);

    @Query(value = """
            select f from FactorSubsidio f
            where (:municipioId is null or f.municipioId = :municipioId)
              and (:tipo is null or f.tipoServicio = :tipo)
              and (:uso is null or f.uso = :uso)
              and (:fecha is null or (f.vigenteDesde <= :fecha and (f.vigenteHasta is null or f.vigenteHasta >= :fecha)))
            order by f.municipioId, f.tipoServicio, f.uso, f.estrato, f.vigenteDesde desc
            """, countQuery = """
            select count(f) from FactorSubsidio f
            where (:municipioId is null or f.municipioId = :municipioId)
              and (:tipo is null or f.tipoServicio = :tipo)
              and (:uso is null or f.uso = :uso)
              and (:fecha is null or (f.vigenteDesde <= :fecha and (f.vigenteHasta is null or f.vigenteHasta >= :fecha)))
            """)
    Page<FactorSubsidio> buscar(Long municipioId, TipoServicio tipo, UsoPredio uso, LocalDate fecha, Pageable pageable);
}
