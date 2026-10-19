package co.gov.epq.sigca.contratos;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import co.gov.epq.sigca.seguridad.cuentas.Funcionario;

/** Consultas de funcionarios que necesitan contratos y lecturas (destinatarios de avisos internos). */
public interface ContratosFuncionarioRepository extends JpaRepository<Funcionario, Long> {

    /** Asesores activos del municipio, más los asesores sin municipio asignado (atienden todos). */
    @Query("""
            select distinct f.id from Funcionario f
            join FuncionarioRol fr on fr.funcionarioId = f.id
            join Rol r on r.id = fr.rolId
            where r.codigo = 'ASESOR' and r.activo = true and f.activo = true
              and (f.municipioId = :municipioId or f.municipioId is null)
            """)
    List<Long> asesoresDelMunicipio(Long municipioId);
}
