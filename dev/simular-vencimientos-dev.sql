-- =====================================================================
-- SIGCA-EPQ · SOLO DESARROLLO. NUNCA en pruebas compartidas ni en producción.
--
-- Simula el paso del tiempo para probar en un solo día lo que en la vida
-- real tarda semanas:
--   1. Facturas PENDIENTES de un usuario hasta un periodo: corre hacia atrás
--      su emisión y su vencimiento y las pasa a VENCIDA (mora -> Cartera).
--   2. Una PQR abierta (RECIBIDA o EN_TRAMITE): corre hacia atrás su fecha
--      límite y la pasa a VENCIDA (silencio administrativo positivo).
--
-- Las fechas emitidas son inmutables (TRG_FACTURA_CIUD, TRG_PQR_CIUD), así
-- que el script desactiva el trigger SOLO para cambiar las fechas, lo vuelve
-- a activar y hace el cambio de estado con el trigger activo (la BD valida
-- la transición). Ejecutar como SIGCA (SQL Developer: F5) y con el backend
-- encendido o apagado, da igual. Edite las tres constantes de abajo.
-- =====================================================================
SET SERVEROUTPUT ON
SET DEFINE OFF
DECLARE
    c_usuario      CONSTANT VARCHAR2(12) := 'USR-00000002';  -- código del usuario moroso
    c_hasta        CONSTANT NUMBER       := 202608;          -- vence las facturas de periodos <= a este
    c_dias_mora    CONSTANT NUMBER       := 10;              -- días que lleva vencida cada factura
    c_radicado     CONSTANT VARCHAR2(11) := '202610-0002';   -- PQR a vencer (NULL para omitir)
    v_venc  DATE := TRUNC(SYSDATE) - c_dias_mora;
    v_emis  DATE;
    v_n     PLS_INTEGER := 0;
    v_ids   SYS.ODCINUMBERLIST := SYS.ODCINUMBERLIST();
BEGIN
    -- 1. Facturas
    EXECUTE IMMEDIATE 'ALTER TRIGGER TRG_FACTURA_CIUD DISABLE';
    BEGIN
        FOR f IN (SELECT F.ID, F.NUMERO_FACTURA, F.PERIODO FROM FACTURA F JOIN USUARIO U ON U.ID = F.USUARIO_ID
                   WHERE U.CODIGO = c_usuario AND F.ESTADO = 'PENDIENTE' AND F.PERIODO <= c_hasta) LOOP
            v_emis := GREATEST(PKG_SIGCA.fin_periodo(f.PERIODO) + 1, v_venc - 15);
            IF v_emis >= v_venc THEN
                DBMS_OUTPUT.PUT_LINE('Se omite ' || f.NUMERO_FACTURA || ': el periodo es muy reciente para ' || c_dias_mora || ' días de mora');
            ELSE
                UPDATE FACTURA SET FECHA_EMISION = v_emis, FECHA_VENCIMIENTO = v_venc WHERE ID = f.ID;
                v_ids.EXTEND;
                v_ids(v_ids.COUNT) := f.ID;
            END IF;
        END LOOP;
    EXCEPTION
        WHEN OTHERS THEN
            EXECUTE IMMEDIATE 'ALTER TRIGGER TRG_FACTURA_CIUD ENABLE';
            RAISE;
    END;
    EXECUTE IMMEDIATE 'ALTER TRIGGER TRG_FACTURA_CIUD ENABLE';   -- (el ALTER confirma lo anterior)
    FOR i IN 1 .. v_ids.COUNT LOOP
        UPDATE FACTURA SET ESTADO = 'VENCIDA' WHERE ID = v_ids(i);   -- con el trigger activo
        v_n := v_n + 1;
    END LOOP;
    DBMS_OUTPUT.PUT_LINE(v_n || ' facturas de ' || c_usuario || ' quedaron VENCIDAS el ' || TO_CHAR(v_venc, 'YYYY-MM-DD'));

    -- 2. PQR
    IF c_radicado IS NOT NULL THEN
        EXECUTE IMMEDIATE 'ALTER TRIGGER TRG_PQR_CIUD DISABLE';
        BEGIN
            UPDATE PQR SET FECHA_REGISTRO = FECHA_REGISTRO - 30, FECHA_ASIGNACION = FECHA_ASIGNACION - 30,
                           FECHA_ESTADO = FECHA_ESTADO - 30,
                           FECHA_INICIO_TRAMITE = FECHA_INICIO_TRAMITE - 30,
                           FECHA_LIMITE_RESPUESTA = TRUNC(SYSDATE) - 1
             WHERE NUMERO_RADICADO = c_radicado AND ESTADO IN ('RECIBIDA','EN_TRAMITE');
            v_n := SQL%ROWCOUNT;
        EXCEPTION
            WHEN OTHERS THEN
                EXECUTE IMMEDIATE 'ALTER TRIGGER TRG_PQR_CIUD ENABLE';
                RAISE;
        END;
        EXECUTE IMMEDIATE 'ALTER TRIGGER TRG_PQR_CIUD ENABLE';
        UPDATE PQR SET ESTADO = 'VENCIDA' WHERE NUMERO_RADICADO = c_radicado AND ESTADO IN ('RECIBIDA','EN_TRAMITE');
        DBMS_OUTPUT.PUT_LINE(CASE WHEN v_n = 1 THEN 'PQR ' || c_radicado || ' VENCIDA con silencio positivo'
                                  ELSE 'La PQR ' || c_radicado || ' no existe o no está RECIBIDA/EN_TRAMITE' END);
    END IF;
    COMMIT;
END;
/
