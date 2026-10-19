-- =====================================================================
-- SIGCA-EPQ  ·  03_logica.sql
-- Paquete de utilidades y triggers que hacen cumplir, dentro de Oracle,
-- las reglas que no se pueden expresar con CHECK/UNIQUE/FK:
--   * generación de códigos y del radicado YYYYMM-NNNN (tabla CONSECUTIVO)
--   * fechas límite en días hábiles (festivos)
--   * crítica de lecturas y consumo estimado por promedio (P-07)
--   * liquidación de cada línea de factura contra tarifa CRA, factor de
--     subsidio/contribución y lectura (P-08); saldos de la factura
--   * pago del valor exigible, reclamos que suspenden el cobro (RN-33)
--   * órdenes de suspensión y reconexión automática (P-11, RN-36)
--   * máquinas de estado de Factura, PQR, Contrato, Lectura, Acuerdo, Cuota,
--     Orden y Transacción; recursos y silencio administrativo positivo
--   * inmutabilidad del log de auditoría, historial y adjuntos
--
-- Códigos de error (el backend los traduce a excepciones de dominio):
--   -20001 Transición de estado no permitida
--   -20002 Contrato inexistente, no vigente o no corresponde (ContratoNoEncontradoException)
--   -20003 Municipio no atendido por EPQ                    (MunicipioNoAtendidoException)
--   -20004 La factura o cuota no admite pagos                (SWR-06)
--   -20005 El valor no coincide con el valor exigible        (CO-02)
--   -20006 Registro inmutable
--   -20007 Tarifa o factor de subsidio no corresponde o no está vigente (RN-08)
--   -20008 Fecha futura no permitida
--   -20009 La PQR no tiene evidencia adjunta                 (RN-06)
--   -20010 Consecutivo agotado
--   -20011 Parámetro del sistema inexistente o fuera de rango
--   -20012 Usuario inactivo o con obligaciones pendientes
--   -20013 Datos del acuerdo de pago inválidos               (RN-30, P-12)
--   -20014 Campo no modificable
--   -20015 Funcionario sin rol o municipio requerido
--   -20016 Pago sin confirmación válida / PAGADA sin pago    (RN-05)
--   -20017 Lectura inválida, fuera de secuencia o no apta para facturar (P-07)
--   -20018 La liquidación no coincide con tarifa, factor o lectura (P-08)
--   -20019 Plazo legal vencido o no cumplido                 (RN-32, RN-34)
--   -20020 Orden de suspensión o reconexión no procedente    (P-11)
--   -20021 Operación no permitida sobre valores en reclamo   (RN-33)
-- =====================================================================

CREATE OR REPLACE PACKAGE PKG_SIGCA AS
    -- Banderas de contexto: permiten a un trigger cambiar columnas que el
    -- cliente no puede escribir directamente.
    g_en_pago     BOOLEAN := FALSE;   -- pago registrado: líneas y cuotas a PAGADA
    g_en_acuerdo  BOOLEAN := FALSE;   -- acuerdo: facturas EN_ACUERDO y totales del acuerdo
    g_en_linea    BOOLEAN := FALSE;   -- totales de la factura desde sus líneas
    g_en_reclamo  BOOLEAN := FALSE;   -- marca EN_RECLAMO de una línea desde la PQR
    g_en_critica  BOOLEAN := FALSE;   -- crítica automática de lecturas
    g_en_orden    BOOLEAN := FALSE;   -- ejecución de una orden de suspensión o reconexión

    PROCEDURE error(p_codigo PLS_INTEGER, p_mensaje VARCHAR2);
    FUNCTION  parametro_num(p_clave VARCHAR2) RETURN NUMBER;
    FUNCTION  es_dia_habil(p_fecha DATE) RETURN BOOLEAN;
    FUNCTION  sumar_dias_habiles(p_fecha DATE, p_dias PLS_INTEGER) RETURN DATE;
    FUNCTION  primer_dia_habil(p_periodo NUMBER) RETURN DATE;
    FUNCTION  inicio_periodo(p_periodo NUMBER) RETURN DATE;
    FUNCTION  fin_periodo(p_periodo NUMBER) RETURN DATE;
    FUNCTION  periodo_siguiente(p_periodo NUMBER) RETURN NUMBER;
    FUNCTION  siguiente_consecutivo(p_prefijo VARCHAR2, p_maximo NUMBER) RETURN NUMBER;
    FUNCTION  siguiente_radicado(p_fecha TIMESTAMP) RETURN VARCHAR2;
    FUNCTION  municipio_activo(p_municipio_id NUMBER) RETURN BOOLEAN;
    FUNCTION  tiene_rol(p_funcionario_id NUMBER, p_rol VARCHAR2) RETURN BOOLEAN;
    FUNCTION  actor RETURN VARCHAR2;
    -- Comparación que trata NULL como valor (NULL vs NULL = iguales)
    FUNCTION  distinto(a NUMBER, b NUMBER) RETURN BOOLEAN;
    FUNCTION  distinto(a VARCHAR2, b VARCHAR2) RETURN BOOLEAN;
    -- RN-33: recalcula EN_RECLAMO de una línea (y de su pareja acueducto/alcantarillado)
    PROCEDURE sincronizar_reclamo(p_linea_id NUMBER);
    -- RN-36 / P-11: anula suspensiones por mora y ordena la reconexión si ya no hay deuda vencida
    PROCEDURE reconectar_si_procede(p_usuario_id NUMBER, p_predio_id NUMBER, p_causa VARCHAR2,
                                    p_pago_id NUMBER, p_acuerdo_id NUMBER);
END PKG_SIGCA;
/

CREATE OR REPLACE PACKAGE BODY PKG_SIGCA AS

    PROCEDURE error(p_codigo PLS_INTEGER, p_mensaje VARCHAR2) IS
    BEGIN
        RAISE_APPLICATION_ERROR(p_codigo, p_mensaje);
    END;

    FUNCTION parametro_num(p_clave VARCHAR2) RETURN NUMBER IS
        v_valor PARAMETRO_SISTEMA.VALOR%TYPE;
    BEGIN
        SELECT VALOR INTO v_valor FROM PARAMETRO_SISTEMA WHERE CLAVE = p_clave;
        RETURN TO_NUMBER(v_valor, '999999999D9999', 'NLS_NUMERIC_CHARACTERS=''.,''');
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            error(-20011, 'Parámetro del sistema inexistente: ' || p_clave);
    END;

    FUNCTION es_dia_habil(p_fecha DATE) RETURN BOOLEAN IS
        v_festivo PLS_INTEGER;
    BEGIN
        IF TO_CHAR(p_fecha, 'DY', 'NLS_DATE_LANGUAGE=ENGLISH') IN ('SAT','SUN') THEN
            RETURN FALSE;
        END IF;
        SELECT COUNT(*) INTO v_festivo FROM FESTIVO WHERE FECHA = TRUNC(p_fecha);
        RETURN v_festivo = 0;
    END;

    -- RN-19: suma días hábiles excluyendo sábados, domingos y festivos
    FUNCTION sumar_dias_habiles(p_fecha DATE, p_dias PLS_INTEGER) RETURN DATE IS
        v_fecha    DATE := TRUNC(p_fecha);
        v_contados PLS_INTEGER := 0;
    BEGIN
        IF p_dias < 0 THEN
            error(-20011, 'Número de días hábiles inválido');
        END IF;
        WHILE v_contados < p_dias LOOP
            v_fecha := v_fecha + 1;
            IF es_dia_habil(v_fecha) THEN
                v_contados := v_contados + 1;
            END IF;
        END LOOP;
        RETURN v_fecha;
    END;

    -- RN-02: primer día hábil del periodo AAAAMM
    FUNCTION primer_dia_habil(p_periodo NUMBER) RETURN DATE IS
        v_fecha DATE := inicio_periodo(p_periodo);
    BEGIN
        WHILE NOT es_dia_habil(v_fecha) LOOP
            v_fecha := v_fecha + 1;
        END LOOP;
        RETURN v_fecha;
    END;

    FUNCTION inicio_periodo(p_periodo NUMBER) RETURN DATE IS
    BEGIN
        RETURN TO_DATE(TO_CHAR(p_periodo) || '01', 'YYYYMMDD');
    END;

    FUNCTION fin_periodo(p_periodo NUMBER) RETURN DATE IS
    BEGIN
        RETURN LAST_DAY(inicio_periodo(p_periodo));
    END;

    FUNCTION periodo_siguiente(p_periodo NUMBER) RETURN NUMBER IS
    BEGIN
        RETURN TO_NUMBER(TO_CHAR(ADD_MONTHS(inicio_periodo(p_periodo), 1), 'YYYYMM'));
    END;

    -- P-27: consecutivo que se reinicia por prefijo. El bloqueo de fila serializa
    -- solicitudes concurrentes del mismo prefijo y no deja huecos.
    FUNCTION siguiente_consecutivo(p_prefijo VARCHAR2, p_maximo NUMBER) RETURN NUMBER IS
        v_ultimo CONSECUTIVO.ULTIMO%TYPE;
        v_maximo CONSECUTIVO.MAXIMO%TYPE;
    BEGIN
        BEGIN
            INSERT INTO CONSECUTIVO (PREFIJO, ULTIMO, MAXIMO) VALUES (p_prefijo, 0, p_maximo);
        EXCEPTION
            WHEN DUP_VAL_ON_INDEX THEN NULL;
        END;
        SELECT ULTIMO, MAXIMO INTO v_ultimo, v_maximo FROM CONSECUTIVO WHERE PREFIJO = p_prefijo FOR UPDATE;
        IF v_ultimo >= v_maximo THEN
            error(-20010, 'Se agotó el consecutivo ' || p_prefijo);
        END IF;
        UPDATE CONSECUTIVO SET ULTIMO = ULTIMO + 1 WHERE PREFIJO = p_prefijo;
        RETURN v_ultimo + 1;
    END;

    -- SWR-02 / RN-18: radicado YYYYMM-NNNN con consecutivo que reinicia cada mes
    FUNCTION siguiente_radicado(p_fecha TIMESTAMP) RETURN VARCHAR2 IS
        v_periodo VARCHAR2(6) := TO_CHAR(p_fecha, 'YYYYMM');
    BEGIN
        RETURN v_periodo || '-' || LPAD(siguiente_consecutivo('RAD-' || v_periodo, 9999), 4, '0');
    END;

    FUNCTION municipio_activo(p_municipio_id NUMBER) RETURN BOOLEAN IS
        v_activo MUNICIPIO.ACTIVO%TYPE;
    BEGIN
        SELECT ACTIVO INTO v_activo FROM MUNICIPIO WHERE ID = p_municipio_id;
        RETURN v_activo = 'S';
    EXCEPTION
        WHEN NO_DATA_FOUND THEN RETURN FALSE;
    END;

    FUNCTION tiene_rol(p_funcionario_id NUMBER, p_rol VARCHAR2) RETURN BOOLEAN IS
        v_n PLS_INTEGER;
    BEGIN
        SELECT COUNT(*) INTO v_n
          FROM FUNCIONARIO_ROL FR
          JOIN ROL R ON R.ID = FR.ROL_ID
          JOIN FUNCIONARIO F ON F.ID = FR.FUNCIONARIO_ID
         WHERE FR.FUNCIONARIO_ID = p_funcionario_id
           AND R.CODIGO = p_rol AND R.ACTIVO = 'S' AND F.ACTIVO = 'S';
        RETURN v_n > 0;
    END;

    -- Quién ejecuta: el backend fija CLIENT_IDENTIFIER con DBMS_SESSION.SET_IDENTIFIER(username)
    FUNCTION actor RETURN VARCHAR2 IS
    BEGIN
        RETURN NVL(SYS_CONTEXT('USERENV', 'CLIENT_IDENTIFIER'), 'SISTEMA');
    END;

    FUNCTION distinto(a NUMBER, b NUMBER) RETURN BOOLEAN IS
    BEGIN
        RETURN (a IS NULL AND b IS NOT NULL) OR (a IS NOT NULL AND b IS NULL) OR a <> b;
    END;

    FUNCTION distinto(a VARCHAR2, b VARCHAR2) RETURN BOOLEAN IS
    BEGIN
        RETURN (a IS NULL AND b IS NOT NULL) OR (a IS NOT NULL AND b IS NULL) OR a <> b;
    END;

    -- Una línea está en reclamo mientras exista una PQR abierta (RECIBIDA, EN_TRAMITE,
    -- VENCIDA o TRASLADADA_SSPD) sobre ella. Acueducto y alcantarillado se liquidan sobre
    -- el mismo consumo y no se pagan por separado (RN-33): el reclamo cubre a ambas.
    PROCEDURE sincronizar_reclamo(p_linea_id NUMBER) IS
        v_factura_id  LINEA_FACTURA.FACTURA_ID%TYPE;
        v_tipo        LINEA_FACTURA.TIPO_SERVICIO%TYPE;
        v_estado_fac  FACTURA.ESTADO%TYPE;
        v_abiertas    PLS_INTEGER;
        v_marca       CHAR(1);
    BEGIN
        SELECT L.FACTURA_ID, L.TIPO_SERVICIO, F.ESTADO INTO v_factura_id, v_tipo, v_estado_fac
          FROM LINEA_FACTURA L JOIN FACTURA F ON F.ID = L.FACTURA_ID
         WHERE L.ID = p_linea_id;
        -- Una factura en acuerdo, pagada o anulada ya no tiene cobro que suspender
        IF v_estado_fac NOT IN ('PENDIENTE','VENCIDA') THEN
            RETURN;
        END IF;
        SELECT COUNT(*) INTO v_abiertas
          FROM PQR P JOIN LINEA_FACTURA L ON L.ID = P.LINEA_FACTURA_ID
         WHERE L.FACTURA_ID = v_factura_id
           AND (L.ID = p_linea_id
                OR (v_tipo IN ('ACUEDUCTO','ALCANTARILLADO') AND L.TIPO_SERVICIO IN ('ACUEDUCTO','ALCANTARILLADO')))
           AND P.ESTADO IN ('RECIBIDA','EN_TRAMITE','VENCIDA','TRASLADADA_SSPD');
        v_marca := CASE WHEN v_abiertas > 0 THEN 'S' ELSE 'N' END;
        g_en_reclamo := TRUE;
        BEGIN
            UPDATE LINEA_FACTURA SET EN_RECLAMO = v_marca
             WHERE FACTURA_ID = v_factura_id AND ESTADO = 'PENDIENTE' AND EN_RECLAMO <> v_marca
               AND (ID = p_linea_id
                    OR (v_tipo IN ('ACUEDUCTO','ALCANTARILLADO') AND TIPO_SERVICIO IN ('ACUEDUCTO','ALCANTARILLADO')));
            g_en_reclamo := FALSE;
        EXCEPTION
            WHEN OTHERS THEN
                g_en_reclamo := FALSE;
                RAISE;
        END;
    END;

    PROCEDURE reconectar_si_procede(p_usuario_id NUMBER, p_predio_id NUMBER, p_causa VARCHAR2,
                                    p_pago_id NUMBER, p_acuerdo_id NUMBER) IS
        v_deuda       PLS_INTEGER;
        v_ultima      ORDEN_SERVICIO.CAUSA%TYPE;
        v_pendientes  PLS_INTEGER;
    BEGIN
        SELECT COUNT(*) INTO v_deuda FROM FACTURA
         WHERE USUARIO_ID = p_usuario_id AND PREDIO_ID = p_predio_id AND ESTADO = 'VENCIDA'
           AND SALDO_PENDIENTE - VALOR_EN_RECLAMO > 0;
        IF v_deuda > 0 THEN
            RETURN;
        END IF;
        FOR c IN (SELECT ID, ESTADO FROM CONTRATO_SERVICIO
                   WHERE USUARIO_ID = p_usuario_id AND PREDIO_ID = p_predio_id
                     AND ESTADO IN ('ACTIVO','SUSPENDIDO')) LOOP
            UPDATE ORDEN_SERVICIO
               SET ESTADO = 'ANULADA',
                   MOTIVO_ANULACION = 'Deuda normalizada por ' || LOWER(p_causa)
             WHERE CONTRATO_ID = c.ID AND TIPO = 'SUSPENSION' AND CAUSA = 'MORA' AND ESTADO = 'PENDIENTE';
            IF c.ESTADO = 'SUSPENDIDO' THEN
                BEGIN
                    SELECT CAUSA INTO v_ultima FROM ORDEN_SERVICIO
                     WHERE CONTRATO_ID = c.ID AND TIPO = 'SUSPENSION' AND ESTADO = 'EJECUTADA'
                     ORDER BY FECHA_EJECUCION DESC FETCH FIRST 1 ROW ONLY;
                EXCEPTION
                    WHEN NO_DATA_FOUND THEN v_ultima := NULL;
                END;
                SELECT COUNT(*) INTO v_pendientes FROM ORDEN_SERVICIO
                 WHERE CONTRATO_ID = c.ID AND TIPO = 'RECONEXION' AND ESTADO = 'PENDIENTE';
                IF v_ultima = 'MORA' AND v_pendientes = 0 THEN
                    INSERT INTO ORDEN_SERVICIO (CONTRATO_ID, TIPO, CAUSA, PAGO_ID, ACUERDO_ID, FECHA_LIMITE)
                    VALUES (c.ID, 'RECONEXION', p_causa, p_pago_id, p_acuerdo_id, SYSTIMESTAMP + INTERVAL '1' DAY);
                END IF;
            END IF;
        END LOOP;
    END;

END PKG_SIGCA;
/

-- =====================================================================
-- PARÁMETROS, CATÁLOGOS Y SEGURIDAD
-- =====================================================================

-- Rangos legales de los parámetros (se validan sobre la tabla completa al terminar la sentencia)
CREATE OR REPLACE TRIGGER TRG_PARAMETRO_CIU
FOR INSERT OR UPDATE ON PARAMETRO_SISTEMA
COMPOUND TRIGGER
    FUNCTION valor(p_clave VARCHAR2) RETURN NUMBER IS
        v PARAMETRO_SISTEMA.VALOR%TYPE;
    BEGIN
        SELECT VALOR INTO v FROM PARAMETRO_SISTEMA WHERE CLAVE = p_clave AND TIPO_DATO IN ('ENTERO','DECIMAL');
        RETURN TO_NUMBER(v, '999999999D9999', 'NLS_NUMERIC_CHARACTERS=''.,''');
    EXCEPTION
        WHEN NO_DATA_FOUND THEN RETURN NULL;
    END;

    PROCEDURE rango(p_clave VARCHAR2, p_min NUMBER, p_max NUMBER) IS
        v NUMBER := valor(p_clave);
    BEGIN
        IF v IS NOT NULL AND (v < p_min OR v > p_max) THEN
            PKG_SIGCA.error(-20011, 'El parámetro ' || p_clave || ' debe estar entre ' || p_min || ' y ' || p_max);
        END IF;
    END;

    BEFORE EACH ROW IS
    BEGIN
        IF UPDATING THEN
            IF :NEW.CLAVE <> :OLD.CLAVE OR :NEW.TIPO_DATO <> :OLD.TIPO_DATO THEN
                PKG_SIGCA.error(-20014, 'La clave y el tipo de un parámetro no se modifican');
            END IF;
        END IF;
        :NEW.ACTUALIZADO_EN := SYSTIMESTAMP;
    END BEFORE EACH ROW;

    AFTER STATEMENT IS
    BEGIN
        -- P-03 / Ley 142 art. 158: ningún tipo de PQR supera 15 días hábiles
        FOR p IN (SELECT CLAVE FROM PARAMETRO_SISTEMA WHERE CLAVE LIKE 'PQR\_DIAS\_%' ESCAPE '\') LOOP
            rango(p.CLAVE, 1, 15);
        END LOOP;
        rango('RECURSO_DIAS_PLAZO', 5, 10);                 -- RN-32: al menos los 5 días legales
        rango('PQR_HORAS_RECONOCIMIENTO_SILENCIO', 1, 72);  -- RN-31
        rango('MORA_PERIODOS_SUSPENSION', 1, 3);            -- P-11
        rango('ACUERDO_MAX_CUOTAS', 2, 24);                 -- P-12
        rango('ACUERDO_CUOTA_INICIAL_PCT', 0, 99);
        rango('ACUERDO_DIAS_TOLERANCIA', 0, 30);
        rango('MORA_TASA_RESIDENCIAL_ANUAL', 0, 6);         -- RN-38: interés civil
        rango('MORA_TASA_NO_RESIDENCIAL_ANUAL', 0, NVL(valor('TASA_USURA_ANUAL'), 100));
        rango('TASA_USURA_ANUAL', 0, 100);
        rango('LECTURA_DESVIACION_PCT', 1, 100);            -- P-07
        rango('LECTURA_PERIODOS_PROMEDIO', 1, 12);
        rango('RECONEXION_HORAS', 1, 24);                   -- RN-36
        rango('VALOR_CARGO_RECONEXION', 0, 10000000);
        rango('FACTURA_MESES_COBRO_OMITIDO', 1, 5);         -- RN-34
        rango('ADJUNTO_MAX_MB', 1, 10);
    END AFTER STATEMENT;
END TRG_PARAMETRO_CIU;
/

CREATE OR REPLACE TRIGGER TRG_SECTOR_BU
BEFORE UPDATE ON SECTOR
FOR EACH ROW
BEGIN
    IF :NEW.MUNICIPIO_ID <> :OLD.MUNICIPIO_ID THEN
        PKG_SIGCA.error(-20014, 'El municipio de un sector no se modifica');
    END IF;
END;
/

CREATE OR REPLACE TRIGGER TRG_FUNCIONARIO_BIU
BEFORE INSERT OR UPDATE ON FUNCIONARIO
FOR EACH ROW
DECLARE
    v_n PLS_INTEGER;
BEGIN
    IF :NEW.MUNICIPIO_ID IS NOT NULL AND (INSERTING OR PKG_SIGCA.distinto(:NEW.MUNICIPIO_ID, :OLD.MUNICIPIO_ID))
       AND NOT PKG_SIGCA.municipio_activo(:NEW.MUNICIPIO_ID) THEN
        PKG_SIGCA.error(-20003, 'El municipio asignado no es atendido por EPQ');
    END IF;
    IF UPDATING THEN
        IF :NEW.CODIGO_FUNCIONARIO <> :OLD.CODIGO_FUNCIONARIO THEN
            PKG_SIGCA.error(-20014, 'El código del funcionario no se puede modificar');
        END IF;
        -- Un Gestor de PQR necesita municipio (RN-20)
        IF :NEW.MUNICIPIO_ID IS NULL AND :OLD.MUNICIPIO_ID IS NOT NULL THEN
            SELECT COUNT(*) INTO v_n FROM FUNCIONARIO_ROL FR JOIN ROL R ON R.ID = FR.ROL_ID
             WHERE FR.FUNCIONARIO_ID = :NEW.ID AND R.CODIGO = 'GESTOR_PQR';
            IF v_n > 0 THEN
                PKG_SIGCA.error(-20015, 'Un Gestor de PQR debe tener municipio asignado');
            END IF;
        END IF;
        :NEW.ACTUALIZADO_EN := SYSTIMESTAMP;
    END IF;
END;
/

CREATE OR REPLACE TRIGGER TRG_FUNCIONARIO_ROL_BI
BEFORE INSERT ON FUNCIONARIO_ROL
FOR EACH ROW
DECLARE
    v_rol       ROL.CODIGO%TYPE;
    v_municipio FUNCIONARIO.MUNICIPIO_ID%TYPE;
BEGIN
    SELECT CODIGO INTO v_rol FROM ROL WHERE ID = :NEW.ROL_ID;
    SELECT MUNICIPIO_ID INTO v_municipio FROM FUNCIONARIO WHERE ID = :NEW.FUNCIONARIO_ID;
    -- P-16: CIUDADANO es el rol del portal; no se asigna a funcionarios
    IF v_rol = 'CIUDADANO' THEN
        PKG_SIGCA.error(-20015, 'El rol CIUDADANO es exclusivo de las cuentas del portal');
    END IF;
    IF v_rol = 'GESTOR_PQR' AND v_municipio IS NULL THEN
        PKG_SIGCA.error(-20015, 'Para asignar el rol Gestor de PQR el funcionario debe tener municipio');
    END IF;
END;
/

-- =====================================================================
-- USUARIOS, PREDIOS, MEDIDORES, TARIFAS Y FACTORES
-- =====================================================================

CREATE OR REPLACE TRIGGER TRG_USUARIO_BIU
BEFORE INSERT OR UPDATE ON USUARIO
FOR EACH ROW
DECLARE
    v_n PLS_INTEGER;
BEGIN
    IF INSERTING THEN
        :NEW.CODIGO := 'USR-' || LPAD(SEQ_COD_USUARIO.NEXTVAL, 8, '0');
        IF :NEW.ACTIVO <> 'S' THEN
            PKG_SIGCA.error(-20001, 'Un usuario se crea siempre activo');
        END IF;
    ELSE
        IF :NEW.CODIGO <> :OLD.CODIGO OR :NEW.TIPO_PERSONA <> :OLD.TIPO_PERSONA THEN
            PKG_SIGCA.error(-20014, 'El código y el tipo de persona no se pueden modificar');
        END IF;
        -- RN-26 / P-24: desactivar solo sin contratos vigentes ni deuda
        IF :OLD.ACTIVO = 'S' AND :NEW.ACTIVO = 'N' THEN
            SELECT COUNT(*) INTO v_n FROM CONTRATO_SERVICIO
             WHERE USUARIO_ID = :NEW.ID AND ESTADO IN ('ACTIVO','SUSPENDIDO');
            IF v_n > 0 THEN
                PKG_SIGCA.error(-20012, 'El usuario tiene contratos vigentes');
            END IF;
            SELECT COUNT(*) INTO v_n FROM FACTURA
             WHERE USUARIO_ID = :NEW.ID AND ESTADO IN ('PENDIENTE','VENCIDA','EN_ACUERDO') AND SALDO_PENDIENTE > 0;
            IF v_n > 0 THEN
                PKG_SIGCA.error(-20012, 'El usuario tiene facturas con saldo pendiente');
            END IF;
        END IF;
        :NEW.ACTUALIZADO_EN := SYSTIMESTAMP;
    END IF;
    IF (INSERTING OR :NEW.MUNICIPIO_ID <> :OLD.MUNICIPIO_ID)
       AND NOT PKG_SIGCA.municipio_activo(:NEW.MUNICIPIO_ID) THEN
        PKG_SIGCA.error(-20003, 'El municipio no es atendido por EPQ');
    END IF;
END;
/

CREATE OR REPLACE TRIGGER TRG_PREDIO_BIU
BEFORE INSERT OR UPDATE ON PREDIO
FOR EACH ROW
DECLARE
    v_activo SECTOR.ACTIVO%TYPE;
BEGIN
    IF INSERTING THEN
        :NEW.CODIGO_PREDIO := 'PRD-' || LPAD(SEQ_COD_PREDIO.NEXTVAL, 8, '0');
    ELSE
        IF :NEW.CODIGO_PREDIO <> :OLD.CODIGO_PREDIO THEN
            PKG_SIGCA.error(-20014, 'El código del predio no se puede modificar');
        END IF;
        :NEW.ACTUALIZADO_EN := SYSTIMESTAMP;
    END IF;
    IF (INSERTING OR :NEW.MUNICIPIO_ID <> :OLD.MUNICIPIO_ID)
       AND NOT PKG_SIGCA.municipio_activo(:NEW.MUNICIPIO_ID) THEN
        PKG_SIGCA.error(-20003, 'El municipio del predio no es atendido por EPQ');
    END IF;
    IF :NEW.SECTOR_ID IS NOT NULL AND (INSERTING OR PKG_SIGCA.distinto(:NEW.SECTOR_ID, :OLD.SECTOR_ID)) THEN
        SELECT ACTIVO INTO v_activo FROM SECTOR WHERE ID = :NEW.SECTOR_ID;
        IF v_activo <> 'S' THEN
            PKG_SIGCA.error(-20003, 'El sector no está activo');
        END IF;
    END IF;
END;
/

CREATE OR REPLACE TRIGGER TRG_MEDIDOR_BIU
BEFORE INSERT OR UPDATE ON MEDIDOR
FOR EACH ROW
BEGIN
    IF :NEW.FECHA_INSTALACION > TRUNC(SYSDATE) THEN
        PKG_SIGCA.error(-20008, 'La fecha de instalación no puede ser futura');
    END IF;
    IF INSERTING THEN
        :NEW.LECTURA_ACTUAL := :NEW.LECTURA_INICIAL;
        :NEW.ULTIMO_PERIODO := NULL;
        :NEW.LECTURA_EN_REVISION := 'N';
        IF :NEW.ESTADO <> 'ACTIVO' THEN
            PKG_SIGCA.error(-20001, 'Un medidor se instala siempre activo');
        END IF;
    ELSE
        IF :NEW.SERIAL <> :OLD.SERIAL OR :NEW.PREDIO_ID <> :OLD.PREDIO_ID
           OR :NEW.TIPO_SERVICIO <> :OLD.TIPO_SERVICIO OR :NEW.LECTURA_INICIAL <> :OLD.LECTURA_INICIAL THEN
            PKG_SIGCA.error(-20014, 'Serial, predio, tipo y lectura inicial del medidor no se modifican');
        END IF;
        IF :OLD.ESTADO = 'RETIRADO' AND :NEW.ESTADO = 'ACTIVO' THEN
            PKG_SIGCA.error(-20001, 'Un medidor retirado no se puede reactivar');
        END IF;
        IF :NEW.LECTURA_ACTUAL < :OLD.LECTURA_ACTUAL THEN
            PKG_SIGCA.error(-20017, 'La lectura del medidor no puede disminuir');
        END IF;
        :NEW.ACTUALIZADO_EN := SYSTIMESTAMP;
    END IF;
END;
/

CREATE OR REPLACE TRIGGER TRG_TARIFA_BIU
BEFORE INSERT OR UPDATE ON TARIFA
FOR EACH ROW
BEGIN
    IF NOT PKG_SIGCA.municipio_activo(:NEW.MUNICIPIO_ID) THEN
        PKG_SIGCA.error(-20003, 'El municipio de la tarifa no es atendido por EPQ');
    END IF;
    IF UPDATING THEN
        -- Solo se puede cerrar la vigencia; los valores de una tarifa ya usada no cambian
        IF :NEW.TIPO_SERVICIO <> :OLD.TIPO_SERVICIO OR :NEW.MUNICIPIO_ID <> :OLD.MUNICIPIO_ID
           OR :NEW.CARGO_FIJO <> :OLD.CARGO_FIJO OR :NEW.VALOR_M3_BASICO <> :OLD.VALOR_M3_BASICO
           OR PKG_SIGCA.distinto(:NEW.VALOR_M3_COMPLEMENTARIO, :OLD.VALOR_M3_COMPLEMENTARIO)
           OR PKG_SIGCA.distinto(:NEW.VALOR_M3_SUNTUARIO, :OLD.VALOR_M3_SUNTUARIO)
           OR PKG_SIGCA.distinto(:NEW.CONSUMO_BASICO_M3, :OLD.CONSUMO_BASICO_M3)
           OR PKG_SIGCA.distinto(:NEW.CONSUMO_SUNTUARIO_DESDE_M3, :OLD.CONSUMO_SUNTUARIO_DESDE_M3)
           OR :NEW.VIGENTE_DESDE <> :OLD.VIGENTE_DESDE THEN
            PKG_SIGCA.error(-20014, 'Una tarifa solo admite cerrar su vigencia; cree una tarifa nueva');
        END IF;
        :NEW.ACTUALIZADO_EN := SYSTIMESTAMP;
    END IF;
END;
/

CREATE OR REPLACE TRIGGER TRG_FACTOR_BIU
BEFORE INSERT OR UPDATE ON FACTOR_SUBSIDIO
FOR EACH ROW
BEGIN
    IF NOT PKG_SIGCA.municipio_activo(:NEW.MUNICIPIO_ID) THEN
        PKG_SIGCA.error(-20003, 'El municipio del factor no es atendido por EPQ');
    END IF;
    IF UPDATING THEN
        IF :NEW.MUNICIPIO_ID <> :OLD.MUNICIPIO_ID OR :NEW.TIPO_SERVICIO <> :OLD.TIPO_SERVICIO
           OR :NEW.USO <> :OLD.USO OR PKG_SIGCA.distinto(:NEW.ESTRATO, :OLD.ESTRATO)
           OR :NEW.PORCENTAJE <> :OLD.PORCENTAJE OR :NEW.VIGENTE_DESDE <> :OLD.VIGENTE_DESDE THEN
            PKG_SIGCA.error(-20014, 'Un factor solo admite cerrar su vigencia; cree un factor nuevo');
        END IF;
    END IF;
END;
/

-- =====================================================================
-- CONTRATOS (RF-04, RF-17, RN-01, RN-16, P-06, P-11)
-- =====================================================================

CREATE OR REPLACE TRIGGER TRG_CONTRATO_CIU
FOR INSERT OR UPDATE OR DELETE ON CONTRATO_SERVICIO
COMPOUND TRIGGER
    TYPE t_ids IS TABLE OF NUMBER INDEX BY PLS_INTEGER;
    g_ids t_ids;

    BEFORE EACH ROW IS
        v_usuario_activo USUARIO.ACTIVO%TYPE;
        v_municipio      PREDIO.MUNICIPIO_ID%TYPE;
        v_medidor_estado MEDIDOR.ESTADO%TYPE;
        v_n              PLS_INTEGER;
    BEGIN
        IF DELETING THEN
            PKG_SIGCA.error(-20006, 'Los contratos no se eliminan; se cancelan');
        END IF;
        IF INSERTING THEN
            :NEW.NUMERO_CONTRATO := 'CTR-' || LPAD(SEQ_COD_CONTRATO.NEXTVAL, 8, '0');
            IF :NEW.ESTADO <> 'ACTIVO' THEN
                PKG_SIGCA.error(-20001, 'Un contrato se registra en estado ACTIVO (SWR-14)');
            END IF;
            IF :NEW.FECHA_VINCULACION > TRUNC(SYSDATE) THEN
                PKG_SIGCA.error(-20008, 'La fecha de vinculación no puede ser futura');
            END IF;
            SELECT ACTIVO INTO v_usuario_activo FROM USUARIO WHERE ID = :NEW.USUARIO_ID;
            IF v_usuario_activo <> 'S' THEN
                PKG_SIGCA.error(-20012, 'No se puede contratar con un usuario inactivo');
            END IF;
            SELECT MUNICIPIO_ID INTO v_municipio FROM PREDIO WHERE ID = :NEW.PREDIO_ID;
            IF NOT PKG_SIGCA.municipio_activo(v_municipio) THEN
                PKG_SIGCA.error(-20003, 'El municipio del predio no es atendido por EPQ');
            END IF;
            :NEW.FECHA_ESTADO := SYSTIMESTAMP;
        ELSE
            IF :NEW.NUMERO_CONTRATO <> :OLD.NUMERO_CONTRATO OR :NEW.USUARIO_ID <> :OLD.USUARIO_ID
               OR :NEW.PREDIO_ID <> :OLD.PREDIO_ID OR :NEW.TIPO_SERVICIO <> :OLD.TIPO_SERVICIO
               OR :NEW.FECHA_VINCULACION <> :OLD.FECHA_VINCULACION THEN
                PKG_SIGCA.error(-20014, 'Número, usuario, predio, servicio y fecha del contrato no se modifican');
            END IF;
            IF :OLD.ESTADO = 'CANCELADO' AND :NEW.ESTADO = 'CANCELADO' THEN
                PKG_SIGCA.error(-20006, 'Un contrato cancelado no se modifica');
            END IF;
            -- Cambio de medidor (reposición): el nuevo debe estar activo
            IF PKG_SIGCA.distinto(:NEW.MEDIDOR_ID, :OLD.MEDIDOR_ID) AND :NEW.MEDIDOR_ID IS NOT NULL THEN
                SELECT ESTADO INTO v_medidor_estado FROM MEDIDOR WHERE ID = :NEW.MEDIDOR_ID;
                IF v_medidor_estado <> 'ACTIVO' THEN
                    PKG_SIGCA.error(-20017, 'El medidor asignado está retirado');
                END IF;
            END IF;
            IF :NEW.ESTADO <> :OLD.ESTADO THEN
                IF (:OLD.ESTADO = 'ACTIVO' AND :NEW.ESTADO = 'SUSPENDIDO')
                   OR (:OLD.ESTADO = 'SUSPENDIDO' AND :NEW.ESTADO = 'ACTIVO') THEN
                    -- P-11: suspensión y reconexión solo ejecutando una orden de servicio
                    IF NOT PKG_SIGCA.g_en_orden THEN
                        PKG_SIGCA.error(-20020, 'La suspensión y la reconexión se hacen ejecutando una orden de servicio');
                    END IF;
                ELSIF :OLD.ESTADO IN ('ACTIVO','SUSPENDIDO') AND :NEW.ESTADO = 'CANCELADO' THEN
                    -- P-11: cancelar requiere no tener deuda ni órdenes pendientes
                    SELECT COUNT(*) INTO v_n FROM LINEA_FACTURA L JOIN FACTURA F ON F.ID = L.FACTURA_ID
                     WHERE L.CONTRATO_ID = :NEW.ID AND L.ESTADO = 'PENDIENTE' AND F.ESTADO <> 'ANULADA';
                    IF v_n > 0 THEN
                        PKG_SIGCA.error(-20012, 'El contrato tiene valores pendientes de pago');
                    END IF;
                    SELECT COUNT(*) INTO v_n FROM ORDEN_SERVICIO WHERE CONTRATO_ID = :NEW.ID AND ESTADO = 'PENDIENTE';
                    IF v_n > 0 THEN
                        PKG_SIGCA.error(-20020, 'El contrato tiene órdenes de servicio pendientes');
                    END IF;
                ELSE
                    PKG_SIGCA.error(-20001, 'Transición de contrato no permitida: ' || :OLD.ESTADO || ' -> ' || :NEW.ESTADO);
                END IF;
                :NEW.FECHA_ESTADO := SYSTIMESTAMP;
            END IF;
            :NEW.ACTUALIZADO_EN := SYSTIMESTAMP;
        END IF;
    END BEFORE EACH ROW;

    AFTER EACH ROW IS
    BEGIN
        IF INSERTING OR :NEW.ESTADO <> :OLD.ESTADO THEN
            g_ids(g_ids.COUNT + 1) := :NEW.ID;
        END IF;
    END AFTER EACH ROW;

    -- P-06: alcantarillado exige acueducto vigente del mismo usuario en el predio,
    -- y el acueducto no se cancela mientras el alcantarillado siga vigente
    AFTER STATEMENT IS
        v_c CONTRATO_SERVICIO%ROWTYPE;
        v_n PLS_INTEGER;
    BEGIN
        FOR i IN 1 .. g_ids.COUNT LOOP
            SELECT * INTO v_c FROM CONTRATO_SERVICIO WHERE ID = g_ids(i);
            IF v_c.TIPO_SERVICIO = 'ALCANTARILLADO' AND v_c.ESTADO IN ('ACTIVO','SUSPENDIDO') THEN
                SELECT COUNT(*) INTO v_n FROM CONTRATO_SERVICIO
                 WHERE PREDIO_ID = v_c.PREDIO_ID AND USUARIO_ID = v_c.USUARIO_ID
                   AND TIPO_SERVICIO = 'ACUEDUCTO' AND ESTADO IN ('ACTIVO','SUSPENDIDO');
                IF v_n = 0 THEN
                    PKG_SIGCA.error(-20002, 'El alcantarillado requiere un contrato de acueducto vigente del mismo usuario en el predio');
                END IF;
            ELSIF v_c.TIPO_SERVICIO = 'ACUEDUCTO' AND v_c.ESTADO = 'CANCELADO' THEN
                SELECT COUNT(*) INTO v_n FROM CONTRATO_SERVICIO
                 WHERE PREDIO_ID = v_c.PREDIO_ID AND TIPO_SERVICIO = 'ALCANTARILLADO' AND ESTADO IN ('ACTIVO','SUSPENDIDO');
                IF v_n > 0 THEN
                    PKG_SIGCA.error(-20002, 'Cancele primero el contrato de alcantarillado del predio');
                END IF;
            END IF;
        END LOOP;
    END AFTER STATEMENT;
END TRG_CONTRATO_CIU;
/

-- =====================================================================
-- LECTURAS Y CRÍTICA (P-07, RN-35)
-- =====================================================================

CREATE OR REPLACE TRIGGER TRG_LECTURA_CIUD
FOR INSERT OR UPDATE OR DELETE ON LECTURA
COMPOUND TRIGGER
    TYPE t_ids IS TABLE OF NUMBER INDEX BY PLS_INTEGER;
    g_ids       t_ids;      -- lecturas insertadas en la sentencia
    g_medidores t_ids;      -- medidores tocados (índice = ID del medidor)

    BEFORE EACH ROW IS
        v_med     MEDIDOR%ROWTYPE;
        v_inicio  DATE;
    BEGIN
        IF DELETING THEN
            PKG_SIGCA.error(-20006, 'Las lecturas no se eliminan');
        END IF;

        IF INSERTING THEN
            IF g_medidores.EXISTS(:NEW.MEDIDOR_ID) THEN
                PKG_SIGCA.error(-20017, 'Una sola lectura por medidor en cada carga');
            END IF;
            g_medidores(:NEW.MEDIDOR_ID) := 1;
            SELECT * INTO v_med FROM MEDIDOR WHERE ID = :NEW.MEDIDOR_ID FOR UPDATE;
            IF v_med.ESTADO <> 'ACTIVO' THEN
                PKG_SIGCA.error(-20017, 'No se registran lecturas a un medidor retirado');
            END IF;
            IF v_med.LECTURA_EN_REVISION = 'S' THEN
                PKG_SIGCA.error(-20017, 'El medidor tiene una lectura en revisión; confírmela o recházela primero');
            END IF;
            IF :NEW.FECHA_LECTURA > TRUNC(SYSDATE) THEN
                PKG_SIGCA.error(-20008, 'La fecha de lectura no puede ser futura');
            END IF;
            -- La lectura del periodo se toma dentro del mes o en el mes siguiente
            v_inicio := PKG_SIGCA.inicio_periodo(:NEW.PERIODO);
            IF :NEW.FECHA_LECTURA < v_inicio OR :NEW.FECHA_LECTURA > LAST_DAY(ADD_MONTHS(v_inicio, 1)) THEN
                PKG_SIGCA.error(-20017, 'La fecha de lectura no corresponde al periodo ' || :NEW.PERIODO);
            END IF;
            -- Periodos consecutivos: un periodo sin lectura se registra como ESTIMADA, no se salta
            IF v_med.ULTIMO_PERIODO IS NOT NULL THEN
                IF :NEW.PERIODO <> PKG_SIGCA.periodo_siguiente(v_med.ULTIMO_PERIODO) THEN
                    PKG_SIGCA.error(-20017, 'Lectura fuera de secuencia: el siguiente periodo del medidor es '
                        || PKG_SIGCA.periodo_siguiente(v_med.ULTIMO_PERIODO));
                END IF;
            ELSIF :NEW.PERIODO < TO_NUMBER(TO_CHAR(v_med.FECHA_INSTALACION, 'YYYYMM')) THEN
                PKG_SIGCA.error(-20017, 'El periodo es anterior a la instalación del medidor');
            END IF;
            -- Valores que impone el sistema
            :NEW.ESTADO := 'REGISTRADA';
            :NEW.LECTURA_ANTERIOR := v_med.LECTURA_ACTUAL;
            :NEW.PROMEDIO_REFERENCIA := NULL;
            :NEW.DESVIACION_PCT := NULL;
            :NEW.REVISADA_POR := NULL;
            :NEW.FECHA_REVISION := NULL;
            :NEW.CREADO_EN := SYSTIMESTAMP;
            IF :NEW.LECTURA_ACTUAL IS NOT NULL THEN
                IF :NEW.LECTURA_ACTUAL < :NEW.LECTURA_ANTERIOR THEN
                    PKG_SIGCA.error(-20017, 'La lectura actual (' || :NEW.LECTURA_ACTUAL
                        || ') es menor que la anterior (' || :NEW.LECTURA_ANTERIOR || ')');
                END IF;
                :NEW.CONSUMO := :NEW.LECTURA_ACTUAL - :NEW.LECTURA_ANTERIOR;
            ELSE
                :NEW.CONSUMO := NULL;
            END IF;
            UPDATE MEDIDOR SET ULTIMO_PERIODO = :NEW.PERIODO WHERE ID = :NEW.MEDIDOR_ID;
            GOTO fin_before_0;
        END IF;

        -- UPDATING
        IF PKG_SIGCA.g_en_critica THEN
            GOTO fin_before_0;     -- resultado de la crítica automática (AFTER STATEMENT)
        END IF;
        IF :NEW.MEDIDOR_ID <> :OLD.MEDIDOR_ID OR :NEW.PERIODO <> :OLD.PERIODO
           OR :NEW.FECHA_LECTURA <> :OLD.FECHA_LECTURA OR :NEW.LECTURA_ANTERIOR <> :OLD.LECTURA_ANTERIOR
           OR PKG_SIGCA.distinto(:NEW.LECTURA_ACTUAL, :OLD.LECTURA_ACTUAL) OR :NEW.ORIGEN <> :OLD.ORIGEN
           OR PKG_SIGCA.distinto(:NEW.CAUSA_NO_LECTURA, :OLD.CAUSA_NO_LECTURA)
           OR PKG_SIGCA.distinto(:NEW.PROMEDIO_REFERENCIA, :OLD.PROMEDIO_REFERENCIA)
           OR PKG_SIGCA.distinto(:NEW.DESVIACION_PCT, :OLD.DESVIACION_PCT)
           OR PKG_SIGCA.distinto(:NEW.REGISTRADA_POR, :OLD.REGISTRADA_POR) THEN
            PKG_SIGCA.error(-20006, 'Los datos de una lectura no se modifican');
        END IF;
        IF :OLD.ESTADO <> 'EN_REVISION' OR :NEW.ESTADO NOT IN ('CONFIRMADA','ESTIMADA') THEN
            PKG_SIGCA.error(-20001, 'Transición de lectura no permitida: ' || :OLD.ESTADO || ' -> ' || :NEW.ESTADO);
        END IF;
        -- P-07: la revisión la hace el Asesor
        IF NOT PKG_SIGCA.tiene_rol(:NEW.REVISADA_POR, 'ASESOR') THEN
            PKG_SIGCA.error(-20015, 'La revisión de lecturas la hace un Asesor');
        END IF;
        :NEW.FECHA_REVISION := SYSTIMESTAMP;
        SELECT * INTO v_med FROM MEDIDOR WHERE ID = :NEW.MEDIDOR_ID FOR UPDATE;
        IF :NEW.ESTADO = 'CONFIRMADA' THEN
            :NEW.CONSUMO := :NEW.LECTURA_ACTUAL - :NEW.LECTURA_ANTERIOR;
            IF :NEW.LECTURA_ACTUAL < v_med.LECTURA_ACTUAL THEN
                PKG_SIGCA.error(-20017, 'La lectura es menor que la lectura vigente del medidor');
            END IF;
            UPDATE MEDIDOR SET LECTURA_ACTUAL = :NEW.LECTURA_ACTUAL, LECTURA_EN_REVISION = 'N' WHERE ID = :NEW.MEDIDOR_ID;
        ELSE
            -- Rechazada: se factura por promedio y el medidor conserva la última lectura confirmada
            :NEW.CONSUMO := :NEW.PROMEDIO_REFERENCIA;
            UPDATE MEDIDOR SET LECTURA_EN_REVISION = 'N' WHERE ID = :NEW.MEDIDOR_ID;
        END IF;
    <<fin_before_0>>
        NULL;
    END BEFORE EACH ROW;

    AFTER EACH ROW IS
    BEGIN
        IF INSERTING THEN
            g_ids(g_ids.COUNT + 1) := :NEW.ID;
        END IF;
    END AFTER EACH ROW;

    -- Crítica: promedio de los últimos N periodos CONFIRMADOS o ESTIMADOS del medidor
    AFTER STATEMENT IS
        v_l          LECTURA%ROWTYPE;
        v_promedio   NUMBER;
        v_desv       NUMBER;
        v_limite     NUMBER;
        v_n          PLS_INTEGER;
    BEGIN
        IF g_ids.COUNT = 0 THEN
            GOTO fin_after_4;
        END IF;
        v_limite := PKG_SIGCA.parametro_num('LECTURA_DESVIACION_PCT');
        v_n      := PKG_SIGCA.parametro_num('LECTURA_PERIODOS_PROMEDIO');
        PKG_SIGCA.g_en_critica := TRUE;
        BEGIN
            FOR i IN 1 .. g_ids.COUNT LOOP
                SELECT * INTO v_l FROM LECTURA WHERE ID = g_ids(i);
                SELECT ROUND(AVG(CONSUMO), 3) INTO v_promedio FROM (
                    SELECT CONSUMO FROM LECTURA
                     WHERE MEDIDOR_ID = v_l.MEDIDOR_ID AND PERIODO < v_l.PERIODO
                       AND ESTADO IN ('CONFIRMADA','ESTIMADA')
                     ORDER BY PERIODO DESC FETCH FIRST v_n ROWS ONLY);
                IF v_l.LECTURA_ACTUAL IS NULL THEN
                    -- RN-35: sin lectura se factura por promedio
                    IF v_promedio IS NULL THEN
                        PKG_SIGCA.error(-20017, 'El medidor no tiene historial para estimar el consumo del periodo ' || v_l.PERIODO);
                    END IF;
                    UPDATE LECTURA SET ESTADO = 'ESTIMADA', PROMEDIO_REFERENCIA = v_promedio, CONSUMO = v_promedio
                     WHERE ID = v_l.ID;
                ELSIF v_promedio IS NULL THEN
                    -- Primera lectura del medidor: no hay contra qué comparar
                    UPDATE LECTURA SET ESTADO = 'CONFIRMADA' WHERE ID = v_l.ID;
                    UPDATE MEDIDOR SET LECTURA_ACTUAL = v_l.LECTURA_ACTUAL WHERE ID = v_l.MEDIDOR_ID;
                ELSE
                    v_desv := CASE
                                  WHEN v_promedio = 0 AND v_l.CONSUMO = 0 THEN 0
                                  WHEN v_promedio = 0 THEN NULL      -- consumo nuevo sobre promedio cero
                                  ELSE LEAST(ROUND(ABS(v_l.CONSUMO - v_promedio) / v_promedio * 100, 2), 9999999.99)
                              END;
                    IF v_desv IS NULL OR v_desv > v_limite THEN
                        UPDATE LECTURA SET ESTADO = 'EN_REVISION', PROMEDIO_REFERENCIA = v_promedio, DESVIACION_PCT = v_desv
                         WHERE ID = v_l.ID;
                        UPDATE MEDIDOR SET LECTURA_EN_REVISION = 'S' WHERE ID = v_l.MEDIDOR_ID;
                    ELSE
                        UPDATE LECTURA SET ESTADO = 'CONFIRMADA', PROMEDIO_REFERENCIA = v_promedio, DESVIACION_PCT = v_desv
                         WHERE ID = v_l.ID;
                        UPDATE MEDIDOR SET LECTURA_ACTUAL = v_l.LECTURA_ACTUAL WHERE ID = v_l.MEDIDOR_ID;
                    END IF;
                END IF;
            END LOOP;
            PKG_SIGCA.g_en_critica := FALSE;
        EXCEPTION
            WHEN OTHERS THEN
                PKG_SIGCA.g_en_critica := FALSE;
                RAISE;
        END;
    <<fin_after_4>>
        NULL;
    END AFTER STATEMENT;
END TRG_LECTURA_CIUD;
/

-- =====================================================================
-- FACTURACIÓN (DE-01, P-05, P-10, RN-02, RN-16, RN-33, RN-34)
-- =====================================================================

CREATE OR REPLACE TRIGGER TRG_FACTURA_CIUD
FOR INSERT OR UPDATE OR DELETE ON FACTURA
COMPOUND TRIGGER
    TYPE t_ids IS TABLE OF NUMBER INDEX BY PLS_INTEGER;
    g_reexpedidas t_ids;

    BEFORE EACH ROW IS
        v_activo USUARIO.ACTIVO%TYPE;
    BEGIN
        IF DELETING THEN
            PKG_SIGCA.error(-20006, 'Las facturas no se eliminan; se anulan');
        END IF;

        IF INSERTING THEN
            :NEW.NUMERO_FACTURA := 'FAC-' || TO_CHAR(:NEW.PERIODO) || '-' || LPAD(SEQ_COD_FACTURA.NEXTVAL, 7, '0');
            IF :NEW.ESTADO <> 'PENDIENTE' OR :NEW.ESTADO_SYNC_ERP <> 'PENDIENTE' THEN
                PKG_SIGCA.error(-20001, 'Una factura se genera en estado PENDIENTE');
            END IF;
            -- Los totales los acumulan las líneas
            :NEW.NUMERO_LINEAS := 0;
            :NEW.TOTAL_A_PAGAR := 0;
            :NEW.VALOR_AJUSTES := 0;
            :NEW.VALOR_PAGADO := 0;
            :NEW.VALOR_EN_RECLAMO := 0;
            :NEW.SALDO_PENDIENTE := 0;
            SELECT ACTIVO INTO v_activo FROM USUARIO WHERE ID = :NEW.USUARIO_ID;
            IF v_activo <> 'S' THEN
                PKG_SIGCA.error(-20012, 'No se factura a un usuario inactivo');
            END IF;
            IF :NEW.FECHA_EMISION > TRUNC(SYSDATE) THEN
                PKG_SIGCA.error(-20008, 'La fecha de emisión no puede ser futura');
            END IF;
            IF :NEW.FECHA_EMISION <= PKG_SIGCA.fin_periodo(:NEW.PERIODO) THEN
                PKG_SIGCA.error(-20019, 'El periodo ' || :NEW.PERIODO || ' aún no ha terminado');
            END IF;
            -- RN-34: no se cobra un periodo omitido después de N meses (Ley 142 art. 150)
            IF :NEW.REEXPEDICION_DE_ID IS NULL
               AND :NEW.FECHA_EMISION > ADD_MONTHS(PKG_SIGCA.fin_periodo(:NEW.PERIODO),
                                                   PKG_SIGCA.parametro_num('FACTURA_MESES_COBRO_OMITIDO')) THEN
                PKG_SIGCA.error(-20019, 'Ya no se puede facturar el periodo ' || :NEW.PERIODO || ' (RN-34)');
            END IF;
            :NEW.FECHA_ESTADO := SYSTIMESTAMP;
            GOTO fin_before_0;
        END IF;

        -- Una factura emitida no se corrige: se anula y se reexpide (P-10)
        IF :NEW.NUMERO_FACTURA <> :OLD.NUMERO_FACTURA OR :NEW.USUARIO_ID <> :OLD.USUARIO_ID
           OR :NEW.PREDIO_ID <> :OLD.PREDIO_ID OR :NEW.PERIODO <> :OLD.PERIODO
           OR :NEW.FECHA_EMISION <> :OLD.FECHA_EMISION OR :NEW.FECHA_VENCIMIENTO <> :OLD.FECHA_VENCIMIENTO
           OR PKG_SIGCA.distinto(:NEW.LOTE_ID, :OLD.LOTE_ID)
           OR PKG_SIGCA.distinto(:NEW.REEXPEDICION_DE_ID, :OLD.REEXPEDICION_DE_ID) THEN
            PKG_SIGCA.error(-20014, 'Los datos de una factura emitida no se modifican; anúlela y reexpídala');
        END IF;
        IF NOT PKG_SIGCA.g_en_linea AND (
               :NEW.NUMERO_LINEAS <> :OLD.NUMERO_LINEAS OR :NEW.TOTAL_A_PAGAR <> :OLD.TOTAL_A_PAGAR
            OR :NEW.VALOR_AJUSTES <> :OLD.VALOR_AJUSTES OR :NEW.VALOR_PAGADO <> :OLD.VALOR_PAGADO
            OR :NEW.VALOR_EN_RECLAMO <> :OLD.VALOR_EN_RECLAMO OR :NEW.SALDO_PENDIENTE <> :OLD.SALDO_PENDIENTE) THEN
            PKG_SIGCA.error(-20014, 'Los totales de la factura los calculan sus líneas y pagos');
        END IF;
        :NEW.SALDO_PENDIENTE := :NEW.TOTAL_A_PAGAR + :NEW.VALOR_AJUSTES - :NEW.VALOR_PAGADO;

        IF :OLD.ESTADO IN ('ANULADA','PAGADA') AND (:NEW.ESTADO <> :OLD.ESTADO
            OR PKG_SIGCA.distinto(:NEW.MOTIVO_ANULACION, :OLD.MOTIVO_ANULACION)
            OR PKG_SIGCA.distinto(:NEW.ANULADA_POR, :OLD.ANULADA_POR)) THEN
            PKG_SIGCA.error(-20006, 'Una factura ' || :OLD.ESTADO || ' es un estado final');
        END IF;

        -- PAGADA se deriva: saldo en cero con al menos una línea
        IF :OLD.ESTADO IN ('PENDIENTE','VENCIDA','EN_ACUERDO') AND :NEW.ESTADO = :OLD.ESTADO
           AND :NEW.NUMERO_LINEAS > 0 AND :NEW.SALDO_PENDIENTE = 0 THEN
            :NEW.ESTADO := 'PAGADA';
        END IF;

        IF :NEW.ESTADO <> :OLD.ESTADO THEN
            CASE
                WHEN :NEW.ESTADO = 'PAGADA' THEN
                    IF :NEW.SALDO_PENDIENTE <> 0 OR :NEW.NUMERO_LINEAS = 0 THEN
                        PKG_SIGCA.error(-20016, 'Una factura solo queda PAGADA cuando sus pagos saldan el total (RN-05)');
                    END IF;
                WHEN :OLD.ESTADO = 'PENDIENTE' AND :NEW.ESTADO = 'VENCIDA' THEN
                    IF :OLD.FECHA_VENCIMIENTO >= TRUNC(SYSDATE) THEN
                        PKG_SIGCA.error(-20001, 'La factura aún no ha vencido');
                    END IF;
                    IF :NEW.SALDO_PENDIENTE - :NEW.VALOR_EN_RECLAMO <= 0 THEN
                        PKG_SIGCA.error(-20021, 'El saldo de la factura está en reclamo y no es exigible');
                    END IF;
                WHEN (:OLD.ESTADO = 'VENCIDA' AND :NEW.ESTADO = 'EN_ACUERDO')
                  OR (:OLD.ESTADO = 'EN_ACUERDO' AND :NEW.ESTADO = 'VENCIDA') THEN
                    IF NOT PKG_SIGCA.g_en_acuerdo THEN
                        PKG_SIGCA.error(-20001, 'Una factura entra o sale de EN_ACUERDO solo desde un acuerdo de pago');
                    END IF;
                WHEN :OLD.ESTADO IN ('PENDIENTE','VENCIDA') AND :NEW.ESTADO = 'ANULADA' THEN
                    -- P-10: solo sin pagos aplicados ni ajustes por reclamo
                    IF :OLD.VALOR_PAGADO <> 0 OR :OLD.VALOR_AJUSTES <> 0 THEN
                        PKG_SIGCA.error(-20001, 'No se anula una factura con pagos o ajustes aplicados');
                    END IF;
                ELSE
                    PKG_SIGCA.error(-20001, 'Transición de factura no permitida: ' || :OLD.ESTADO || ' -> ' || :NEW.ESTADO);
            END CASE;
            :NEW.FECHA_ESTADO := SYSTIMESTAMP;
        END IF;
        :NEW.ACTUALIZADO_EN := SYSTIMESTAMP;
    <<fin_before_0>>
        NULL;
    END BEFORE EACH ROW;

    AFTER EACH ROW IS
    BEGIN
        IF INSERTING AND :NEW.REEXPEDICION_DE_ID IS NOT NULL THEN
            g_reexpedidas(g_reexpedidas.COUNT + 1) := :NEW.ID;
        END IF;
    END AFTER EACH ROW;

    -- P-10 / RN-34: se reexpide solo una factura anulada, dentro de los N meses siguientes a su emisión
    AFTER STATEMENT IS
        v_estado   FACTURA.ESTADO%TYPE;
        v_emision  DATE;
        v_nueva    DATE;
    BEGIN
        FOR i IN 1 .. g_reexpedidas.COUNT LOOP
            SELECT O.ESTADO, O.FECHA_EMISION, N.FECHA_EMISION INTO v_estado, v_emision, v_nueva
              FROM FACTURA N JOIN FACTURA O ON O.ID = N.REEXPEDICION_DE_ID
             WHERE N.ID = g_reexpedidas(i);
            IF v_estado <> 'ANULADA' THEN
                PKG_SIGCA.error(-20001, 'Solo se reexpide una factura anulada');
            END IF;
            IF v_nueva > ADD_MONTHS(v_emision, PKG_SIGCA.parametro_num('FACTURA_MESES_COBRO_OMITIDO')) THEN
                PKG_SIGCA.error(-20019, 'Pasó el plazo para cobrar valores omitidos de la factura original (RN-34)');
            END IF;
        END LOOP;
    END AFTER STATEMENT;
END TRG_FACTURA_CIUD;
/

-- Liquidación de la línea: se verifica contra tarifa, factor y lectura (P-08)
CREATE OR REPLACE TRIGGER TRG_LINEA_BI
BEFORE INSERT ON LINEA_FACTURA
FOR EACH ROW
DECLARE
    v_fac      FACTURA%ROWTYPE;
    v_con      CONTRATO_SERVICIO%ROWTYPE;
    v_pre      PREDIO%ROWTYPE;
    v_tar      TARIFA%ROWTYPE;
    v_fsu      FACTOR_SUBSIDIO%ROWTYPE;
    v_lec      LECTURA%ROWTYPE;
    v_fin      DATE;
    v_medidor  NUMBER;
    v_b        NUMBER;
    v_c        NUMBER;
    v_s        NUMBER;
    v_valor    NUMBER;
    v_subsidio NUMBER := 0;
    v_contrib  NUMBER := 0;
BEGIN
    SELECT * INTO v_fac FROM FACTURA WHERE ID = :NEW.FACTURA_ID FOR UPDATE;
    IF v_fac.ESTADO <> 'PENDIENTE' OR v_fac.VALOR_PAGADO <> 0 OR v_fac.ESTADO_SYNC_ERP <> 'PENDIENTE' THEN
        PKG_SIGCA.error(-20006, 'La factura ' || v_fac.NUMERO_FACTURA || ' ya no admite líneas');
    END IF;
    SELECT * INTO v_con FROM CONTRATO_SERVICIO WHERE ID = :NEW.CONTRATO_ID;
    IF v_con.USUARIO_ID <> v_fac.USUARIO_ID OR v_con.PREDIO_ID <> v_fac.PREDIO_ID
       OR v_con.TIPO_SERVICIO <> :NEW.TIPO_SERVICIO THEN
        PKG_SIGCA.error(-20002, 'El contrato no corresponde al usuario, predio o servicio de la factura');
    END IF;
    -- RN-16: solo se facturan contratos activos
    IF v_con.ESTADO <> 'ACTIVO' THEN
        PKG_SIGCA.error(-20002, 'Solo se facturan contratos activos');
    END IF;
    SELECT * INTO v_pre FROM PREDIO WHERE ID = v_fac.PREDIO_ID;
    v_fin := PKG_SIGCA.fin_periodo(v_fac.PERIODO);

    -- Tarifa del servicio y municipio, vigente al cierre del periodo
    SELECT * INTO v_tar FROM TARIFA WHERE ID = :NEW.TARIFA_ID;
    IF v_tar.TIPO_SERVICIO <> :NEW.TIPO_SERVICIO OR v_tar.MUNICIPIO_ID <> v_pre.MUNICIPIO_ID
       OR v_tar.VIGENTE_DESDE > v_fin OR (v_tar.VIGENTE_HASTA IS NOT NULL AND v_tar.VIGENTE_HASTA < v_fin) THEN
        PKG_SIGCA.error(-20007, 'La tarifa no corresponde al servicio y municipio o no está vigente en el periodo');
    END IF;
    IF :NEW.TIPO_SERVICIO <> 'GAS' THEN
        SELECT * INTO v_fsu FROM FACTOR_SUBSIDIO WHERE ID = :NEW.FACTOR_SUBSIDIO_ID;
        IF v_fsu.TIPO_SERVICIO <> :NEW.TIPO_SERVICIO OR v_fsu.MUNICIPIO_ID <> v_pre.MUNICIPIO_ID
           OR v_fsu.USO <> v_pre.USO OR PKG_SIGCA.distinto(v_fsu.ESTRATO, v_pre.ESTRATO)
           OR v_fsu.VIGENTE_DESDE > v_fin OR (v_fsu.VIGENTE_HASTA IS NOT NULL AND v_fsu.VIGENTE_HASTA < v_fin) THEN
            PKG_SIGCA.error(-20007, 'El factor de subsidio no corresponde al uso y estrato del predio o no está vigente');
        END IF;
    END IF;

    -- Lectura del periodo: del medidor del contrato, o del acueducto del predio para alcantarillado
    IF :NEW.TIPO_SERVICIO = 'ALCANTARILLADO' THEN
        BEGIN
            SELECT MEDIDOR_ID INTO v_medidor FROM CONTRATO_SERVICIO
             WHERE PREDIO_ID = v_fac.PREDIO_ID AND TIPO_SERVICIO = 'ACUEDUCTO' AND ESTADO IN ('ACTIVO','SUSPENDIDO');
        EXCEPTION
            WHEN NO_DATA_FOUND THEN
                PKG_SIGCA.error(-20002, 'El predio no tiene acueducto vigente para liquidar el alcantarillado');
        END;
    ELSE
        v_medidor := v_con.MEDIDOR_ID;
    END IF;
    SELECT * INTO v_lec FROM LECTURA WHERE ID = :NEW.LECTURA_ID;
    IF v_lec.MEDIDOR_ID <> v_medidor OR v_lec.PERIODO <> v_fac.PERIODO THEN
        PKG_SIGCA.error(-20017, 'La lectura no es del medidor ni del periodo que se factura');
    END IF;
    IF v_lec.ESTADO NOT IN ('CONFIRMADA','ESTIMADA') THEN
        PKG_SIGCA.error(-20017, 'La lectura está ' || v_lec.ESTADO || ' y no se puede facturar (P-07)');
    END IF;
    :NEW.CONSUMO_ESTIMADO := CASE WHEN v_lec.ESTADO = 'ESTIMADA' THEN 'S' ELSE 'N' END;

    -- Rangos de consumo
    IF :NEW.TIPO_SERVICIO = 'GAS' THEN
        v_b := v_lec.CONSUMO; v_c := 0; v_s := 0;
    ELSE
        v_b := LEAST(v_lec.CONSUMO, v_tar.CONSUMO_BASICO_M3);
        v_s := GREATEST(v_lec.CONSUMO - v_tar.CONSUMO_SUNTUARIO_DESDE_M3, 0);
        v_c := v_lec.CONSUMO - v_b - v_s;
    END IF;
    v_valor := ROUND(v_b * v_tar.VALOR_M3_BASICO + v_c * NVL(v_tar.VALOR_M3_COMPLEMENTARIO, 0)
                     + v_s * NVL(v_tar.VALOR_M3_SUNTUARIO, 0), 2);
    IF :NEW.TIPO_SERVICIO <> 'GAS' THEN
        IF v_fsu.PORCENTAJE < 0 THEN
            -- RN-37: el subsidio aplica solo al consumo básico
            v_subsidio := ROUND(v_b * v_tar.VALOR_M3_BASICO * v_fsu.PORCENTAJE / 100, 2);
        ELSIF v_fsu.PORCENTAJE > 0 THEN
            v_contrib := ROUND((v_tar.CARGO_FIJO + v_valor) * v_fsu.PORCENTAJE / 100, 2);
        END IF;
    END IF;

    IF :NEW.CONSUMO <> v_lec.CONSUMO OR :NEW.CONSUMO_BASICO <> v_b
       OR :NEW.CONSUMO_COMPLEMENTARIO <> v_c OR :NEW.CONSUMO_SUNTUARIO <> v_s THEN
        PKG_SIGCA.error(-20018, 'Consumo liquidado distinto al de la lectura (esperado ' || v_lec.CONSUMO
            || ' m3: básico ' || v_b || ', complementario ' || v_c || ', suntuario ' || v_s || ')');
    END IF;
    IF :NEW.CARGO_FIJO <> v_tar.CARGO_FIJO OR :NEW.VALOR_CONSUMO <> v_valor
       OR :NEW.SUBSIDIO <> v_subsidio OR :NEW.CONTRIBUCION <> v_contrib THEN
        PKG_SIGCA.error(-20018, 'Liquidación distinta a la tarifa (esperado cargo fijo ' || v_tar.CARGO_FIJO
            || ', consumo ' || v_valor || ', subsidio ' || v_subsidio || ', contribución ' || v_contrib || ')');
    END IF;

    :NEW.ESTADO := 'PENDIENTE';
    :NEW.EN_RECLAMO := 'N';
    :NEW.VALOR_AJUSTE := 0;
    :NEW.PAGO_ID := NULL;
    :NEW.FECHA_PAGO := NULL;
    :NEW.CREADO_EN := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER TRG_LINEA_AI
AFTER INSERT ON LINEA_FACTURA
FOR EACH ROW
BEGIN
    PKG_SIGCA.g_en_linea := TRUE;
    BEGIN
        UPDATE FACTURA SET NUMERO_LINEAS = NUMERO_LINEAS + 1, TOTAL_A_PAGAR = TOTAL_A_PAGAR + :NEW.TOTAL_LINEA
         WHERE ID = :NEW.FACTURA_ID;
        PKG_SIGCA.g_en_linea := FALSE;
    EXCEPTION
        WHEN OTHERS THEN
            PKG_SIGCA.g_en_linea := FALSE;
            RAISE;
    END;
END;
/

CREATE OR REPLACE TRIGGER TRG_LINEA_BUD
BEFORE UPDATE OR DELETE ON LINEA_FACTURA
FOR EACH ROW
DECLARE
    v_estado_fac FACTURA.ESTADO%TYPE;
BEGIN
    IF DELETING THEN
        PKG_SIGCA.error(-20006, 'Las líneas de factura no se eliminan; se anula la factura');
    END IF;
    IF :NEW.FACTURA_ID <> :OLD.FACTURA_ID OR :NEW.CONTRATO_ID <> :OLD.CONTRATO_ID
       OR :NEW.TIPO_SERVICIO <> :OLD.TIPO_SERVICIO OR :NEW.TARIFA_ID <> :OLD.TARIFA_ID
       OR PKG_SIGCA.distinto(:NEW.FACTOR_SUBSIDIO_ID, :OLD.FACTOR_SUBSIDIO_ID) OR :NEW.LECTURA_ID <> :OLD.LECTURA_ID
       OR :NEW.CONSUMO <> :OLD.CONSUMO OR :NEW.CONSUMO_ESTIMADO <> :OLD.CONSUMO_ESTIMADO
       OR :NEW.CONSUMO_BASICO <> :OLD.CONSUMO_BASICO OR :NEW.CONSUMO_COMPLEMENTARIO <> :OLD.CONSUMO_COMPLEMENTARIO
       OR :NEW.CONSUMO_SUNTUARIO <> :OLD.CONSUMO_SUNTUARIO OR :NEW.CARGO_FIJO <> :OLD.CARGO_FIJO
       OR :NEW.VALOR_CONSUMO <> :OLD.VALOR_CONSUMO OR :NEW.SUBSIDIO <> :OLD.SUBSIDIO
       OR :NEW.CONTRIBUCION <> :OLD.CONTRIBUCION OR :NEW.OTROS_CARGOS <> :OLD.OTROS_CARGOS
       OR :NEW.INTERESES_MORA <> :OLD.INTERESES_MORA OR :NEW.TOTAL_LINEA <> :OLD.TOTAL_LINEA THEN
        PKG_SIGCA.error(-20014, 'Los valores liquidados de una línea no se modifican; anule y reexpida la factura');
    END IF;
    SELECT ESTADO INTO v_estado_fac FROM FACTURA WHERE ID = :NEW.FACTURA_ID;
    IF v_estado_fac = 'ANULADA' THEN
        PKG_SIGCA.error(-20006, 'Las líneas de una factura anulada no se modifican');
    END IF;
    IF :OLD.ESTADO <> 'PENDIENTE' AND (:NEW.ESTADO <> :OLD.ESTADO OR :NEW.EN_RECLAMO <> :OLD.EN_RECLAMO
                                      OR :NEW.VALOR_AJUSTE <> :OLD.VALOR_AJUSTE) THEN
        PKG_SIGCA.error(-20006, 'Una línea ' || :OLD.ESTADO || ' no se modifica');
    END IF;
    IF :NEW.EN_RECLAMO <> :OLD.EN_RECLAMO AND NOT PKG_SIGCA.g_en_reclamo THEN
        PKG_SIGCA.error(-20014, 'La marca de reclamo la gestiona la PQR');
    END IF;

    -- Ajuste por reclamo: solo mientras la línea está en reclamo
    IF :NEW.VALOR_AJUSTE <> :OLD.VALOR_AJUSTE THEN
        IF :OLD.EN_RECLAMO <> 'S' THEN
            PKG_SIGCA.error(-20021, 'Solo se ajusta una línea con reclamo en trámite');
        END IF;
        IF :NEW.TOTAL_LINEA + :NEW.VALOR_AJUSTE = 0 THEN
            :NEW.ESTADO := 'EXONERADA';
            :NEW.EN_RECLAMO := 'N';
        END IF;
    END IF;

    IF :NEW.ESTADO <> :OLD.ESTADO AND :NEW.ESTADO <> 'EXONERADA' THEN
        IF :NEW.ESTADO = 'PAGADA' AND (PKG_SIGCA.g_en_pago OR PKG_SIGCA.g_en_acuerdo) THEN
            IF :NEW.EN_RECLAMO = 'S' THEN
                PKG_SIGCA.error(-20021, 'Una línea en reclamo no se cobra');
            END IF;
            :NEW.FECHA_PAGO := NVL(:NEW.FECHA_PAGO, SYSTIMESTAMP);
        ELSE
            PKG_SIGCA.error(-20016, 'Una línea solo queda PAGADA registrando un pago o cumpliendo un acuerdo');
        END IF;
    END IF;
    IF PKG_SIGCA.distinto(:NEW.PAGO_ID, :OLD.PAGO_ID) AND NOT PKG_SIGCA.g_en_pago THEN
        PKG_SIGCA.error(-20014, 'El pago de una línea lo asigna el registro del pago');
    END IF;
END;
/

-- Mantiene los saldos de la factura a partir de los cambios de la línea
CREATE OR REPLACE TRIGGER TRG_LINEA_AU
AFTER UPDATE OF ESTADO, EN_RECLAMO, VALOR_AJUSTE ON LINEA_FACTURA
FOR EACH ROW
DECLARE
    v_ajuste  NUMBER := :NEW.VALOR_AJUSTE - :OLD.VALOR_AJUSTE;
    v_pagado  NUMBER := 0;
    v_reclamo NUMBER;
BEGIN
    IF :OLD.ESTADO = 'PENDIENTE' AND :NEW.ESTADO = 'PAGADA' THEN
        v_pagado := :NEW.TOTAL_LINEA + :NEW.VALOR_AJUSTE;
    END IF;
    v_reclamo := CASE WHEN :NEW.EN_RECLAMO = 'S' AND :NEW.ESTADO = 'PENDIENTE' THEN :NEW.TOTAL_LINEA + :NEW.VALOR_AJUSTE ELSE 0 END
               - CASE WHEN :OLD.EN_RECLAMO = 'S' AND :OLD.ESTADO = 'PENDIENTE' THEN :OLD.TOTAL_LINEA + :OLD.VALOR_AJUSTE ELSE 0 END;
    IF v_ajuste = 0 AND v_pagado = 0 AND v_reclamo = 0 THEN
        RETURN;
    END IF;
    PKG_SIGCA.g_en_linea := TRUE;
    BEGIN
        UPDATE FACTURA
           SET VALOR_AJUSTES = VALOR_AJUSTES + v_ajuste,
               VALOR_PAGADO = VALOR_PAGADO + v_pagado,
               VALOR_EN_RECLAMO = VALOR_EN_RECLAMO + v_reclamo
         WHERE ID = :NEW.FACTURA_ID;
        PKG_SIGCA.g_en_linea := FALSE;
    EXCEPTION
        WHEN OTHERS THEN
            PKG_SIGCA.g_en_linea := FALSE;
            RAISE;
    END;
END;
/

CREATE OR REPLACE TRIGGER TRG_LOTE_BU
BEFORE UPDATE ON LOTE_FACTURACION
FOR EACH ROW
BEGIN
    IF :OLD.ESTADO <> 'EN_PROCESO' AND :NEW.ESTADO <> :OLD.ESTADO THEN
        PKG_SIGCA.error(-20001, 'Un lote finalizado no cambia de estado');
    END IF;
END;
/

-- =====================================================================
-- CARTERA: ACUERDOS, CUOTAS, COBRO Y ÓRDENES (RF-12, RN-03, RN-07, RN-30, RN-36, RN-38, P-11, P-12)
-- =====================================================================

CREATE OR REPLACE TRIGGER TRG_ACUERDO_BIU
BEFORE INSERT OR UPDATE ON ACUERDO_PAGO
FOR EACH ROW
DECLARE
    v_activo   USUARIO.ACTIVO%TYPE;
    v_n        PLS_INTEGER;
BEGIN
    IF INSERTING THEN
        :NEW.NUMERO_ACUERDO := 'AP-' || TO_CHAR(:NEW.FECHA_ACUERDO, 'YYYY') || '-' || LPAD(SEQ_COD_ACUERDO.NEXTVAL, 6, '0');
        IF :NEW.ESTADO <> 'VIGENTE' THEN
            PKG_SIGCA.error(-20001, 'Un acuerdo se crea VIGENTE');
        END IF;
        IF :NEW.FECHA_ACUERDO > TRUNC(SYSDATE) THEN
            PKG_SIGCA.error(-20008, 'La fecha del acuerdo no puede ser futura');
        END IF;
        IF :NEW.NUMERO_CUOTAS > PKG_SIGCA.parametro_num('ACUERDO_MAX_CUOTAS') THEN
            PKG_SIGCA.error(-20013, 'El número de cuotas supera el máximo permitido');
        END IF;
        -- P-12: cuota inicial mínima
        IF :NEW.CUOTA_INICIAL < ROUND(:NEW.TOTAL_DEUDA * PKG_SIGCA.parametro_num('ACUERDO_CUOTA_INICIAL_PCT') / 100, 2) THEN
            PKG_SIGCA.error(-20013, 'La cuota inicial es menor al ' || PKG_SIGCA.parametro_num('ACUERDO_CUOTA_INICIAL_PCT')
                || ' % de la deuda');
        END IF;
        -- RN-38: nunca por encima de la tasa de usura (el tope residencial lo valida ACUERDO_FACTURA)
        IF :NEW.TASA_INTERES_ANUAL > PKG_SIGCA.parametro_num('TASA_USURA_ANUAL') THEN
            PKG_SIGCA.error(-20013, 'La tasa supera la tasa de usura vigente');
        END IF;
        SELECT ACTIVO INTO v_activo FROM USUARIO WHERE ID = :NEW.USUARIO_ID;
        IF v_activo <> 'S' THEN
            PKG_SIGCA.error(-20012, 'El usuario está inactivo');
        END IF;
        -- RN-30: solo para usuarios en mora
        SELECT COUNT(*) INTO v_n FROM FACTURA WHERE USUARIO_ID = :NEW.USUARIO_ID AND ESTADO = 'VENCIDA';
        IF v_n = 0 THEN
            PKG_SIGCA.error(-20013, 'El usuario no tiene facturas vencidas');
        END IF;
        :NEW.TOTAL_INCLUIDO := 0;
        :NEW.TOTAL_CAPITAL_CUOTAS := 0;
        :NEW.CUOTAS_REGISTRADAS := 0;
        :NEW.FECHA_ESTADO := SYSTIMESTAMP;
        RETURN;
    END IF;

    IF :NEW.NUMERO_ACUERDO <> :OLD.NUMERO_ACUERDO OR :NEW.USUARIO_ID <> :OLD.USUARIO_ID
       OR :NEW.TOTAL_DEUDA <> :OLD.TOTAL_DEUDA OR :NEW.NUMERO_CUOTAS <> :OLD.NUMERO_CUOTAS
       OR :NEW.TASA_INTERES_ANUAL <> :OLD.TASA_INTERES_ANUAL OR :NEW.CUOTA_INICIAL <> :OLD.CUOTA_INICIAL
       OR :NEW.FECHA_ACUERDO <> :OLD.FECHA_ACUERDO THEN
        PKG_SIGCA.error(-20014, 'Las condiciones de un acuerdo no se modifican');
    END IF;
    IF NOT PKG_SIGCA.g_en_acuerdo AND (:NEW.TOTAL_INCLUIDO <> :OLD.TOTAL_INCLUIDO
        OR :NEW.TOTAL_CAPITAL_CUOTAS <> :OLD.TOTAL_CAPITAL_CUOTAS OR :NEW.CUOTAS_REGISTRADAS <> :OLD.CUOTAS_REGISTRADAS) THEN
        PKG_SIGCA.error(-20014, 'Los totales del acuerdo los calculan sus facturas y cuotas');
    END IF;
    IF :NEW.ESTADO <> :OLD.ESTADO THEN
        IF :OLD.ESTADO <> 'VIGENTE' THEN
            PKG_SIGCA.error(-20001, 'Transición de acuerdo no permitida: ' || :OLD.ESTADO || ' -> ' || :NEW.ESTADO);
        END IF;
        IF :NEW.ESTADO = 'CUMPLIDO' THEN
            SELECT COUNT(*) INTO v_n FROM CUOTA_ACUERDO WHERE ACUERDO_ID = :NEW.ID AND ESTADO <> 'PAGADA';
            IF v_n > 0 THEN
                PKG_SIGCA.error(-20001, 'El acuerdo tiene cuotas sin pagar');
            END IF;
        END IF;
        :NEW.FECHA_ESTADO := SYSTIMESTAMP;
    END IF;
    :NEW.ACTUALIZADO_EN := SYSTIMESTAMP;
END;
/

-- Efectos del acuerdo sobre cobro, órdenes y facturas
CREATE OR REPLACE TRIGGER TRG_ACUERDO_AIU
AFTER INSERT OR UPDATE OF ESTADO ON ACUERDO_PAGO
FOR EACH ROW
BEGIN
    IF INSERTING THEN
        -- SWR-22: el acuerdo suspende el cobro persuasivo abierto
        UPDATE COBRO_PERSUASIVO SET ESTADO = 'SUSPENDIDO', ACUERDO_ID = :NEW.ID, CANDIDATO_SUSPENSION = 'N',
               FECHA_AVISO_SUSPENSION = NULL
         WHERE USUARIO_ID = :NEW.USUARIO_ID AND ESTADO = 'ACTIVO';
        -- P-12: un acuerdo vigente deja sin efecto las órdenes de suspensión por mora
        UPDATE ORDEN_SERVICIO SET ESTADO = 'ANULADA', MOTIVO_ANULACION = 'Acuerdo de pago ' || :NEW.NUMERO_ACUERDO
         WHERE ESTADO = 'PENDIENTE' AND TIPO = 'SUSPENSION' AND CAUSA = 'MORA'
           AND CONTRATO_ID IN (SELECT ID FROM CONTRATO_SERVICIO WHERE USUARIO_ID = :NEW.USUARIO_ID);
        RETURN;
    END IF;
    IF :NEW.ESTADO = :OLD.ESTADO THEN
        RETURN;
    END IF;
    PKG_SIGCA.g_en_acuerdo := TRUE;
    BEGIN
        IF :NEW.ESTADO = 'CUMPLIDO' THEN
            -- Las líneas pendientes quedan saldadas por el acuerdo; la factura pasa sola a PAGADA
            UPDATE LINEA_FACTURA SET ESTADO = 'PAGADA', FECHA_PAGO = SYSTIMESTAMP
             WHERE ESTADO = 'PENDIENTE'
               AND FACTURA_ID IN (SELECT FACTURA_ID FROM ACUERDO_FACTURA WHERE ACUERDO_ID = :NEW.ID);
            UPDATE COBRO_PERSUASIVO SET ESTADO = 'CERRADO' WHERE ACUERDO_ID = :NEW.ID AND ESTADO = 'SUSPENDIDO';
        ELSE
            -- SWR-23 / RN-07: incumplido o anulado => las facturas vuelven a VENCIDA y se reactiva el cobro
            UPDATE FACTURA SET ESTADO = 'VENCIDA'
             WHERE ESTADO = 'EN_ACUERDO' AND ID IN (SELECT FACTURA_ID FROM ACUERDO_FACTURA WHERE ACUERDO_ID = :NEW.ID);
            UPDATE COBRO_PERSUASIVO SET ESTADO = 'ACTIVO' WHERE ACUERDO_ID = :NEW.ID AND ESTADO = 'SUSPENDIDO';
        END IF;
        PKG_SIGCA.g_en_acuerdo := FALSE;
    EXCEPTION
        WHEN OTHERS THEN
            PKG_SIGCA.g_en_acuerdo := FALSE;
            RAISE;
    END;
END;
/

CREATE OR REPLACE TRIGGER TRG_ACUERDO_FACTURA_BI
BEFORE INSERT ON ACUERDO_FACTURA
FOR EACH ROW
DECLARE
    v_acuerdo ACUERDO_PAGO%ROWTYPE;
    v_factura FACTURA%ROWTYPE;
    v_uso     PREDIO.USO%TYPE;
BEGIN
    SELECT * INTO v_acuerdo FROM ACUERDO_PAGO WHERE ID = :NEW.ACUERDO_ID;
    SELECT * INTO v_factura FROM FACTURA WHERE ID = :NEW.FACTURA_ID FOR UPDATE;
    IF v_acuerdo.ESTADO <> 'VIGENTE' THEN
        PKG_SIGCA.error(-20013, 'El acuerdo no está vigente');
    END IF;
    IF v_factura.ESTADO <> 'VENCIDA' THEN
        PKG_SIGCA.error(-20013, 'Solo se incluyen facturas VENCIDAS en un acuerdo (RN-30)');
    END IF;
    IF v_factura.USUARIO_ID <> v_acuerdo.USUARIO_ID THEN
        PKG_SIGCA.error(-20013, 'La factura no pertenece al usuario del acuerdo');
    END IF;
    -- RN-33: lo que está en reclamo no se financia
    IF v_factura.VALOR_EN_RECLAMO > 0 THEN
        PKG_SIGCA.error(-20021, 'La factura tiene valores en reclamo; resuelva la PQR antes del acuerdo');
    END IF;
    IF :NEW.VALOR_INCLUIDO <> v_factura.SALDO_PENDIENTE THEN
        PKG_SIGCA.error(-20005, 'El valor incluido debe ser el saldo pendiente de la factura (' || v_factura.SALDO_PENDIENTE || ')');
    END IF;
    -- RN-38: usuarios residenciales, interés civil
    SELECT USO INTO v_uso FROM PREDIO WHERE ID = v_factura.PREDIO_ID;
    IF v_uso = 'RESIDENCIAL' AND v_acuerdo.TASA_INTERES_ANUAL > PKG_SIGCA.parametro_num('MORA_TASA_RESIDENCIAL_ANUAL') THEN
        PKG_SIGCA.error(-20013, 'La tasa supera el interés máximo para usuarios residenciales (RN-38)');
    ELSIF v_uso <> 'RESIDENCIAL' AND v_acuerdo.TASA_INTERES_ANUAL > PKG_SIGCA.parametro_num('MORA_TASA_NO_RESIDENCIAL_ANUAL') THEN
        PKG_SIGCA.error(-20013, 'La tasa supera el interés parametrizado para usuarios no residenciales (RN-38)');
    END IF;
    -- P-10: VENCIDA -> EN_ACUERDO
    PKG_SIGCA.g_en_acuerdo := TRUE;
    BEGIN
        UPDATE FACTURA SET ESTADO = 'EN_ACUERDO' WHERE ID = :NEW.FACTURA_ID;
        UPDATE ACUERDO_PAGO SET TOTAL_INCLUIDO = TOTAL_INCLUIDO + :NEW.VALOR_INCLUIDO WHERE ID = :NEW.ACUERDO_ID;
        PKG_SIGCA.g_en_acuerdo := FALSE;
    EXCEPTION
        WHEN OTHERS THEN
            PKG_SIGCA.g_en_acuerdo := FALSE;
            RAISE;
    END;
END;
/

-- P-11 / RN-36: firmado el acuerdo, se ordena la reconexión si ya no queda deuda vencida en el predio
CREATE OR REPLACE TRIGGER TRG_ACUERDO_FACTURA_AI
AFTER INSERT ON ACUERDO_FACTURA
FOR EACH ROW
DECLARE
    v_usuario NUMBER;
    v_predio  NUMBER;
BEGIN
    SELECT USUARIO_ID, PREDIO_ID INTO v_usuario, v_predio FROM FACTURA WHERE ID = :NEW.FACTURA_ID;
    PKG_SIGCA.reconectar_si_procede(v_usuario, v_predio, 'ACUERDO', NULL, :NEW.ACUERDO_ID);
END;
/

CREATE OR REPLACE TRIGGER TRG_ACUERDO_FACTURA_BUD
BEFORE UPDATE OR DELETE ON ACUERDO_FACTURA
FOR EACH ROW
BEGIN
    PKG_SIGCA.error(-20006, 'Las facturas de un acuerdo no se modifican');
END;
/

CREATE OR REPLACE TRIGGER TRG_CUOTA_BIU
BEFORE INSERT OR UPDATE ON CUOTA_ACUERDO
FOR EACH ROW
DECLARE
    v_acuerdo ACUERDO_PAGO%ROWTYPE;
BEGIN
    IF INSERTING THEN
        SELECT * INTO v_acuerdo FROM ACUERDO_PAGO WHERE ID = :NEW.ACUERDO_ID;
        IF v_acuerdo.ESTADO <> 'VIGENTE' THEN
            PKG_SIGCA.error(-20013, 'Solo se agregan cuotas a un acuerdo vigente');
        END IF;
        IF :NEW.NUMERO_CUOTA > v_acuerdo.NUMERO_CUOTAS THEN
            PKG_SIGCA.error(-20013, 'El número de cuota excede las cuotas pactadas');
        END IF;
        IF :NEW.NUMERO_CUOTA = 0 THEN
            -- P-12: la cuota 0 es la cuota inicial
            IF :NEW.VALOR_CAPITAL <> v_acuerdo.CUOTA_INICIAL THEN
                PKG_SIGCA.error(-20013, 'La cuota 0 debe ser la cuota inicial (' || v_acuerdo.CUOTA_INICIAL || ')');
            END IF;
            IF :NEW.FECHA_VENCIMIENTO < v_acuerdo.FECHA_ACUERDO THEN
                PKG_SIGCA.error(-20013, 'La cuota inicial no puede vencer antes del acuerdo');
            END IF;
        ELSIF :NEW.FECHA_VENCIMIENTO <= v_acuerdo.FECHA_ACUERDO THEN
            PKG_SIGCA.error(-20013, 'La cuota debe vencer después de la fecha del acuerdo');
        END IF;
        IF :NEW.ESTADO <> 'PENDIENTE' THEN
            PKG_SIGCA.error(-20001, 'Una cuota se crea PENDIENTE');
        END IF;
        PKG_SIGCA.g_en_acuerdo := TRUE;
        BEGIN
            UPDATE ACUERDO_PAGO
               SET TOTAL_CAPITAL_CUOTAS = TOTAL_CAPITAL_CUOTAS + :NEW.VALOR_CAPITAL,
                   CUOTAS_REGISTRADAS = CUOTAS_REGISTRADAS + 1
             WHERE ID = :NEW.ACUERDO_ID;
            PKG_SIGCA.g_en_acuerdo := FALSE;
        EXCEPTION
            WHEN OTHERS THEN
                PKG_SIGCA.g_en_acuerdo := FALSE;
                RAISE;
        END;
        RETURN;
    END IF;
    IF :NEW.ACUERDO_ID <> :OLD.ACUERDO_ID OR :NEW.NUMERO_CUOTA <> :OLD.NUMERO_CUOTA
       OR :NEW.VALOR_CUOTA <> :OLD.VALOR_CUOTA OR :NEW.VALOR_CAPITAL <> :OLD.VALOR_CAPITAL
       OR :NEW.VALOR_INTERES <> :OLD.VALOR_INTERES OR :NEW.FECHA_VENCIMIENTO <> :OLD.FECHA_VENCIMIENTO THEN
        PKG_SIGCA.error(-20014, 'Las condiciones de una cuota no se modifican');
    END IF;
    IF :NEW.ESTADO <> :OLD.ESTADO THEN
        IF :OLD.ESTADO = 'PENDIENTE' AND :NEW.ESTADO = 'PAGADA' THEN
            IF NOT PKG_SIGCA.g_en_pago THEN
                PKG_SIGCA.error(-20016, 'Una cuota solo pasa a PAGADA registrando un pago');
            END IF;
        ELSIF :OLD.ESTADO = 'PENDIENTE' AND :NEW.ESTADO = 'VENCIDA' THEN
            -- P-12: incumplimiento al día siguiente del vencimiento (más la tolerancia parametrizada)
            IF :OLD.FECHA_VENCIMIENTO + PKG_SIGCA.parametro_num('ACUERDO_DIAS_TOLERANCIA') >= TRUNC(SYSDATE) THEN
                PKG_SIGCA.error(-20001, 'La cuota aún no ha vencido');
            END IF;
        ELSE
            PKG_SIGCA.error(-20001, 'Transición de cuota no permitida: ' || :OLD.ESTADO || ' -> ' || :NEW.ESTADO);
        END IF;
    END IF;
END;
/

-- RN-07: una cuota vencida invalida automáticamente el acuerdo
CREATE OR REPLACE TRIGGER TRG_CUOTA_AU
AFTER UPDATE OF ESTADO ON CUOTA_ACUERDO
FOR EACH ROW
WHEN (NEW.ESTADO = 'VENCIDA' AND OLD.ESTADO = 'PENDIENTE')
BEGIN
    UPDATE ACUERDO_PAGO
       SET ESTADO = 'INCUMPLIDO',
           MOTIVO_ESTADO = 'Cuota ' || :NEW.NUMERO_CUOTA || ' vencida el ' || TO_CHAR(:NEW.FECHA_VENCIMIENTO, 'YYYY-MM-DD')
     WHERE ID = :NEW.ACUERDO_ID AND ESTADO = 'VIGENTE';
END;
/

CREATE OR REPLACE TRIGGER TRG_COBRO_BIU
BEFORE INSERT OR UPDATE ON COBRO_PERSUASIVO
FOR EACH ROW
BEGIN
    IF INSERTING AND :NEW.ESTADO <> 'ACTIVO' THEN
        PKG_SIGCA.error(-20001, 'Un cobro persuasivo se inicia ACTIVO');
    END IF;
    -- RN-03 / P-11: candidato a suspensión con N periodos impagos (parámetro, máximo 3)
    IF :NEW.CANDIDATO_SUSPENSION = 'S' AND (INSERTING OR :OLD.CANDIDATO_SUSPENSION = 'N')
       AND :NEW.PERIODOS_MORA < PKG_SIGCA.parametro_num('MORA_PERIODOS_SUSPENSION') THEN
        PKG_SIGCA.error(-20020, 'El usuario no completa ' || PKG_SIGCA.parametro_num('MORA_PERIODOS_SUSPENSION')
            || ' periodos de mora para ser candidato a suspensión');
    END IF;
    IF UPDATING AND :NEW.ESTADO <> :OLD.ESTADO THEN
        IF NOT ((:OLD.ESTADO = 'ACTIVO'     AND :NEW.ESTADO IN ('SUSPENDIDO','CERRADO'))
             OR (:OLD.ESTADO = 'SUSPENDIDO' AND :NEW.ESTADO IN ('ACTIVO','CERRADO'))) THEN
            PKG_SIGCA.error(-20001, 'Transición de cobro no permitida: ' || :OLD.ESTADO || ' -> ' || :NEW.ESTADO);
        END IF;
        IF :NEW.ESTADO <> 'ACTIVO' THEN
            :NEW.CANDIDATO_SUSPENSION := 'N';
            :NEW.FECHA_AVISO_SUSPENSION := NULL;
        END IF;
        :NEW.FECHA_ESTADO := SYSTIMESTAMP;
    END IF;
END;
/

CREATE OR REPLACE TRIGGER TRG_ORDEN_BIU
BEFORE INSERT OR UPDATE ON ORDEN_SERVICIO
FOR EACH ROW
DECLARE
    v_con    CONTRATO_SERVICIO%ROWTYPE;
    v_cobro  COBRO_PERSUASIVO%ROWTYPE;
    v_n      PLS_INTEGER;
BEGIN
    IF INSERTING THEN
        :NEW.NUMERO_ORDEN := 'OS-' || LPAD(SEQ_COD_ORDEN.NEXTVAL, 8, '0');
        IF :NEW.ESTADO <> 'PENDIENTE' THEN
            PKG_SIGCA.error(-20001, 'Una orden se crea PENDIENTE');
        END IF;
        :NEW.FECHA_ORDEN := SYSTIMESTAMP;
        :NEW.CREADO_POR := PKG_SIGCA.actor;
        SELECT * INTO v_con FROM CONTRATO_SERVICIO WHERE ID = :NEW.CONTRATO_ID;
        IF :NEW.TIPO = 'SUSPENSION' THEN
            IF v_con.ESTADO <> 'ACTIVO' THEN
                PKG_SIGCA.error(-20020, 'Solo se suspende un contrato activo');
            END IF;
            SELECT COUNT(*) INTO v_n FROM ORDEN_SERVICIO
             WHERE CONTRATO_ID = :NEW.CONTRATO_ID AND TIPO = 'RECONEXION' AND ESTADO = 'PENDIENTE';
            IF v_n > 0 THEN
                PKG_SIGCA.error(-20020, 'El contrato tiene una reconexión pendiente');
            END IF;
            IF :NEW.CAUSA = 'MORA' THEN
                -- RN-03: candidato notificado, con cobro activo y sin acuerdo vigente
                IF :NEW.COBRO_ID IS NULL THEN
                    PKG_SIGCA.error(-20020, 'La suspensión por mora exige el cobro persuasivo del candidato');
                END IF;
                SELECT * INTO v_cobro FROM COBRO_PERSUASIVO WHERE ID = :NEW.COBRO_ID;
                IF v_cobro.USUARIO_ID <> v_con.USUARIO_ID OR v_cobro.ESTADO <> 'ACTIVO'
                   OR v_cobro.CANDIDATO_SUSPENSION <> 'S' OR v_cobro.FECHA_AVISO_SUSPENSION IS NULL THEN
                    PKG_SIGCA.error(-20020, 'La suspensión por mora exige un candidato notificado del mismo usuario');
                END IF;
                SELECT COUNT(*) INTO v_n FROM ACUERDO_PAGO WHERE USUARIO_ID = v_con.USUARIO_ID AND ESTADO = 'VIGENTE';
                IF v_n > 0 THEN
                    PKG_SIGCA.error(-20020, 'El usuario tiene un acuerdo de pago vigente (P-12)');
                END IF;
            END IF;
        ELSE
            IF v_con.ESTADO <> 'SUSPENDIDO' THEN
                PKG_SIGCA.error(-20020, 'Solo se reconecta un contrato suspendido');
            END IF;
            -- RN-36: reconexión en máximo N horas; con cargo si la suspensión fue por mora
            :NEW.FECHA_LIMITE := :NEW.FECHA_ORDEN
                + NUMTODSINTERVAL(PKG_SIGCA.parametro_num('RECONEXION_HORAS'), 'HOUR');
            :NEW.VALOR_CARGO := CASE WHEN :NEW.CAUSA IN ('PAGO','ACUERDO')
                                     THEN PKG_SIGCA.parametro_num('VALOR_CARGO_RECONEXION') ELSE 0 END;
            IF :NEW.CAUSA = 'ACUERDO' THEN
                SELECT COUNT(*) INTO v_n FROM ACUERDO_PAGO
                 WHERE ID = :NEW.ACUERDO_ID AND USUARIO_ID = v_con.USUARIO_ID AND ESTADO = 'VIGENTE';
                IF v_n = 0 THEN
                    PKG_SIGCA.error(-20020, 'El acuerdo no está vigente o no es del titular del contrato');
                END IF;
            END IF;
        END IF;
        RETURN;
    END IF;

    IF :NEW.NUMERO_ORDEN <> :OLD.NUMERO_ORDEN OR :NEW.CONTRATO_ID <> :OLD.CONTRATO_ID OR :NEW.TIPO <> :OLD.TIPO
       OR :NEW.CAUSA <> :OLD.CAUSA OR PKG_SIGCA.distinto(:NEW.COBRO_ID, :OLD.COBRO_ID)
       OR PKG_SIGCA.distinto(:NEW.PAGO_ID, :OLD.PAGO_ID) OR PKG_SIGCA.distinto(:NEW.ACUERDO_ID, :OLD.ACUERDO_ID)
       OR :NEW.FECHA_ORDEN <> :OLD.FECHA_ORDEN OR :NEW.VALOR_CARGO <> :OLD.VALOR_CARGO
       OR PKG_SIGCA.distinto(TO_CHAR(:NEW.FECHA_LIMITE, 'YYYYMMDDHH24MISSFF'), TO_CHAR(:OLD.FECHA_LIMITE, 'YYYYMMDDHH24MISSFF')) THEN
        PKG_SIGCA.error(-20014, 'Los datos de una orden de servicio no se modifican');
    END IF;
    IF :NEW.ESTADO <> :OLD.ESTADO THEN
        IF :OLD.ESTADO <> 'PENDIENTE' THEN
            PKG_SIGCA.error(-20001, 'Transición de orden no permitida: ' || :OLD.ESTADO || ' -> ' || :NEW.ESTADO);
        END IF;
        IF :NEW.ESTADO = 'EJECUTADA' THEN
            IF NOT (PKG_SIGCA.tiene_rol(:NEW.EJECUTADA_POR, 'ASESOR') OR PKG_SIGCA.tiene_rol(:NEW.EJECUTADA_POR, 'ADMIN')) THEN
                PKG_SIGCA.error(-20015, 'Las órdenes las ejecuta un Asesor');
            END IF;
            IF :NEW.TIPO = 'SUSPENSION' AND :NEW.CAUSA = 'MORA' THEN
                SELECT COUNT(*) INTO v_n FROM ACUERDO_PAGO A JOIN CONTRATO_SERVICIO C ON C.USUARIO_ID = A.USUARIO_ID
                 WHERE C.ID = :NEW.CONTRATO_ID AND A.ESTADO = 'VIGENTE';
                IF v_n > 0 THEN
                    PKG_SIGCA.error(-20020, 'El usuario firmó un acuerdo de pago; la suspensión ya no procede');
                END IF;
            END IF;
            :NEW.FECHA_EJECUCION := SYSTIMESTAMP;
        END IF;
    ELSIF :OLD.ESTADO <> 'PENDIENTE' THEN
        PKG_SIGCA.error(-20006, 'Una orden ' || :OLD.ESTADO || ' no se modifica');
    END IF;
END;
/

-- Ejecutar la orden cambia el estado del contrato
CREATE OR REPLACE TRIGGER TRG_ORDEN_AU
AFTER UPDATE OF ESTADO ON ORDEN_SERVICIO
FOR EACH ROW
WHEN (NEW.ESTADO = 'EJECUTADA' AND OLD.ESTADO = 'PENDIENTE')
BEGIN
    PKG_SIGCA.g_en_orden := TRUE;
    BEGIN
        IF :NEW.TIPO = 'SUSPENSION' THEN
            UPDATE CONTRATO_SERVICIO
               SET ESTADO = 'SUSPENDIDO',
                   MOTIVO_ESTADO = 'Orden ' || :NEW.NUMERO_ORDEN || ' (' || :NEW.CAUSA || ')'
             WHERE ID = :NEW.CONTRATO_ID AND ESTADO = 'ACTIVO';
        ELSE
            UPDATE CONTRATO_SERVICIO SET ESTADO = 'ACTIVO', MOTIVO_ESTADO = NULL
             WHERE ID = :NEW.CONTRATO_ID AND ESTADO = 'SUSPENDIDO';
        END IF;
        IF SQL%ROWCOUNT = 0 THEN
            PKG_SIGCA.error(-20020, 'El contrato ya no está en el estado que la orden supone');
        END IF;
        PKG_SIGCA.g_en_orden := FALSE;
    EXCEPTION
        WHEN OTHERS THEN
            PKG_SIGCA.g_en_orden := FALSE;
            RAISE;
    END;
END;
/

CREATE OR REPLACE TRIGGER TRG_ORDEN_BD
BEFORE DELETE ON ORDEN_SERVICIO
FOR EACH ROW
BEGIN
    PKG_SIGCA.error(-20006, 'Las órdenes de servicio no se eliminan; se anulan');
END;
/

-- =====================================================================
-- PAGOS (RF-06, SWR-05..07, RN-05, RN-13..15, RN-33, CO-02, P-18)
-- =====================================================================

CREATE OR REPLACE TRIGGER TRG_TRANSACCION_BIU
BEFORE INSERT OR UPDATE ON TRANSACCION_PASARELA
FOR EACH ROW
DECLARE
    v_factura  FACTURA%ROWTYPE;
    v_cuota    CUOTA_ACUERDO%ROWTYPE;
    v_acuerdo  ACUERDO_PAGO%ROWTYPE;
    v_total    NUMBER;
    v_ref      VARCHAR2(20);
    v_usuario  NUMBER;
BEGIN
    IF INSERTING THEN
        IF :NEW.ESTADO <> 'INICIADA' OR :NEW.FIRMA_VALIDADA <> 'N' THEN
            PKG_SIGCA.error(-20001, 'Una transacción se crea INICIADA y sin firma validada');
        END IF;
        :NEW.INICIADA_EN := SYSTIMESTAMP;
        -- SWR-06: no se inicia el pago de una factura sin valor exigible
        IF :NEW.FACTURA_ID IS NOT NULL THEN
            SELECT * INTO v_factura FROM FACTURA WHERE ID = :NEW.FACTURA_ID;
            IF v_factura.ESTADO NOT IN ('PENDIENTE','VENCIDA') THEN
                PKG_SIGCA.error(-20004, 'La factura ' || v_factura.NUMERO_FACTURA || ' está ' || v_factura.ESTADO);
            END IF;
            v_total := v_factura.SALDO_PENDIENTE - v_factura.VALOR_EN_RECLAMO;
            v_ref := v_factura.NUMERO_FACTURA;
            v_usuario := v_factura.USUARIO_ID;
        ELSE
            SELECT * INTO v_cuota FROM CUOTA_ACUERDO WHERE ID = :NEW.CUOTA_ID;
            SELECT * INTO v_acuerdo FROM ACUERDO_PAGO WHERE ID = v_cuota.ACUERDO_ID;
            IF v_cuota.ESTADO <> 'PENDIENTE' OR v_acuerdo.ESTADO <> 'VIGENTE' THEN
                PKG_SIGCA.error(-20004, 'La cuota no admite pagos en su estado actual');
            END IF;
            v_total := v_cuota.VALOR_CUOTA;
            v_ref := v_acuerdo.NUMERO_ACUERDO || '-' || LPAD(v_cuota.NUMERO_CUOTA, 2, '0');
            v_usuario := v_acuerdo.USUARIO_ID;
        END IF;
        IF v_total <= 0 THEN
            PKG_SIGCA.error(-20004, 'No hay valor exigible: el saldo está en reclamo');
        END IF;
        IF :NEW.MONTO <> v_total THEN
            PKG_SIGCA.error(-20005, 'El monto debe ser igual al valor exigible (' || v_total || ')');
        END IF;
        IF :NEW.REFERENCIA_PAGO <> v_ref THEN
            PKG_SIGCA.error(-20005, 'La referencia de pago debe ser ' || v_ref || ' (P-18)');
        END IF;
        IF :NEW.USUARIO_ID <> v_usuario THEN
            PKG_SIGCA.error(-20014, 'La transacción no corresponde al titular de la obligación');
        END IF;
        RETURN;
    END IF;

    IF :NEW.REFERENCIA <> :OLD.REFERENCIA OR :NEW.MONTO <> :OLD.MONTO OR :NEW.REFERENCIA_PAGO <> :OLD.REFERENCIA_PAGO
       OR PKG_SIGCA.distinto(:NEW.FACTURA_ID, :OLD.FACTURA_ID) OR PKG_SIGCA.distinto(:NEW.CUOTA_ID, :OLD.CUOTA_ID) THEN
        PKG_SIGCA.error(-20014, 'Referencia, monto y destino de la transacción no se modifican');
    END IF;
    IF :NEW.ESTADO <> :OLD.ESTADO THEN
        -- INICIADA -> APROBADA | RECHAZADA | ERROR | EXPIRADA;  ERROR -> INICIADA (reintento)
        IF NOT ((:OLD.ESTADO = 'INICIADA' AND :NEW.ESTADO IN ('APROBADA','RECHAZADA','ERROR','EXPIRADA'))
             OR (:OLD.ESTADO = 'ERROR'    AND :NEW.ESTADO = 'INICIADA')) THEN
            PKG_SIGCA.error(-20001, 'Transición de transacción no permitida: ' || :OLD.ESTADO || ' -> ' || :NEW.ESTADO);
        END IF;
        IF :NEW.ESTADO = 'INICIADA' THEN
            :NEW.INTENTOS := :OLD.INTENTOS + 1;
            :NEW.RESPONDIDA_EN := NULL;
            :NEW.FIRMA_VALIDADA := 'N';
        ELSIF :NEW.ESTADO <> 'EXPIRADA' THEN
            :NEW.RESPONDIDA_EN := SYSTIMESTAMP;
        END IF;
    ELSIF :OLD.ESTADO IN ('APROBADA','RECHAZADA','EXPIRADA') THEN
        PKG_SIGCA.error(-20006, 'Una transacción finalizada no se modifica');
    END IF;
END;
/

CREATE OR REPLACE TRIGGER TRG_PAGO_BI
BEFORE INSERT ON PAGO
FOR EACH ROW
DECLARE
    v_factura   FACTURA%ROWTYPE;
    v_cuota     CUOTA_ACUERDO%ROWTYPE;
    v_acuerdo   ACUERDO_PAGO%ROWTYPE;
    v_tx        TRANSACCION_PASARELA%ROWTYPE;
    v_usuario   NUMBER(19);
    v_total     NUMBER(14,2);
    v_previas   PLS_INTEGER;
BEGIN
    :NEW.CODIGO_PAGO := 'PAG-' || LPAD(SEQ_COD_PAGO.NEXTVAL, 10, '0');
    :NEW.FECHA_PAGO  := SYSTIMESTAMP;   -- la asigna el servidor, no el cliente

    IF :NEW.FACTURA_ID IS NOT NULL THEN
        -- Bloqueo de la fila: dos pagos simultáneos de la misma factura se serializan
        SELECT * INTO v_factura FROM FACTURA WHERE ID = :NEW.FACTURA_ID FOR UPDATE;
        IF v_factura.ESTADO = 'EN_ACUERDO' THEN
            PKG_SIGCA.error(-20004, 'La factura está en un acuerdo de pago; se pagan las cuotas');
        ELSIF v_factura.ESTADO NOT IN ('PENDIENTE','VENCIDA') THEN
            PKG_SIGCA.error(-20004, 'La factura ' || v_factura.NUMERO_FACTURA || ' está ' || v_factura.ESTADO);
        END IF;
        -- RN-33: se paga todo lo no reclamado; acueducto y alcantarillado no se separan
        v_total := v_factura.SALDO_PENDIENTE - v_factura.VALOR_EN_RECLAMO;
        IF v_total <= 0 THEN
            PKG_SIGCA.error(-20004, 'No hay valor exigible: el saldo de la factura está en reclamo');
        END IF;
        v_usuario := v_factura.USUARIO_ID;
    ELSE
        SELECT * INTO v_cuota FROM CUOTA_ACUERDO WHERE ID = :NEW.CUOTA_ID FOR UPDATE;
        SELECT * INTO v_acuerdo FROM ACUERDO_PAGO WHERE ID = v_cuota.ACUERDO_ID;
        IF v_cuota.ESTADO <> 'PENDIENTE' OR v_acuerdo.ESTADO <> 'VIGENTE' THEN
            PKG_SIGCA.error(-20004, 'La cuota no admite pagos (cuota ' || v_cuota.ESTADO || ', acuerdo ' || v_acuerdo.ESTADO || ')');
        END IF;
        -- Las cuotas se pagan en orden, empezando por la cuota inicial
        SELECT COUNT(*) INTO v_previas FROM CUOTA_ACUERDO
         WHERE ACUERDO_ID = v_cuota.ACUERDO_ID AND NUMERO_CUOTA < v_cuota.NUMERO_CUOTA AND ESTADO <> 'PAGADA';
        IF v_previas > 0 THEN
            PKG_SIGCA.error(-20004, 'Hay cuotas anteriores sin pagar');
        END IF;
        v_usuario := v_acuerdo.USUARIO_ID;
        v_total := v_cuota.VALOR_CUOTA;
    END IF;

    IF :NEW.USUARIO_ID <> v_usuario THEN
        PKG_SIGCA.error(-20014, 'El pago no corresponde al titular de la obligación');
    END IF;
    -- P-05 / CO-02: sin abonos libres, se paga el valor exigible completo
    IF :NEW.VALOR <> v_total THEN
        PKG_SIGCA.error(-20005, 'El valor pagado (' || :NEW.VALOR || ') no coincide con el valor exigible (' || v_total || ')');
    END IF;

    -- RN-05: un pago en línea exige una transacción APROBADA, con firma validada, para esa misma obligación
    IF :NEW.CANAL_PAGO = 'EN_LINEA' THEN
        SELECT * INTO v_tx FROM TRANSACCION_PASARELA WHERE ID = :NEW.TRANSACCION_PASARELA_ID;
        IF v_tx.ESTADO <> 'APROBADA' OR v_tx.FIRMA_VALIDADA <> 'S' OR v_tx.CODIGO_TRANSACCION <> :NEW.CODIGO_TRANSACCION
           OR v_tx.MONTO <> :NEW.VALOR
           OR PKG_SIGCA.distinto(v_tx.FACTURA_ID, :NEW.FACTURA_ID)
           OR PKG_SIGCA.distinto(v_tx.CUOTA_ID, :NEW.CUOTA_ID) THEN
            PKG_SIGCA.error(-20016, 'La transacción de la pasarela no está aprobada o no corresponde al pago');
        END IF;
    END IF;
END;
/

-- SWR-07: el pago confirmado salda las líneas exigibles o la cuota en la misma transacción
CREATE OR REPLACE TRIGGER TRG_PAGO_AI
AFTER INSERT ON PAGO
FOR EACH ROW
DECLARE
    v_acuerdo_id   NUMBER(19);
    v_pendientes   PLS_INTEGER;
    v_restante     NUMBER;
    v_predio       NUMBER;
BEGIN
    PKG_SIGCA.g_en_pago := TRUE;
    BEGIN
        IF :NEW.FACTURA_ID IS NOT NULL THEN
            UPDATE LINEA_FACTURA SET ESTADO = 'PAGADA', PAGO_ID = :NEW.ID, FECHA_PAGO = :NEW.FECHA_PAGO
             WHERE FACTURA_ID = :NEW.FACTURA_ID AND ESTADO = 'PENDIENTE' AND EN_RECLAMO = 'N';
            SELECT SALDO_PENDIENTE - VALOR_EN_RECLAMO, PREDIO_ID INTO v_restante, v_predio
              FROM FACTURA WHERE ID = :NEW.FACTURA_ID;
            IF v_restante <> 0 THEN
                PKG_SIGCA.error(-20005, 'El pago no saldó el valor exigible de la factura');
            END IF;
        ELSE
            UPDATE CUOTA_ACUERDO SET ESTADO = 'PAGADA', FECHA_PAGO = :NEW.FECHA_PAGO
             WHERE ID = :NEW.CUOTA_ID
            RETURNING ACUERDO_ID INTO v_acuerdo_id;
            SELECT COUNT(*) INTO v_pendientes FROM CUOTA_ACUERDO WHERE ACUERDO_ID = v_acuerdo_id AND ESTADO <> 'PAGADA';
            IF v_pendientes = 0 THEN
                UPDATE ACUERDO_PAGO SET ESTADO = 'CUMPLIDO' WHERE ID = v_acuerdo_id;
            END IF;
        END IF;
        PKG_SIGCA.g_en_pago := FALSE;
    EXCEPTION
        WHEN OTHERS THEN
            PKG_SIGCA.g_en_pago := FALSE;
            RAISE;
    END;

    IF :NEW.FACTURA_ID IS NOT NULL THEN
        -- RN-36: pagada la deuda vencida del predio, reconexión automática
        PKG_SIGCA.reconectar_si_procede(:NEW.USUARIO_ID, v_predio, 'PAGO', :NEW.ID, NULL);
        -- Sin deuda vencida exigible en ningún predio, se cierra el cobro persuasivo
        UPDATE COBRO_PERSUASIVO SET ESTADO = 'CERRADO'
         WHERE USUARIO_ID = :NEW.USUARIO_ID AND ESTADO = 'ACTIVO'
           AND NOT EXISTS (SELECT 1 FROM FACTURA
                            WHERE USUARIO_ID = :NEW.USUARIO_ID AND ESTADO = 'VENCIDA'
                              AND SALDO_PENDIENTE - VALOR_EN_RECLAMO > 0);
    END IF;
END;
/

CREATE OR REPLACE TRIGGER TRG_PAGO_BUD
BEFORE UPDATE OR DELETE ON PAGO
FOR EACH ROW
BEGIN
    IF DELETING THEN
        PKG_SIGCA.error(-20006, 'Los pagos no se eliminan');
    END IF;
    -- Solo cambian el envío del comprobante y la sincronización con el ERP
    IF :NEW.CODIGO_PAGO <> :OLD.CODIGO_PAGO OR :NEW.USUARIO_ID <> :OLD.USUARIO_ID
       OR PKG_SIGCA.distinto(:NEW.FACTURA_ID, :OLD.FACTURA_ID) OR PKG_SIGCA.distinto(:NEW.CUOTA_ID, :OLD.CUOTA_ID)
       OR :NEW.FECHA_PAGO <> :OLD.FECHA_PAGO OR :NEW.VALOR <> :OLD.VALOR OR :NEW.CANAL_PAGO <> :OLD.CANAL_PAGO
       OR PKG_SIGCA.distinto(:NEW.TRANSACCION_PASARELA_ID, :OLD.TRANSACCION_PASARELA_ID)
       OR PKG_SIGCA.distinto(:NEW.CODIGO_TRANSACCION, :OLD.CODIGO_TRANSACCION)
       OR PKG_SIGCA.distinto(:NEW.REFERENCIA_BANCO, :OLD.REFERENCIA_BANCO)
       OR PKG_SIGCA.distinto(:NEW.FUNCIONARIO_ID, :OLD.FUNCIONARIO_ID) THEN
        PKG_SIGCA.error(-20006, 'Un pago registrado no se modifica');
    END IF;
END;
/

-- =====================================================================
-- PQR (RF-08, RF-09, DE-02, RN-04, RN-06, RN-17..22, RN-31..33, CO-01, P-03, P-14, P-15)
-- =====================================================================

CREATE OR REPLACE TRIGGER TRG_PQR_CIUD
FOR INSERT OR UPDATE OR DELETE ON PQR
COMPOUND TRIGGER
    TYPE t_ids IS TABLE OF NUMBER INDEX BY PLS_INTEGER;
    g_recursos t_ids;
    g_lineas   t_ids;

    PROCEDURE exige_evidencia_y_rol(p_pqr_id NUMBER, p_funcionario NUMBER) IS
        v_n PLS_INTEGER;
    BEGIN
        -- RN-06: evidencia documental obligatoria
        SELECT COUNT(*) INTO v_n FROM PQR_ADJUNTO WHERE PQR_ID = p_pqr_id AND TIPO = 'EVIDENCIA';
        IF v_n = 0 THEN
            PKG_SIGCA.error(-20009, 'La respuesta requiere al menos una evidencia adjunta');
        END IF;
        IF NOT (PKG_SIGCA.tiene_rol(p_funcionario, 'GESTOR_PQR') OR PKG_SIGCA.tiene_rol(p_funcionario, 'JEFE_PQR')) THEN
            PKG_SIGCA.error(-20015, 'Solo un Gestor o el Jefe de PQR responde una PQR');
        END IF;
    END;

    BEFORE EACH ROW IS
        v_contrato   CONTRATO_SERVICIO%ROWTYPE;
        v_municipio  PREDIO.MUNICIPIO_ID%TYPE;
        v_gestor_mun FUNCIONARIO.MUNICIPIO_ID%TYPE;
        v_linea_con  LINEA_FACTURA.CONTRATO_ID%TYPE;
    BEGIN
        IF DELETING THEN
            PKG_SIGCA.error(-20006, 'Las PQR no se eliminan');
        END IF;

        IF INSERTING THEN
            IF :NEW.CONTRATO_ID IS NOT NULL THEN
                -- RN-17: contrato existente y vigente (P-29: activo o suspendido)
                BEGIN
                    SELECT * INTO v_contrato FROM CONTRATO_SERVICIO WHERE ID = :NEW.CONTRATO_ID;
                EXCEPTION
                    WHEN NO_DATA_FOUND THEN
                        PKG_SIGCA.error(-20002, 'El contrato no existe');
                END;
                IF v_contrato.ESTADO NOT IN ('ACTIVO','SUSPENDIDO') THEN
                    PKG_SIGCA.error(-20002, 'El contrato ' || v_contrato.NUMERO_CONTRATO || ' no está vigente');
                END IF;
                SELECT MUNICIPIO_ID INTO v_municipio FROM PREDIO WHERE ID = v_contrato.PREDIO_ID;
                -- Datos derivados que no se aceptan del cliente
                :NEW.USUARIO_ID   := v_contrato.USUARIO_ID;
                :NEW.MUNICIPIO_ID := v_municipio;
            ELSE
                -- P-14: sin contrato solo se radican peticiones
                IF :NEW.TIPO <> 'PETICION' THEN
                    PKG_SIGCA.error(-20002, 'Una ' || :NEW.TIPO || ' exige el contrato del solicitante');
                END IF;
                :NEW.USUARIO_ID := NULL;
            END IF;
            IF NOT PKG_SIGCA.municipio_activo(:NEW.MUNICIPIO_ID) THEN
                PKG_SIGCA.error(-20003, 'El municipio no es atendido por EPQ');
            END IF;
            IF :NEW.LINEA_FACTURA_ID IS NOT NULL THEN
                SELECT CONTRATO_ID INTO v_linea_con FROM LINEA_FACTURA WHERE ID = :NEW.LINEA_FACTURA_ID;
                IF PKG_SIGCA.distinto(v_linea_con, :NEW.CONTRATO_ID) THEN
                    PKG_SIGCA.error(-20002, 'La línea de factura reclamada no es del contrato');
                END IF;
            END IF;
            IF :NEW.ESTADO <> 'RECIBIDA' THEN
                PKG_SIGCA.error(-20001, 'Una PQR se radica en estado RECIBIDA (EST-03)');
            END IF;
            :NEW.FECHA_REGISTRO   := SYSTIMESTAMP;
            :NEW.FECHA_ESTADO     := :NEW.FECHA_REGISTRO;
            :NEW.FECHA_ASIGNACION := :NEW.FECHA_REGISTRO;
            :NEW.NUMERO_RADICADO  := PKG_SIGCA.siguiente_radicado(:NEW.FECHA_REGISTRO);
            -- P-03: plazo en días hábiles según el tipo (15 por defecto, máximo legal 15)
            :NEW.FECHA_LIMITE_RESPUESTA := PKG_SIGCA.sumar_dias_habiles(
                CAST(:NEW.FECHA_REGISTRO AS DATE), PKG_SIGCA.parametro_num('PQR_DIAS_' || :NEW.TIPO));
            :NEW.FECHA_INICIO_TRAMITE := NULL;
            :NEW.RESPUESTA := NULL;
            :NEW.DECISION_RECURSO := NULL;
            :NEW.RESPUESTA_EXTEMPORANEA := 'N';
            :NEW.SILENCIO_POSITIVO := 'N';
            :NEW.FECHA_LIMITE_RECONOCIMIENTO := NULL;
            :NEW.FECHA_RECONOCIMIENTO_SILENCIO := NULL;
            :NEW.ALERTA_48H_ENVIADA := 'N';
            :NEW.REPORTADA_SSPD := 'N';
            IF :NEW.CANAL = 'PRESENCIAL' AND NOT (PKG_SIGCA.tiene_rol(:NEW.REGISTRADA_POR, 'GESTOR_PQR')
                                                OR PKG_SIGCA.tiene_rol(:NEW.REGISTRADA_POR, 'JEFE_PQR')) THEN
                PKG_SIGCA.error(-20015, 'Una PQR presencial la registra un Gestor o el Jefe de PQR');
            END IF;
        ELSE
            IF :NEW.NUMERO_RADICADO <> :OLD.NUMERO_RADICADO OR :NEW.TIPO <> :OLD.TIPO OR :NEW.CANAL <> :OLD.CANAL
               OR PKG_SIGCA.distinto(:NEW.CONTRATO_ID, :OLD.CONTRATO_ID)
               OR PKG_SIGCA.distinto(:NEW.USUARIO_ID, :OLD.USUARIO_ID)
               OR :NEW.MUNICIPIO_ID <> :OLD.MUNICIPIO_ID
               OR PKG_SIGCA.distinto(:NEW.SOLICITANTE_IDENTIFICACION_HASH, :OLD.SOLICITANTE_IDENTIFICACION_HASH)
               OR PKG_SIGCA.distinto(:NEW.PQR_ORIGEN_ID, :OLD.PQR_ORIGEN_ID)
               OR :NEW.SOLICITA_APELACION <> :OLD.SOLICITA_APELACION
               OR PKG_SIGCA.distinto(:NEW.LINEA_FACTURA_ID, :OLD.LINEA_FACTURA_ID)
               OR DBMS_LOB.COMPARE(:NEW.DESCRIPCION, :OLD.DESCRIPCION) <> 0
               OR :NEW.FECHA_REGISTRO <> :OLD.FECHA_REGISTRO OR :NEW.FECHA_LIMITE_RESPUESTA <> :OLD.FECHA_LIMITE_RESPUESTA
               OR :NEW.NOMBRE_CONTACTO <> :OLD.NOMBRE_CONTACTO OR :NEW.CORREO_CONTACTO <> :OLD.CORREO_CONTACTO
               OR :NEW.TELEFONO_CONTACTO <> :OLD.TELEFONO_CONTACTO
               OR PKG_SIGCA.distinto(:NEW.REGISTRADA_POR, :OLD.REGISTRADA_POR) THEN
                PKG_SIGCA.error(-20014, 'Los datos radicados de una PQR no se modifican');
            END IF;
            -- La respuesta de EPQ es definitiva desde que se emite (RN-22)
            IF :OLD.ESTADO IN ('RESPONDIDA','TRASLADADA_SSPD')
               AND (DBMS_LOB.COMPARE(NVL(:NEW.RESPUESTA, EMPTY_CLOB()), NVL(:OLD.RESPUESTA, EMPTY_CLOB())) <> 0
                    OR PKG_SIGCA.distinto(:NEW.DECISION_RECURSO, :OLD.DECISION_RECURSO)
                    OR :NEW.GESTOR_ID <> :OLD.GESTOR_ID) THEN
                PKG_SIGCA.error(-20006, 'La respuesta de una PQR no se modifica (RN-22)');
            END IF;
            IF :OLD.ESTADO = 'RESPONDIDA' AND (:NEW.ESTADO <> :OLD.ESTADO
                OR PKG_SIGCA.distinto(:NEW.DECISION_SSPD, :OLD.DECISION_SSPD)) THEN
                PKG_SIGCA.error(-20006, 'Una PQR respondida es un estado final (RN-22)');
            END IF;

            IF :NEW.ESTADO <> :OLD.ESTADO THEN
                CASE
                    WHEN :OLD.ESTADO = 'RECIBIDA' AND :NEW.ESTADO = 'EN_TRAMITE' THEN
                        :NEW.FECHA_INICIO_TRAMITE := SYSTIMESTAMP;
                    WHEN :OLD.ESTADO IN ('RECIBIDA','EN_TRAMITE') AND :NEW.ESTADO = 'VENCIDA' THEN
                        IF TRUNC(SYSDATE) <= :OLD.FECHA_LIMITE_RESPUESTA THEN
                            PKG_SIGCA.error(-20001, 'La PQR aún está dentro del plazo de respuesta');
                        END IF;
                        -- RN-31: silencio administrativo positivo; EPQ lo reconoce en N horas
                        :NEW.SILENCIO_POSITIVO := 'S';
                        :NEW.FECHA_LIMITE_RECONOCIMIENTO := SYSTIMESTAMP
                            + NUMTODSINTERVAL(PKG_SIGCA.parametro_num('PQR_HORAS_RECONOCIMIENTO_SILENCIO'), 'HOUR');
                    WHEN :OLD.ESTADO IN ('EN_TRAMITE','VENCIDA') AND :NEW.ESTADO = 'RESPONDIDA' THEN
                        exige_evidencia_y_rol(:NEW.ID, :NEW.RESPONDIDA_POR);
                        IF :NEW.TIPO = 'RECURSO' AND :NEW.DECISION_RECURSO = 'CONFIRMA' AND :NEW.SOLICITA_APELACION = 'S' THEN
                            PKG_SIGCA.error(-20001, 'Se pidió apelación y la decisión se confirma: el recurso pasa a TRASLADADA_SSPD');
                        END IF;
                        :NEW.FECHA_RESPUESTA := SYSTIMESTAMP;
                        :NEW.RESPUESTA_EXTEMPORANEA := CASE WHEN :OLD.ESTADO = 'VENCIDA' THEN 'S' ELSE 'N' END;
                    WHEN :OLD.ESTADO IN ('EN_TRAMITE','VENCIDA') AND :NEW.ESTADO = 'TRASLADADA_SSPD' THEN
                        -- P-15: reposición confirmada con apelación subsidiaria
                        IF NOT (:NEW.TIPO = 'RECURSO' AND :NEW.SOLICITA_APELACION = 'S'
                                AND NVL(:NEW.DECISION_RECURSO, 'X') = 'CONFIRMA') THEN
                            PKG_SIGCA.error(-20001, 'Solo un recurso con apelación y decisión confirmada se traslada a la SSPD');
                        END IF;
                        exige_evidencia_y_rol(:NEW.ID, :NEW.RESPONDIDA_POR);
                        :NEW.FECHA_RESPUESTA := SYSTIMESTAMP;
                        :NEW.FECHA_TRASLADO_SSPD := SYSTIMESTAMP;
                        :NEW.RESPUESTA_EXTEMPORANEA := CASE WHEN :OLD.ESTADO = 'VENCIDA' THEN 'S' ELSE 'N' END;
                    WHEN :OLD.ESTADO = 'TRASLADADA_SSPD' AND :NEW.ESTADO = 'RESPONDIDA' THEN
                        IF :NEW.DECISION_SSPD IS NULL THEN
                            PKG_SIGCA.error(-20001, 'Registre la decisión de la Superintendencia');
                        END IF;
                        :NEW.FECHA_DECISION_SSPD := SYSTIMESTAMP;
                    ELSE
                        PKG_SIGCA.error(-20001, 'Transición de PQR no permitida: ' || :OLD.ESTADO || ' -> ' || :NEW.ESTADO);
                END CASE;
                :NEW.FECHA_ESTADO := SYSTIMESTAMP;
            END IF;

            -- RN-31: el reconocimiento del silencio positivo se registra una sola vez
            IF PKG_SIGCA.distinto(TO_CHAR(:NEW.FECHA_RECONOCIMIENTO_SILENCIO, 'YYYYMMDDHH24MISSFF'),
                                  TO_CHAR(:OLD.FECHA_RECONOCIMIENTO_SILENCIO, 'YYYYMMDDHH24MISSFF')) THEN
                IF :OLD.FECHA_RECONOCIMIENTO_SILENCIO IS NOT NULL THEN
                    PKG_SIGCA.error(-20006, 'El reconocimiento del silencio positivo ya está registrado');
                END IF;
                :NEW.FECHA_RECONOCIMIENTO_SILENCIO := SYSTIMESTAMP;
            END IF;

            IF :NEW.GESTOR_ID <> :OLD.GESTOR_ID THEN
                -- P-15: se reasigna en RECIBIDA o EN_TRAMITE
                IF :OLD.ESTADO NOT IN ('RECIBIDA','EN_TRAMITE') THEN
                    PKG_SIGCA.error(-20001, 'Una PQR ' || :OLD.ESTADO || ' no se reasigna');
                END IF;
                :NEW.FECHA_ASIGNACION := SYSTIMESTAMP;
            END IF;
            :NEW.ACTUALIZADO_EN := SYSTIMESTAMP;
        END IF;

        -- RN-20: el responsable es un Gestor de PQR del municipio o el Jefe de PQR
        IF INSERTING OR :NEW.GESTOR_ID <> :OLD.GESTOR_ID THEN
            SELECT MUNICIPIO_ID INTO v_gestor_mun FROM FUNCIONARIO WHERE ID = :NEW.GESTOR_ID;
            IF NOT ((PKG_SIGCA.tiene_rol(:NEW.GESTOR_ID, 'GESTOR_PQR') AND v_gestor_mun = :NEW.MUNICIPIO_ID)
                 OR PKG_SIGCA.tiene_rol(:NEW.GESTOR_ID, 'JEFE_PQR')) THEN
                PKG_SIGCA.error(-20015, 'El responsable debe ser Gestor de PQR del municipio o Jefe de PQR');
            END IF;
        END IF;
    END BEFORE EACH ROW;

    AFTER EACH ROW IS
    BEGIN
        -- Trazabilidad automática de estado y responsable (RF-09)
        IF INSERTING OR :NEW.ESTADO <> :OLD.ESTADO OR :NEW.GESTOR_ID <> :OLD.GESTOR_ID THEN
            INSERT INTO PQR_HISTORIAL (PQR_ID, ESTADO_ANTERIOR, ESTADO_NUEVO, GESTOR_ANTERIOR, GESTOR_NUEVO, REALIZADO_POR)
            VALUES (:NEW.ID, :OLD.ESTADO, :NEW.ESTADO, :OLD.GESTOR_ID, :NEW.GESTOR_ID,
                    NVL(:NEW.ACTUALIZADO_POR, PKG_SIGCA.actor));
        END IF;
        IF INSERTING AND :NEW.TIPO = 'RECURSO' THEN
            g_recursos(g_recursos.COUNT + 1) := :NEW.ID;
        END IF;
        IF :NEW.LINEA_FACTURA_ID IS NOT NULL AND (INSERTING OR :NEW.ESTADO <> :OLD.ESTADO) THEN
            g_lineas(g_lineas.COUNT + 1) := :NEW.LINEA_FACTURA_ID;
        END IF;
    END AFTER EACH ROW;

    AFTER STATEMENT IS
        v_rec PQR%ROWTYPE;
        v_ori PQR%ROWTYPE;
    BEGIN
        -- P-15 / RN-32: el recurso se presenta contra una PQR respondida, dentro del plazo
        FOR i IN 1 .. g_recursos.COUNT LOOP
            SELECT * INTO v_rec FROM PQR WHERE ID = g_recursos(i);
            SELECT * INTO v_ori FROM PQR WHERE ID = v_rec.PQR_ORIGEN_ID;
            IF v_ori.ESTADO <> 'RESPONDIDA' OR v_ori.TIPO = 'RECURSO' THEN
                PKG_SIGCA.error(-20001, 'Solo se recurre la respuesta de una petición, queja o reclamo');
            END IF;
            IF v_ori.CONTRATO_ID IS NULL OR v_ori.CONTRATO_ID <> v_rec.CONTRATO_ID THEN
                PKG_SIGCA.error(-20002, 'El recurso debe ser del mismo contrato de la PQR original');
            END IF;
            IF PKG_SIGCA.distinto(v_ori.LINEA_FACTURA_ID, v_rec.LINEA_FACTURA_ID) THEN
                PKG_SIGCA.error(-20014, 'El recurso se refiere a la misma línea de factura de la PQR original');
            END IF;
            IF TRUNC(v_rec.FECHA_REGISTRO) > PKG_SIGCA.sumar_dias_habiles(
                    CAST(v_ori.FECHA_RESPUESTA AS DATE), PKG_SIGCA.parametro_num('RECURSO_DIAS_PLAZO')) THEN
                PKG_SIGCA.error(-20019, 'Venció el plazo para recurrir la respuesta (RN-32)');
            END IF;
        END LOOP;
        -- RN-33: suspende o reanuda el cobro de las líneas reclamadas
        FOR i IN 1 .. g_lineas.COUNT LOOP
            PKG_SIGCA.sincronizar_reclamo(g_lineas(i));
        END LOOP;
    END AFTER STATEMENT;
END TRG_PQR_CIUD;
/

CREATE OR REPLACE TRIGGER TRG_PQR_ACTUACION_BI
BEFORE INSERT ON PQR_ACTUACION
FOR EACH ROW
DECLARE
    v_estado PQR.ESTADO%TYPE;
BEGIN
    SELECT ESTADO INTO v_estado FROM PQR WHERE ID = :NEW.PQR_ID;
    IF v_estado NOT IN ('EN_TRAMITE','VENCIDA','TRASLADADA_SSPD') THEN
        PKG_SIGCA.error(-20001, 'Solo se documentan actuaciones de una PQR en trámite, vencida o trasladada');
    END IF;
    :NEW.FECHA := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER TRG_PQR_ADJUNTO_BI
BEFORE INSERT ON PQR_ADJUNTO
FOR EACH ROW
DECLARE
    v_estado PQR.ESTADO%TYPE;
BEGIN
    SELECT ESTADO INTO v_estado FROM PQR WHERE ID = :NEW.PQR_ID;
    IF v_estado = 'RESPONDIDA' THEN
        PKG_SIGCA.error(-20006, 'No se agregan adjuntos a una PQR respondida');
    END IF;
    IF :NEW.TAMANO_BYTES <> DBMS_LOB.GETLENGTH(:NEW.CONTENIDO) THEN
        PKG_SIGCA.error(-20014, 'El tamaño declarado no coincide con el contenido');
    END IF;
    IF :NEW.TAMANO_BYTES > PKG_SIGCA.parametro_num('ADJUNTO_MAX_MB') * 1048576 THEN
        PKG_SIGCA.error(-20014, 'El archivo supera el tamaño máximo permitido');
    END IF;
    :NEW.CARGADO_EN := SYSTIMESTAMP;
END;
/

-- Historial, actuaciones y adjuntos son evidencia: inmutables
CREATE OR REPLACE TRIGGER TRG_PQR_HISTORIAL_BUD
BEFORE UPDATE OR DELETE ON PQR_HISTORIAL
FOR EACH ROW
BEGIN
    PKG_SIGCA.error(-20006, 'El historial de PQR es inmutable');
END;
/

CREATE OR REPLACE TRIGGER TRG_PQR_ACTUACION_BUD
BEFORE UPDATE OR DELETE ON PQR_ACTUACION
FOR EACH ROW
BEGIN
    PKG_SIGCA.error(-20006, 'Las actuaciones de PQR son inmutables');
END;
/

CREATE OR REPLACE TRIGGER TRG_PQR_ADJUNTO_BUD
BEFORE UPDATE OR DELETE ON PQR_ADJUNTO
FOR EACH ROW
BEGIN
    PKG_SIGCA.error(-20006, 'Los adjuntos de PQR son inmutables');
END;
/

-- =====================================================================
-- AUDITORÍA
-- =====================================================================

-- RF-15 / RNF-16: log de auditoría de solo inserción
CREATE OR REPLACE TRIGGER TRG_LOG_AUDITORIA_BUD
BEFORE UPDATE OR DELETE ON LOG_AUDITORIA
FOR EACH ROW
BEGIN
    PKG_SIGCA.error(-20006, 'El log de auditoría es inmutable');
END;
/

CREATE OR REPLACE TRIGGER TRG_LOG_AUDITORIA_BI
BEFORE INSERT ON LOG_AUDITORIA
FOR EACH ROW
BEGIN
    :NEW.FECHA := SYSTIMESTAMP;   -- la fecha del evento no la decide el cliente
END;
/
