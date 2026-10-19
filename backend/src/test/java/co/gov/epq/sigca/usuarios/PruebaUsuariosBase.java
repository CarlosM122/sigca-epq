package co.gov.epq.sigca.usuarios;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import co.gov.epq.sigca.soporte.PruebaIntegracion;

/**
 * Fábrica de datos de prueba de los módulos de usuarios, predios, contratos y lecturas. Todo se crea por la API
 * (como lo haría el asesor) y con valores únicos, porque las pruebas confirman sus transacciones.
 */
public abstract class PruebaUsuariosBase extends PruebaIntegracion {

    protected Long circasia() {
        return jdbc.queryForObject("SELECT ID FROM SIGCA.MUNICIPIO WHERE CODIGO_DANE = '63190'", Long.class);
    }

    /** Cédula única de 10 dígitos que no cae en las convenciones de la RNEC simulada (0000 / 9999). */
    protected static String cedulaUnica() {
        long n = Math.abs(System.nanoTime()) % 100_000_000L;
        return "1" + String.format("%08d", n) + "7";
    }

    protected static String jsonUsuario(String tipoPersona, String tipoId, String numero, String nombre, Long municipioId) {
        return """
                {"tipoPersona":"%s","tipoIdentificacion":"%s","numeroIdentificacion":"%s","nombreCompleto":"%s",
                 "correo":"prueba%s@correo.com","telefono":"3001234567","direccionNotificacion":"Calle 10 # 5-20",
                 "municipioId":%d,"autorizaTratamientoDatos":true}
                """.formatted(tipoPersona, tipoId, numero, nombre, unico(), municipioId);
    }

    protected Long crearUsuario() throws Exception {
        String r = mvc.perform(post("/api/usuarios").with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content(jsonUsuario("NATURAL", "CC", cedulaUnica(), "María José Pérez", circasia())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(r, "$.id")).longValue();
    }

    protected Long crearPredio() throws Exception {
        String r = mvc.perform(post("/api/predios").with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"direccion":"Carrera 14 # 7-%s","barrioVereda":"Centro","municipioId":%d,"uso":"RESIDENCIAL","estrato":3}
                        """.formatted(System.nanoTime() % 100000, circasia())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(r, "$.id")).longValue();
    }

    protected static String serialUnico(String prefijo) {
        return prefijo + "-" + (Math.abs(System.nanoTime()) % 10_000_000_000L);
    }

    /** Instala un medidor y devuelve su id. */
    protected Long instalarMedidor(Long predioId, String tipo, String serial, LocalDate fecha) throws Exception {
        String r = mvc.perform(post("/api/predios/{id}/medidores", predioId).with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"serial":"%s","tipoServicio":"%s","marca":"Prueba","fechaInstalacion":"%s","lecturaInicial":0}
                        """.formatted(serial, tipo, fecha)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(r, "$.id")).longValue();
    }

    protected Long crearContrato(Long usuarioId, Long predioId, String tipo) throws Exception {
        String r = mvc.perform(post("/api/contratos").with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content(jsonContrato(usuarioId, predioId, tipo)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(r, "$.id")).longValue();
    }

    protected static String jsonContrato(Long usuarioId, Long predioId, String tipo) {
        return """
                {"usuarioId":%d,"predioId":%d,"tipoServicio":"%s","fechaVinculacion":"%s"}
                """.formatted(usuarioId, predioId, tipo, LocalDate.now());
    }
}
