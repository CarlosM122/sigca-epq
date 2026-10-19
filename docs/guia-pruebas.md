# SIGCA-EPQ · Guía de pruebas manuales de punta a punta

Esta guía recorre el sistema completo desde una base **recién instalada** (esquema oficial + `dev/semilla-dev.sql`,
sin usuarios, predios ni contratos). Todos los pasos, valores y resultados se ejecutaron y comprobaron el
14 de octubre de 2026 con el backend en perfil `dev`; los números (USR-, CTR-, FAC-, PQR, OS-, AP-) coinciden si
sigue el orden exacto de la guía sobre una base limpia. Si un número le sale distinto, use el suyo.

> **Fechas.** La guía está escrita para probar en **octubre de 2026**: se facturan los periodos 202607, 202608
> y 202609 (meses cerrados). Si prueba en otro mes, corra todo lo de la guía un mes (o los que hagan falta):
> el sistema solo factura meses cerrados y como máximo 5 meses atrás.

---

## 1. Requisitos previos

1. Oracle arriba, esquema instalado y semilla cargada (README, "Puesta en marcha").
2. Backend en `http://localhost:8080` con perfil `dev` y frontend en `http://localhost:4200`.
3. Una app autenticadora en el celular (Google Authenticator, Microsoft Authenticator, etc.).
4. SQL Developer con dos conexiones: `SIGCA` / `Sigca2026x` (para los ayudantes DEV) y, si quiere ver
   tablas, la misma.
5. Un PDF real cualquiera de menos de 10 MB (para soportes y evidencias de PQR), y un archivo `.txt`
   renombrado a `.pdf` (para la prueba negativa de adjuntos).

### 1.1 Compruebe la zona horaria de Oracle (¡importante entre las 19:00 y las 24:00!)

En SQL Developer, conectado como SIGCA:

```sql
SELECT SYSTIMESTAMP FROM DUAL;
```

- Debe terminar en **`-05:00`** y mostrar la hora de Colombia. Así queda con el `docker-compose.yml` del
  proyecto (`TZ: America/Bogota`).
- Si muestra **`+00:00`** (contenedor en UTC, típico si lo creó con `docker run` sin `-e TZ`), la base va
  5 horas adelante de la aplicación. No se cae nada, pero después de las 19:00 de Colombia verá:
  - Fechas y horas grabadas por la base 5 horas adelante (y con fecha de **mañana**): creación de usuarios,
    fecha de pago del comprobante (un pago de las 21:00 sale como 02:00 del día siguiente), radicación y
    respuesta de PQR, órdenes, auditoría.
  - **Administración > Auditoría** aparece **vacía** con los filtros por defecto (hasta = hoy), porque los
    registros quedaron con la fecha de mañana. Ponga en *Hasta* la fecha de mañana.
  - Plazos de PQR contados desde mañana (un día hábil de más), el mes del radicado errado el último día del
    mes, días de mora con +1, y el Dashboard calculado con la fecha de la base.

  **Remedio** (conserva los datos, que viven en el volumen):

  ```bash
  # si el contenedor se creó con el docker-compose.yml del proyecto
  docker compose up -d --force-recreate
  # si se creó a mano con docker run: bórrelo y recréelo con la zona, montando EL MISMO volumen
  docker stop sigca-oracle && docker rm sigca-oracle
  docker run -d --name sigca-oracle -p 1521:1521 -e ORACLE_PASSWORD=OraSys2026x -e TZ=America/Bogota ^
    -v <su-volumen>:/opt/oracle/oradata gvenzl/oracle-free:23-slim-faststart
  ```

  (en PowerShell cambie `^` por `` ` ``). Espere a que quede *healthy*, reinicie el backend y repita el
  `SELECT SYSTIMESTAMP FROM DUAL`. Antes de las 19:00 el desfase solo se nota en las horas mostradas.

### 1.2 Primer ingreso de los funcionarios (segundo factor)

Para **cada** funcionario que use (asesor, admin, cartera, gestor.circasia, gestor.salento, jefe.pqr, gerente):

1. Abra `http://localhost:4200/ingresar`, pestaña **Soy funcionario**, escriba usuario y contraseña, **Ingresar**.
2. La pantalla dice **"Registre SIGCA en su aplicación autenticadora"** y muestra la clave en grupos de 4.
   Agréguela en la app (cuenta manual, basada en tiempo).
3. Escriba el código de 6 dígitos en **"Código de verificación de 6 dígitos (app autenticadora)"** e
   **Ingresar**.
4. `admin` entra con `Cambiar.Ya2026` y el sistema lo lleva a **Cambiar contraseña**: *Contraseña actual*
   `Cambiar.Ya2026`, *Nueva contraseña* y *Confirme la nueva contraseña* `Admin.Sigca2026`, **Guardar**.

Los módulos están en el menú lateral izquierdo (en el celular se abre con el botón ☰ de la barra superior).
Para cambiar de usuario use **Salir** (arriba a la derecha). Conviene usar una ventana normal para los
funcionarios y una ventana de incógnito para el ciudadano.

---

## 2. Datos de prueba (copiar y pegar)

### Funcionarios (semilla)

| Usuario | Rol | Contraseña |
|---|---|---|
| `asesor` | Asesor (Circasia) | `Sigca.Dev2026` |
| `admin` | Administrador | `Cambiar.Ya2026` → `Admin.Sigca2026` |
| `cartera` | Gestor de cartera | `Sigca.Dev2026` |
| `gestor.circasia` / `gestor.salento` | Gestor de PQR | `Sigca.Dev2026` |
| `jefe.pqr` | Jefe de PQR | `Sigca.Dev2026` |
| `gerente` | Gerente | `Sigca.Dev2026` |

### Usuarios (suscriptores), todos en **Circasia**

| Dato | María | Jorge | Panadería (NIT) |
|---|---|---|---|
| Tipo de persona | Natural | Natural | Jurídica |
| Tipo de identificación | CC | CC | NIT |
| Número | `1094123456` | `1094888777` | `900123456-8` |
| Nombre / Razón social | `María Fernanda López Gómez` | `Jorge Andrés Ramírez Ospina` | `Panadería El Trigal S.A.S.` |
| Correo | `maria.lopez@example.com` | `jorge.ramirez@example.com` | `contacto@eltrigal.example.com` |
| Teléfono | `3104567890` | `3157654321` | `6067491234` |
| Dirección de notificación | `Calle 7 # 12-34, barrio El Bosque` | `Carrera 14 # 5-20, barrio La Pista` | `Carrera 15 # 8-10, local 2` |
| Código esperado | USR-00000001 | USR-00000002 | USR-00000003 |

Simulación de la Registraduría (RNEC): un número que **termina en `0000`** es rechazado ("no coincide") y
uno que **termina en `9999`** queda en estado PENDIENTE de verificación. No use esos finales para los datos
buenos.

### Predios, medidores y contratos

| | Predio de María | Predio de Jorge |
|---|---|---|
| Dirección | `Calle 7 # 12-34` | `Carrera 14 # 5-20` |
| Barrio o vereda | `El Bosque` | `La Pista` |
| Uso / Estrato | Residencial / 3 | Residencial / 2 |
| Ficha catastral | (vacía) | (vacía) |
| Medidor acueducto | `ACU-CIR-1001`, Itron, instalación `2026-08-01`, lectura inicial `100` | `ACU-CIR-1002`, Itron, `2026-07-01`, `200` |
| Medidor gas | `GAS-CIR-1001`, Elster, `2026-08-01`, `50` | (no tiene) |
| Contratos (vinculación) | Acueducto, Alcantarillado y Gas, `2026-08-01` | Acueducto y Alcantarillado, `2026-07-01` |

### Lecturas

| Medidor | Periodo | Fecha | Lectura | Resultado esperado |
|---|---|---|---|---|
| ACU-CIR-1001 | 202608 | 2026-09-01 | 115 | CONFIRMADA, 15 m³ |
| GAS-CIR-1001 | 202608 | 2026-09-01 | 62 | CONFIRMADA, 12 m³ |
| GAS-CIR-1001 | 202609 | 2026-10-01 | 75 | CONFIRMADA, 13 m³ |
| ACU-CIR-1001 | 202609 | 2026-10-01 | 160 | **EN REVISIÓN** (45 m³ vs promedio 15) |
| ACU-CIR-1002 (CSV) | 202607 / 202608 / 202609 | 01 del mes siguiente | 220 / 242 / 263 | CONFIRMADAS: 20, 22, 21 m³ |

Archivo `lecturas-jorge.csv` (créelo con el Bloc de notas):

```
serial,periodo,fecha,lectura,causa
ACU-CIR-1002,202607,2026-08-01,220,
ACU-CIR-1002,202608,2026-09-01,242,
ACU-CIR-1002,202609,2026-10-01,263,
NO-EXISTE-01,202609,2026-10-01,10,
```

### Facturas esperadas (con las tarifas de ejemplo de la semilla)

| Lote | Factura | Titular | Total |
|---|---|---|---|
| 202607 | FAC-202607-0000001 | Jorge | $85.120 |
| 202608 | FAC-202608-0000002 | María | $123.620 |
| 202608 | FAC-202608-0000003 | Jorge | $93.920 |
| 202609 | FAC-202609-0000004 | María | $275.820 (acueducto 129.125, alcantarillado 97.395, gas 49.300) |
| 202609 | FAC-202609-0000005 | Jorge | $89.520 (se anula y se reexpide como FAC-202609-0000006) |

### Otros datos

| Para | Valor |
|---|---|
| Contraseña del portal de María | `Portal.Maria26` |
| Ciudadana sin cuenta (PQR pública) | Laura Gómez Arias, CC `41950123`, `laura.gomez@example.com`, `3001234567` |
| Funcionario nuevo | Código `FUN-0100`, usuario `pedro.ruiz`, `Pedro Antonio Ruiz Mejía`, `pedro.ruiz@epq.example.com`, `3112223344`, Circasia, rol Asesor |
| Recaudo bancario de la cuota | `BCO-778812` |

---

## 3. Escenarios

Formato: **Quién** · **Dónde** (menú > pantalla) · **Qué escribir** · **Qué debe ver**.

### E1. Registrar usuarios (asesor)

1. `asesor` · **Usuarios** > **Registrar usuario**. Llene los datos de **María** de la tabla, Municipio
   *Circasia*, marque **"El usuario autoriza el tratamiento…"** y **Guardar**.
   → Queda el usuario **USR-00000001**, estado ACTIVO, y se abre su ficha.
2. Repita con **Jorge** → USR-00000002.
3. Repita con la **Panadería**: Tipo de persona *Jurídica*, Tipo de identificación *NIT*, Número
   `900123456-8` → USR-00000003.
4. (Opcional) Un usuario con CC `1094559999` → se crea con aviso de verificación **pendiente** ante la
   Registraduría.

### E2. Predio y medidores (asesor)

1. En la ficha de María abra **Registrar contrato** > **Registrar un predio nuevo**. Dirección `Calle 7 # 12-34`,
   Barrio o vereda `El Bosque`, Municipio *Circasia*, Sector vacío, Uso *Residencial*, Estrato *3*, Ficha
   catastral vacía, **Registrar predio**. → PRD-00000001 queda seleccionado en *Predio*.
2. Elija Servicio *Acueducto*: el formulario avisa que el predio no tiene medidor y muestra el enlace
   **"Instálelo en la ficha del predio"**. Ábralo.
3. En la ficha del predio abra **Instalar medidor**: Servicio *Acueducto*, Serial `ACU-CIR-1001`, Marca
   `Itron`, Instalación `2026-08-01`, Lectura inicial `100`, **Instalar**. → Aparece en la tabla de medidores.
4. Otra vez **Instalar medidor**: Servicio *Gas*, `GAS-CIR-1001`, `Elster`, `2026-08-01`, `50`, **Instalar**.
5. Para Jorge haga lo mismo: predio `Carrera 14 # 5-20`, `La Pista`, Residencial, estrato 2 (PRD-00000002) y
   medidor de acueducto `ACU-CIR-1002`, `Itron`, `2026-07-01`, `200`.

### E3. Contratos (asesor)

1. Ficha de María > **Registrar contrato**: Predio *PRD-00000001*, Servicio *Acueducto*,
   **Fecha de vinculación `2026-08-01`** (viene con la de hoy: cámbiela; si deja hoy no podrá facturar los
   meses anteriores), Medidor *ACU-CIR-1001*, **Registrar contrato**. → CTR-00000001, **ACTIVO** de una vez
   (no hay paso de activación).
2. Igual con *Alcantarillado* (no pide medidor; exige el contrato de acueducto vigente) → CTR-00000002.
3. Igual con *Gas*, medidor *GAS-CIR-1001* → CTR-00000003.
4. Jorge: *Acueducto* (medidor ACU-CIR-1002) y *Alcantarillado*, vinculación **`2026-07-01`** → CTR-00000004 y
   CTR-00000005.

> Si intenta el alcantarillado **antes** que el acueducto, la base lo rechaza ("El alcantarillado requiere un
> contrato de acueducto vigente…") y **consume un número** de contrato: desde ahí sus CTR quedan corridos en 1.

### E4. Lecturas (asesor)

1. **Lecturas** > pestaña **Registro manual**: Serial `ACU-CIR-1001`, Periodo `202608`, Fecha de lectura
   `2026-09-01`, Lectura actual `115`, **Registrar**. → Consumo 15 m³, CONFIRMADA (la primera lectura de un
   medidor no se critica).
2. Registre las otras dos de gas de la tabla (202608 = 62, 202609 = 75). → 12 m³ y 13 m³, CONFIRMADAS.
3. `ACU-CIR-1001`, `202609`, `2026-10-01`, `160`. → **EN REVISIÓN**: 45 m³ contra un promedio de 15 m³
   (desviación > 35 %).
4. Pestaña **Cargue por archivo**: elija `lecturas-jorge.csv`, **Cargar**. → 3 registradas y 1 error:
   *"No existe un medidor con serial NO-EXISTE-01"*.
5. Pestaña **En revisión** > **Revisar** en la lectura de ACU-CIR-1001:
   - Marque *Estimar* sin observación y **Guardar revisión** → *"Explique en la observación por qué se
     rechaza la lectura"*.
   - Marque *Confirmar*, Observación `Se verificó en terreno: fuga reparada`, **Guardar revisión**. →
     CONFIRMADA, 45 m³, y desaparece de la bandeja.
6. Pestaña **Historial**: consulte `ACU-CIR-1001` y vea las dos lecturas.

### E5. Lotes, facturas y PDF (admin y asesor)

1. `admin` · **Lotes de facturación**: Periodo a facturar `202610`, **Generar lote** → rechazado: el periodo
   aún no ha cerrado.
2. `202604` → rechazado: supera el plazo legal de cobro (5 meses).
3. `202607` → lote COMPLETADO, 1 factura, $85.120. Luego `202608` (2 facturas, $217.540) y `202609`
   (2 facturas, $365.340). El orden importa para que la numeración coincida.
4. Repetir `202609` → rechazado, código `LOTE_EXISTENTE` (un lote completado no se repite).
5. **Facturas** > **Buscar**: aparecen las 5 facturas con vencimiento = hoy + 15 días. **Ver** la de María de
   202609: acueducto 129.125, alcantarillado 97.395, gas 49.300. **Descargar PDF** abre la factura.
6. `asesor` · **Facturas** > **Ver** FAC-202609-0000005 (Jorge) > **Anular**: motivo `corto` → *"El motivo
   debe tener entre 10 y 500 caracteres"*. Motivo `Error en la dirección del predio impresa en la factura`,
   marque **"Reexpedir de inmediato…"**, **Confirmar anulación**. → Queda ANULADA y se crea
   **FAC-202609-0000006** PENDIENTE por el mismo valor.

### E6. Portal del ciudadano: activación, consulta y pago en línea (María)

En una ventana de **incógnito**:

1. `http://localhost:4200/ingresar` > **Active su cuenta**. Tipo *CC*, número `1094123456`, **Enviar enlace a mi
   correo**. → Mensaje de que el enlace se envió al correo registrado.
2. **Dónde está el "correo"** (en desarrollo es simulado), ~30 segundos después:
   - En la consola del backend: `[CORREO SIMULADO] enlace: http://localhost:4200/cuenta/activacion?token=…`
     (requiere el backend con este commit; antes solo salía el asunto).
   - Siempre, en SQL Developer como SIGCA:
     ```sql
     SELECT DBMS_LOB.SUBSTR(CUERPO, 1500, 1) FROM NOTIFICACION
      WHERE ASUNTO LIKE '%active%' ORDER BY ID DESC FETCH FIRST 1 ROW ONLY;
     ```
3. Abra el enlace. *Nueva contraseña* y *Confirme la contraseña* `Portal.Maria26`, **Guardar contraseña**.
4. **Ingresar**, pestaña **Soy usuario**: *CC*, `1094123456`, `Portal.Maria26`. → Menú *Mis facturas, Mis
   contratos, Mis PQR*.
5. **Mis contratos** > **Consumos** en el acueducto → 15 m³ (202608) y 45 m³ (202609). El alcantarillado
   muestra la nota de que se liquida con el consumo del acueducto.
6. **Mis facturas**: FAC-202608-0000002 y FAC-202609-0000004. **Detalle** y **PDF** funcionan. Arriba indica
   la fecha estimada de la próxima factura (comienzos del mes siguiente).
7. **Pagar en línea** en FAC-202609-0000004 → página **"Pasarela de pagos (simulada)"** > **Rechazar pago** →
   resultado rechazado ("Fondos insuficientes (simulado)"); la factura sigue PENDIENTE.
8. **Pagar en línea** otra vez > **Aprobar pago** → **"Pago aprobado. Comprobante PAG-0000000001"**. En
   **Mis facturas** queda PAGADA; en **Mis pagos**, **Descargar** baja el comprobante PDF.

### E7. Pago presencial (asesor)

1. `asesor` · **Registrar pago**: referencia `FAC-202608-0000002`, **Buscar**. → Valor exigible $123.620.
2. Canal *Caja (presencial)*, **Registrar pago de $123.620** → comprobante PAG-0000000002 y
   **Descargar comprobante**. La factura queda PAGADA.
3. Buscar `FAC-202608-0000002` de nuevo → ya está pagada (no deja pagar dos veces).

### E8. PQR

1. **Radicación pública** (sin sesión): en `/ingresar` > **radicar una PQR**. Tipo *Petición*, sin contrato,
   Municipio *Circasia*, Descripción `Solicito información sobre el horario de atención y los requisitos para
   un nuevo punto de acueducto.`, Soportes: su PDF, datos de **Laura**, marque **Autorizo…**, **Radicar**. →
   *"Su solicitud quedó radicada con el número 202610-0001"*.
2. **Radicación pública de queja** (la que después vencerá): Tipo *Queja*, Número de contrato
   `CTR-00000001` (**Buscar contrato**), Circasia, descripción de 20+ caracteres
   (`El funcionario de la oficina no me atendió el turno asignado el lunes.`), datos de **María** →
   **202610-0002**. No la atienda.
3. **Radicación en el portal** (María, incógnito): **Mis PQR** > **Radicar nueva**, Tipo *Reclamo*, Contrato
   *CTR-00000001 · ACUEDUCTO*, Factura reclamada *Sin factura específica*, Descripción
   `El consumo de septiembre es tres veces mayor que el de agosto sin causa aparente.`, soporte PDF,
   **Radicar** → **202610-0003**.
4. **Gestor** (`gestor.circasia`) · **PQR** > **Buscar** > abra 202610-0003:
   - **Iniciar trámite** → EN_TRAMITE.
   - **Registrar actuación**: `Se programó visita técnica al predio.` > **Guardar actuación**.
   - **Evidencia de la respuesta**: suba su PDF.
   - **Responder**: texto de respuesta (20+ caracteres) > **Enviar respuesta** → RESPONDIDA.
5. `gestor.salento` · **PQR** → no ve ninguna de Circasia (otro municipio).
6. **Recurso** (María, portal): **Mis PQR** > en 202610-0003 **Presentar recurso**, marque **"Solicito también
   el recurso de apelación ante la Superintendencia…"**, descripción, **Radicar** → **202610-0004** (RECURSO).
7. `gestor.circasia` abre 202610-0004: **Iniciar trámite**, suba evidencia, **Decisión** *Confirma la
   decisión* → el botón cambia a **Responder y trasladar a la SSPD** → estado TRASLADADA_SSPD. Luego
   **Decisión de la Superintendencia** *Modifica la decisión* > **Registrar decisión** → RESPONDIDA.
8. `jefe.pqr` · **PQR** > abra 202610-0001 > **Reasignar**: elija *Julia Jefa PQR (Jefe de PQR…)*, motivo
   `Requiere respuesta de la jefatura`, **Reasignar**. → Cambia el responsable. (Si elige a Sara de Salento
   → la rechaza: no tiene competencia en Circasia.)
9. **Silencio positivo**: corra el ayudante DEV de la sección 5.1 (vence la 202610-0002). Luego `jefe.pqr` ·
   **PQR**, filtro **Silencio sin reconocer**, **Buscar** > abra 202610-0002 > **Reconocer el silencio
   administrativo positivo** > **Registrar reconocimiento**. → Queda registrado; un segundo intento lo
   rechaza (registro inmutable).
10. **Consulta pública**: `/ingresar` > **consultar su radicado**: radicado `202610-0001`, CC `41950123` →
    estado e historial. Con otra cédula → *no encontrada*.

### E9. Cartera: mora, cobro, suspensión, acuerdo y reconexión

1. **Poner a Jorge en mora** con el ayudante DEV de la sección 5.1 (si ya lo corrió en E8.9, no lo repita):
   FAC-202607-0000001 y FAC-202608-0000003 pasan a VENCIDA con 10 días de mora.
2. `cartera` · **Cartera** ("Cartera morosa") > **Filtrar** → Jorge: 2 facturas, 2 periodos, $179.040,
   10 días.
3. Las acciones aparecen una a la vez y en orden; **Ordenar suspensión** solo se ofrece después del aviso
   (por API, ordenarla antes responde 409 `ORDEN_NO_PROCEDENTE`).
4. **Iniciar cobro** → **Marcar candidato** → **Enviar aviso** → **Ordenar suspensión** (acepte cada
   confirmación). → Se crean 2 órdenes de suspensión (acueducto y alcantarillado).
5. `asesor` · **Órdenes** > **Filtrar** > **Ejecutar** > **Confirmar** en cada una → contratos de Jorge
   SUSPENDIDO.
   Abra uno de esos contratos en **Contratos**: en lugar del formulario "Ordenar reconexión" aparece *"Suspendido
   por mora: se reconecta automáticamente cuando el usuario paga la deuda o firma un acuerdo de pago"*. Por API,
   una reconexión manual responde 422 `RECONEXION_POR_MORA`.
6. `cartera` · **Cartera** > **Acuerdo de pago** (fila de Jorge): marque las 2 facturas, Cuota inicial
   `10000` → error (mínimo $17.904, el 10 %). Cuota inicial `20000`, Número de cuotas `3`, Tasa `8` →
   error (máximo 6 % residencial). Tasa `6`, **Simular** → plan con cuota 0 de $20.000 y 3 cuotas de
   $53.530,26. **Firmar acuerdo** → **AP-2026-000001**. Las facturas quedan EN_ACUERDO y se crean de
   inmediato 2 órdenes de **reconexión** (causa acuerdo).
7. `cartera` · **Registrar pago**: referencia `AP-2026-000001-01` → *"Las cuotas se pagan en orden: primero
   la cuota 0"*. Referencia `AP-2026-000001-00` (o `AP-2026-000001`, que cobra la siguiente), Canal
   *Recaudo bancario*, Referencia del recaudo `BCO-778812`, **Registrar pago de $20.000**.
8. `asesor` · **Órdenes** > **Ejecutar** las 2 reconexiones → contratos ACTIVO. (`cartera` no puede ejecutar
   órdenes.)

### E10. Gerente y administración

1. `gerente` · **Dashboard** ("Dashboard gerencial"), Periodo `202609`, **Consultar** → facturado $365.340
   (2 facturas vigentes), recaudo, acuerdos vigentes 1, PQR abiertas/vencidas, contratos activos, y la tabla
   por municipio (solo Circasia con datos).
2. `admin` · **Administración** > **Parámetros** > **Modificar** en `LECTURA_DESVIACION_PCT`: `40`,
   **Guardar** → guardado con su usuario y hora. Devuélvalo a `35`.
3. **Funcionarios** > **Crear funcionario** con los datos de Pedro (tabla 2), rol *Asesor*, **Guardar** → muestra
   una **contraseña temporal** una sola vez (cópiela) y la etiqueta *Clave temporal*.
4. **Salir** e ingresar como `pedro.ruiz` con la temporal → enrola el segundo factor → lo obliga a
   **Cambiar contraseña** (`corta` es rechazada; `Pedro.Ruiz2026` sirve) → entra con el menú del asesor.
5. **Auditoría**: **Buscar** → creación de usuarios, contratos, pagos, cambio del parámetro, creación del
   funcionario… (si la base está en UTC y son más de las 19:00, ponga *Hasta* = mañana; ver 1.1).

---

## 4. Pruebas negativas

Para ver el **código** de error del backend abra las herramientas del navegador (F12) > Red, y mire la
respuesta: `{"codigo": "...", "mensaje": "...", "errores": [...]}`. La pantalla muestra el mensaje.

### 4.1 Frontend (no deja enviar el formulario)

| Dónde | Dato | Mensaje |
|---|---|---|
| Registrar usuario | CC `0123456` | "La cédula debe tener entre 6 y 10 dígitos y no empezar por 0" |
| Registrar usuario | NIT `900123456-7` | "El dígito de verificación del NIT no es correcto" |
| Registrar usuario | Persona *Natural* con tipo *NIT* | "El NIT es solo para persona jurídica…" |
| Registrar usuario | Dirección `<b>Calle 1</b>` | "No se permiten etiquetas HTML ni caracteres de control" |
| Registrar usuario | Sin marcar la autorización | "Debe aceptar para continuar" |
| Registrar usuario | Teléfono `12345` | "El formato no es válido" (el backend, por API, responde 400 "Teléfono inválido: use 10 dígitos…") |
| Activar cuenta | Contraseña `abc` | "La contraseña necesita …" |

### 4.2 Backend (reglas de negocio; el mensaje aparece en pantalla)

| Dónde | Acción | Respuesta |
|---|---|---|
| Registrar usuario | María otra vez (CC 1094123456) | 409 `DUPLICADO` |
| Registrar usuario | CC `1094550000` | 422 `RNEC_NO_COINCIDE` |
| Predio | Uso *Comercial* con estrato | 400 "El estrato solo aplica al uso residencial" |
| Predio | Ficha catastral `123` | 400 "La ficha catastral debe tener exactamente 30 dígitos" |
| Instalar medidor | Serial `ACU-CIR-1001` de nuevo | 409 "Ya existe un medidor con ese serial" |
| Instalar medidor | Segundo medidor de acueducto activo en el mismo predio | 409 "El predio ya tiene el medidor activo …; retírelo primero" |
| Registrar contrato | Acueducto repetido en PRD-00000001 | 409 "Ya existe un contrato vigente para ese predio y servicio (RN-01)" |
| Registrar contrato | Gas en el predio de Jorge (sin medidor de gas) | 422 `SIN_MEDIDOR` |
| Lecturas | Registrar ACU-CIR-1001 202610 mientras la 202609 está en revisión | 422 "El medidor tiene una lectura en revisión; confírmela o recházela primero" |
| Lecturas | GAS-CIR-1001, 202610, `2026-10-14`, lectura `70` | 422 "La lectura actual (70) es menor que la anterior (75)" |
| Lecturas | GAS-CIR-1001, 202610, fecha `2026-08-15` | 400 "La fecha debe estar en el mes del periodo o en el siguiente" |
| Lecturas | GAS-CIR-1001, periodo `202607` | 422 "Lectura fuera de secuencia: el siguiente periodo del medidor es 202610" |
| Lotes | `202610` / `202604` | 422 `PERIODO_ABIERTO` / `PLAZO_LEGAL` |
| Facturas | Anular FAC-202609-0000004 (pagada) | 409 "Solo se anulan facturas PENDIENTES o VENCIDAS…" |
| Registrar pago | Referencia `FAC-202699-0000001` | 422 `REFERENCIA_INVALIDA` |
| Activación | Contraseña que contiene la cédula (`Maria.1094123456`) | 422 `PASSWORD_ES_IDENTIFICACION` |
| Activación | Abrir otra vez un enlace ya usado | 410 `ENLACE_INVALIDO` |
| PQR pública | *Queja* sin contrato | 400 "Una queja exige el número de contrato" |
| PQR pública | Contrato CTR-00000001 con la cédula de Laura | 422 `IDENTIFICACION_NO_COINCIDE` |
| PQR pública | Soporte `.txt` renombrado a `.pdf` | 422 `ADJUNTO_INVALIDO` |
| PQR (gestor) | Enviar respuesta sin evidencia (el botón se habilita solo con evidencia; por API) | 422 `SIN_EVIDENCIA` |
| PQR (gestor) | Recurso con apelación: *Confirma* pero responder sin trasladar (por API) | 409 `REQUIERE_TRASLADO` |
| PQR (jefe) | Reconocer silencio de una PQR que no ha vencido | 409 `SIN_SILENCIO` |
| PQR (gestor.salento) | Abrir por URL una PQR de Circasia | 403 "La PQR pertenece a otro municipio" |
| Cartera | Acuerdo con tasa 8 % | 422 "La tasa máxima aplicable es 6 %…" |
| Contrato | **Cancelar contrato** de Jorge con deuda / **Inactivar usuario** Jorge | 422 `OBLIGACIONES_PENDIENTES` |
| Parámetros | `MORA_PERIODOS_SUSPENSION` = `5` | 422 `DATO_INVALIDO` (CK_PARAMETRO_MORA_PERIODOS) |
| Funcionarios | Crear `FUN-0100` otra vez | 409 "El código de funcionario ya existe" |
| Cualquiera | `gerente` abre `/admin`; `asesor` abre `/dashboard` | 403 `ACCESO_DENEGADO` |

### 4.3 Oracle (triggers y restricciones, directo en SQL Developer como SIGCA)

Ejecute cada sentencia **sola** y después `ROLLBACK;`. Todas deben fallar:

```sql
UPDATE FACTURA SET ESTADO = 'PAGADA' WHERE NUMERO_FACTURA = 'FAC-202609-0000006';   -- ORA-20016: pagada solo con un pago
UPDATE CONTRATO SET ESTADO = 'SUSPENDIDO' WHERE NUMERO_CONTRATO = 'CTR-00000001';  -- ORA-20020: suspender solo con orden
DELETE FROM PQR WHERE NUMERO_RADICADO = '202610-0001';                             -- ORA-20006: no se borra
UPDATE PQR SET ESTADO = 'RESPONDIDA' WHERE NUMERO_RADICADO = '202610-0001';        -- ORA-20001: transición inválida
UPDATE LECTURA SET LECTURA_ACTUAL = 999 WHERE ROWNUM = 1;                          -- ORA-20006: registro inmutable
UPDATE PARAMETRO_SISTEMA SET VALOR = '5' WHERE CLAVE = 'MORA_PERIODOS_SUSPENSION'; -- ORA-02290: CK_PARAMETRO_MORA_PERIODOS
ROLLBACK;
```

---

## 5. Ayudantes SQL **SOLO DESARROLLO**

> **Nunca** los ejecute en una base de pruebas compartida ni en producción. Simulan el paso del tiempo.

### 5.1 `dev/simular-vencimientos-dev.sql` (mora y silencio positivo)

Las facturas pasan a VENCIDA solo con el proceso nocturno de las 00:30 y su fecha de vencimiento es
inmutable; las PQR vencen con el proceso de las 00:10/08:10/14:10. Este script corre las fechas hacia atrás
(desactiva el trigger un instante solo para eso) y hace el cambio de estado con el trigger activo.

1. Abra el archivo en SQL Developer, conexión **SIGCA**.
2. Revise las constantes: `c_usuario` (`USR-00000002`, Jorge), `c_hasta` (`202608`), `c_dias_mora` (`10`),
   `c_radicado` (`202610-0002`, o `NULL` para no tocar PQR).
3. Ejecute con **F5**. Salida esperada:
   ```
   2 facturas de USR-00000002 quedaron VENCIDAS el 2026-10-04
   PQR 202610-0002 VENCIDA con silencio positivo
   ```
   (la fecha es hoy − 10 días).

Requisito: correrlo **después** de los lotes 202607 y 202608 y **antes** de que Jorge pague.

### 5.2 Ver el enlace de activación o de recuperación de contraseña

```sql
SELECT ID, ASUNTO, DBMS_LOB.SUBSTR(CUERPO, 1500, 1) FROM NOTIFICACION ORDER BY ID DESC FETCH FIRST 5 ROWS ONLY;
```

---

## 6. Solución de problemas

| Síntoma | Causa y remedio |
|---|---|
| Auditoría vacía, horas 5 h adelantadas, comprobante con fecha de mañana | Oracle en UTC. Ver 1.1. |
| "Código de verificación inválido" con el código correcto | Reloj de Windows o del celular desfasado: sincronice la hora (Configuración > Hora > Sincronizar ahora). |
| Perdió el autenticador de un funcionario | `admin` > **Funcionarios** > **Reiniciar 2FA** en ese funcionario; enrola de nuevo en el próximo ingreso. |
| No llega el correo de activación | No hay correo real: ver E6.2 o 5.2. Espere ~30 s (el envío es en segundo plano). |
| Lote rechazado `PERIODO_ABIERTO` / `PLAZO_LEGAL` | Solo meses cerrados y hasta 5 meses atrás. Ajuste los periodos al mes en que prueba. |
| El lote sale con 0 facturas | Contratos con vinculación posterior al periodo (quedó la fecha de hoy) o lecturas sin registrar / en revisión. |
| "Lectura fuera de secuencia" | Los periodos de un medidor van seguidos desde el mes de instalación. |
| Cartera no muestra a Jorge | Las facturas no están VENCIDAS (se requieren 2 periodos): corra 5.1. |
| El contrato de alcantarillado no se crea | Primero el acueducto del mismo predio. |
| 401 al volver tras un rato | La sesión dura 15 min; vuelva a ingresar. |
| 429 "Demasiadas solicitudes" | Límite por minuto; espere un minuto. |
| La cuota no se deja pagar | Se pagan en orden, empezando por la cuota 0 (`AP-…-00`). |
| El acuerdo no reconecta | Las órdenes de reconexión se crean al **firmar** y las ejecuta el asesor en **Órdenes**. |

---

## 7. Observaciones conocidas

- El texto del error al responder una PQR sin iniciar trámite dice "No se puede responder de una PQR
  RECIBIDA" (redacción).
