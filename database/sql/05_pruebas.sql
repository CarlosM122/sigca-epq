-- =====================================================================
-- SIGCA-EPQ  ·  05_pruebas.sql
-- Pruebas de las validaciones de la base de datos. Crea datos de prueba,
-- recorre los flujos de negocio con operaciones válidas e inválidas,
-- verifica el resultado y al final hace ROLLBACK (no deja nada).
-- Termina con error si alguna prueba falla.
--   sqlplus SIGCA/<clave>@localhost:1521/FREEPDB1 @05_pruebas.sql
--
-- Las fechas son relativas a SYSDATE: los periodos P1..P6 son los meses
-- -7..-2 respecto al mes actual, así la prueba sirve en cualquier fecha.
-- =====================================================================
SET SERVEROUTPUT ON SIZE UNLIMITED
SET DEFINE OFF
SET FEEDBACK OFF

DECLARE
    v_ok      PLS_INTEGER := 0;
    v_fallas  PLS_INTEGER := 0;

    TYPE t_num IS TABLE OF NUMBER INDEX BY PLS_INTEGER;
    p         t_num;          -- periodos P1..P6

    v_circ NUMBER; v_salento NUMBER; v_buenavista NUMBER; v_sector_bv NUMBER;
    v_asesor NUMBER; v_gestor NUMBER; v_gestor_sal NUMBER; v_jefe NUMBER; v_cartera NUMBER;
    v_u1 NUMBER; v_u2 NUMBER; v_pr1 NUMBER; v_pr2 NUMBER;
    v_med_acu NUMBER; v_med_gas NUMBER; v_med_acu2 NUMBER;
    v_c_acu NUMBER; v_c_alc NUMBER; v_c_gas NUMBER; v_c2_acu NUMBER;
    v_f3 NUMBER; v_f3r NUMBER; v_f4 NUMBER; v_f5 NUMBER; v_f6 NUMBER; v_g4 NUMBER; v_g5 NUMBER;
    v_l_acu6 NUMBER; v_l_alc6 NUMBER; v_l_gas6 NUMBER; v_l_acu4 NUMBER;
    v_lec NUMBER; v_pqr NUMBER; v_pet NUMBER; v_rec NUMBER; v_queja NUMBER;
    v_cobro NUMBER; v_cobro2 NUMBER; v_orden NUMBER; v_tx NUMBER; v_pago NUMBER;
    v_ac NUMBER; v_q0 NUMBER; v_q1 NUMBER; v_q2 NUMBER;
    v_txt  VARCHAR2(1000);
    v_ins_pqr VARCHAR2(400);
    v_num  NUMBER;
    v_num2 NUMBER;
    v_fecha DATE;
    c_hash CONSTANT VARCHAR2(64) := RPAD('$2a$12$', 60, 'x');
    c_cifrado CONSTANT VARCHAR2(40) := 'QUJDREVGR0hJSktMTU5PUFFSU1RVVg==';
    c_texto CONSTANT VARCHAR2(200) := 'Se revisó el caso con la evidencia adjunta y se toma la decisión indicada.';

    PROCEDURE ok(t VARCHAR2) IS BEGIN v_ok := v_ok + 1; DBMS_OUTPUT.PUT_LINE('  OK     ' || t); END;
    PROCEDURE falla(t VARCHAR2) IS BEGIN v_fallas := v_fallas + 1; DBMS_OUTPUT.PUT_LINE('  FALLA  ' || t); END;
    PROCEDURE titulo(t VARCHAR2) IS BEGIN DBMS_OUTPUT.PUT_LINE('-- ' || t); END;

    PROCEDURE espera_error(p_nombre VARCHAR2, p_sql VARCHAR2, p_codigo PLS_INTEGER) IS
    BEGIN
        EXECUTE IMMEDIATE p_sql;
        falla(p_nombre || ' -> se esperaba ORA' || p_codigo || ' y no hubo error');
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE = p_codigo THEN ok(p_nombre || '  [' || SQLCODE || ']');
            ELSE falla(p_nombre || ' -> ' || SQLERRM); END IF;
    END;

    PROCEDURE afirma(p_nombre VARCHAR2, p_cond BOOLEAN) IS
    BEGIN
        IF p_cond THEN ok(p_nombre); ELSE falla(p_nombre); END IF;
    END;

    FUNCTION estado(p_tabla VARCHAR2, p_id NUMBER) RETURN VARCHAR2 IS
        v VARCHAR2(30);
    BEGIN
        EXECUTE IMMEDIATE 'SELECT ESTADO FROM ' || p_tabla || ' WHERE ID = :1' INTO v USING p_id;
        RETURN v;
    END;

    FUNCTION nuevo_funcionario(p_cod VARCHAR2, p_user VARCHAR2, p_mun NUMBER, p_rol VARCHAR2) RETURN NUMBER IS
        v_id NUMBER;
    BEGIN
        INSERT INTO FUNCIONARIO (CODIGO_FUNCIONARIO, NOMBRE_COMPLETO, CORREO, USERNAME, PASSWORD_HASH, MUNICIPIO_ID)
        VALUES (p_cod, 'Funcionario ' || p_user, p_user || '@epq.com.co', p_user, c_hash, p_mun)
        RETURNING ID INTO v_id;
        INSERT INTO FUNCIONARIO_ROL (FUNCIONARIO_ID, ROL_ID) SELECT v_id, ID FROM ROL WHERE CODIGO = p_rol;
        RETURN v_id;
    END;

    FUNCTION nuevo_usuario(p_nombre VARCHAR2, p_hash CHAR, p_mun NUMBER) RETURN NUMBER IS
        v_id NUMBER;
    BEGIN
        INSERT INTO USUARIO (TIPO_PERSONA, TIPO_IDENTIFICACION, NUMERO_IDENTIFICACION_CIFRADO, NUMERO_IDENTIFICACION_HASH,
                             NUMERO_IDENTIFICACION_MASCARA, NOMBRE_COMPLETO, CORREO, TELEFONO, DIRECCION_NOTIFICACION,
                             MUNICIPIO_ID, AUTORIZA_TRATAMIENTO_DATOS, FECHA_AUTORIZACION_DATOS)
        VALUES ('NATURAL', 'CC', c_cifrado, RPAD(p_hash, 64, p_hash), '******5678', p_nombre,
                'titular' || p_hash || '@correo.co', '3101234567', 'Calle 5 # 10-20', p_mun, 'S', SYSTIMESTAMP)
        RETURNING ID INTO v_id;
        RETURN v_id;
    END;

    -- Lectura del medidor en el periodo; NULL en p_actual = no se tomó (causa PREDIO_CERRADO)
    FUNCTION lectura(p_medidor NUMBER, p_periodo NUMBER, p_actual NUMBER) RETURN NUMBER IS
        v_id NUMBER;
    BEGIN
        INSERT INTO LECTURA (MEDIDOR_ID, PERIODO, FECHA_LECTURA, LECTURA_ANTERIOR, LECTURA_ACTUAL, CAUSA_NO_LECTURA, REGISTRADA_POR)
        VALUES (p_medidor, p_periodo, PKG_SIGCA.fin_periodo(p_periodo), 0, p_actual,
                CASE WHEN p_actual IS NULL THEN 'PREDIO_CERRADO' END, v_asesor)
        RETURNING ID INTO v_id;
        RETURN v_id;
    END;

    FUNCTION lectura_de(p_medidor NUMBER, p_periodo NUMBER) RETURN NUMBER IS
        v_id NUMBER;
    BEGIN
        SELECT ID INTO v_id FROM LECTURA WHERE MEDIDOR_ID = p_medidor AND PERIODO = p_periodo;
        RETURN v_id;
    END;

    FUNCTION nueva_factura(p_usuario NUMBER, p_predio NUMBER, p_periodo NUMBER) RETURN NUMBER IS
        v_id NUMBER;
        v_em DATE := PKG_SIGCA.fin_periodo(p_periodo) + 1;
    BEGIN
        INSERT INTO FACTURA (USUARIO_ID, PREDIO_ID, PERIODO, FECHA_EMISION, FECHA_VENCIMIENTO)
        VALUES (p_usuario, p_predio, p_periodo, v_em, v_em + 1)
        RETURNING ID INTO v_id;
        RETURN v_id;
    END;

    -- Liquida la línea como lo haría el backend (tarifa y factor vigentes al cierre del periodo).
    -- p_error altera el valor del consumo para probar que la base rechaza liquidaciones erradas.
    FUNCTION linea(p_factura NUMBER, p_contrato NUMBER, p_lectura NUMBER, p_error NUMBER DEFAULT 0) RETURN NUMBER IS
        v_tipo VARCHAR2(20); v_mun NUMBER; v_uso VARCHAR2(20); v_estrato NUMBER; v_periodo NUMBER; v_fin DATE;
        v_tar TARIFA%ROWTYPE; v_fsu NUMBER; v_pct NUMBER := 0; v_cons NUMBER;
        v_b NUMBER; v_c NUMBER; v_s NUMBER; v_valor NUMBER; v_sub NUMBER := 0; v_con NUMBER := 0;
        v_id NUMBER;
    BEGIN
        SELECT C.TIPO_SERVICIO, P.MUNICIPIO_ID, P.USO, P.ESTRATO, F.PERIODO
          INTO v_tipo, v_mun, v_uso, v_estrato, v_periodo
          FROM FACTURA F JOIN PREDIO P ON P.ID = F.PREDIO_ID JOIN CONTRATO_SERVICIO C ON C.ID = p_contrato
         WHERE F.ID = p_factura;
        v_fin := PKG_SIGCA.fin_periodo(v_periodo);
        SELECT * INTO v_tar FROM TARIFA
         WHERE TIPO_SERVICIO = v_tipo AND MUNICIPIO_ID = v_mun AND VIGENTE_DESDE <= v_fin
           AND (VIGENTE_HASTA IS NULL OR VIGENTE_HASTA >= v_fin);
        SELECT CONSUMO INTO v_cons FROM LECTURA WHERE ID = p_lectura;
        IF v_tipo = 'GAS' THEN
            v_b := v_cons; v_c := 0; v_s := 0;
        ELSE
            SELECT ID, PORCENTAJE INTO v_fsu, v_pct FROM FACTOR_SUBSIDIO
             WHERE TIPO_SERVICIO = v_tipo AND MUNICIPIO_ID = v_mun AND USO = v_uso AND NVL(ESTRATO, 0) = NVL(v_estrato, 0)
               AND VIGENTE_DESDE <= v_fin AND (VIGENTE_HASTA IS NULL OR VIGENTE_HASTA >= v_fin);
            v_b := LEAST(v_cons, v_tar.CONSUMO_BASICO_M3);
            v_s := GREATEST(v_cons - v_tar.CONSUMO_SUNTUARIO_DESDE_M3, 0);
            v_c := v_cons - v_b - v_s;
        END IF;
        v_valor := ROUND(v_b * v_tar.VALOR_M3_BASICO + v_c * NVL(v_tar.VALOR_M3_COMPLEMENTARIO, 0)
                         + v_s * NVL(v_tar.VALOR_M3_SUNTUARIO, 0), 2) + p_error;
        IF v_pct < 0 THEN
            v_sub := ROUND(v_b * v_tar.VALOR_M3_BASICO * v_pct / 100, 2);
        ELSIF v_pct > 0 THEN
            v_con := ROUND((v_tar.CARGO_FIJO + v_valor) * v_pct / 100, 2);
        END IF;
        INSERT INTO LINEA_FACTURA (FACTURA_ID, CONTRATO_ID, TIPO_SERVICIO, TARIFA_ID, FACTOR_SUBSIDIO_ID, LECTURA_ID,
                                   CONSUMO, CONSUMO_BASICO, CONSUMO_COMPLEMENTARIO, CONSUMO_SUNTUARIO,
                                   CARGO_FIJO, VALOR_CONSUMO, SUBSIDIO, CONTRIBUCION, TOTAL_LINEA)
        VALUES (p_factura, p_contrato, v_tipo, v_tar.ID, v_fsu, p_lectura, v_cons, v_b, v_c, v_s,
                v_tar.CARGO_FIJO, v_valor, v_sub, v_con, v_tar.CARGO_FIJO + v_valor + v_sub + v_con)
        RETURNING ID INTO v_id;
        RETURN v_id;
    END;

    PROCEDURE espera_error_linea(p_nombre VARCHAR2, p_factura NUMBER, p_contrato NUMBER, p_lectura NUMBER,
                                 p_error NUMBER, p_codigo PLS_INTEGER) IS
        v_id NUMBER;
    BEGIN
        v_id := linea(p_factura, p_contrato, p_lectura, p_error);
        falla(p_nombre || ' -> se esperaba ORA' || p_codigo || ' y no hubo error');
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE = p_codigo THEN ok(p_nombre || '  [' || SQLCODE || ']');
            ELSE falla(p_nombre || ' -> ' || SQLERRM); END IF;
    END;

    FUNCTION total(p_factura NUMBER, p_columna VARCHAR2) RETURN NUMBER IS
        v NUMBER;
    BEGIN
        EXECUTE IMMEDIATE 'SELECT ' || p_columna || ' FROM FACTURA WHERE ID = :1' INTO v USING p_factura;
        RETURN v;
    END;

    PROCEDURE evidencia(p_pqr NUMBER) IS
    BEGIN
        INSERT INTO PQR_ADJUNTO (PQR_ID, TIPO, NOMBRE_ARCHIVO, TIPO_MIME, TAMANO_BYTES, HASH_SHA256, CONTENIDO, CARGADO_POR)
        VALUES (p_pqr, 'EVIDENCIA', 'acta.pdf', 'application/pdf', 4, RPAD('0', 64, '0'), HEXTORAW('25504446'), v_gestor);
    END;

    FUNCTION pqr(p_tipo VARCHAR2, p_contrato NUMBER, p_linea NUMBER, p_gestor NUMBER,
                 p_origen NUMBER DEFAULT NULL, p_apelacion CHAR DEFAULT 'N') RETURN NUMBER IS
        v_id NUMBER;
    BEGIN
        INSERT INTO PQR (TIPO, CONTRATO_ID, MUNICIPIO_ID, LINEA_FACTURA_ID, PQR_ORIGEN_ID, SOLICITA_APELACION,
                         DESCRIPCION, NOMBRE_CONTACTO, CORREO_CONTACTO, TELEFONO_CONTACTO,
                         FECHA_LIMITE_RESPUESTA, GESTOR_ID, NUMERO_RADICADO)
        VALUES (p_tipo, p_contrato, 0, p_linea, p_origen, p_apelacion,
                'El consumo facturado no corresponde al uso real del predio en el periodo.',
                'José Núñez', 'jose.nunez@correo.co', '3101234567', DATE '2000-01-01', p_gestor, 'X')
        RETURNING ID INTO v_id;
        RETURN v_id;
    END;
BEGIN
    DBMS_OUTPUT.PUT_LINE('== Pruebas del modelo SIGCA-EPQ');
    FOR i IN 1 .. 6 LOOP
        p(i) := TO_NUMBER(TO_CHAR(ADD_MONTHS(TRUNC(SYSDATE, 'MM'), i - 8), 'YYYYMM'));
    END LOOP;
    SELECT ID INTO v_circ       FROM MUNICIPIO WHERE NOMBRE = 'Circasia';
    SELECT ID INTO v_salento    FROM MUNICIPIO WHERE NOMBRE = 'Salento';
    SELECT ID INTO v_buenavista FROM MUNICIPIO WHERE NOMBRE = 'Buenavista';
    SELECT ID INTO v_sector_bv  FROM SECTOR WHERE MUNICIPIO_ID = v_buenavista;

    -- -----------------------------------------------------------------
    titulo('Catálogos, parámetros y seguridad');
    SELECT COUNT(*) INTO v_num FROM MUNICIPIO WHERE ACTIVO = 'S';
    SELECT COUNT(*) INTO v_num2 FROM MUNICIPIO WHERE NOMBRE LIKE 'C_rdoba';
    afirma('9 municipios atendidos y sin Córdoba', v_num = 9 AND v_num2 = 0);
    espera_error('municipio con código DANE fuera del Quindío',
        'INSERT INTO MUNICIPIO (CODIGO_DANE, NOMBRE) VALUES (''05001'', ''Medellín'')', -2290);
    espera_error('candidato a suspensión con más de 3 periodos',
        'UPDATE PARAMETRO_SISTEMA SET VALOR = ''4'' WHERE CLAVE = ''MORA_PERIODOS_SUSPENSION''', -2290);
    espera_error('plazo de PQR mayor a 15 días hábiles',
        'UPDATE PARAMETRO_SISTEMA SET VALOR = ''16'' WHERE CLAVE = ''PQR_DIAS_QUEJA''', -20011);
    espera_error('plazo para recurrir menor a 5 días',
        'UPDATE PARAMETRO_SISTEMA SET VALOR = ''4'' WHERE CLAVE = ''RECURSO_DIAS_PLAZO''', -20011);
    espera_error('interés residencial por encima del 6 %',
        'UPDATE PARAMETRO_SISTEMA SET VALOR = ''7'' WHERE CLAVE = ''MORA_TASA_RESIDENCIAL_ANUAL''', -20011);
    espera_error('reconocimiento del silencio en más de 72 horas',
        'UPDATE PARAMETRO_SISTEMA SET VALOR = ''80'' WHERE CLAVE = ''PQR_HORAS_RECONOCIMIENTO_SILENCIO''', -20011);
    UPDATE PARAMETRO_SISTEMA SET VALOR = '3' WHERE CLAVE = 'MORA_PERIODOS_SUSPENSION';
    UPDATE PARAMETRO_SISTEMA SET VALOR = '2' WHERE CLAVE = 'MORA_PERIODOS_SUSPENSION';
    ok('MORA_PERIODOS_SUSPENSION acepta 3 y vuelve a 2');

    v_asesor     := nuevo_funcionario('T-ASE', 't.asesor',  NULL,      'ASESOR');
    v_gestor     := nuevo_funcionario('T-GPQ', 't.gestor',  v_circ,    'GESTOR_PQR');
    v_gestor_sal := nuevo_funcionario('T-GPS', 't.gestors', v_salento, 'GESTOR_PQR');
    v_jefe       := nuevo_funcionario('T-JPQ', 't.jefe',    NULL,      'JEFE_PQR');
    v_cartera    := nuevo_funcionario('T-CAR', 't.cartera', NULL,      'GESTOR_CARTERA');
    espera_error('rol CIUDADANO a un funcionario',
        'INSERT INTO FUNCIONARIO_ROL (FUNCIONARIO_ID, ROL_ID) SELECT ' || v_asesor || ', ID FROM ROL WHERE CODIGO = ''CIUDADANO''', -20015);
    espera_error('Gestor de PQR sin municipio',
        'INSERT INTO FUNCIONARIO_ROL (FUNCIONARIO_ID, ROL_ID) SELECT ' || v_jefe || ', ID FROM ROL WHERE CODIGO = ''GESTOR_PQR''', -20015);
    espera_error('inicio de sesión sin segundo factor',
        'UPDATE FUNCIONARIO SET ULTIMO_ACCESO = SYSTIMESTAMP WHERE ID = ' || v_asesor, -2290);
    UPDATE FUNCIONARIO SET DOBLE_FACTOR_ACTIVO = 'S', SECRETO_DOBLE_FACTOR = 'JBSWY3DPEHPK3PXP', ULTIMO_ACCESO = SYSTIMESTAMP
     WHERE ID = v_asesor;
    ok('inicio de sesión con segundo factor enrolado');

    -- -----------------------------------------------------------------
    titulo('Usuarios, predios, medidores y contratos');
    v_u1 := nuevo_usuario('José Ñúñez Pérez', 'a', v_circ);
    v_u2 := nuevo_usuario('Ana Gómez Ríos', 'b', v_salento);
    SELECT CODIGO INTO v_txt FROM USUARIO WHERE ID = v_u1;
    afirma('código de usuario generado (' || v_txt || ')', REGEXP_LIKE(v_txt, '^USR-[0-9]{8}$'));
    v_txt := 'INSERT INTO USUARIO (TIPO_PERSONA, TIPO_IDENTIFICACION, NUMERO_IDENTIFICACION_CIFRADO, NUMERO_IDENTIFICACION_HASH, '
          || 'NUMERO_IDENTIFICACION_MASCARA, NOMBRE_COMPLETO, CORREO, TELEFONO, DIRECCION_NOTIFICACION, MUNICIPIO_ID, '
          || 'AUTORIZA_TRATAMIENTO_DATOS, FECHA_AUTORIZACION_DATOS) VALUES (''NATURAL'',';
    espera_error('NIT con persona natural',
        v_txt || '''NIT'',''' || c_cifrado || ''',RPAD(''c'',64,''c''),''****1234'',''Ana Gómez'',''ana@correo.co'',''3101234567'',''Calle 1 # 2-3'',' || v_circ || ',''S'',SYSTIMESTAMP)', -2290);
    espera_error('teléfono con formato inválido',
        v_txt || '''CC'',''' || c_cifrado || ''',RPAD(''d'',64,''d''),''****1234'',''Ana Gómez'',''ana@correo.co'',''12345'',''Calle 1 # 2-3'',' || v_circ || ',''S'',SYSTIMESTAMP)', -2290);
    espera_error('sin autorización de tratamiento de datos',
        v_txt || '''CC'',''' || c_cifrado || ''',RPAD(''9'',64,''9''),''****1234'',''Ana Gómez'',''ana@correo.co'',''3101234567'',''Calle 1 # 2-3'',' || v_circ || ',''N'',SYSTIMESTAMP)', -2290);
    espera_error('identificación duplicada (mismo tipo y hash)',
        v_txt || '''CC'',''' || c_cifrado || ''',RPAD(''a'',64,''a''),''****1234'',''Ana Gómez'',''ana@correo.co'',''3101234567'',''Calle 1 # 2-3'',' || v_circ || ',''S'',SYSTIMESTAMP)', -1);

    INSERT INTO PREDIO (DIRECCION, MUNICIPIO_ID, USO, ESTRATO, FICHA_CATASTRAL)
    VALUES ('Calle 5 # 10-20', v_circ, 'RESIDENCIAL', 2, RPAD('63190', 30, '0')) RETURNING ID INTO v_pr1;
    INSERT INTO PREDIO (DIRECCION, MUNICIPIO_ID, USO, ESTRATO) VALUES ('Carrera 6 # 3-15', v_salento, 'RESIDENCIAL', 5)
    RETURNING ID INTO v_pr2;
    espera_error('predio residencial sin estrato',
        'INSERT INTO PREDIO (DIRECCION, MUNICIPIO_ID, USO) VALUES (''Calle 9 # 9-9'',' || v_circ || ',''RESIDENCIAL'')', -2290);
    espera_error('predio comercial con estrato',
        'INSERT INTO PREDIO (DIRECCION, MUNICIPIO_ID, USO, ESTRATO) VALUES (''Calle 9 # 9-9'',' || v_circ || ',''COMERCIAL'',3)', -2290);
    espera_error('ficha catastral que no tiene 30 dígitos',
        'INSERT INTO PREDIO (DIRECCION, MUNICIPIO_ID, USO, ESTRATO, FICHA_CATASTRAL) VALUES (''Calle 9 # 9-9'',' || v_circ || ',''RESIDENCIAL'',3,''12345'')', -2290);
    espera_error('ficha catastral repetida',
        'INSERT INTO PREDIO (DIRECCION, MUNICIPIO_ID, USO, ESTRATO, FICHA_CATASTRAL) VALUES (''Calle 9 # 9-9'',' || v_circ || ',''RESIDENCIAL'',3,RPAD(''63190'',30,''0''))', -1);
    espera_error('sector rural de otro municipio',
        'INSERT INTO PREDIO (DIRECCION, MUNICIPIO_ID, USO, ESTRATO, SECTOR_ID) VALUES (''Vereda El Cairo'',' || v_circ || ',''RESIDENCIAL'',1,' || v_sector_bv || ')', -2291);
    INSERT INTO PREDIO (DIRECCION, MUNICIPIO_ID, USO, ESTRATO, SECTOR_ID)
    VALUES ('Vereda La Granja finca 3', v_buenavista, 'RESIDENCIAL', 1, v_sector_bv) RETURNING ID INTO v_num;
    ok('predio rural en un sector de su municipio');

    v_fecha := PKG_SIGCA.inicio_periodo(p(1));
    INSERT INTO MEDIDOR (SERIAL, PREDIO_ID, TIPO_SERVICIO, FECHA_INSTALACION, LECTURA_INICIAL)
    VALUES ('ACU-T0001', v_pr1, 'ACUEDUCTO', v_fecha, 100) RETURNING ID INTO v_med_acu;
    INSERT INTO MEDIDOR (SERIAL, PREDIO_ID, TIPO_SERVICIO, FECHA_INSTALACION, LECTURA_INICIAL)
    VALUES ('GAS-T0001', v_pr1, 'GAS', v_fecha, 0) RETURNING ID INTO v_med_gas;
    INSERT INTO MEDIDOR (SERIAL, PREDIO_ID, TIPO_SERVICIO, FECHA_INSTALACION, LECTURA_INICIAL)
    VALUES ('ACU-T0002', v_pr2, 'ACUEDUCTO', PKG_SIGCA.inicio_periodo(p(4)), 0) RETURNING ID INTO v_med_acu2;
    espera_error('medidor de alcantarillado (se liquida con el de acueducto)',
        'INSERT INTO MEDIDOR (SERIAL, PREDIO_ID, TIPO_SERVICIO, FECHA_INSTALACION) VALUES (''ALC-T0001'',' || v_pr1 || ',''ALCANTARILLADO'',TRUNC(SYSDATE))', -2290);

    espera_error('alcantarillado sin acueducto vigente',
        'INSERT INTO CONTRATO_SERVICIO (USUARIO_ID, PREDIO_ID, TIPO_SERVICIO, FECHA_VINCULACION, FUNCIONARIO_ID) VALUES ('
        || v_u2 || ',' || v_pr2 || ',''ALCANTARILLADO'',TRUNC(SYSDATE),' || v_asesor || ')', -20002);
    INSERT INTO CONTRATO_SERVICIO (USUARIO_ID, PREDIO_ID, TIPO_SERVICIO, MEDIDOR_ID, FECHA_VINCULACION, FUNCIONARIO_ID)
    VALUES (v_u1, v_pr1, 'ACUEDUCTO', v_med_acu, v_fecha, v_asesor) RETURNING ID INTO v_c_acu;
    INSERT INTO CONTRATO_SERVICIO (USUARIO_ID, PREDIO_ID, TIPO_SERVICIO, FECHA_VINCULACION, FUNCIONARIO_ID)
    VALUES (v_u1, v_pr1, 'ALCANTARILLADO', v_fecha, v_asesor) RETURNING ID INTO v_c_alc;
    INSERT INTO CONTRATO_SERVICIO (USUARIO_ID, PREDIO_ID, TIPO_SERVICIO, MEDIDOR_ID, FECHA_VINCULACION, FUNCIONARIO_ID)
    VALUES (v_u1, v_pr1, 'GAS', v_med_gas, v_fecha, v_asesor) RETURNING ID INTO v_c_gas;
    INSERT INTO CONTRATO_SERVICIO (USUARIO_ID, PREDIO_ID, TIPO_SERVICIO, MEDIDOR_ID, FECHA_VINCULACION, FUNCIONARIO_ID)
    VALUES (v_u2, v_pr2, 'ACUEDUCTO', v_med_acu2, PKG_SIGCA.inicio_periodo(p(4)), v_asesor) RETURNING ID INTO v_c2_acu;
    SELECT NUMERO_CONTRATO INTO v_txt FROM CONTRATO_SERVICIO WHERE ID = v_c_acu;
    afirma('número de contrato generado (' || v_txt || ')', REGEXP_LIKE(v_txt, '^CTR-[0-9]{8}$'));
    espera_error('suspender un contrato a mano (sin orden de servicio)',
        'UPDATE CONTRATO_SERVICIO SET ESTADO = ''SUSPENDIDO'', MOTIVO_ESTADO = ''Mora'' WHERE ID = ' || v_c_acu, -20020);
    espera_error('cancelar el acueducto con alcantarillado vigente',
        'UPDATE CONTRATO_SERVICIO SET ESTADO = ''CANCELADO'', MOTIVO_ESTADO = ''Solicitud del usuario'' WHERE ID = ' || v_c_acu, -20002);

    -- -----------------------------------------------------------------
    titulo('Tarifas CRA y factores de subsidio');
    v_fecha := ADD_MONTHS(TRUNC(SYSDATE, 'MM'), -12);
    INSERT INTO TARIFA (TIPO_SERVICIO, MUNICIPIO_ID, CARGO_FIJO, VALOR_M3_BASICO, VALOR_M3_COMPLEMENTARIO, VALOR_M3_SUNTUARIO,
                        CONSUMO_BASICO_M3, CONSUMO_SUNTUARIO_DESDE_M3, VIGENTE_DESDE, RESOLUCION)
    VALUES ('ACUEDUCTO', v_circ, 12000, 2500, 2500, 3000, 13, 26, v_fecha, 'CRA 943 de 2021');
    INSERT INTO TARIFA (TIPO_SERVICIO, MUNICIPIO_ID, CARGO_FIJO, VALOR_M3_BASICO, VALOR_M3_COMPLEMENTARIO, VALOR_M3_SUNTUARIO,
                        CONSUMO_BASICO_M3, CONSUMO_SUNTUARIO_DESDE_M3, VIGENTE_DESDE, RESOLUCION)
    VALUES ('ALCANTARILLADO', v_circ, 8000, 1900, 1900, 2300, 13, 26, v_fecha, 'CRA 943 de 2021');
    INSERT INTO TARIFA (TIPO_SERVICIO, MUNICIPIO_ID, CARGO_FIJO, VALOR_M3_BASICO, VIGENTE_DESDE, RESOLUCION)
    VALUES ('GAS', v_circ, 9000, 3100, v_fecha, 'CREG 102 de 2020');
    INSERT INTO TARIFA (TIPO_SERVICIO, MUNICIPIO_ID, CARGO_FIJO, VALOR_M3_BASICO, VALOR_M3_COMPLEMENTARIO, VALOR_M3_SUNTUARIO,
                        CONSUMO_BASICO_M3, CONSUMO_SUNTUARIO_DESDE_M3, VIGENTE_DESDE, RESOLUCION)
    VALUES ('ACUEDUCTO', v_salento, 11000, 2400, 2400, 2900, 13, 26, v_fecha, 'CRA 943 de 2021') RETURNING ID INTO v_num;
    espera_error('tarifa de gas con rangos de consumo',
        'INSERT INTO TARIFA (TIPO_SERVICIO, MUNICIPIO_ID, CARGO_FIJO, VALOR_M3_BASICO, VALOR_M3_COMPLEMENTARIO, VIGENTE_DESDE, RESOLUCION) VALUES (''GAS'','
        || v_salento || ',9000,3100,3100,TRUNC(SYSDATE),''CREG 102 de 2020'')', -2290);
    espera_error('tarifa de acueducto sin consumo básico',
        'INSERT INTO TARIFA (TIPO_SERVICIO, MUNICIPIO_ID, CARGO_FIJO, VALOR_M3_BASICO, VIGENTE_DESDE, RESOLUCION) VALUES (''ACUEDUCTO'','
        || v_buenavista || ',9000,3100,TRUNC(SYSDATE),''CRA 943 de 2021'')', -2290);
    espera_error('dos tarifas abiertas del mismo servicio y municipio',
        'INSERT INTO TARIFA (TIPO_SERVICIO, MUNICIPIO_ID, CARGO_FIJO, VALOR_M3_BASICO, VIGENTE_DESDE, RESOLUCION) VALUES (''GAS'','
        || v_circ || ',9500,3200,TRUNC(SYSDATE),''CREG 102 de 2020'')', -1);
    espera_error('modificar el valor de una tarifa',
        'UPDATE TARIFA SET VALOR_M3_BASICO = 2600 WHERE ID = ' || v_num, -20014);

    INSERT INTO FACTOR_SUBSIDIO (MUNICIPIO_ID, TIPO_SERVICIO, USO, ESTRATO, PORCENTAJE, VIGENTE_DESDE, ACUERDO_MUNICIPAL)
    VALUES (v_circ, 'ACUEDUCTO', 'RESIDENCIAL', 2, -40, v_fecha, 'Acuerdo 012 de 2024');
    INSERT INTO FACTOR_SUBSIDIO (MUNICIPIO_ID, TIPO_SERVICIO, USO, ESTRATO, PORCENTAJE, VIGENTE_DESDE, ACUERDO_MUNICIPAL)
    VALUES (v_circ, 'ALCANTARILLADO', 'RESIDENCIAL', 2, -40, v_fecha, 'Acuerdo 012 de 2024');
    INSERT INTO FACTOR_SUBSIDIO (MUNICIPIO_ID, TIPO_SERVICIO, USO, ESTRATO, PORCENTAJE, VIGENTE_DESDE, ACUERDO_MUNICIPAL)
    VALUES (v_salento, 'ACUEDUCTO', 'RESIDENCIAL', 5, 50, v_fecha, 'Acuerdo 007 de 2024');
    v_txt := 'INSERT INTO FACTOR_SUBSIDIO (MUNICIPIO_ID, TIPO_SERVICIO, USO, ESTRATO, PORCENTAJE, VIGENTE_DESDE, ACUERDO_MUNICIPAL) VALUES ('
          || v_circ || ',''ACUEDUCTO'',';
    espera_error('subsidio estrato 3 mayor al 15 %', v_txt || '''RESIDENCIAL'',3,-20,TRUNC(SYSDATE),''Acuerdo 012 de 2024'')', -2290);
    espera_error('estrato 4 con subsidio o contribución', v_txt || '''RESIDENCIAL'',4,5,TRUNC(SYSDATE),''Acuerdo 012 de 2024'')', -2290);
    espera_error('subsidio para uso comercial', v_txt || '''COMERCIAL'',NULL,-10,TRUNC(SYSDATE),''Acuerdo 012 de 2024'')', -2290);
    espera_error('factor para gas', 'INSERT INTO FACTOR_SUBSIDIO (MUNICIPIO_ID, TIPO_SERVICIO, USO, ESTRATO, PORCENTAJE, VIGENTE_DESDE, ACUERDO_MUNICIPAL) VALUES ('
        || v_circ || ',''GAS'',''RESIDENCIAL'',2,-40,TRUNC(SYSDATE),''Acuerdo 012 de 2024'')', -2290);
    espera_error('dos factores abiertos para el mismo estrato', v_txt || '''RESIDENCIAL'',2,-30,TRUNC(SYSDATE),''Acuerdo 013 de 2025'')', -1);

    -- -----------------------------------------------------------------
    titulo('Lecturas y crítica (promedio de 6 periodos, desviación 35 %)');
    v_lec := lectura(v_med_acu, p(1), 120);
    afirma('primera lectura del medidor queda CONFIRMADA (consumo 20)', estado('LECTURA', v_lec) = 'CONFIRMADA');
    v_lec := lectura(v_med_acu, p(2), 142);
    v_lec := lectura(v_med_acu, p(3), 163);
    afirma('lectura dentro del promedio queda CONFIRMADA', estado('LECTURA', v_lec) = 'CONFIRMADA');
    v_lec := lectura(v_med_acu, p(4), 200);
    SELECT DESVIACION_PCT, PROMEDIO_REFERENCIA INTO v_num, v_num2 FROM LECTURA WHERE ID = v_lec;
    afirma('consumo 37 sobre promedio 21 pasa a EN_REVISION (desviación ' || v_num || ' %)',
           estado('LECTURA', v_lec) = 'EN_REVISION' AND v_num2 = 21);
    SELECT LECTURA_ACTUAL, LECTURA_EN_REVISION INTO v_num, v_txt FROM MEDIDOR WHERE ID = v_med_acu;
    afirma('el medidor no avanza mientras la lectura está en revisión', v_num = 163 AND v_txt = 'S');
    espera_error('nueva lectura con otra en revisión',
        'INSERT INTO LECTURA (MEDIDOR_ID, PERIODO, FECHA_LECTURA, LECTURA_ANTERIOR, LECTURA_ACTUAL, REGISTRADA_POR) VALUES ('
        || v_med_acu || ',' || p(5) || ',TRUNC(SYSDATE),0,210,' || v_asesor || ')', -20017);

    -- Factura del periodo 4 con la lectura todavía en revisión
    v_f4 := nueva_factura(v_u1, v_pr1, p(4));
    espera_error_linea('facturar con la lectura en revisión', v_f4, v_c_acu, v_lec, 0, -20017);

    espera_error('revisión de lectura por quien no es Asesor',
        'UPDATE LECTURA SET ESTADO = ''CONFIRMADA'', REVISADA_POR = ' || v_gestor || ' WHERE ID = ' || v_lec, -20015);
    UPDATE LECTURA SET ESTADO = 'CONFIRMADA', REVISADA_POR = v_asesor WHERE ID = v_lec;
    SELECT LECTURA_ACTUAL, LECTURA_EN_REVISION INTO v_num, v_txt FROM MEDIDOR WHERE ID = v_med_acu;
    afirma('el Asesor confirma la lectura y el medidor avanza a 200', v_num = 200 AND v_txt = 'N');
    espera_error('cambiar los datos de una lectura',
        'UPDATE LECTURA SET LECTURA_ACTUAL = 199 WHERE ID = ' || v_lec, -20006);
    espera_error('eliminar una lectura', 'DELETE FROM LECTURA WHERE ID = ' || v_lec, -20006);
    v_txt := 'INSERT INTO LECTURA (MEDIDOR_ID, PERIODO, FECHA_LECTURA, LECTURA_ANTERIOR, LECTURA_ACTUAL, REGISTRADA_POR) VALUES (' || v_med_acu || ',';
    espera_error('lectura fuera de secuencia (salta un periodo)',
        v_txt || p(6) || ',' || 'TRUNC(SYSDATE),0,260,' || v_asesor || ')', -20017);
    espera_error('lectura menor que la anterior',
        v_txt || p(5) || ',TO_DATE(''' || TO_CHAR(PKG_SIGCA.fin_periodo(p(5)), 'YYYYMMDD') || ''',''YYYYMMDD''),0,150,' || v_asesor || ')', -20017);
    espera_error('lectura con fecha futura',
        v_txt || p(5) || ',TRUNC(SYSDATE) + 1,0,210,' || v_asesor || ')', -20008);
    espera_error('lectura con fecha de otro periodo',
        v_txt || p(5) || ',TO_DATE(''' || TO_CHAR(PKG_SIGCA.inicio_periodo(p(4)), 'YYYYMMDD') || ''',''YYYYMMDD''),0,210,' || v_asesor || ')', -20017);
    espera_error('lectura y causa de no lectura a la vez',
        'INSERT INTO LECTURA (MEDIDOR_ID, PERIODO, FECHA_LECTURA, LECTURA_ANTERIOR, LECTURA_ACTUAL, CAUSA_NO_LECTURA, REGISTRADA_POR) VALUES ('
        || v_med_acu || ',' || p(5) || ',TO_DATE(''' || TO_CHAR(PKG_SIGCA.fin_periodo(p(5)), 'YYYYMMDD') || ''',''YYYYMMDD''),0,210,''PREDIO_CERRADO'',' || v_asesor || ')', -2290);
    v_lec := lectura(v_med_acu, p(5), NULL);
    SELECT CONSUMO INTO v_num FROM LECTURA WHERE ID = v_lec;
    afirma('sin lectura se factura el promedio: ESTIMADA con 25 m3', estado('LECTURA', v_lec) = 'ESTIMADA' AND v_num = 25);
    v_lec := lectura(v_med_acu, p(6), 230);
    SELECT CONSUMO INTO v_num FROM LECTURA WHERE ID = v_lec;
    afirma('la lectura siguiente parte de la última confirmada (consumo 30)', estado('LECTURA', v_lec) = 'CONFIRMADA' AND v_num = 30);

    v_lec := lectura(v_med_gas, p(1), 10);
    v_lec := lectura(v_med_gas, p(2), 50);
    afirma('gas: consumo 40 sobre promedio 10 pasa a EN_REVISION', estado('LECTURA', v_lec) = 'EN_REVISION');
    UPDATE LECTURA SET ESTADO = 'ESTIMADA', REVISADA_POR = v_asesor WHERE ID = v_lec;
    SELECT L.CONSUMO, M.LECTURA_ACTUAL INTO v_num, v_num2 FROM LECTURA L JOIN MEDIDOR M ON M.ID = L.MEDIDOR_ID WHERE L.ID = v_lec;
    afirma('el Asesor rechaza la lectura: se estima el promedio y el medidor no avanza', v_num = 10 AND v_num2 = 10);
    v_lec := lectura(v_med_gas, p(3), 21);
    v_lec := lectura(v_med_gas, p(4), 31);
    v_lec := lectura(v_med_gas, p(5), 41);
    v_lec := lectura(v_med_gas, p(6), 51);
    afirma('gas: lecturas siguientes confirmadas', estado('LECTURA', v_lec) = 'CONFIRMADA');
    v_lec := lectura(v_med_acu2, p(4), 15);
    v_lec := lectura(v_med_acu2, p(5), 30);

    -- -----------------------------------------------------------------
    titulo('Facturación: una factura por predio, una línea por servicio');
    espera_error_linea('liquidación distinta a la tarifa', v_f4, v_c_acu, lectura_de(v_med_acu, p(4)), 100, -20018);
    espera_error_linea('lectura de otro periodo', v_f4, v_c_acu, lectura_de(v_med_acu, p(3)), 0, -20017);
    espera_error_linea('contrato de otro usuario', v_f4, v_c2_acu, lectura_de(v_med_acu2, p(4)), 0, -20002);
    v_l_acu4 := linea(v_f4, v_c_acu, lectura_de(v_med_acu, p(4)));
    v_num := linea(v_f4, v_c_alc, lectura_de(v_med_acu, p(4)));
    v_num := linea(v_f4, v_c_gas, lectura_de(v_med_gas, p(4)));
    SELECT COUNT(*) INTO v_num FROM LINEA_FACTURA
     WHERE ID = v_l_acu4 AND CONSUMO_BASICO = 13 AND CONSUMO_COMPLEMENTARIO = 13 AND CONSUMO_SUNTUARIO = 11
       AND VALOR_CONSUMO = 98000 AND SUBSIDIO = -13000 AND CONTRIBUCION = 0 AND TOTAL_LINEA = 97000;
    afirma('acueducto 37 m3 estrato 2: 13 básico + 13 complementario + 11 suntuario, subsidio 40 % del básico = 97.000', v_num = 1);
    afirma('factura P4 con 3 líneas y total 209.820 (97.000 + 72.820 + 40.000)',
           total(v_f4, 'NUMERO_LINEAS') = 3 AND total(v_f4, 'TOTAL_A_PAGAR') = 209820 AND total(v_f4, 'SALDO_PENDIENTE') = 209820);
    espera_error_linea('segunda línea del mismo contrato', v_f4, v_c_acu, lectura_de(v_med_acu, p(4)), 0, -1);
    espera_error('segunda factura del predio en el periodo',
        'INSERT INTO FACTURA (USUARIO_ID, PREDIO_ID, PERIODO, FECHA_EMISION, FECHA_VENCIMIENTO) VALUES ('
        || v_u1 || ',' || v_pr1 || ',' || p(4) || ',TRUNC(SYSDATE),TRUNC(SYSDATE)+15)', -1);
    espera_error('factura de un periodo que no ha terminado',
        'INSERT INTO FACTURA (USUARIO_ID, PREDIO_ID, PERIODO, FECHA_EMISION, FECHA_VENCIMIENTO) VALUES ('
        || v_u1 || ',' || v_pr1 || ',' || TO_CHAR(SYSDATE, 'YYYYMM') || ',TRUNC(SYSDATE),TRUNC(SYSDATE)+15)', -20019);
    espera_error('factura de un periodo omitido hace más de 5 meses (RN-34)',
        'INSERT INTO FACTURA (USUARIO_ID, PREDIO_ID, PERIODO, FECHA_EMISION, FECHA_VENCIMIENTO) VALUES ('
        || v_u1 || ',' || v_pr1 || ',' || p(1) || ',TRUNC(SYSDATE),TRUNC(SYSDATE)+15)', -20019);
    espera_error('modificar el total de la factura',
        'UPDATE FACTURA SET TOTAL_A_PAGAR = 1000 WHERE ID = ' || v_f4, -20014);
    espera_error('modificar el valor de una línea',
        'UPDATE LINEA_FACTURA SET CARGO_FIJO = 0, TOTAL_LINEA = 85000 WHERE ID = ' || v_l_acu4, -20014);
    espera_error('eliminar una línea', 'DELETE FROM LINEA_FACTURA WHERE ID = ' || v_l_acu4, -20006);
    espera_error('marcar PAGADA una factura sin pagos',
        'UPDATE FACTURA SET ESTADO = ''PAGADA'' WHERE ID = ' || v_f4, -20016);

    v_f5 := nueva_factura(v_u1, v_pr1, p(5));
    v_num := linea(v_f5, v_c_acu, lectura_de(v_med_acu, p(5)));
    SELECT CONSUMO_ESTIMADO INTO v_txt FROM LINEA_FACTURA WHERE ID = v_num;
    afirma('la línea con lectura estimada queda marcada', v_txt = 'S');
    v_num := linea(v_f5, v_c_alc, lectura_de(v_med_acu, p(5)));
    v_num := linea(v_f5, v_c_gas, lectura_de(v_med_gas, p(5)));
    afirma('factura P5 con promedio: total 147.120', total(v_f5, 'TOTAL_A_PAGAR') = 147120);

    v_f6 := nueva_factura(v_u1, v_pr1, p(6));
    v_l_acu6 := linea(v_f6, v_c_acu, lectura_de(v_med_acu, p(6)));
    v_l_alc6 := linea(v_f6, v_c_alc, lectura_de(v_med_acu, p(6)));
    v_l_gas6 := linea(v_f6, v_c_gas, lectura_de(v_med_gas, p(6)));
    afirma('factura P6: total 172.720', total(v_f6, 'TOTAL_A_PAGAR') = 172720);

    v_g4 := nueva_factura(v_u2, v_pr2, p(4));
    v_num := linea(v_g4, v_c2_acu, lectura_de(v_med_acu2, p(4)));
    SELECT CONTRIBUCION, TOTAL_LINEA INTO v_num, v_num2 FROM LINEA_FACTURA WHERE ID = v_num;
    afirma('estrato 5: contribución del 50 % sobre cargo fijo y consumo (23.500, total 70.500)', v_num = 23500 AND v_num2 = 70500);
    v_g5 := nueva_factura(v_u2, v_pr2, p(5));
    v_num := linea(v_g5, v_c2_acu, lectura_de(v_med_acu2, p(5)));

    -- Factura completa (restricción diferida): se verifica al confirmar
    SAVEPOINT sp_vacia;
    v_num := nueva_factura(v_u2, v_pr2, p(6));
    espera_error('factura sin líneas al confirmar (CK_FACTURA_COMPLETA diferida)',
        'SET CONSTRAINT CK_FACTURA_COMPLETA IMMEDIATE', -2290);
    ROLLBACK TO sp_vacia;
    EXECUTE IMMEDIATE 'SET CONSTRAINT CK_FACTURA_COMPLETA IMMEDIATE';
    ok('todas las facturas emitidas tienen líneas y saldo');
    EXECUTE IMMEDIATE 'SET CONSTRAINT CK_FACTURA_COMPLETA DEFERRED';

    -- Anulación y reexpedición (P-10)
    v_f3 := nueva_factura(v_u1, v_pr1, p(3));
    v_num := linea(v_f3, v_c_acu, lectura_de(v_med_acu, p(3)));
    v_num := linea(v_f3, v_c_alc, lectura_de(v_med_acu, p(3)));
    v_num := linea(v_f3, v_c_gas, lectura_de(v_med_gas, p(3)));
    espera_error('anular sin motivo suficiente',
        'UPDATE FACTURA SET ESTADO = ''ANULADA'', MOTIVO_ANULACION = ''Error'', ANULADA_POR = ' || v_asesor || ' WHERE ID = ' || v_f3, -2290);
    UPDATE FACTURA SET ESTADO = 'ANULADA', MOTIVO_ANULACION = 'Lectura mal digitada en terreno', ANULADA_POR = v_asesor
     WHERE ID = v_f3;
    INSERT INTO FACTURA (USUARIO_ID, PREDIO_ID, PERIODO, FECHA_EMISION, FECHA_VENCIMIENTO, REEXPEDICION_DE_ID)
    VALUES (v_u1, v_pr1, p(3), TRUNC(SYSDATE), TRUNC(SYSDATE) + 15, v_f3) RETURNING ID INTO v_f3r;
    v_num := linea(v_f3r, v_c_acu, lectura_de(v_med_acu, p(3)));
    v_num := linea(v_f3r, v_c_alc, lectura_de(v_med_acu, p(3)));
    v_num := linea(v_f3r, v_c_gas, lectura_de(v_med_gas, p(3)));
    ok('factura anulada y reexpedida para el mismo periodo');
    espera_error('reexpedir dos veces la misma factura',
        'INSERT INTO FACTURA (USUARIO_ID, PREDIO_ID, PERIODO, FECHA_EMISION, FECHA_VENCIMIENTO, REEXPEDICION_DE_ID) VALUES ('
        || v_u1 || ',' || v_pr1 || ',' || p(3) || ',TRUNC(SYSDATE),TRUNC(SYSDATE)+15,' || v_f3 || ')', -1);
    espera_error('reactivar una factura anulada',
        'UPDATE FACTURA SET ESTADO = ''PENDIENTE'', MOTIVO_ANULACION = NULL, ANULADA_POR = NULL WHERE ID = ' || v_f3, -20006);

    UPDATE FACTURA SET ESTADO = 'VENCIDA' WHERE ID IN (v_f4, v_f5, v_g4, v_g5);
    ok('facturas P4 y P5 vencidas');
    espera_error('vencer una factura antes de su fecha',
        'UPDATE FACTURA SET ESTADO = ''VENCIDA'' WHERE ID = ' || v_f3r, -20001);

    -- -----------------------------------------------------------------
    titulo('PQR: radicación, plazos y reclamo sobre una línea');
    v_ins_pqr := 'INSERT INTO PQR (TIPO, CONTRATO_ID, MUNICIPIO_ID, LINEA_FACTURA_ID, DESCRIPCION, NOMBRE_CONTACTO, CORREO_CONTACTO, '
              || 'TELEFONO_CONTACTO, FECHA_LIMITE_RESPUESTA, GESTOR_ID, NUMERO_RADICADO';
    INSERT INTO PQR (TIPO, MUNICIPIO_ID, SOLICITANTE_TIPO_IDENTIFICACION, SOLICITANTE_IDENTIFICACION_CIFRADO,
                     SOLICITANTE_IDENTIFICACION_HASH, SOLICITANTE_IDENTIFICACION_MASCARA, DESCRIPCION, NOMBRE_CONTACTO,
                     CORREO_CONTACTO, TELEFONO_CONTACTO, FECHA_LIMITE_RESPUESTA, GESTOR_ID, NUMERO_RADICADO)
    VALUES ('PETICION', v_circ, 'CC', c_cifrado, RPAD('e', 64, 'e'), '******4321',
            'Solicito información sobre los requisitos para conectar un predio nuevo.', 'Luis Mora',
            'luis.mora@correo.co', '3157654321', DATE '2000-01-01', v_gestor, 'X')
    RETURNING ID INTO v_pet;
    SELECT NUMERO_RADICADO, FECHA_LIMITE_RESPUESTA INTO v_txt, v_fecha FROM PQR WHERE ID = v_pet;
    afirma('petición sin contrato radicada (' || v_txt || ') con plazo de 15 días hábiles',
           REGEXP_LIKE(v_txt, '^' || TO_CHAR(SYSDATE, 'YYYYMM') || '-[0-9]{4}$')
           AND v_fecha = PKG_SIGCA.sumar_dias_habiles(TRUNC(SYSDATE), 15));
    espera_error('queja sin contrato',
        'INSERT INTO PQR (TIPO, MUNICIPIO_ID, SOLICITANTE_TIPO_IDENTIFICACION, SOLICITANTE_IDENTIFICACION_CIFRADO, '
        || 'SOLICITANTE_IDENTIFICACION_HASH, SOLICITANTE_IDENTIFICACION_MASCARA, DESCRIPCION, NOMBRE_CONTACTO, CORREO_CONTACTO, '
        || 'TELEFONO_CONTACTO, FECHA_LIMITE_RESPUESTA, GESTOR_ID, NUMERO_RADICADO) VALUES (''QUEJA'',' || v_circ
        || ',''CC'',''' || c_cifrado || ''',RPAD(''e'',64,''e''),''******4321'',''El funcionario de la oficina me atendió mal.'','
        || '''Luis Mora'',''luis.mora@correo.co'',''3157654321'',DATE ''2000-01-01'',' || v_gestor || ',''X'')', -20002);
    espera_error('petición sin contrato ni identificación del solicitante',
        'INSERT INTO PQR (TIPO, MUNICIPIO_ID, DESCRIPCION, NOMBRE_CONTACTO, CORREO_CONTACTO, TELEFONO_CONTACTO, '
        || 'FECHA_LIMITE_RESPUESTA, GESTOR_ID, NUMERO_RADICADO) VALUES (''PETICION'',' || v_circ
        || ',''Solicito información sobre los requisitos de conexión.'',''Luis Mora'',''luis.mora@correo.co'',''3157654321'','
        || 'DATE ''2000-01-01'',' || v_gestor || ',''X'')', -2290);
    espera_error('reclamo sobre la línea de otro contrato',
        v_ins_pqr || ') VALUES (''RECLAMO'',' || v_c_acu || ',0,' || v_l_gas6 || ',''El consumo facturado no corresponde al uso real.'','
        || '''José Núñez'',''jose.nunez@correo.co'',''3101234567'',DATE ''2000-01-01'',' || v_gestor || ',''X'')', -20002);
    espera_error('queja con línea de factura',
        v_ins_pqr || ') VALUES (''QUEJA'',' || v_c_acu || ',0,' || v_l_acu6 || ',''El consumo facturado no corresponde al uso real.'','
        || '''José Núñez'',''jose.nunez@correo.co'',''3101234567'',DATE ''2000-01-01'',' || v_gestor || ',''X'')', -2290);
    espera_error('responsable Gestor de otro municipio',
        v_ins_pqr || ') VALUES (''RECLAMO'',' || v_c_acu || ',0,' || v_l_acu6 || ',''El consumo facturado no corresponde al uso real.'','
        || '''José Núñez'',''jose.nunez@correo.co'',''3101234567'',DATE ''2000-01-01'',' || v_gestor_sal || ',''X'')', -20015);

    v_pqr := pqr('RECLAMO', v_c_acu, v_l_acu6, v_gestor);
    SELECT COUNT(*) INTO v_num FROM LINEA_FACTURA WHERE FACTURA_ID = v_f6 AND EN_RECLAMO = 'S';
    afirma('el reclamo del acueducto cubre también el alcantarillado (RN-33)',
           v_num = 2 AND total(v_f6, 'VALOR_EN_RECLAMO') = 132720);
    espera_error('marcar el reclamo a mano', 'UPDATE LINEA_FACTURA SET EN_RECLAMO = ''N'' WHERE ID = ' || v_l_acu6, -20014);
    espera_error('pagar también lo reclamado',
        'INSERT INTO PAGO (USUARIO_ID, FACTURA_ID, VALOR, CANAL_PAGO, FUNCIONARIO_ID) VALUES (' || v_u1 || ',' || v_f6 || ',172720,''PRESENCIAL'',' || v_asesor || ')', -20005);
    INSERT INTO PAGO (USUARIO_ID, FACTURA_ID, VALOR, CANAL_PAGO, FUNCIONARIO_ID) VALUES (v_u1, v_f6, 40000, 'PRESENCIAL', v_asesor);
    afirma('se paga solo lo no reclamado: gas PAGADA, factura sigue PENDIENTE',
           estado('LINEA_FACTURA', v_l_gas6) = 'PAGADA' AND estado('FACTURA', v_f6) = 'PENDIENTE'
           AND total(v_f6, 'SALDO_PENDIENTE') = 132720);
    espera_error('vencer una factura cuyo saldo está todo en reclamo',
        'UPDATE FACTURA SET ESTADO = ''VENCIDA'' WHERE ID = ' || v_f6, -20021);
    espera_error('pagar cuando todo el saldo está en reclamo',
        'INSERT INTO PAGO (USUARIO_ID, FACTURA_ID, VALOR, CANAL_PAGO, FUNCIONARIO_ID) VALUES (' || v_u1 || ',' || v_f6 || ',132720,''PRESENCIAL'',' || v_asesor || ')', -20004);
    espera_error('anular una factura con pagos',
        'UPDATE FACTURA SET ESTADO = ''ANULADA'', MOTIVO_ANULACION = ''Error de liquidación'', ANULADA_POR = ' || v_asesor || ' WHERE ID = ' || v_f6, -20001);
    espera_error('ajustar una línea sin reclamo', 'UPDATE LINEA_FACTURA SET VALOR_AJUSTE = -1000 WHERE ID = ' || v_l_gas6, -20006);
    espera_error('ajuste mayor que la línea', 'UPDATE LINEA_FACTURA SET VALOR_AJUSTE = -60000 WHERE ID = ' || v_l_alc6, -2290);
    UPDATE LINEA_FACTURA SET VALOR_AJUSTE = -6720 WHERE ID = v_l_alc6;
    afirma('ajuste parcial por reclamo descuenta del saldo y del valor en reclamo',
           total(v_f6, 'SALDO_PENDIENTE') = 126000 AND total(v_f6, 'VALOR_EN_RECLAMO') = 126000 AND total(v_f6, 'VALOR_AJUSTES') = -6720);

    espera_error('vencer la PQR dentro del plazo', 'UPDATE PQR SET ESTADO = ''VENCIDA'' WHERE ID = ' || v_pqr, -20001);
    espera_error('silencio positivo sin plazo de reconocimiento',
        'UPDATE PQR SET SILENCIO_POSITIVO = ''S'' WHERE ID = ' || v_pqr, -2290);
    espera_error('responder una PQR que no está en trámite',
        'UPDATE PQR SET ESTADO = ''RESPONDIDA'', RESPUESTA = ''' || c_texto || ''', RESPONDIDA_POR = ' || v_gestor || ' WHERE ID = ' || v_pqr, -20001);
    UPDATE PQR SET ESTADO = 'EN_TRAMITE' WHERE ID = v_pqr;
    espera_error('responder sin evidencia',
        'UPDATE PQR SET ESTADO = ''RESPONDIDA'', RESPUESTA = ''' || c_texto || ''', RESPONDIDA_POR = ' || v_gestor || ' WHERE ID = ' || v_pqr, -20009);
    espera_error('adjunto con tamaño distinto al contenido',
        'INSERT INTO PQR_ADJUNTO (PQR_ID, TIPO, NOMBRE_ARCHIVO, TIPO_MIME, TAMANO_BYTES, HASH_SHA256, CONTENIDO, CARGADO_POR) VALUES ('
        || v_pqr || ',''EVIDENCIA'',''acta.pdf'',''application/pdf'',10,RPAD(''0'',64,''0''),HEXTORAW(''25504446''),' || v_gestor || ')', -20014);
    evidencia(v_pqr);
    espera_error('responde quien no es Gestor ni Jefe de PQR',
        'UPDATE PQR SET ESTADO = ''RESPONDIDA'', RESPUESTA = ''' || c_texto || ''', RESPONDIDA_POR = ' || v_asesor || ' WHERE ID = ' || v_pqr, -20015);
    UPDATE PQR SET ESTADO = 'RESPONDIDA', RESPUESTA = c_texto, RESPONDIDA_POR = v_gestor WHERE ID = v_pqr;
    afirma('respondido el reclamo se libera el cobro de las líneas', total(v_f6, 'VALOR_EN_RECLAMO') = 0);
    espera_error('reasignar una PQR respondida', 'UPDATE PQR SET GESTOR_ID = ' || v_jefe || ' WHERE ID = ' || v_pqr, -20006);
    espera_error('modificar la respuesta (RN-22)', 'UPDATE PQR SET RESPUESTA = ''' || c_texto || ' Otra.'' WHERE ID = ' || v_pqr, -20006);

    -- -----------------------------------------------------------------
    titulo('Recurso de reposición con apelación ante la SSPD');
    v_queja := pqr('PETICION', v_c_acu, NULL, v_jefe);
    espera_error('recurso contra una PQR sin responder',
        'INSERT INTO PQR (TIPO, CONTRATO_ID, MUNICIPIO_ID, PQR_ORIGEN_ID, SOLICITA_APELACION, DESCRIPCION, NOMBRE_CONTACTO, CORREO_CONTACTO, '
        || 'TELEFONO_CONTACTO, FECHA_LIMITE_RESPUESTA, GESTOR_ID, NUMERO_RADICADO) VALUES (''RECURSO'',' || v_c_acu || ',0,' || v_queja
        || ',''N'',''No estoy de acuerdo con la respuesta recibida.'',''José Núñez'',''jose.nunez@correo.co'',''3101234567'','
        || 'DATE ''2000-01-01'',' || v_gestor || ',''X'')', -20001);
    espera_error('recurso sobre otra línea de factura',
        'INSERT INTO PQR (TIPO, CONTRATO_ID, MUNICIPIO_ID, PQR_ORIGEN_ID, LINEA_FACTURA_ID, SOLICITA_APELACION, DESCRIPCION, NOMBRE_CONTACTO, '
        || 'CORREO_CONTACTO, TELEFONO_CONTACTO, FECHA_LIMITE_RESPUESTA, GESTOR_ID, NUMERO_RADICADO) VALUES (''RECURSO'',' || v_c_acu || ',0,'
        || v_pqr || ',' || v_l_acu4 || ',''N'',''No estoy de acuerdo con la respuesta recibida.'',''José Núñez'',''jose.nunez@correo.co'','
        || '''3101234567'',DATE ''2000-01-01'',' || v_gestor || ',''X'')', -20014);
    espera_error('recurso sin PQR de origen',
        v_ins_pqr || ') VALUES (''RECURSO'',' || v_c_acu || ',0,NULL,''No estoy de acuerdo con la respuesta recibida.'','
        || '''José Núñez'',''jose.nunez@correo.co'',''3101234567'',DATE ''2000-01-01'',' || v_gestor || ',''X'')', -2290);
    v_rec := pqr('RECURSO', v_c_acu, v_l_acu6, v_gestor, v_pqr, 'S');
    afirma('el recurso vuelve a suspender el cobro de lo reclamado', total(v_f6, 'VALOR_EN_RECLAMO') = 126000);
    espera_error('segundo recurso contra la misma respuesta',
        'INSERT INTO PQR (TIPO, CONTRATO_ID, MUNICIPIO_ID, PQR_ORIGEN_ID, LINEA_FACTURA_ID, SOLICITA_APELACION, DESCRIPCION, NOMBRE_CONTACTO, '
        || 'CORREO_CONTACTO, TELEFONO_CONTACTO, FECHA_LIMITE_RESPUESTA, GESTOR_ID, NUMERO_RADICADO) VALUES (''RECURSO'',' || v_c_acu || ',0,'
        || v_pqr || ',' || v_l_acu6 || ',''N'',''No estoy de acuerdo con la respuesta recibida.'',''José Núñez'',''jose.nunez@correo.co'','
        || '''3101234567'',DATE ''2000-01-01'',' || v_gestor || ',''X'')', -1);
    UPDATE PQR SET ESTADO = 'EN_TRAMITE' WHERE ID = v_rec;
    evidencia(v_rec);
    -- Revocatoria parcial: se exonera el acueducto
    UPDATE LINEA_FACTURA SET VALOR_AJUSTE = -76000 WHERE ID = v_l_acu6;
    afirma('ajuste total exonera la línea y la saca del reclamo',
           estado('LINEA_FACTURA', v_l_acu6) = 'EXONERADA' AND total(v_f6, 'VALOR_EN_RECLAMO') = 50000);
    espera_error('confirmar con apelación pedida y cerrar como RESPONDIDA',
        'UPDATE PQR SET ESTADO = ''RESPONDIDA'', DECISION_RECURSO = ''CONFIRMA'', RESPUESTA = ''' || c_texto
        || ''', RESPONDIDA_POR = ' || v_gestor || ' WHERE ID = ' || v_rec, -20001);
    espera_error('responder el recurso sin decisión',
        'UPDATE PQR SET ESTADO = ''RESPONDIDA'', RESPUESTA = ''' || c_texto || ''', RESPONDIDA_POR = ' || v_gestor || ' WHERE ID = ' || v_rec, -2290);
    UPDATE PQR SET ESTADO = 'TRASLADADA_SSPD', DECISION_RECURSO = 'CONFIRMA', RESPUESTA = c_texto, RESPONDIDA_POR = v_gestor
     WHERE ID = v_rec;
    afirma('trasladada a la SSPD sigue suspendido el cobro', total(v_f6, 'VALOR_EN_RECLAMO') = 50000);
    INSERT INTO PQR_ACTUACION (PQR_ID, FUNCIONARIO_ID, DESCRIPCION) VALUES (v_rec, v_gestor, 'Se remite el expediente a la Superintendencia.');
    ok('actuación documentada en la PQR trasladada');
    espera_error('cerrar el traslado sin decisión de la SSPD',
        'UPDATE PQR SET ESTADO = ''RESPONDIDA'' WHERE ID = ' || v_rec, -20001);
    UPDATE PQR SET ESTADO = 'RESPONDIDA', DECISION_SSPD = 'CONFIRMA' WHERE ID = v_rec;
    SELECT COUNT(*) INTO v_num FROM PQR_HISTORIAL WHERE PQR_ID = v_rec;
    afirma('decisión de la SSPD registrada; historial con 4 cambios', total(v_f6, 'VALOR_EN_RECLAMO') = 0 AND v_num = 4);
    espera_error('modificar el historial', 'DELETE FROM PQR_HISTORIAL WHERE PQR_ID = ' || v_rec, -20006);
    INSERT INTO PAGO (USUARIO_ID, FACTURA_ID, VALOR, CANAL_PAGO, FUNCIONARIO_ID) VALUES (v_u1, v_f6, 50000, 'PRESENCIAL', v_asesor);
    afirma('pagado el saldo, la factura con línea exonerada queda PAGADA',
           estado('FACTURA', v_f6) = 'PAGADA' AND total(v_f6, 'VALOR_AJUSTES') = -82720);

    -- -----------------------------------------------------------------
    titulo('Cobro persuasivo, suspensión y reconexión');
    espera_error('candidato con un solo periodo en mora',
        'INSERT INTO COBRO_PERSUASIVO (USUARIO_ID, FECHA_INICIO, VALOR_DEUDA, PERIODOS_MORA, CANDIDATO_SUSPENSION) VALUES ('
        || v_u1 || ',TRUNC(SYSDATE),209820,1,''S'')', -20020);
    INSERT INTO COBRO_PERSUASIVO (USUARIO_ID, FECHA_INICIO, VALOR_DEUDA, PERIODOS_MORA, FUNCIONARIO_ID)
    VALUES (v_u1, TRUNC(SYSDATE), 356940, 2, v_cartera) RETURNING ID INTO v_cobro;
    v_txt := 'INSERT INTO ORDEN_SERVICIO (CONTRATO_ID, TIPO, CAUSA, COBRO_ID) VALUES (' || v_c_acu || ',''SUSPENSION'',''MORA'',' || v_cobro || ')';
    espera_error('suspender sin aviso al candidato', v_txt, -20020);
    UPDATE COBRO_PERSUASIVO SET CANDIDATO_SUSPENSION = 'S', FECHA_AVISO_SUSPENSION = SYSTIMESTAMP WHERE ID = v_cobro;
    espera_error('suspensión por mora sin cobro asociado',
        'INSERT INTO ORDEN_SERVICIO (CONTRATO_ID, TIPO, CAUSA) VALUES (' || v_c_acu || ',''SUSPENSION'',''MORA'')', -20020);
    INSERT INTO ORDEN_SERVICIO (CONTRATO_ID, TIPO, CAUSA, COBRO_ID) VALUES (v_c_acu, 'SUSPENSION', 'MORA', v_cobro)
    RETURNING ID INTO v_orden;
    espera_error('dos órdenes de suspensión pendientes', v_txt, -1);
    espera_error('reconectar un contrato activo',
        'INSERT INTO ORDEN_SERVICIO (CONTRATO_ID, TIPO, CAUSA) VALUES (' || v_c_acu || ',''RECONEXION'',''SOLICITUD_USUARIO'')', -20020);
    espera_error('ejecuta la orden quien no es Asesor',
        'UPDATE ORDEN_SERVICIO SET ESTADO = ''EJECUTADA'', EJECUTADA_POR = ' || v_cartera || ' WHERE ID = ' || v_orden, -20015);
    UPDATE ORDEN_SERVICIO SET ESTADO = 'EJECUTADA', EJECUTADA_POR = v_asesor WHERE ID = v_orden;
    afirma('ejecutada la orden, el contrato queda SUSPENDIDO', estado('CONTRATO_SERVICIO', v_c_acu) = 'SUSPENDIDO');
    espera_error('modificar una orden ejecutada',
        'UPDATE ORDEN_SERVICIO SET OBSERVACION = ''Cambio'' WHERE ID = ' || v_orden, -20006);
    espera_error('eliminar una orden', 'DELETE FROM ORDEN_SERVICIO WHERE ID = ' || v_orden, -20006);

    SAVEPOINT sp_suspendido;
    v_num := nueva_factura(v_u1, v_pr1, TO_NUMBER(TO_CHAR(ADD_MONTHS(SYSDATE, -1), 'YYYYMM')));
    espera_error_linea('facturar un contrato suspendido', v_num, v_c_acu, lectura_de(v_med_acu, p(6)), 0, -20002);
    ROLLBACK TO sp_suspendido;

    v_num := pqr('QUEJA', v_c_acu, NULL, v_gestor);
    ok('PQR sobre un contrato suspendido (P-29)');

    INSERT INTO PAGO (USUARIO_ID, FACTURA_ID, VALOR, CANAL_PAGO, FUNCIONARIO_ID, REFERENCIA_BANCO)
    VALUES (v_u1, v_f4, 209820, 'BANCO', v_asesor, 'BCO-778812');
    SELECT COUNT(*) INTO v_num FROM ORDEN_SERVICIO WHERE CONTRATO_ID = v_c_acu AND TIPO = 'RECONEXION';
    afirma('pagada una de dos facturas vencidas no hay reconexión', estado('FACTURA', v_f4) = 'PAGADA' AND v_num = 0);

    -- Pago en línea de la segunda factura
    SELECT NUMERO_FACTURA INTO v_txt FROM FACTURA WHERE ID = v_f5;
    espera_error('transacción con referencia de otra factura',
        'INSERT INTO TRANSACCION_PASARELA (REFERENCIA, REFERENCIA_PAGO, FACTURA_ID, USUARIO_ID, MONTO) VALUES (''REF-T-0001'','''
        || SUBSTR(v_txt, 1, LENGTH(v_txt) - 1) || '9'',' || v_f5 || ',' || v_u1 || ',147120)', -20005);
    espera_error('transacción por un monto distinto',
        'INSERT INTO TRANSACCION_PASARELA (REFERENCIA, REFERENCIA_PAGO, FACTURA_ID, USUARIO_ID, MONTO) VALUES (''REF-T-0002'','''
        || v_txt || ''',' || v_f5 || ',' || v_u1 || ',100000)', -20005);
    INSERT INTO TRANSACCION_PASARELA (REFERENCIA, REFERENCIA_PAGO, FACTURA_ID, USUARIO_ID, MONTO)
    VALUES ('REF-T-0003', v_txt, v_f5, v_u1, 147120) RETURNING ID INTO v_tx;
    v_txt := 'INSERT INTO PAGO (USUARIO_ID, FACTURA_ID, VALOR, CANAL_PAGO, TRANSACCION_PASARELA_ID, CODIGO_TRANSACCION) VALUES ('
          || v_u1 || ',' || v_f5 || ',147120,''EN_LINEA'',' || v_tx || ',''PSE-998877'')';
    espera_error('pago en línea con transacción sin aprobar', v_txt, -20016);
    espera_error('aprobar sin validar la firma de la pasarela',
        'UPDATE TRANSACCION_PASARELA SET ESTADO = ''APROBADA'', CODIGO_TRANSACCION = ''PSE-998877'' WHERE ID = ' || v_tx, -2290);
    UPDATE TRANSACCION_PASARELA SET ESTADO = 'APROBADA', CODIGO_TRANSACCION = 'PSE-998877', FIRMA_VALIDADA = 'S' WHERE ID = v_tx;
    INSERT INTO PAGO (USUARIO_ID, FACTURA_ID, VALOR, CANAL_PAGO, TRANSACCION_PASARELA_ID, CODIGO_TRANSACCION)
    VALUES (v_u1, v_f5, 147120, 'EN_LINEA', v_tx, 'PSE-998877') RETURNING ID INTO v_pago;
    SELECT COUNT(*) INTO v_num FROM ORDEN_SERVICIO
     WHERE CONTRATO_ID = v_c_acu AND TIPO = 'RECONEXION' AND CAUSA = 'PAGO' AND PAGO_ID = v_pago AND ESTADO = 'PENDIENTE'
       AND FECHA_LIMITE <= FECHA_ORDEN + INTERVAL '24' HOUR;
    afirma('sin deuda vencida se ordena la reconexión automática en 24 horas (RN-36)', v_num = 1);
    afirma('sin deuda vencida se cierra el cobro persuasivo', estado('COBRO_PERSUASIVO', v_cobro) = 'CERRADO');
    espera_error('pagar dos veces la misma factura', v_txt, -20004);
    espera_error('modificar un pago', 'UPDATE PAGO SET VALOR = 1 WHERE ID = ' || v_pago, -20006);
    SELECT ID INTO v_orden FROM ORDEN_SERVICIO WHERE CONTRATO_ID = v_c_acu AND TIPO = 'RECONEXION';
    UPDATE ORDEN_SERVICIO SET ESTADO = 'EJECUTADA', EJECUTADA_POR = v_asesor WHERE ID = v_orden;
    afirma('ejecutada la reconexión, el contrato vuelve a ACTIVO', estado('CONTRATO_SERVICIO', v_c_acu) = 'ACTIVO');

    espera_error('cancelar un contrato con valores pendientes',
        'UPDATE CONTRATO_SERVICIO SET ESTADO = ''CANCELADO'', MOTIVO_ESTADO = ''Solicitud del usuario'' WHERE ID = ' || v_c_gas, -20012);
    espera_error('desactivar un usuario con contratos vigentes',
        'UPDATE USUARIO SET ACTIVO = ''N'', FECHA_INACTIVACION = SYSTIMESTAMP, MOTIVO_INACTIVACION = ''Solicitud'' WHERE ID = ' || v_u1, -20012);
    espera_error('acuerdo de pago sin facturas vencidas',
        'INSERT INTO ACUERDO_PAGO (USUARIO_ID, FECHA_ACUERDO, TOTAL_DEUDA, CUOTA_INICIAL, NUMERO_CUOTAS, FUNCIONARIO_ID) VALUES ('
        || v_u1 || ',TRUNC(SYSDATE),100000,10000,2,' || v_cartera || ')', -20013);

    -- -----------------------------------------------------------------
    titulo('Acuerdo de pago (cuota inicial 10 %, interés residencial 6 %)');
    INSERT INTO COBRO_PERSUASIVO (USUARIO_ID, FECHA_INICIO, VALOR_DEUDA, PERIODOS_MORA, CANDIDATO_SUSPENSION, FECHA_AVISO_SUSPENSION, FUNCIONARIO_ID)
    VALUES (v_u2, TRUNC(SYSDATE), 141000, 2, 'S', SYSTIMESTAMP, v_cartera) RETURNING ID INTO v_cobro2;
    INSERT INTO ORDEN_SERVICIO (CONTRATO_ID, TIPO, CAUSA, COBRO_ID) VALUES (v_c2_acu, 'SUSPENSION', 'MORA', v_cobro2)
    RETURNING ID INTO v_orden;
    v_txt := 'INSERT INTO ACUERDO_PAGO (USUARIO_ID, FECHA_ACUERDO, TOTAL_DEUDA, CUOTA_INICIAL, TASA_INTERES_ANUAL, NUMERO_CUOTAS, FUNCIONARIO_ID) VALUES ('
          || v_u2 || ',TRUNC(SYSDATE) - 10,141000,';
    espera_error('cuota inicial menor al 10 %', v_txt || '14000,6,2,' || v_cartera || ')', -20013);
    espera_error('más cuotas que el máximo', v_txt || '14100,6,30,' || v_cartera || ')', -20013);
    espera_error('tasa por encima de la usura', v_txt || '14100,30,2,' || v_cartera || ')', -20013);
    SAVEPOINT sp_tasa;
    EXECUTE IMMEDIATE v_txt || '14100,7,2,' || v_cartera || ')';
    SELECT MAX(ID) INTO v_ac FROM ACUERDO_PAGO WHERE USUARIO_ID = v_u2;
    espera_error('interés mayor al 6 % para un usuario residencial',
        'INSERT INTO ACUERDO_FACTURA (ACUERDO_ID, FACTURA_ID, VALOR_INCLUIDO) VALUES (' || v_ac || ',' || v_g4 || ',70500)', -20013);
    ROLLBACK TO sp_tasa;

    INSERT INTO ACUERDO_PAGO (USUARIO_ID, FECHA_ACUERDO, TOTAL_DEUDA, CUOTA_INICIAL, TASA_INTERES_ANUAL, NUMERO_CUOTAS, FUNCIONARIO_ID)
    VALUES (v_u2, TRUNC(SYSDATE) - 10, 141000, 14100, 6, 2, v_cartera) RETURNING ID INTO v_ac;
    afirma('el acuerdo anula la suspensión pendiente y suspende el cobro',
           estado('ORDEN_SERVICIO', v_orden) = 'ANULADA' AND estado('COBRO_PERSUASIVO', v_cobro2) = 'SUSPENDIDO');
    espera_error('incluir un valor distinto al saldo',
        'INSERT INTO ACUERDO_FACTURA (ACUERDO_ID, FACTURA_ID, VALOR_INCLUIDO) VALUES (' || v_ac || ',' || v_g4 || ',70000)', -20005);
    INSERT INTO ACUERDO_FACTURA (ACUERDO_ID, FACTURA_ID, VALOR_INCLUIDO) VALUES (v_ac, v_g4, 70500);
    INSERT INTO ACUERDO_FACTURA (ACUERDO_ID, FACTURA_ID, VALOR_INCLUIDO) VALUES (v_ac, v_g5, 70500);
    afirma('facturas del acuerdo pasan a EN_ACUERDO', estado('FACTURA', v_g4) = 'EN_ACUERDO' AND estado('FACTURA', v_g5) = 'EN_ACUERDO');
    espera_error('acuerdo sin cuotas al confirmar (CK_ACUERDO_CUADRE diferida)',
        'SET CONSTRAINT CK_ACUERDO_CUADRE IMMEDIATE', -2290);
    EXECUTE IMMEDIATE 'SET CONSTRAINT CK_ACUERDO_CUADRE DEFERRED';
    espera_error('cuota 0 distinta de la cuota inicial',
        'INSERT INTO CUOTA_ACUERDO (ACUERDO_ID, NUMERO_CUOTA, VALOR_CAPITAL, VALOR_CUOTA, FECHA_VENCIMIENTO) VALUES ('
        || v_ac || ',0,15000,15000,TRUNC(SYSDATE) - 5)', -20013);
    espera_error('cuota inicial con intereses',
        'INSERT INTO CUOTA_ACUERDO (ACUERDO_ID, NUMERO_CUOTA, VALOR_CAPITAL, VALOR_INTERES, VALOR_CUOTA, FECHA_VENCIMIENTO) VALUES ('
        || v_ac || ',0,14100,100,14200,TRUNC(SYSDATE) - 5)', -2290);
    INSERT INTO CUOTA_ACUERDO (ACUERDO_ID, NUMERO_CUOTA, VALOR_CAPITAL, VALOR_CUOTA, FECHA_VENCIMIENTO)
    VALUES (v_ac, 0, 14100, 14100, TRUNC(SYSDATE) - 5) RETURNING ID INTO v_q0;
    INSERT INTO CUOTA_ACUERDO (ACUERDO_ID, NUMERO_CUOTA, VALOR_CAPITAL, VALOR_INTERES, VALOR_CUOTA, FECHA_VENCIMIENTO)
    VALUES (v_ac, 1, 63450, 317.25, 63767.25, TRUNC(SYSDATE) + 20) RETURNING ID INTO v_q1;
    INSERT INTO CUOTA_ACUERDO (ACUERDO_ID, NUMERO_CUOTA, VALOR_CAPITAL, VALOR_INTERES, VALOR_CUOTA, FECHA_VENCIMIENTO)
    VALUES (v_ac, 2, 63450, 317.25, 63767.25, TRUNC(SYSDATE) + 50) RETURNING ID INTO v_q2;
    EXECUTE IMMEDIATE 'SET CONSTRAINT CK_ACUERDO_CUADRE IMMEDIATE';
    ok('acuerdo cuadrado: facturas = deuda = cuota 0 + cuotas');
    EXECUTE IMMEDIATE 'SET CONSTRAINT CK_ACUERDO_CUADRE DEFERRED';
    espera_error('modificar las condiciones del acuerdo',
        'UPDATE ACUERDO_PAGO SET NUMERO_CUOTAS = 3 WHERE ID = ' || v_ac, -20014);
    espera_error('pagar directamente una factura en acuerdo',
        'INSERT INTO PAGO (USUARIO_ID, FACTURA_ID, VALOR, CANAL_PAGO, FUNCIONARIO_ID) VALUES (' || v_u2 || ',' || v_g4 || ',70500,''PRESENCIAL'',' || v_asesor || ')', -20004);
    espera_error('pagar la cuota 1 antes de la cuota inicial',
        'INSERT INTO PAGO (USUARIO_ID, CUOTA_ID, VALOR, CANAL_PAGO, FUNCIONARIO_ID) VALUES (' || v_u2 || ',' || v_q1 || ',63767.25,''PRESENCIAL'',' || v_asesor || ')', -20004);
    espera_error('suspensión por mora con acuerdo vigente',
        'INSERT INTO ORDEN_SERVICIO (CONTRATO_ID, TIPO, CAUSA, COBRO_ID) VALUES (' || v_c2_acu || ',''SUSPENSION'',''MORA'',' || v_cobro2 || ')', -20020);
    SELECT NUMERO_ACUERDO INTO v_txt FROM ACUERDO_PAGO WHERE ID = v_ac;
    espera_error('transacción de cuota con referencia equivocada',
        'INSERT INTO TRANSACCION_PASARELA (REFERENCIA, REFERENCIA_PAGO, CUOTA_ID, USUARIO_ID, MONTO) VALUES (''REF-T-0010'','''
        || v_txt || '-01'',' || v_q0 || ',' || v_u2 || ',14100)', -20005);

    -- Incumplimiento (se deshace con el savepoint)
    SAVEPOINT sp_incumple;
    UPDATE CUOTA_ACUERDO SET ESTADO = 'VENCIDA' WHERE ID = v_q0;
    afirma('cuota vencida: acuerdo INCUMPLIDO, facturas VENCIDAS y cobro reactivado',
           estado('ACUERDO_PAGO', v_ac) = 'INCUMPLIDO' AND estado('FACTURA', v_g4) = 'VENCIDA'
           AND estado('COBRO_PERSUASIVO', v_cobro2) = 'ACTIVO');
    ROLLBACK TO sp_incumple;
    espera_error('vencer una cuota antes de su fecha', 'UPDATE CUOTA_ACUERDO SET ESTADO = ''VENCIDA'' WHERE ID = ' || v_q1, -20001);

    INSERT INTO TRANSACCION_PASARELA (REFERENCIA, REFERENCIA_PAGO, CUOTA_ID, USUARIO_ID, MONTO)
    VALUES ('REF-T-0011', v_txt || '-00', v_q0, v_u2, 14100) RETURNING ID INTO v_tx;
    UPDATE TRANSACCION_PASARELA SET ESTADO = 'APROBADA', CODIGO_TRANSACCION = 'PSE-112233', FIRMA_VALIDADA = 'S' WHERE ID = v_tx;
    INSERT INTO PAGO (USUARIO_ID, CUOTA_ID, VALOR, CANAL_PAGO, TRANSACCION_PASARELA_ID, CODIGO_TRANSACCION)
    VALUES (v_u2, v_q0, 14100, 'EN_LINEA', v_tx, 'PSE-112233');
    INSERT INTO PAGO (USUARIO_ID, CUOTA_ID, VALOR, CANAL_PAGO, FUNCIONARIO_ID) VALUES (v_u2, v_q1, 63767.25, 'PRESENCIAL', v_asesor);
    afirma('cuotas pagadas en orden, acuerdo aún vigente', estado('CUOTA_ACUERDO', v_q1) = 'PAGADA' AND estado('ACUERDO_PAGO', v_ac) = 'VIGENTE');
    INSERT INTO PAGO (USUARIO_ID, CUOTA_ID, VALOR, CANAL_PAGO, FUNCIONARIO_ID) VALUES (v_u2, v_q2, 63767.25, 'PRESENCIAL', v_asesor);
    afirma('última cuota: acuerdo CUMPLIDO, facturas PAGADAS y cobro cerrado',
           estado('ACUERDO_PAGO', v_ac) = 'CUMPLIDO' AND estado('FACTURA', v_g4) = 'PAGADA' AND estado('FACTURA', v_g5) = 'PAGADA'
           AND estado('COBRO_PERSUASIVO', v_cobro2) = 'CERRADO');

    -- -----------------------------------------------------------------
    titulo('Auditoría y consistencia final');
    INSERT INTO LOG_AUDITORIA (ACTOR_TIPO, FUNCIONARIO_ID, OPERACION, ENTIDAD, ENTIDAD_ID)
    VALUES ('FUNCIONARIO', v_asesor, 'REVISAR_LECTURA', 'LECTURA', v_lec) RETURNING ID INTO v_num;
    espera_error('modificar el log de auditoría', 'UPDATE LOG_AUDITORIA SET OPERACION = ''LOGIN'' WHERE ID = ' || v_num, -20006);
    espera_error('notificación por correo sin destinatario',
        'INSERT INTO NOTIFICACION (EVENTO, CANAL, ASUNTO, CUERPO) VALUES (''FACTURA_REEXPEDIDA'',''EMAIL'',''Factura'',''Su factura fue reexpedida.'')', -2290);
    espera_error('log de funcionario sin funcionario',
        'INSERT INTO LOG_AUDITORIA (ACTOR_TIPO, OPERACION) VALUES (''FUNCIONARIO'',''REVISAR_LECTURA'')', -2290);
    EXECUTE IMMEDIATE 'SET CONSTRAINTS ALL IMMEDIATE';
    ok('todas las restricciones diferidas se cumplen al final de la transacción');

    ROLLBACK;
    DBMS_OUTPUT.PUT_LINE('== Resultado: ' || v_ok || ' pruebas OK, ' || v_fallas || ' fallas');
    IF v_fallas > 0 THEN
        RAISE_APPLICATION_ERROR(-20998, v_fallas || ' pruebas fallaron');
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;
        DBMS_OUTPUT.PUT_LINE('== Error no controlado tras ' || v_ok || ' OK y ' || v_fallas || ' fallas: ' || SQLERRM);
        DBMS_OUTPUT.PUT_LINE(DBMS_UTILITY.FORMAT_ERROR_BACKTRACE);
        RAISE;
END;
/
EXIT
