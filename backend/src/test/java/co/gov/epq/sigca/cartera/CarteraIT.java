package co.gov.epq.sigca.cartera;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import co.gov.epq.sigca.common.cifrado.CifradorIdentificacion;
import co.gov.epq.sigca.pqr.DatosAtencion;
import co.gov.epq.sigca.soporte.PruebaIntegracion;

/** Cartera contra el Oracle compartido: morosos, cobro persuasivo hasta la orden de suspensión y acuerdo de pago. */
class CarteraIT extends PruebaIntegracion {

    @Autowired
    CifradorIdentificacion cifrador;

    @Autowired
    CarteraProcesos procesos;

    DatosAtencion datos;

    @BeforeEach
    void preparar() {
        datos = new DatosAtencion(jdbc, cifrador);
    }

    String cuerpo(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** Titular con las tres facturas de prueba vencidas. */
    DatosAtencion.Titular moroso(List<DatosAtencion.FacturaCreada> facturas) {
        DatosAtencion.Titular t = datos.titular("63190");
        facturas.addAll(datos.tresFacturas(t));
        facturas.forEach(f -> datos.vencer(f.id()));
        return t;
    }

    static String acuerdo(long usuario, List<Long> facturas, String inicial, int cuotas, String tasa) {
        return "{\"usuarioId\":%d,\"facturaIds\":%s,\"cuotaInicial\":%s,\"numeroCuotas\":%d,\"tasaInteresAnual\":%s}"
                .formatted(usuario, facturas, inicial, cuotas, tasa);
    }

    @Test
    void acuerdoCompletoCuadraEnLaBaseYSeAnula() throws Exception {
        List<DatosAtencion.FacturaCreada> fs = new java.util.ArrayList<>();
        DatosAtencion.Titular t = moroso(fs);
        List<Long> ids = fs.stream().map(DatosAtencion.FacturaCreada::id).toList();
        BigDecimal deuda = jdbc.queryForObject("SELECT SUM(SALDO_PENDIENTE) FROM SIGCA.FACTURA WHERE USUARIO_ID = ?",
                BigDecimal.class, t.usuarioId());

        mvc.perform(get("/api/cartera/usuarios/" + t.usuarioId() + "/facturas-vencidas").with(como("cartera")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facturas.length()").value(3))
                .andExpect(jsonPath("$.totalIncluible").value(deuda.doubleValue()))
                .andExpect(jsonPath("$.tasaMaxima").value(6.0))
                .andExpect(jsonPath("$.maxCuotas").value(24));

        BigDecimal inicial = deuda.multiply(new BigDecimal("0.10")).setScale(2, java.math.RoundingMode.HALF_UP);
        // Validaciones previas a la BD: tasa por encima del tope residencial, cuota inicial insuficiente
        mvc.perform(post("/api/cartera/acuerdos/simulacion").with(como("cartera")).contentType(MediaType.APPLICATION_JSON)
                .content(acuerdo(t.usuarioId(), ids, inicial.toPlainString(), 6, "7")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("ACUERDO_INVALIDO"));
        mvc.perform(post("/api/cartera/acuerdos/simulacion").with(como("cartera")).contentType(MediaType.APPLICATION_JSON)
                .content(acuerdo(t.usuarioId(), ids, "1000", 6, "6")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("ACUERDO_INVALIDO"));
        mvc.perform(post("/api/cartera/acuerdos/simulacion").with(como("cartera")).contentType(MediaType.APPLICATION_JSON)
                .content(acuerdo(t.usuarioId(), ids, inicial.toPlainString(), 1, "6")))
                .andExpect(status().isBadRequest());

        String sim = cuerpo(mvc.perform(post("/api/cartera/acuerdos/simulacion").with(como("cartera"))
                .contentType(MediaType.APPLICATION_JSON).content(acuerdo(t.usuarioId(), ids, inicial.toPlainString(), 6, "6")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuotas.length()").value(7))
                .andExpect(jsonPath("$.cuotas[0].valorInteres").value(0.0)));
        // La simulación no escribe nada
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SIGCA.ACUERDO_PAGO WHERE USUARIO_ID = ?", Long.class, t.usuarioId())).isZero();
        List<Number> capitales = JsonPath.read(sim, "$.cuotas[*].valorCapital");
        BigDecimal suma = capitales.stream().map(n -> new BigDecimal(n.toString())).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(suma).isEqualByComparingTo(deuda);

        String creado = cuerpo(mvc.perform(post("/api/cartera/acuerdos").with(como("cartera"))
                .contentType(MediaType.APPLICATION_JSON).content(acuerdo(t.usuarioId(), ids, inicial.toPlainString(), 6, "6")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.acuerdo.estado").value("VIGENTE"))
                .andExpect(jsonPath("$.cuotas.length()").value(7))
                .andExpect(jsonPath("$.facturas.length()").value(3)));
        long acuerdoId = ((Number) JsonPath.read(creado, "$.acuerdo.id")).longValue();
        var a = jdbc.queryForMap("SELECT * FROM SIGCA.ACUERDO_PAGO WHERE ID = ?", acuerdoId);
        assertThat((BigDecimal) a.get("TOTAL_DEUDA")).isEqualByComparingTo(deuda);
        assertThat((BigDecimal) a.get("TOTAL_INCLUIDO")).isEqualByComparingTo(deuda);
        assertThat((BigDecimal) a.get("TOTAL_CAPITAL_CUOTAS")).isEqualByComparingTo(deuda);
        assertThat(((Number) a.get("CUOTAS_REGISTRADAS")).intValue()).isEqualTo(7);
        assertThat((String) a.get("NUMERO_ACUERDO")).matches("AP-\\d{4}-\\d{6}");
        assertThat(jdbc.queryForList("SELECT DISTINCT ESTADO FROM SIGCA.FACTURA WHERE USUARIO_ID = ?", String.class, t.usuarioId()))
                .containsExactly("EN_ACUERDO");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SIGCA.NOTIFICACION WHERE EVENTO = 'ACUERDO_CREADO' AND ENTIDAD_ID = ?",
                Long.class, acuerdoId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SIGCA.LOG_AUDITORIA WHERE OPERACION = 'CREAR_ACUERDO' AND ENTIDAD_ID = ?",
                Long.class, acuerdoId)).isEqualTo(1);

        // Un segundo acuerdo no procede (las facturas ya no están vencidas)
        mvc.perform(post("/api/cartera/acuerdos").with(como("cartera")).contentType(MediaType.APPLICATION_JSON)
                .content(acuerdo(t.usuarioId(), ids, inicial.toPlainString(), 6, "6")))
                .andExpect(status().is4xxClientError());
        mvc.perform(get("/api/cartera/acuerdos").param("usuarioId", String.valueOf(t.usuarioId())).with(como("gerente")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1));

        mvc.perform(post("/api/cartera/acuerdos/" + acuerdoId + "/anulacion").with(como("cartera")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"corto\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/cartera/acuerdos/" + acuerdoId + "/anulacion").with(como("cartera")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"El usuario desistió del acuerdo firmado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acuerdo.estado").value("ANULADO"));
        assertThat(jdbc.queryForList("SELECT DISTINCT ESTADO FROM SIGCA.FACTURA WHERE USUARIO_ID = ?", String.class, t.usuarioId()))
                .containsExactly("VENCIDA");
    }

    @Test
    void cobroPersuasivoHastaLaOrdenDeSuspensionYElAcuerdoLaAnula() throws Exception {
        List<DatosAtencion.FacturaCreada> fs = new java.util.ArrayList<>();
        DatosAtencion.Titular t = moroso(fs);

        mvc.perform(get("/api/cartera/morosos").param("texto", t.codigo()).with(como("cartera")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].periodosMora").value(3))
                .andExpect(jsonPath("$.content[0].puedeSerCandidato").value(true))
                .andExpect(jsonPath("$.content[0].cobroId").doesNotExist());

        String c = cuerpo(mvc.perform(post("/api/cartera/cobros").with(como("cartera")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuarioId\":" + t.usuarioId() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("ACTIVO")));
        long cobro = ((Number) JsonPath.read(c, "$.id")).longValue();
        mvc.perform(post("/api/cartera/cobros").with(como("cartera")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuarioId\":" + t.usuarioId() + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("COBRO_ABIERTO"));

        // Sin candidato y aviso no hay orden
        mvc.perform(post("/api/cartera/cobros/" + cobro + "/orden-suspension").with(como("cartera")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ORDEN_NO_PROCEDENTE"));
        mvc.perform(post("/api/cartera/cobros/" + cobro + "/aviso").with(como("cartera")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("NO_ES_CANDIDATO"));
        mvc.perform(post("/api/cartera/cobros/" + cobro + "/candidato").with(como("cartera")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.candidatoSuspension").value(true));
        mvc.perform(post("/api/cartera/cobros/" + cobro + "/aviso").with(como("cartera")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fechaAvisoSuspension").exists());
        assertThat(jdbc.queryForList("SELECT CANAL FROM SIGCA.NOTIFICACION WHERE EVENTO = 'AVISO_SUSPENSION' AND ENTIDAD_ID = ? ORDER BY CANAL",
                String.class, cobro)).containsExactly("EMAIL", "SMS");

        mvc.perform(post("/api/cartera/cobros/" + cobro + "/orden-suspension").with(como("cartera")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ordenes.length()").value(1))
                .andExpect(jsonPath("$.ordenes[0].estado").value("PENDIENTE"));
        var orden = jdbc.queryForMap("SELECT * FROM SIGCA.ORDEN_SERVICIO WHERE COBRO_ID = ?", cobro);
        assertThat(orden.get("TIPO")).isEqualTo("SUSPENSION");
        assertThat(orden.get("CAUSA")).isEqualTo("MORA");
        assertThat(((Number) orden.get("CONTRATO_ID")).longValue()).isEqualTo(t.contratoId());
        // Una segunda orden no se duplica
        mvc.perform(post("/api/cartera/cobros/" + cobro + "/orden-suspension").with(como("cartera")))
                .andExpect(status().isConflict());
        for (String op : List.of("INICIAR_COBRO", "MARCAR_CANDIDATO_SUSPENSION", "AVISAR_SUSPENSION")) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SIGCA.LOG_AUDITORIA WHERE OPERACION = ? AND ENTIDAD_ID = ?",
                    Long.class, op, cobro)).as(op).isEqualTo(1);
        }

        // El acuerdo de pago suspende el cobro y anula la orden pendiente (TRG_ACUERDO_AIU)
        BigDecimal deuda = jdbc.queryForObject("SELECT SUM(SALDO_PENDIENTE) FROM SIGCA.FACTURA WHERE USUARIO_ID = ?",
                BigDecimal.class, t.usuarioId());
        mvc.perform(post("/api/cartera/acuerdos").with(como("cartera")).contentType(MediaType.APPLICATION_JSON)
                .content(acuerdo(t.usuarioId(), fs.stream().map(DatosAtencion.FacturaCreada::id).toList(),
                        deuda.multiply(new BigDecimal("0.2")).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(), 3, "0")))
                .andExpect(status().isCreated());
        assertThat(datos.estado("COBRO_PERSUASIVO", cobro)).isEqualTo("SUSPENDIDO");
        assertThat(datos.estado("ORDEN_SERVICIO", ((Number) orden.get("ID")).longValue())).isEqualTo("ANULADA");
    }

    @Test
    void laSuspensionPorMoraNoSeReconectaAMano() throws Exception {
        List<DatosAtencion.FacturaCreada> fs = new java.util.ArrayList<>();
        DatosAtencion.Titular t = moroso(fs);
        String c = cuerpo(mvc.perform(post("/api/cartera/cobros").with(como("cartera")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuarioId\":" + t.usuarioId() + "}")).andExpect(status().isCreated()));
        long cobro = ((Number) JsonPath.read(c, "$.id")).longValue();
        for (String paso : List.of("candidato", "aviso", "orden-suspension")) {
            mvc.perform(post("/api/cartera/cobros/" + cobro + "/" + paso).with(como("cartera"))).andExpect(status().isOk());
        }
        long orden = jdbc.queryForObject("SELECT ID FROM SIGCA.ORDEN_SERVICIO WHERE COBRO_ID = ?", Long.class, cobro);
        mvc.perform(post("/api/ordenes/{id}/ejecucion", orden).with(como("asesor"))).andExpect(status().isOk());
        assertThat(datos.estado("CONTRATO_SERVICIO", t.contratoId())).isEqualTo("SUSPENDIDO");

        // Ni por solicitud del usuario ni por causa técnica: solo el pago o el acuerdo lo reconectan
        for (String causa : List.of("SOLICITUD_USUARIO", "TECNICA")) {
            mvc.perform(post("/api/ordenes").with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"contratoId\":%d,\"tipo\":\"RECONEXION\",\"causa\":\"%s\",\"observacion\":\"Pide reconexión\"}"
                            .formatted(t.contratoId(), causa)))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.codigo").value("RECONEXION_POR_MORA"));
        }

        BigDecimal deuda = jdbc.queryForObject("SELECT SUM(SALDO_PENDIENTE) FROM SIGCA.FACTURA WHERE USUARIO_ID = ?",
                BigDecimal.class, t.usuarioId());
        mvc.perform(post("/api/cartera/acuerdos").with(como("cartera")).contentType(MediaType.APPLICATION_JSON)
                .content(acuerdo(t.usuarioId(), fs.stream().map(DatosAtencion.FacturaCreada::id).toList(),
                        deuda.multiply(new BigDecimal("0.2")).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(), 3, "0")))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM SIGCA.ORDEN_SERVICIO
                WHERE CONTRATO_ID = ? AND TIPO = 'RECONEXION' AND CAUSA = 'ACUERDO' AND ESTADO = 'PENDIENTE'
                """, Long.class, t.contratoId())).isEqualTo(1);
    }

    @Test
    void candidatoExigeLosPeriodosDeMoraYLosPermisosSeRespetan() throws Exception {
        DatosAtencion.Titular t = datos.titular("63190");
        DatosAtencion.FacturaCreada f = datos.factura(t, DatosAtencion.periodos().getLast(), 120);
        datos.vencer(f.id());
        String c = cuerpo(mvc.perform(post("/api/cartera/cobros").with(como("cartera")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuarioId\":" + t.usuarioId() + "}")).andExpect(status().isCreated()));
        long cobro = ((Number) JsonPath.read(c, "$.id")).longValue();
        mvc.perform(post("/api/cartera/cobros/" + cobro + "/candidato").with(como("cartera")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ORDEN_NO_PROCEDENTE"));

        // Permisos: gestor de PQR y asesor no ven cartera; el gerente consulta pero no gestiona cobros
        mvc.perform(get("/api/cartera/morosos").with(como("gestor.circasia"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/cartera/morosos").with(como("asesor"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/cartera/morosos").with(como("gerente"))).andExpect(status().isOk());
        mvc.perform(post("/api/cartera/cobros/" + cobro + "/candidato").with(como("gerente"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/cartera/morosos").with(ciudadano(t.usuarioId()))).andExpect(status().isForbidden());

        // CSV con auditoría EXPORTAR_REPORTE
        long antes = jdbc.queryForObject("SELECT COUNT(*) FROM SIGCA.LOG_AUDITORIA WHERE OPERACION = 'EXPORTAR_REPORTE'", Long.class);
        String csv = cuerpo(mvc.perform(get("/api/cartera/morosos.csv").param("texto", t.codigo()).with(como("cartera")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv")));
        assertThat(csv).startsWith("﻿Código;Nombre").contains("\"" + t.codigo() + "\"");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SIGCA.LOG_AUDITORIA WHERE OPERACION = 'EXPORTAR_REPORTE'", Long.class))
                .isEqualTo(antes + 1);
    }

    @Test
    void laCuotaVencidaIncumpleElAcuerdoYDevuelveLasFacturasACobro() throws Exception {
        DatosAtencion.Titular t = datos.titular("63190");
        DatosAtencion.FacturaCreada f = datos.factura(t, DatosAtencion.periodos().getLast(), 120);
        datos.vencer(f.id());
        long cartera = datos.funcionario("cartera");
        // Acuerdo firmado hace 40 días (la fecha del acuerdo puede ser pasada): cuota 0 y cuota 1 ya vencieron
        Long acuerdoId = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Long>) con -> {
            try (var cs = con.prepareCall("""
                    DECLARE
                      v_a NUMBER; v_saldo NUMBER; v_ini NUMBER; v_fecha DATE := TRUNC(SYSDATE) - 40;
                    BEGIN
                      SELECT SALDO_PENDIENTE INTO v_saldo FROM SIGCA.FACTURA WHERE ID = ?;
                      v_ini := ROUND(v_saldo * 0.2, 2);
                      INSERT INTO SIGCA.ACUERDO_PAGO (USUARIO_ID, FECHA_ACUERDO, TOTAL_DEUDA, CUOTA_INICIAL, TASA_INTERES_ANUAL,
                             NUMERO_CUOTAS, ESTADO, FUNCIONARIO_ID)
                      VALUES (?, v_fecha, v_saldo, v_ini, 0, 2, 'VIGENTE', ?) RETURNING ID INTO v_a;
                      INSERT INTO SIGCA.ACUERDO_FACTURA (ACUERDO_ID, FACTURA_ID, VALOR_INCLUIDO) VALUES (v_a, ?, v_saldo);
                      INSERT INTO SIGCA.CUOTA_ACUERDO (ACUERDO_ID, NUMERO_CUOTA, VALOR_CAPITAL, VALOR_INTERES, VALOR_CUOTA, FECHA_VENCIMIENTO)
                      VALUES (v_a, 0, v_ini, 0, v_ini, v_fecha + 5);
                      INSERT INTO SIGCA.CUOTA_ACUERDO (ACUERDO_ID, NUMERO_CUOTA, VALOR_CAPITAL, VALOR_INTERES, VALOR_CUOTA, FECHA_VENCIMIENTO)
                      VALUES (v_a, 1, ROUND((v_saldo - v_ini) / 2, 2), 0, ROUND((v_saldo - v_ini) / 2, 2), v_fecha + 30);
                      INSERT INTO SIGCA.CUOTA_ACUERDO (ACUERDO_ID, NUMERO_CUOTA, VALOR_CAPITAL, VALOR_INTERES, VALOR_CUOTA, FECHA_VENCIMIENTO)
                      VALUES (v_a, 2, v_saldo - v_ini - ROUND((v_saldo - v_ini) / 2, 2), 0,
                              v_saldo - v_ini - ROUND((v_saldo - v_ini) / 2, 2), v_fecha + 60);
                      ? := v_a;
                    END;""")) {
                cs.setLong(1, f.id());
                cs.setLong(2, t.usuarioId());
                cs.setLong(3, cartera);
                cs.setLong(4, f.id());
                cs.registerOutParameter(5, java.sql.Types.NUMERIC);
                cs.execute();
                return cs.getLong(5);
            }
        });
        assertThat(datos.estado("FACTURA", f.id())).isEqualTo("EN_ACUERDO");

        assertThat(procesos.procesar(acuerdoId, procesos.limite())).isTrue();

        assertThat(datos.estado("ACUERDO_PAGO", acuerdoId)).isEqualTo("INCUMPLIDO");
        assertThat(jdbc.queryForList("SELECT ESTADO FROM SIGCA.CUOTA_ACUERDO WHERE ACUERDO_ID = ? ORDER BY NUMERO_CUOTA",
                String.class, acuerdoId)).containsExactly("VENCIDA", "VENCIDA", "PENDIENTE");
        assertThat(datos.estado("FACTURA", f.id())).isEqualTo("VENCIDA");
        assertThat(jdbc.queryForList("SELECT DISTINCT EVENTO FROM SIGCA.NOTIFICACION WHERE ENTIDAD = 'ACUERDO_PAGO' AND ENTIDAD_ID = ?",
                String.class, acuerdoId)).contains("CUOTA_VENCIDA", "ACUERDO_INCUMPLIDO");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SIGCA.LOG_AUDITORIA WHERE OPERACION = 'INCUMPLIR_ACUERDO' AND ENTIDAD_ID = ?",
                Long.class, acuerdoId)).isEqualTo(1);
        // Idempotente: ya no hay cuotas pendientes vencidas
        assertThat(procesos.procesar(acuerdoId, procesos.limite())).isFalse();
    }
}
