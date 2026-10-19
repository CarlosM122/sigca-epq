# SIGCA-EPQ

Sistema de Información para la Gestión Comercial y Atención al Cliente de Empresas Públicas del Quindío:
usuarios y contratos de acueducto, alcantarillado y gas en 9 municipios, lecturas, facturación mensual,
pagos presenciales y en línea, PQR con plazos legales, cartera y acuerdos de pago, portal ciudadano y
dashboard gerencial.

| Capa | Tecnología |
|---|---|
| Base de datos | Oracle 23 (esquema oficial en `database/sql`, copia de `modelo-datos/sql`; reglas en triggers y PKG_SIGCA) |
| Backend | Spring Boot 4.1 · Java 21 · Hibernate 7 (`ddl-auto=validate`) · Spring Security (JWT HS256 + TOTP) |
| Frontend | Angular 21 (standalone, signals, formularios reactivos, vitest) |

## Validaciones en las tres capas

1. **Frontend**: validadores reactivos espejo del backend (`shared/validadores.ts`) y errores del servidor por campo.
2. **Backend**: Bean Validation en cada DTO (documento por tipo con dígito de verificación del NIT, teléfono
   colombiano, contraseñas, archivos por firma mágica), permisos por endpoint (`@PreAuthorize` con los códigos
   de la tabla PERMISO) y reglas de negocio en los servicios.
3. **Oracle**: CHECK, UNIQUE, FK y triggers (transiciones de estado, liquidación, totales, plazos, crítica de
   lecturas). Los errores ORA-20001..20021 y de restricciones se traducen a un `ApiError` con código estable.

## Puesta en marcha (desarrollo)

Requisitos: Docker, JDK 21, Maven 3.9, Node 22+.

```bash
# 1. Oracle Free (hora de Colombia)
docker compose up -d            # espere a que el contenedor quede "healthy"

# 2. Esquema oficial + datos de desarrollo (BORRA y recrea SIGCA y SIGCA_APP)
dev/instalar-esquema.sh database/sql

# 3. Backend en http://localhost:8080 (Swagger en /swagger-ui.html con el perfil dev)
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 4. Frontend en http://localhost:4200 (proxy de /api al backend)
cd frontend && npm install && npx ng serve
```

### Instalar el esquema con SQL Developer (en lugar del script)

1. Conexión como `system` / `OraSys2026x`, host `localhost`, puerto `1521`, **Nombre del servicio** `FREEPDB1`.
2. Abrir `database/sql/00_crear_esquema.sql` y ejecutarlo con F5; en los diálogos escribir `Sigca2026x`
   (SIGCA) y `SigcaApp2026x` (SIGCA_APP, la que usa `application-dev.yml`).
3. Conexión como `SIGCA` / `Sigca2026x` (mismo host y servicio) y, en ella, ejecutar con F5 y en este orden:
   `database/sql/instalar.sql`, `database/sql/06_permisos_app.sql` y `dev/semilla-dev.sql`.

### Usuarios de desarrollo (`dev/semilla-dev.sql`)

| Usuario | Rol | Contraseña |
|---|---|---|
| `admin` | Administrador | `Cambiar.Ya2026` (temporal: pide cambiarla) |
| `asesor` | Asesor (Circasia) | `Sigca.Dev2026` |
| `gestor.circasia`, `gestor.salento` | Gestor de PQR | `Sigca.Dev2026` |
| `jefe.pqr` | Jefe de PQR | `Sigca.Dev2026` |
| `cartera` | Gestor de cartera | `Sigca.Dev2026` |
| `gerente` | Gerente | `Sigca.Dev2026` |

El segundo factor es obligatorio (P-20): en el primer ingreso la pantalla muestra la clave y el enlace
`otpauth://` para registrarlos en una app autenticadora (Google Authenticator, Microsoft Authenticator, etc.).
La semilla también carga tarifas y factores de subsidio **de ejemplo** desde 2025-01-01; los reales los registra
el Administrador según la resolución CRA y los acuerdos municipales.

Los ciudadanos activan su cuenta del portal desde "Activar cuenta" con su tipo y número de identificación; el
enlace llega al correo registrado (en desarrollo el correo es simulado y queda en el log y en NOTIFICACION).

## Variables de entorno (producción)

| Variable | Uso |
|---|---|
| `SIGCA_DB_URL`, `SIGCA_DB_USUARIO` (SIGCA_APP), `SIGCA_DB_CLAVE` | Conexión a Oracle |
| `SIGCA_JWT_SECRETO` | Secreto HS256, mínimo 32 caracteres |
| `SIGCA_CLAVE_AES`, `SIGCA_CLAVE_HMAC` | 32 bytes en Base64 cada una (`openssl rand -base64 32`): cifrado y búsqueda de identificaciones. **No cambiarlas** después de cargar datos |
| `SIGCA_CORS_ORIGENES`, `SIGCA_URL_PORTAL` | Dominio del frontend y base de los enlaces enviados por correo |
| `SIGCA_PASARELA_MODO`, `SIGCA_PASARELA_URL`, `SIGCA_PASARELA_SECRETO` | Pasarela de pagos (en `simulado` hay una pasarela de prueba) |
| `SIGCA_CORREO_MODO`, `SIGCA_SMTP_*` | Envío de correo |

## Pruebas

```bash
cd backend && mvn test                 # unitarias
cd backend && mvn verify               # + integración (*IT) contra el Oracle local con la semilla
cd frontend && npx ng test --watch=false
```

Las pruebas de integración confirman sus transacciones (como en producción, para evaluar las restricciones
diferidas) y crean datos únicos; conviene correrlas sobre una base de desarrollo, nunca sobre producción.

## Estructura

```
backend/src/main/java/co/gov/epq/sigca/
  common/        errores (traducción ORA), cifrado, validaciones, periodos
  catalogo/      municipios, sectores, festivos, parámetros
  seguridad/     login funcionario con TOTP, portal ciudadano, JWT, administración de funcionarios y roles
  usuarios/      usuarios (identificación cifrada), predios
  contratos/     medidores, contratos, órdenes de suspensión y reconexión
  tarifas/       tarifas CRA y factores de subsidio con vigencias
  lecturas/      registro manual y por CSV, revisión de lecturas atípicas
  facturacion/   lote mensual, liquidación, facturas, PDF, ERP (simulado)
  pagos/         pagos presenciales/banco, pasarela con webhook firmado
  portal/        consultas del ciudadano
  pqr/           radicación, trámite, recursos, SSPD, silencio positivo, alertas
  cartera/       morosos, cobro persuasivo, suspensión por mora, acuerdos de pago
  dashboard/     indicadores gerenciales
  auditoria/     log inmutable y su consulta
  notificaciones/ cola de correo, SMS y bandeja interna con reintentos
frontend/src/app/
  core/ shared/ layout/ features/{auth,admin,usuarios,contratos,tarifas,facturacion,pagos,portal,pqr,publico,cartera,dashboard}
dev/             instalación del esquema y datos de desarrollo
```

## Integraciones simuladas

RNEC, ERP financiero, pasarela de pagos, correo y SMS tienen un puerto (interfaz) y una implementación
simulada activada por `sigca.integraciones.*.modo=simulado`. Para producción se implementa el adaptador real.

## Pendientes conocidos

- Adaptadores reales de RNEC, ERP, pasarela, correo y SMS.
- Los JWT no se revocan antes de vencer (máximo 15 minutos); `renovar` sí revalida estado y permisos.
- Un código TOTP puede reutilizarse dentro de su ventana (~90 s); evitarlo requiere una columna en FUNCIONARIO.
- El límite de solicitudes de los endpoints públicos es por instancia (en memoria).
- Valores marcados como supuestos en PARAMETRO_SISTEMA (tasa de mora residencial, cargo de reconexión,
  días de vencimiento) deben confirmarse con EPQ.
