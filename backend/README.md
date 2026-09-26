# SIGCA-EPQ · Backend · F-01 Registrar usuario

Spring Boot 4.0 · Java 17+ · Maven · Oracle 12c+

## Requisitos
1. Base de datos Oracle con los scripts ya ejecutados, en este orden:
   `V1__f01_registrar_usuario.sql` y luego `seed_f01_catalogos.sql`.
2. JDK 17 o superior y Maven 3.9+.

## Configuración
Edita `src/main/resources/application.properties` o define variables de entorno:

| Variable | Ejemplo |
|---|---|
| `DB_URL` | `jdbc:oracle:thin:@//localhost:1521/XEPDB1` |
| `DB_USER` | `sigca` (el usuario dueño de las tablas) |
| `DB_PASSWORD` | `********` |
| `CORS_ORIGINS` | `http://localhost:5173` (dirección de la página) |

## Ejecutar
```
mvn spring-boot:run
```
La API queda en `http://localhost:8080`.

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/tipos-documento` | Opciones de la lista desplegable |
| POST | `/api/usuarios` | Registra un usuario (201 si todo sale bien) |

### Persona natural
```json
{
  "tipoDocumento": "CC",
  "numeroDocumento": "1094123456",
  "nombres": "María Camila",
  "apellidos": "Gómez Rojas",
  "email": "maria@correo.com",
  "telefono": "3001234567",
  "password": "Clave2026",
  "confirmarPassword": "Clave2026",
  "aceptaTratamientoDatos": true
}
```

### Persona jurídica
```json
{
  "tipoDocumento": "NIT",
  "numeroDocumento": "900123456",
  "razonSocial": "Ferretería El Roble S.A.S.",
  "email": "contacto@elroble.com",
  "telefono": "6067412345",
  "password": "Clave2026",
  "confirmarPassword": "Clave2026",
  "aceptaTratamientoDatos": true
}
```

## Respuestas de error

| Código | Cuándo |
|---|---|
| 400 | Datos inválidos (el detalle por campo viene en `errores`), tipo de documento inexistente, contraseñas distintas, campos que no corresponden al tipo de persona |
| 409 | Documento o correo ya registrados |
| 422 | La validación de identidad falló |

## Cómo probar el rechazo de identidad
El validador es una simulación (`ValidadorIdentidadSimulado`): rechaza documentos formados solo por
ceros, por ejemplo `0000000000` con tipo `CC`. Para la integración real se crea otra clase que
implemente `ValidadorIdentidad`.

## Estados que asigna el registro
- Documento que se valida ante la RNEC (CC) y la validación pasa: `ACTIVO`.
- Documento que no se valida automáticamente (CE, PA, NIT): `PENDIENTE_VALIDACION`.

## Estructura
Organizada por funcionalidad: cada nueva (F-02, F-05...) agrega su propio paquete junto a `usuario`.
```
com.epq.sigca
├── common/    errores uniformes, CORS, BCrypt
└── usuario/   domain (entidades) · repository · dto · service · web (controlador)
```
