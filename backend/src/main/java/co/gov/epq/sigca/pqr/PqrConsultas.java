package co.gov.epq.sigca.pqr;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import co.gov.epq.sigca.common.validation.TipoIdentificacion;
import co.gov.epq.sigca.contratos.EstadoContrato;
import co.gov.epq.sigca.contratos.TipoServicio;
import co.gov.epq.sigca.facturacion.EstadoFactura;
import co.gov.epq.sigca.facturacion.EstadoLinea;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Lecturas de otras entidades (contratos, usuarios, facturas, funcionarios) que necesita el módulo de PQR.
 * Usa JPQL con joins ad hoc para no depender de los repositorios de otros módulos.
 */
@Repository
@Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
public class PqrConsultas {

    static final List<EstadoPqr> ABIERTAS = List.of(EstadoPqr.RECIBIDA, EstadoPqr.EN_TRAMITE, EstadoPqr.VENCIDA);
    private static final List<EstadoContrato> VIGENTES = List.of(EstadoContrato.ACTIVO, EstadoContrato.SUSPENDIDO);
    private static final List<EstadoFactura> COBRABLES = List.of(EstadoFactura.PENDIENTE, EstadoFactura.VENCIDA);

    @PersistenceContext
    private EntityManager em;

    record ContratoInfo(Long id, String numero, Long usuarioId, Long predioId, Long municipioId, EstadoContrato estado,
            TipoServicio tipoServicio, String titular, TipoIdentificacion tipoIdentificacion, String hashIdentificacion,
            String direccion, String municipio) {
    }

    record LineaInfo(Long id, Long facturaId, String numeroFactura, Integer periodo, TipoServicio tipoServicio,
            BigDecimal totalLinea, BigDecimal valorAjuste, EstadoLinea estado, boolean enReclamo,
            EstadoFactura estadoFactura, Long contratoId) {
    }

    record UsuarioInfo(Long id, String nombre, String correo, String telefono, TipoIdentificacion tipoIdentificacion,
            String hashIdentificacion) {
    }

    record FuncionarioInfo(Long id, String nombre, Long municipioId, boolean activo, String correo) {
    }

    record Carga(Long id, String nombre, String rol, Long municipioId, long abiertas) {
    }

    private static final String SELECT_CONTRATO = """
            select c.id, c.numeroContrato, c.usuarioId, c.predioId, p.municipioId, c.estado, c.tipoServicio,
                   u.nombreCompleto, u.tipoIdentificacion, u.numeroIdentificacionHash, p.direccion, m.nombre
            from ContratoServicio c join Predio p on p.id = c.predioId join Usuario u on u.id = c.usuarioId
                 join Municipio m on m.id = p.municipioId
            """;

    Optional<ContratoInfo> contrato(String numero) {
        return em.createQuery(SELECT_CONTRATO + " where c.numeroContrato = :n", Object[].class)
                .setParameter("n", numero).getResultStream().findFirst().map(PqrConsultas::contratoInfo);
    }

    Optional<ContratoInfo> contrato(Long id) {
        return em.createQuery(SELECT_CONTRATO + " where c.id = :id", Object[].class)
                .setParameter("id", id).getResultStream().findFirst().map(PqrConsultas::contratoInfo);
    }

    /** Contratos visibles para el titular (P-29: activos y suspendidos). */
    List<ContratoInfo> contratosVigentes(Long usuarioId) {
        return em.createQuery(SELECT_CONTRATO + " where c.usuarioId = :u and c.estado in :e order by c.numeroContrato",
                Object[].class).setParameter("u", usuarioId).setParameter("e", VIGENTES)
                .getResultList().stream().map(PqrConsultas::contratoInfo).toList();
    }

    private static ContratoInfo contratoInfo(Object[] r) {
        return new ContratoInfo((Long) r[0], (String) r[1], (Long) r[2], (Long) r[3], (Long) r[4], (EstadoContrato) r[5],
                (TipoServicio) r[6], (String) r[7], (TipoIdentificacion) r[8], (String) r[9], (String) r[10], (String) r[11]);
    }

    private static final String SELECT_LINEA = """
            select l.id, f.id, f.numeroFactura, f.periodo, l.tipoServicio, l.totalLinea, l.valorAjuste, l.estado,
                   l.enReclamo, f.estado, l.contratoId
            from LineaFactura l join Factura f on f.id = l.facturaId
            """;

    Optional<LineaInfo> lineaDeFactura(String numeroFactura, Long contratoId) {
        return em.createQuery(SELECT_LINEA + " where f.numeroFactura = :n and l.contratoId = :c", Object[].class)
                .setParameter("n", numeroFactura).setParameter("c", contratoId)
                .getResultStream().findFirst().map(PqrConsultas::lineaInfo);
    }

    Optional<LineaInfo> linea(Long id) {
        return em.createQuery(SELECT_LINEA + " where l.id = :id", Object[].class).setParameter("id", id)
                .getResultStream().findFirst().map(PqrConsultas::lineaInfo);
    }

    /** Líneas pendientes de facturas con cobro (pendientes o vencidas): lo que admite un reclamo (RN-33). */
    List<LineaInfo> lineasReclamables(Long contratoId) {
        return em.createQuery(SELECT_LINEA + """
                 where l.contratoId = :c
                   and l.estado = co.gov.epq.sigca.facturacion.EstadoLinea.PENDIENTE and f.estado in :e
                 order by f.periodo desc""", Object[].class)
                .setParameter("c", contratoId).setParameter("e", COBRABLES).setMaxResults(12)
                .getResultList().stream().map(PqrConsultas::lineaInfo).toList();
    }

    private static LineaInfo lineaInfo(Object[] r) {
        return new LineaInfo((Long) r[0], (Long) r[1], (String) r[2], (Integer) r[3], (TipoServicio) r[4],
                (BigDecimal) r[5], (BigDecimal) r[6], (EstadoLinea) r[7], Boolean.TRUE.equals(r[8]),
                (EstadoFactura) r[9], (Long) r[10]);
    }

    Optional<UsuarioInfo> usuario(Long id) {
        return em.createQuery("""
                select u.id, u.nombreCompleto, u.correo, u.telefono, u.tipoIdentificacion, u.numeroIdentificacionHash
                from Usuario u where u.id = :id""", Object[].class).setParameter("id", id)
                .getResultStream().findFirst()
                .map(r -> new UsuarioInfo((Long) r[0], (String) r[1], (String) r[2], (String) r[3],
                        (TipoIdentificacion) r[4], (String) r[5]));
    }

    Optional<FuncionarioInfo> funcionario(Long id) {
        return em.createQuery("""
                select f.id, f.nombreCompleto, f.municipioId, f.activo, f.correo from Funcionario f where f.id = :id""",
                Object[].class).setParameter("id", id).getResultStream().findFirst()
                .map(r -> new FuncionarioInfo((Long) r[0], (String) r[1], (Long) r[2], Boolean.TRUE.equals(r[3]),
                        (String) r[4]));
    }

    Map<Long, String> nombresFuncionarios(Collection<Long> ids) {
        Map<Long, String> m = new HashMap<>();
        if (ids.isEmpty()) {
            return m;
        }
        em.createQuery("select f.id, f.nombreCompleto from Funcionario f where f.id in :ids", Object[].class)
                .setParameter("ids", ids).getResultList().forEach(r -> m.put((Long) r[0], (String) r[1]));
        return m;
    }

    String nombreMunicipio(Long id) {
        return em.createQuery("select m.nombre from Municipio m where m.id = :id", String.class).setParameter("id", id)
                .getResultStream().findFirst().orElse(null);
    }

    boolean municipioActivo(Long id) {
        return em.createQuery("select count(m) from Municipio m where m.id = :id and m.activo = true", Long.class)
                .setParameter("id", id).getSingleResult() > 0;
    }

    /**
     * Funcionarios activos con el rol activo indicado (y del municipio, si se da) con su número de PQR abiertas.
     * Ordenados de menor a mayor carga; a igual carga, el de menor id (asignación estable).
     */
    List<Carga> cargas(String rol, Long municipioId) {
        return em.createQuery("""
                select f.id, f.nombreCompleto, r.codigo, f.municipioId,
                       (select count(p.id) from Pqr p where p.gestorId = f.id and p.estado in :abiertas)
                from Funcionario f join FuncionarioRol fr on fr.funcionarioId = f.id join Rol r on r.id = fr.rolId
                where f.activo = true and r.activo = true and r.codigo = :rol
                  and (:mun is null or f.municipioId = :mun)""", Object[].class)
                .setParameter("abiertas", ABIERTAS).setParameter("rol", rol).setParameter("mun", municipioId)
                .getResultList().stream()
                .map(r -> new Carga((Long) r[0], (String) r[1], (String) r[2], (Long) r[3], ((Number) r[4]).longValue()))
                .sorted((a, b) -> a.abiertas() != b.abiertas() ? Long.compare(a.abiertas(), b.abiertas())
                        : Long.compare(a.id(), b.id()))
                .toList();
    }

    boolean tieneRol(Long funcionarioId, String rol) {
        return em.createQuery("""
                select count(fr) from FuncionarioRol fr join Rol r on r.id = fr.rolId join Funcionario f on f.id = fr.funcionarioId
                where fr.funcionarioId = :f and r.codigo = :rol and r.activo = true and f.activo = true""", Long.class)
                .setParameter("f", funcionarioId).setParameter("rol", rol).getSingleResult() > 0;
    }

    /** PKG_SIGCA.sumar_dias_habiles: el mismo calendario (festivos) que usa la BD para los plazos. */
    LocalDate sumarDiasHabiles(LocalDate fecha, int dias) {
        Object r = em.createNativeQuery("SELECT SIGCA.PKG_SIGCA.sumar_dias_habiles(?1, ?2) FROM DUAL")
                .setParameter(1, fecha).setParameter(2, dias).getSingleResult();
        if (r instanceof Timestamp t) {
            return t.toLocalDateTime().toLocalDate();
        }
        if (r instanceof LocalDateTime t) {
            return t.toLocalDate();
        }
        if (r instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        return (LocalDate) r;
    }
}
