package co.gov.epq.sigca.usuarios;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PredioRepository extends JpaRepository<Predio, Long> {

    boolean existsByFichaCatastralAndIdNot(String fichaCatastral, Long id);

    boolean existsByFichaCatastral(String fichaCatastral);

    /** Predios donde el usuario tiene o tuvo contratos (el predio no tiene dueño en el modelo). */
    @Query("""
            select p from Predio p
            where p.id in (select c.predioId from ContratoServicio c where c.usuarioId = :usuarioId)
            order by p.codigoPredio
            """)
    List<Predio> delUsuario(Long usuarioId);

    @Query(value = """
            select p from Predio p
            where (:municipioId is null or p.municipioId = :municipioId)
              and (:texto is null or upper(p.codigoPredio) like :texto or upper(p.direccion) like :texto
                   or p.fichaCatastral like :texto)
            order by p.codigoPredio desc
            """, countQuery = """
            select count(p) from Predio p
            where (:municipioId is null or p.municipioId = :municipioId)
              and (:texto is null or upper(p.codigoPredio) like :texto or upper(p.direccion) like :texto
                   or p.fichaCatastral like :texto)
            """)
    Page<Predio> buscar(Long municipioId, String texto, Pageable pageable);
}
