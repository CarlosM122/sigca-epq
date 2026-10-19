package co.gov.epq.sigca.seguridad.cuentas;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RolRepository extends JpaRepository<Rol, Long> {

    Optional<Rol> findByCodigo(String codigo);

    List<Rol> findAllByOrderByNombre();

    /** Pares (código de rol, código de permiso) de la matriz ROL_PERMISO. */
    @Query(nativeQuery = true, value = """
            SELECT R.CODIGO, P.CODIGO
              FROM {h-schema}ROL_PERMISO RP
              JOIN {h-schema}ROL R ON R.ID = RP.ROL_ID
              JOIN {h-schema}PERMISO P ON P.ID = RP.PERMISO_ID
             ORDER BY R.CODIGO, P.CODIGO
            """)
    List<Object[]> matriz();

    @Query(nativeQuery = true, value = """
            SELECT P.CODIGO FROM {h-schema}ROL_PERMISO RP JOIN {h-schema}PERMISO P ON P.ID = RP.PERMISO_ID
             WHERE RP.ROL_ID = :rolId ORDER BY P.CODIGO
            """)
    List<String> permisosDe(@Param("rolId") Long rolId);

    @Modifying
    @Query(nativeQuery = true, value = "INSERT INTO {h-schema}ROL_PERMISO (ROL_ID, PERMISO_ID) VALUES (:rolId, :permisoId)")
    int otorgar(@Param("rolId") Long rolId, @Param("permisoId") Long permisoId);

    @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM {h-schema}ROL_PERMISO WHERE ROL_ID = :rolId AND PERMISO_ID = :permisoId")
    int quitar(@Param("rolId") Long rolId, @Param("permisoId") Long permisoId);
}
