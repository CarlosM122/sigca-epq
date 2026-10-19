package co.gov.epq.sigca;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableJpaAuditing(auditorAwareRef = "auditorActual")
@EnableScheduling
@EnableAsync
public class SigcaApplication {

    public static final String ZONA_HORARIA = "America/Bogota";

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone(ZONA_HORARIA));
        SpringApplication.run(SigcaApplication.class, args);
    }
}
