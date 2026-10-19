package co.gov.epq.sigca.catalogo;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FestivoRepository extends JpaRepository<Festivo, LocalDate> {

    List<Festivo> findByFechaBetweenOrderByFecha(LocalDate desde, LocalDate hasta);
}
