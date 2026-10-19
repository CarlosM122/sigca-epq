package co.gov.epq.sigca.common.persistencia;

import java.sql.Connection;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.stereotype.Component;

import co.gov.epq.sigca.seguridad.UsuarioActual;

/**
 * Fija CLIENT_IDENTIFIER en cada conexión que se toma del pool (equivale a
 * {@code DBMS_SESSION.SET_IDENTIFIER(username)}). Los triggers lo leen con {@code PKG_SIGCA.actor}
 * para el historial de PQR. Con el driver de Oracle viaja junto a la siguiente llamada, sin ida y vuelta extra.
 */
@Component
public class IdentificadorSesionOracle implements BeanPostProcessor {

    private static final String CLAVE = "OCSID.CLIENTID";

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DataSource ds && !(bean instanceof DelegatingDataSource)) {
            return new DelegatingDataSource(ds) {
                @Override
                public Connection getConnection() throws SQLException {
                    return identificar(super.getConnection());
                }

                @Override
                public Connection getConnection(String username, String password) throws SQLException {
                    return identificar(super.getConnection(username, password));
                }
            };
        }
        return bean;
    }

    private static Connection identificar(Connection con) throws SQLException {
        if (con.getMetaData().getDatabaseProductName().startsWith("Oracle")) {
            try {
                con.setClientInfo(CLAVE, UsuarioActual.usernameOSistema());
            } catch (SQLClientInfoException e) {
                // El driver no soporta la propiedad: el historial registrará 'SISTEMA'.
            }
        }
        return con;
    }
}
