package co.gov.epq.sigca.catalogo;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import co.gov.epq.sigca.common.error.NegocioException;
import org.springframework.http.HttpStatus;

/**
 * Lectura de PARAMETRO_SISTEMA (RF-29). Se lee de la BD en cada uso: el Administrador puede cambiarlos
 * en caliente y la base valida los rangos legales (TRG_PARAMETRO_CIU), así que no hay copia en memoria
 * que pueda quedar desactualizada.
 */
@Service
@Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
public class Parametros {

    public static final String PQR_HORAS_ALERTA = "PQR_HORAS_ALERTA";
    public static final String PQR_HORAS_RECONOCIMIENTO_SILENCIO = "PQR_HORAS_RECONOCIMIENTO_SILENCIO";
    public static final String RECURSO_DIAS_PLAZO = "RECURSO_DIAS_PLAZO";
    public static final String PORTAL_MAX_INTENTOS = "PORTAL_MAX_INTENTOS";
    public static final String PORTAL_MINUTOS_BLOQUEO = "PORTAL_MINUTOS_BLOQUEO";
    public static final String PASSWORD_MIN_LONGITUD = "PASSWORD_MIN_LONGITUD";
    public static final String TOKEN_HORAS_VIGENCIA = "TOKEN_HORAS_VIGENCIA";
    public static final String HISTORIAL_MESES = "HISTORIAL_MESES";
    public static final String FACTURA_DIAS_VENCIMIENTO = "FACTURA_DIAS_VENCIMIENTO";
    public static final String FACTURA_MESES_COBRO_OMITIDO = "FACTURA_MESES_COBRO_OMITIDO";
    public static final String LOTE_MAX_MINUTOS = "LOTE_MAX_MINUTOS";
    public static final String MORA_PERIODOS_SUSPENSION = "MORA_PERIODOS_SUSPENSION";
    public static final String MORA_TASA_RESIDENCIAL_ANUAL = "MORA_TASA_RESIDENCIAL_ANUAL";
    public static final String MORA_TASA_NO_RESIDENCIAL_ANUAL = "MORA_TASA_NO_RESIDENCIAL_ANUAL";
    public static final String TASA_USURA_ANUAL = "TASA_USURA_ANUAL";
    public static final String ACUERDO_MAX_CUOTAS = "ACUERDO_MAX_CUOTAS";
    public static final String ACUERDO_CUOTA_INICIAL_PCT = "ACUERDO_CUOTA_INICIAL_PCT";
    public static final String ACUERDO_DIAS_TOLERANCIA = "ACUERDO_DIAS_TOLERANCIA";
    public static final String ADJUNTO_MAX_MB = "ADJUNTO_MAX_MB";
    public static final String NOTIFICACION_MAX_INTENTOS = "NOTIFICACION_MAX_INTENTOS";
    public static final String DASHBOARD_REFRESCO_SEG = "DASHBOARD_REFRESCO_SEG";
    public static final String MODO_OPERACION = "MODO_OPERACION";

    private final ParametroSistemaRepository repo;

    public Parametros(ParametroSistemaRepository repo) {
        this.repo = repo;
    }

    public String texto(String clave) {
        return repo.findById(clave)
                .map(ParametroSistema::getValor)
                .orElseThrow(() -> new NegocioException("PARAMETRO_INEXISTENTE",
                        "Falta el parámetro " + clave + " en PARAMETRO_SISTEMA", HttpStatus.INTERNAL_SERVER_ERROR));
    }

    public int entero(String clave) {
        return Integer.parseInt(texto(clave));
    }

    public BigDecimal decimal(String clave) {
        return new BigDecimal(texto(clave));
    }

    public boolean booleano(String clave) {
        return "S".equals(texto(clave));
    }
}
