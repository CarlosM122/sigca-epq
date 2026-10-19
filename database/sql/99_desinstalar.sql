-- =====================================================================
-- SIGCA-EPQ  ·  99_desinstalar.sql
-- BORRA todo el modelo del esquema actual. Solo para entornos de desarrollo.
-- =====================================================================
BEGIN
    FOR o IN (SELECT OBJECT_NAME, OBJECT_TYPE FROM USER_OBJECTS
               WHERE OBJECT_TYPE IN ('TABLE','SEQUENCE','PACKAGE') ORDER BY OBJECT_TYPE DESC) LOOP
        BEGIN
            IF o.OBJECT_TYPE = 'TABLE' THEN
                EXECUTE IMMEDIATE 'DROP TABLE ' || o.OBJECT_NAME || ' CASCADE CONSTRAINTS PURGE';
            ELSE
                EXECUTE IMMEDIATE 'DROP ' || o.OBJECT_TYPE || ' ' || o.OBJECT_NAME;
            END IF;
        EXCEPTION
            WHEN OTHERS THEN NULL;  -- la tabla pudo caer ya por CASCADE
        END;
    END LOOP;
END;
/
