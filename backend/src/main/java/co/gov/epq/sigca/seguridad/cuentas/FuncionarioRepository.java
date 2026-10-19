package co.gov.epq.sigca.seguridad.cuentas;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FuncionarioRepository extends JpaRepository<Funcionario, Long> {

    Optional<Funcionario> findByUsername(String username);

    @Query("""
            select f from Funcionario f
            where (:texto is null
                   or lower(f.username) like :texto or lower(f.nombreCompleto) like :texto
                   or lower(f.correo) like :texto or lower(f.codigoFuncionario) like :texto)
              and (:activo is null or f.activo = :activo)
            order by f.nombreCompleto
            """)
    Page<Funcionario> buscar(@Param("texto") String texto, @Param("activo") Boolean activo, Pageable pageable);

    /** Códigos de los roles activos del funcionario. */
    @Query("""
            select r.codigo from FuncionarioRol fr join Rol r on r.id = fr.rolId
            where fr.funcionarioId = :id and r.activo = true order by r.codigo
            """)
    List<String> rolesActivos(@Param("id") Long funcionarioId);

    /** Unión de los permisos de los roles activos del funcionario (ROL_PERMISO no tiene entidad). */
    @Query(nativeQuery = true, value = """
            SELECT DISTINCT P.CODIGO
              FROM {h-schema}FUNCIONARIO_ROL FR
              JOIN {h-schema}ROL R ON R.ID = FR.ROL_ID AND R.ACTIVO = 'S'
              JOIN {h-schema}ROL_PERMISO RP ON RP.ROL_ID = R.ID
              JOIN {h-schema}PERMISO P ON P.ID = RP.PERMISO_ID
             WHERE FR.FUNCIONARIO_ID = :id
             ORDER BY P.CODIGO
            """)
    List<String> permisos(@Param("id") Long funcionarioId);
}
