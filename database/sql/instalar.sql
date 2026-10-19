-- =====================================================================
-- SIGCA-EPQ  ·  instalar.sql
-- Instala todo el modelo en el esquema actual (conectado como SIGCA):
--   sqlplus SIGCA/<clave>@localhost:1521/FREEPDB1 @instalar.sql
-- Se detiene en el primer error.
-- =====================================================================
SET DEFINE OFF
SET ECHO OFF
SET FEEDBACK OFF
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK

PROMPT == 01 Tablas
@@01_tablas.sql
PROMPT == 02 Indices
@@02_indices.sql
PROMPT == 03 Paquete y triggers
@@03_logica.sql
PROMPT == 04 Datos iniciales
@@04_datos_iniciales.sql

-- Verifica que no quedaron objetos inválidos
DECLARE
    v_invalidos PLS_INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_invalidos FROM USER_OBJECTS WHERE STATUS <> 'VALID';
    IF v_invalidos > 0 THEN
        RAISE_APPLICATION_ERROR(-20999, v_invalidos || ' objetos inválidos tras la instalación');
    END IF;
END;
/
PROMPT == Instalación completa
