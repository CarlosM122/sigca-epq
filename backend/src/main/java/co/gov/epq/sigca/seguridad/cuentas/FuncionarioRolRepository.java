package co.gov.epq.sigca.seguridad.cuentas;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FuncionarioRolRepository extends JpaRepository<FuncionarioRol, FuncionarioRol.Clave> {

    List<FuncionarioRol> findByFuncionarioId(Long funcionarioId);

    /** Pares (funcionarioId, código de rol) para pintar una página de funcionarios sin N+1. */
    @Query("""
            select fr.funcionarioId, r.codigo from FuncionarioRol fr join Rol r on r.id = fr.rolId
            where fr.funcionarioId in :ids order by r.codigo
            """)
    List<Object[]> rolesDe(@Param("ids") Collection<Long> funcionarioIds);
}
