-- =====================================================================
-- SIGCA-EPQ  ·  00_crear_esquema.sql
-- Ejecutar como SYSTEM (o un DBA) en la PDB, por ejemplo:
--   sqlplus system/<clave>@localhost:1521/FREEPDB1 @00_crear_esquema.sql
-- Crea el esquema dueño (SIGCA) y un usuario de aplicación (SIGCA_APP)
-- con permisos de solo DML, que es el que usa Spring Boot.
-- =====================================================================
SET DEFINE ON
ACCEPT clave_sigca CHAR PROMPT 'Contraseña para el esquema SIGCA: ' HIDE
ACCEPT clave_app   CHAR PROMPT 'Contraseña para el usuario SIGCA_APP: ' HIDE

CREATE USER SIGCA IDENTIFIED BY "&clave_sigca" DEFAULT TABLESPACE USERS QUOTA UNLIMITED ON USERS;
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE PROCEDURE, CREATE TRIGGER, CREATE VIEW TO SIGCA;

CREATE USER SIGCA_APP IDENTIFIED BY "&clave_app";
GRANT CREATE SESSION TO SIGCA_APP;
