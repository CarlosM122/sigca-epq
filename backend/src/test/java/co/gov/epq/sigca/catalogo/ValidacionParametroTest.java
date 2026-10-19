package co.gov.epq.sigca.catalogo;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import co.gov.epq.sigca.common.error.NegocioException;

class ValidacionParametroTest {

    private static ParametroSistema parametro(String clave, TipoDato tipo) {
        ParametroSistema p = new ParametroSistema();
        p.setClave(clave);
        p.setTipoDato(tipo);
        return p;
    }

    @Test
    void enteros() {
        ParametroSistema p = parametro("PORTAL_MAX_INTENTOS", TipoDato.ENTERO);
        assertThatCode(() -> AdminCatalogoService.validarFormato(p, "5")).doesNotThrowAnyException();
        assertThatThrownBy(() -> AdminCatalogoService.validarFormato(p, "5.5")).isInstanceOf(NegocioException.class);
        assertThatThrownBy(() -> AdminCatalogoService.validarFormato(p, "abc")).isInstanceOf(NegocioException.class);
    }

    @Test
    void decimalesConPunto() {
        ParametroSistema p = parametro("TASA_USURA_ANUAL", TipoDato.DECIMAL);
        assertThatCode(() -> AdminCatalogoService.validarFormato(p, "25.37")).doesNotThrowAnyException();
        assertThatCode(() -> AdminCatalogoService.validarFormato(p, "25")).doesNotThrowAnyException();
        assertThatThrownBy(() -> AdminCatalogoService.validarFormato(p, "25,37")).isInstanceOf(NegocioException.class);
    }

    @Test
    void booleanosYModoDeOperacion() {
        assertThatThrownBy(() -> AdminCatalogoService.validarFormato(parametro("X", TipoDato.BOOLEANO), "SI"))
                .isInstanceOf(NegocioException.class);
        ParametroSistema modo = parametro("MODO_OPERACION", TipoDato.TEXTO);
        assertThatCode(() -> AdminCatalogoService.validarFormato(modo, "MANTENIMIENTO")).doesNotThrowAnyException();
        assertThatThrownBy(() -> AdminCatalogoService.validarFormato(modo, "APAGADO")).isInstanceOf(NegocioException.class);
    }
}
