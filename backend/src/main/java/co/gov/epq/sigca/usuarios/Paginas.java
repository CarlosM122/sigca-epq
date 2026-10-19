package co.gov.epq.sigca.usuarios;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/** Límite de tamaño de página para las consultas de los módulos de usuarios, contratos, tarifas y lecturas. */
public final class Paginas {

    public static final int MAXIMO = 100;

    private Paginas() {
    }

    public static Pageable limitar(Pageable p) {
        return PageRequest.of(Math.max(0, p.getPageNumber()), Math.min(Math.max(1, p.getPageSize()), MAXIMO));
    }
}
