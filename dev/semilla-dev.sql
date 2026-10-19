-- =====================================================================
-- SIGCA-EPQ · Datos SOLO para desarrollo local (no ejecutar en producción).
-- Funcionarios de prueba por rol (contraseña: Sigca.Dev2026, sin 2FA: lo
-- enrolan en el primer ingreso), tarifas y factores de subsidio de ejemplo
-- para los 9 municipios. Ejecutar como SIGCA después de instalar.sql.
-- =====================================================================
SET DEFINE OFF
DECLARE
    c_hash CONSTANT VARCHAR2(60) := '$2a$12$ka32bkHCPLq54Gy1r2Pg3.2TmtkoDdo22qtLuirUsIzpoaEG/7Eha';
    v_desde CONSTANT DATE := DATE '2025-01-01';

    PROCEDURE funcionario(p_cod VARCHAR2, p_user VARCHAR2, p_nombre VARCHAR2, p_rol VARCHAR2, p_dane VARCHAR2) IS
        v_id NUMBER;
    BEGIN
        INSERT INTO FUNCIONARIO (CODIGO_FUNCIONARIO, NOMBRE_COMPLETO, CORREO, USERNAME, PASSWORD_HASH,
                                 CAMBIO_PASSWORD_REQUERIDO, MUNICIPIO_ID)
        VALUES (p_cod, p_nombre, p_user || '@epq.com.co', p_user, c_hash, 'N',
                (SELECT ID FROM MUNICIPIO WHERE CODIGO_DANE = p_dane))
        RETURNING ID INTO v_id;
        INSERT INTO FUNCIONARIO_ROL (FUNCIONARIO_ID, ROL_ID) SELECT v_id, ID FROM ROL WHERE CODIGO = p_rol;
    END;
BEGIN
    funcionario('ASE-001', 'asesor', 'Ana Asesora', 'ASESOR', '63190');
    funcionario('PQR-001', 'gestor.circasia', 'Gabriel Gestor Circasia', 'GESTOR_PQR', '63190');
    funcionario('PQR-002', 'gestor.salento', 'Sara Gestora Salento', 'GESTOR_PQR', '63690');
    funcionario('PQR-100', 'jefe.pqr', 'Julia Jefa PQR', 'JEFE_PQR', NULL);
    funcionario('CAR-001', 'cartera', 'Carlos Cartera', 'GESTOR_CARTERA', NULL);
    funcionario('GER-001', 'gerente', 'Gloria Gerente', 'GERENTE', NULL);

    FOR m IN (SELECT ID FROM MUNICIPIO) LOOP
        INSERT INTO TARIFA (TIPO_SERVICIO, MUNICIPIO_ID, CARGO_FIJO, VALOR_M3_BASICO, VALOR_M3_COMPLEMENTARIO, VALOR_M3_SUNTUARIO,
                            CONSUMO_BASICO_M3, CONSUMO_SUNTUARIO_DESDE_M3, VIGENTE_DESDE, RESOLUCION)
        VALUES ('ACUEDUCTO', m.ID, 12000, 2500, 2500, 3000, 13, 26, v_desde, 'CRA 943 de 2021 (ejemplo)');
        INSERT INTO TARIFA (TIPO_SERVICIO, MUNICIPIO_ID, CARGO_FIJO, VALOR_M3_BASICO, VALOR_M3_COMPLEMENTARIO, VALOR_M3_SUNTUARIO,
                            CONSUMO_BASICO_M3, CONSUMO_SUNTUARIO_DESDE_M3, VIGENTE_DESDE, RESOLUCION)
        VALUES ('ALCANTARILLADO', m.ID, 8000, 1900, 1900, 2300, 13, 26, v_desde, 'CRA 943 de 2021 (ejemplo)');
        INSERT INTO TARIFA (TIPO_SERVICIO, MUNICIPIO_ID, CARGO_FIJO, VALOR_M3_BASICO, VIGENTE_DESDE, RESOLUCION)
        VALUES ('GAS', m.ID, 9000, 3100, v_desde, 'CREG 102 de 2020 (ejemplo)');
        FOR s IN (SELECT 'ACUEDUCTO' T FROM DUAL UNION ALL SELECT 'ALCANTARILLADO' FROM DUAL) LOOP
            INSERT INTO FACTOR_SUBSIDIO (MUNICIPIO_ID, TIPO_SERVICIO, USO, ESTRATO, PORCENTAJE, VIGENTE_DESDE, ACUERDO_MUNICIPAL)
            SELECT m.ID, s.T, 'RESIDENCIAL', e, p, v_desde, 'Acuerdo 012 de 2024 (ejemplo)'
              FROM (SELECT 1 e, -70 p FROM DUAL UNION ALL SELECT 2, -40 FROM DUAL UNION ALL SELECT 3, -15 FROM DUAL
                    UNION ALL SELECT 4, 0 FROM DUAL UNION ALL SELECT 5, 50 FROM DUAL UNION ALL SELECT 6, 60 FROM DUAL);
            INSERT INTO FACTOR_SUBSIDIO (MUNICIPIO_ID, TIPO_SERVICIO, USO, ESTRATO, PORCENTAJE, VIGENTE_DESDE, ACUERDO_MUNICIPAL)
            SELECT m.ID, s.T, u, NULL, p, v_desde, 'Acuerdo 012 de 2024 (ejemplo)'
              FROM (SELECT 'COMERCIAL' u, 50 p FROM DUAL UNION ALL SELECT 'INDUSTRIAL', 30 FROM DUAL
                    UNION ALL SELECT 'OFICIAL', 0 FROM DUAL);
        END LOOP;
    END LOOP;
    COMMIT;
END;
/
