package co.gov.epq.sigca.tarifas;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import co.gov.epq.sigca.contratos.TipoServicio;
import jakarta.persistence.LockModeType;

public interface TarifaRepository extends JpaRepository<Tarifa, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Tarifa> findFirstByTipoServicioAndMunicipioIdAndVigenteHastaIsNull(TipoServicio tipo, Long municipioId);

    /** ¿Alguna tarifa (distinta de la indicada) cubre una fecha igual o posterior a {@code desde}? */
    @Query("""
            select count(t) > 0 from Tarifa t
            where t.tipoServicio = :tipo and t.municipioId = :municipioId and (:excluir is null or t.id <> :excluir)
              and (t.vigenteHasta is null or t.vigenteHasta >= :desde)
            """)
    boolean seSuperpone(TipoServicio tipo, Long municipioId, LocalDate desde, Long excluir);

    @Query(value = """
            select t from Tarifa t
            where (:municipioId is null or t.municipioId = :municipioId)
              and (:tipo is null or t.tipoServicio = :tipo)
              and (:fecha is null or (t.vigenteDesde <= :fecha and (t.vigenteHasta is null or t.vigenteHasta >= :fecha)))
            order by t.municipioId, t.tipoServicio, t.vigenteDesde desc
            """, countQuery = """
            select count(t) from Tarifa t
            where (:municipioId is null or t.municipioId = :municipioId)
              and (:tipo is null or t.tipoServicio = :tipo)
              and (:fecha is null or (t.vigenteDesde <= :fecha and (t.vigenteHasta is null or t.vigenteHasta >= :fecha)))
            """)
    Page<Tarifa> buscar(Long municipioId, TipoServicio tipo, LocalDate fecha, Pageable pageable);
}
