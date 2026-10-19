-- =====================================================================
-- SIGCA-EPQ  ·  06_permisos_app.sql
-- Ejecutar como SIGCA después de instalar. El backend se conecta como
-- SIGCA_APP: puede leer y escribir datos pero no cambiar la estructura,
-- y no puede borrar ni modificar el log de auditoría.
-- =====================================================================
BEGIN
    FOR t IN (SELECT TABLE_NAME FROM USER_TABLES) LOOP
        IF t.TABLE_NAME IN ('LOG_AUDITORIA','PQR_HISTORIAL') THEN
            EXECUTE IMMEDIATE 'GRANT SELECT, INSERT ON ' || t.TABLE_NAME || ' TO SIGCA_APP';
        ELSE
            EXECUTE IMMEDIATE 'GRANT SELECT, INSERT, UPDATE, DELETE ON ' || t.TABLE_NAME || ' TO SIGCA_APP';
        END IF;
    END LOOP;
    FOR s IN (SELECT SEQUENCE_NAME FROM USER_SEQUENCES) LOOP
        EXECUTE IMMEDIATE 'GRANT SELECT ON ' || s.SEQUENCE_NAME || ' TO SIGCA_APP';
    END LOOP;
    EXECUTE IMMEDIATE 'GRANT EXECUTE ON PKG_SIGCA TO SIGCA_APP';
END;
/
-- En Spring Boot: spring.jpa.properties.hibernate.default_schema=SIGCA
