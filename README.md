# SIGCA-EPQ

Prototipo del sistema SIGCA-EPQ. Este repositorio agrupa el backend, el frontend y los
scripts de base de datos del proyecto, organizados por funcionalidad (PBI).

## Estructura

```
sigca-epq/
├── backend/                  Spring Boot 4.0 · Java 17+ · Maven · Oracle
├── frontend/                 Angular · formulario de registro de usuario
├── database/
│   ├── migrations/           DDL, uno por funcionalidad (V1__f01_registrar_usuario.sql, ...)
│   └── seeds/                 Datos iniciales de catálogos
├── docs/                      Documentación del proyecto
└── pruebas-f01.http           Casos de prueba de la API (IntelliJ / VS Code REST Client)
```

## Funcionalidades implementadas

| PBI | Funcionalidad | Migración |
|---|---|---|
| PBI-01 | F-01 Registrar usuario | `database/migrations/V1__f01_registrar_usuario.sql` |

## Cómo ejecutar

1. **Base de datos**: ejecute en Oracle, en orden, los archivos de `database/migrations/`
   y luego los de `database/seeds/` correspondientes.
2. **Backend**: vea `backend/README.md` (configuración de conexión y endpoints).
3. **Frontend**: vea `frontend/README.md` (o la carpeta `frontend/` si ya generó el
   proyecto con `ng new`).

## Convención de nombres de migraciones

Cada nuevo requisito agrega su propio archivo numerado (`V2__...`, `V3__...`), sin editar
los anteriores, para mantener la trazabilidad con los requisitos del proyecto.
