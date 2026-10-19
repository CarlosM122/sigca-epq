#!/bin/bash
# Instala (o reinstala desde cero) el esquema oficial de SIGCA-EPQ en el contenedor Oracle local
# y carga los datos de desarrollo. BORRA los usuarios SIGCA y SIGCA_APP si existen.
#
# Uso: dev/instalar-esquema.sh /ruta/a/modelo-datos/sql
set -euo pipefail
SQL_DIR=${1:?Indique la carpeta modelo-datos/sql}
C=${ORACLE_CONTENEDOR:-sigca-oracle}
SYS_CLAVE=${ORACLE_PASSWORD:-OraSys2026x}
SIGCA_CLAVE=${SIGCA_CLAVE:-Sigca2026x}
APP_CLAVE=${SIGCA_DB_CLAVE:-SigcaApp2026x}
DIR=$(cd "$(dirname "$0")" && pwd)

docker exec -u root "$C" rm -rf /tmp/sql
docker cp "$SQL_DIR" "$C":/tmp/sql
docker cp "$DIR/semilla-dev.sql" "$C":/tmp/sql/semilla-dev.sql
docker exec -u root "$C" chmod -R a+rwX /tmp/sql
docker exec -i "$C" sqlplus -s "system/$SYS_CLAVE@localhost:1521/FREEPDB1" <<SQL
whenever sqlerror continue
DROP USER SIGCA CASCADE;
DROP USER SIGCA_APP CASCADE;
whenever sqlerror exit failure
CREATE USER SIGCA IDENTIFIED BY "$SIGCA_CLAVE" DEFAULT TABLESPACE USERS QUOTA UNLIMITED ON USERS;
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE PROCEDURE, CREATE TRIGGER, CREATE VIEW TO SIGCA;
CREATE USER SIGCA_APP IDENTIFIED BY "$APP_CLAVE";
GRANT CREATE SESSION TO SIGCA_APP;
exit
SQL
docker exec "$C" bash -c "cd /tmp/sql && echo exit | sqlplus -s SIGCA/$SIGCA_CLAVE@localhost:1521/FREEPDB1 @instalar.sql \
  && echo exit | sqlplus -s SIGCA/$SIGCA_CLAVE@localhost:1521/FREEPDB1 @06_permisos_app.sql \
  && echo exit | sqlplus -s SIGCA/$SIGCA_CLAVE@localhost:1521/FREEPDB1 @semilla-dev.sql"
echo "Esquema SIGCA instalado con datos de desarrollo."
