-- =============================================================================
-- SIGCA-EPQ | Datos iniciales de catálogos para F-01 Registrar usuario (Oracle)
-- Ejecutar DESPUÉS de V1__f01_registrar_usuario.sql
-- Es re-ejecutable: MERGE no duplica filas si ya existen.
-- =============================================================================

MERGE INTO tipo_documento t
USING (
    SELECT 'CC' AS codigo, 'Cédula de ciudadanía' AS nombre, 'NATURAL' AS tipo_persona, 1 AS valida_rnec FROM dual
    UNION ALL SELECT 'CE',  'Cédula de extranjería',                'NATURAL',  0 FROM dual
    UNION ALL SELECT 'PA',  'Pasaporte',                            'NATURAL',  0 FROM dual
    UNION ALL SELECT 'NIT', 'Número de identificación tributaria',  'JURIDICA', 0 FROM dual
) s
ON (t.codigo = s.codigo)
WHEN NOT MATCHED THEN
    INSERT (codigo, nombre, tipo_persona, valida_rnec)
    VALUES (s.codigo, s.nombre, s.tipo_persona, s.valida_rnec);

MERGE INTO estado_usuario t
USING (
    SELECT 'PENDIENTE_VALIDACION' AS codigo, 'Pendiente de validación de identidad' AS nombre FROM dual
    UNION ALL SELECT 'ACTIVO',    'Activo'                 FROM dual
    UNION ALL SELECT 'RECHAZADO', 'Identidad no validada'  FROM dual
    UNION ALL SELECT 'INACTIVO',  'Inactivo'               FROM dual
) s
ON (t.codigo = s.codigo)
WHEN NOT MATCHED THEN
    INSERT (codigo, nombre)
    VALUES (s.codigo, s.nombre);

COMMIT;
