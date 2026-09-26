-- =============================================================================
-- SIGCA-EPQ | Migración V1 | F-01 Registrar usuario
-- Motor: Oracle Database 12c o superior (usa columnas IDENTITY)
--
-- Alcance: solo lo necesario para registrar ciudadanos (personas naturales y
-- jurídicas). Las demás funcionalidades (contratos, facturas, PQR, pagos...)
-- agregarán sus tablas en migraciones posteriores (V2, V3, ...).
--
-- Decisiones de diseño:
--   * Una sola tabla USUARIO para persona natural y jurídica, con CHECK que
--     obliga a diligenciar los campos que corresponden a cada tipo.
--   * Catálogos (TIPO_DOCUMENTO, ESTADO_USUARIO) en tablas, para poder
--     ampliarlos con un INSERT sin modificar la estructura.
--   * La contraseña se guarda solo como hash (bcrypt/argon2 generado en la app).
--   * La dirección NO va aquí: pertenece al predio, que llega con F-02.
--   * Oracle no tiene BOOLEAN (antes de 23ai): se usa NUMBER(1) con 0 / 1.
--   * Todos los identificadores tienen máximo 30 caracteres (compatible con 12.1).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Catálogo: tipo de documento
-- -----------------------------------------------------------------------------
CREATE TABLE tipo_documento (
    id            NUMBER(5) GENERATED ALWAYS AS IDENTITY,
    codigo        VARCHAR2(10 CHAR) NOT NULL,
    nombre        VARCHAR2(80 CHAR) NOT NULL,
    tipo_persona  VARCHAR2(10 CHAR) NOT NULL,
    valida_rnec   NUMBER(1) DEFAULT 0 NOT NULL,
    CONSTRAINT pk_tipo_documento PRIMARY KEY (id),
    CONSTRAINT uq_tipo_documento_codigo UNIQUE (codigo),
    CONSTRAINT ck_tipo_documento_persona
        CHECK (tipo_persona IN ('NATURAL', 'JURIDICA')),
    CONSTRAINT ck_tipo_documento_rnec
        CHECK (valida_rnec IN (0, 1)),
    -- Necesaria para la FK compuesta desde USUARIO (ver abajo)
    CONSTRAINT uq_tipo_documento_id_persona UNIQUE (id, tipo_persona)
);

COMMENT ON TABLE  tipo_documento             IS 'Catálogo de tipos de documento de identificación.';
COMMENT ON COLUMN tipo_documento.valida_rnec IS '1 si la identidad se valida ante la Registraduría (RNEC), 0 si no.';

-- -----------------------------------------------------------------------------
-- Catálogo: estado del usuario
-- -----------------------------------------------------------------------------
CREATE TABLE estado_usuario (
    id      NUMBER(5) GENERATED ALWAYS AS IDENTITY,
    codigo  VARCHAR2(30 CHAR) NOT NULL,
    nombre  VARCHAR2(60 CHAR) NOT NULL,
    CONSTRAINT pk_estado_usuario PRIMARY KEY (id),
    CONSTRAINT uq_estado_usuario_codigo UNIQUE (codigo)
);

COMMENT ON TABLE estado_usuario IS 'Catálogo de estados del ciclo de vida del usuario registrado.';

-- -----------------------------------------------------------------------------
-- Usuario (ciudadano natural o jurídico)
-- -----------------------------------------------------------------------------
CREATE TABLE usuario (
    id                          NUMBER(19) GENERATED ALWAYS AS IDENTITY,
    -- Identificación
    tipo_persona                VARCHAR2(10 CHAR)  NOT NULL,
    tipo_documento_id           NUMBER(5)          NOT NULL,
    numero_documento            VARCHAR2(20 CHAR)  NOT NULL,
    nombres                     VARCHAR2(100 CHAR),
    apellidos                   VARCHAR2(100 CHAR),
    razon_social                VARCHAR2(200 CHAR),
    -- Contacto y acceso al portal
    email                       VARCHAR2(254 CHAR) NOT NULL,
    telefono                    VARCHAR2(15 CHAR)  NOT NULL,
    password_hash               VARCHAR2(255 CHAR) NOT NULL,
    -- Estado y validación de identidad
    estado_usuario_id           NUMBER(5)          NOT NULL,
    fecha_validacion_identidad  TIMESTAMP WITH TIME ZONE,
    -- Habeas data (Ley 1581 de 2012): la app debe enviar la fecha en que el
    -- usuario aceptó el tratamiento de datos; sin DEFAULT a propósito.
    fecha_acepta_trat_datos     TIMESTAMP WITH TIME ZONE NOT NULL,
    fecha_registro              TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_usuario PRIMARY KEY (id),
    -- Un documento solo puede registrarse una vez
    CONSTRAINT uq_usuario_documento UNIQUE (tipo_documento_id, numero_documento),
    CONSTRAINT ck_usuario_tipo_persona
        CHECK (tipo_persona IN ('NATURAL', 'JURIDICA')),
    -- Natural: nombres + apellidos. Jurídica: solo razón social.
    CONSTRAINT ck_usuario_datos_segun_persona CHECK (
        (tipo_persona = 'NATURAL'
            AND nombres IS NOT NULL AND apellidos IS NOT NULL
            AND razon_social IS NULL)
        OR
        (tipo_persona = 'JURIDICA'
            AND razon_social IS NOT NULL
            AND nombres IS NULL AND apellidos IS NULL)
    ),
    CONSTRAINT ck_usuario_numero_documento
        CHECK (REGEXP_LIKE(numero_documento, '^[A-Za-z0-9-]+$')),
    CONSTRAINT ck_usuario_email
        CHECK (REGEXP_LIKE(email, '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$')),
    -- FK compuesta: impide, por ejemplo, un NIT asociado a una persona natural
    CONSTRAINT fk_usuario_tipo_documento
        FOREIGN KEY (tipo_documento_id, tipo_persona)
        REFERENCES tipo_documento (id, tipo_persona),
    CONSTRAINT fk_usuario_estado
        FOREIGN KEY (estado_usuario_id)
        REFERENCES estado_usuario (id)
);

-- El correo es el usuario de acceso: único sin distinguir mayúsculas
CREATE UNIQUE INDEX uq_usuario_email ON usuario (LOWER(email));

COMMENT ON TABLE  usuario                            IS 'F-01: ciudadanos (personas naturales o jurídicas) registrados en el portal.';
COMMENT ON COLUMN usuario.password_hash              IS 'Hash de la contraseña. Nunca almacenar la contraseña en texto plano.';
COMMENT ON COLUMN usuario.fecha_validacion_identidad IS 'Fecha en que se validó la identidad (RNEC). NULL si aún no se valida.';
COMMENT ON COLUMN usuario.fecha_acepta_trat_datos    IS 'Fecha y hora en que el usuario aceptó el tratamiento de datos personales.';
