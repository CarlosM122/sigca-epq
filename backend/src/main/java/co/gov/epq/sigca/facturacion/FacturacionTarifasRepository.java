package co.gov.epq.sigca.facturacion;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.gov.epq.sigca.contratos.TipoServicio;
import co.gov.epq.sigca.tarifas.FactorSubsidio;
import co.gov.epq.sigca.tarifas.Tarifa;
import co.gov.epq.sigca.usuarios.UsoPredio;

/** Tarifas y factores vigentes al cierre del periodo (P-08). Solo lectura. */
public interface FacturacionTarifasRepository extends JpaRepository<Tarifa, Long> {

    @Query("""
            select t from Tarifa t
            where t.tipoServicio = :tipo and t.municipioId = :municipioId
              and t.vigenteDesde <= :fecha and (t.vigenteHasta is null or t.vigenteHasta >= :fecha)
            order by t.vigenteDesde desc""")
    List<Tarifa> vigentes(@Param("tipo") TipoServicio tipo, @Param("municipioId") Long municipioId,
            @Param("fecha") LocalDate fecha);

    @Query("""
            select f from FactorSubsidio f
            where f.tipoServicio = :tipo and f.municipioId = :municipioId and f.uso = :uso
              and f.vigenteDesde <= :fecha and (f.vigenteHasta is null or f.vigenteHasta >= :fecha)
            order by f.vigenteDesde desc""")
    List<FactorSubsidio> factoresVigentes(@Param("tipo") TipoServicio tipo, @Param("municipioId") Long municipioId,
            @Param("uso") UsoPredio uso, @Param("fecha") LocalDate fecha);
}
