# E3: acceso persistente del dispositivo de la persona usuaria

Dominio separado de la sesión del cuidador. `Dispositivo` conserva pertenencia, estado ACTIVO/DESVINCULADO y fechas. `credencial_dispositivo` contiene únicamente el hash SHA-256 de un secreto aleatorio de 256 bits (`du_…`), una FK única al dispositivo y la fecha límite de preparación. No almacena el código del perfil ni el secreto en claro.

La credencial pendiente dura 10 minutos según el reloj de BD. Tras vincularse no caduca periódicamente: deja de autorizar al desactivar el perfil o desvincular el dispositivo. Cada petición autentica esos estados. No se utilizan OTP, sesiones Bearer del cuidador ni IDs internos como credencial. Las APIs del usuario derivan el perfil de `AccesoDispositivoService.Identidad`, autorizan expresamente ROLE_USUARIO y mantienen la comprobación de estado. Están disponibles [agenda/detalle](agenda-usuario-http.md) y [ejecución diaria](ejecucion-usuario-http.md); las demás rutas no autorizadas se deniegan.

## Contrato HTTP

Todas las respuestas exitosas tienen `Cache-Control: no-store`. No registrar cuerpos, códigos ni Authorization en aplicaciones, proxies o herramientas de seguimiento. HTTPS es obligatorio fuera de la prueba LAN de desarrollo.

1. **POST `/api/dispositivos/preparar-vinculacion`**, público, sin body → **201** `{ "credencialDispositivo": "du_…" }`. No comprueba ningún código y todavía no crea un Dispositivo. El móvil guarda el secreto en SecureStore **antes** del siguiente POST. Si se pierde esta respuesta solo caduca una preparación sin dispositivo.
2. **POST `/api/dispositivos/vincular`**, `Authorization: Bearer du_…`, body `{ "codigoVinculacion": "DF-7KQ9W6H2M8V3X5RA", "nombreDispositivo": "Android" }` → **201**:

   ```json
   { "estado": "VINCULADO", "perfil": { "idPerfilUsuario": 12, "nombre": "Persona", "avatar": null } }
   ```

   El nombre del dispositivo es el modelo técnico (máximo 80 caracteres), sin identificadores hardware ni nombre personal. El perfil se resuelve únicamente por código dentro de esta operación. No hay endpoint de comprobación previa de existencia. El frontend solo valida formato.
3. **GET `/api/dispositivos/vinculacion`**, mismo Bearer → **200**, respuesta anterior o `{ "estado": "PENDIENTE", "perfil": null }`. Permite restaurar y recuperar un POST de resultado desconocido; **no se reenvía automáticamente el POST**. El secreto ya guardado no cambia al vincular.
4. **DELETE `/api/dispositivos/vinculacion`**, Bearer pendiente → **204**. Cancela exclusivamente una preparación. Una credencial vinculada obtiene 403, también después de bloquear/releer en servicio. Salir del perfil es ahora una pausa local, no desvinculación. Véase [salida local y reconexión](agenda-usuario-http.md#salida-local-y-reconexión) para `credencialesPrevias` y el secreto canónico opcional en la respuesta de vincular.

Errores ProblemDetail con `codigo`:

| HTTP | Código | Significado público |
| --- | --- | --- |
| 422 | CODIGO_VINCULACION_INVALIDO | Formato inválido, inexistente, renovado o perfil no ACTIVO: misma respuesta. |
| 401 | VINCULACION_NO_VALIDA | Credencial inválida/revocada, preparación caducada o perfil no disponible. |
| 409 | DISPOSITIVO_YA_VINCULADO | La credencial ya está asociada; consultar GET, nunca reasignarla. |
| 400 | SOLICITUD_INVALIDA | Body o nombre de dispositivo incorrectos. |
| 429 | LIMITE_VINCULACION | Esperar `Retry-After`; no reintentar automáticamente. |
| 503 | SERVICIO_NO_DISPONIBLE | Estado técnico o comprobaciones de arranque pendientes. |

Crear Dispositivo y activar la credencial es una transacción READ_COMMITTED. Se bloquea/relee primero el perfil y luego la credencial. E1 renueva bajo el mismo bloqueo de perfil: si la renovación gana, el código anterior se rechaza; si vincular gana, ese dispositivo queda vinculado y la renovación posterior no lo revoca. La credencial y FK únicas impiden crear dos dispositivos con el mismo secreto. Credenciales distintas pueden vincularse al mismo perfil, como requiere el producto. Reutilizar el código para otro dispositivo es intencional; no es una capacidad de un solo uso. La repetición con una credencial ya vinculada devuelve 409.

Los POST tienen límite compartido por dirección remota: 10/minuto y 60/hora, más 300/minuto por proceso. Propiedades `diafacil.dispositivos.limite.minuto`, `.hora`, `.global`. Mapa limitado a 10.000 orígenes y limpieza temporal; no se confía en X-Forwarded-For recibido del cliente. Despliegues detrás de proxy deben fijar el origen real únicamente desde proxies de confianza. En varias réplicas se necesita un limitador compartido antes de exponer el servicio. Limpieza horaria elimina exclusivamente preparaciones caducadas sin Dispositivo.

## Activación y transición local segura

La transición siguiente es un procedimiento para una **BD existente que lo necesite**,
no una tarea de cada arranque. Durante su implementación no se ejecutó sobre la
BD local desde el entorno del agente; posteriormente el usuario confirmó E3
funcionando físicamente. No repetirla por seguir una nota histórica, ni afirmar
una nueva inspección MySQL desde esta documentación.

La configuración general mantiene E3 desactivado (`diafacil.dispositivos.enabled=false`),
mientras que la plantilla local incluye `true`. El perfil `local` debe estar activo.
La guarda exige esquema válido y ausencia de códigos legacy. E1 frontend también
espera el formato vigente. En otra BD, auditar primero y aplicar solo si corresponde.

1. Hacer copia de seguridad y detener otras instancias/escrituras. No ejecutar el SQL v7 completo en una BD existente. Para BD nueva, Hibernate crea el modelo; para existente, `ddl-auto=update` puede crear la tabla nueva compatible. Alternativamente revisar/aplicar **solo** [el DDL aditivo](sql/e3-credencial-dispositivo.sql). Si la tabla ya existe, inspeccionarla; el script no la reemplaza ni corrige automáticamente.
2. Con el backend detenido, ejecutar su clase principal desde el IDE usando la conexión local habitual y estos **Program arguments** (no VM options):

   ```text
   --spring.main.web-application-type=none
   --spring.jpa.hibernate.ddl-auto=none
   --diafacil.datos-base.enabled=false
   --diafacil.dispositivos.enabled=false
   --diafacil.dispositivos.codigos.transicion=audit
   ```

   El comando informa total, actuales, legacy, inesperados, duplicados (ocurrencias repetidas) y `unique_codigo`, sin mostrar los códigos. Comprueba el índice UNIQUE de una sola columna `codigo_vinculacion` mediante metadatos JDBC, sin DDL; admite un índice equivalente a `uq_perfil_codigo`. Formatos inesperados, duplicados o falta de UNIQUE detienen la operación para revisión, sin reparar automáticamente nada.
3. Revisado el resultado y con copia de seguridad, ejecutar los mismos argumentos cambiando únicamente `audit` por `apply`. Una sola transacción bloquea y relee los perfiles y sustituye **solo** `DF-` + seis dígitos. Reutiliza exactamente `GeneradorCodigoPerfil` de E1: SecureRandom, alfabeto aprobado y 16 símbolos. Comprueba candidatos existentes y reservados en esta operación, con hasta tres candidatos por código; UNIQUE sigue siendo la autoridad final. Una colisión no resuelta, un fallo de UNIQUE o cualquier comprobación fallida revierte toda la transacción, sin reintentar dentro de una transacción abortada. Conserva IDs, nombres, estados, actividades, configuraciones, dispositivos y relaciones. No hay limpieza destructiva, seed ni ejecución automática de esta transición.
4. Antes del commit se hace flush/relectura, se comprueba la conservación exacta de fechas y códigos previamente vigentes y se auditan de nuevo formato, unicidad y cantidad total. Deben quedar legacy=0, inesperados=0, duplicados=0 y `unique_codigo=true`. Tras el commit el comando informa cantidades otra vez. Repetir `audit` debe confirmar esos resultados; repetir `apply` es idempotente (cambiados=0). Detener el proceso de transición y retirar esos argumentos.
5. Arrancar normalmente con el perfil local, que ya configura `diafacil.dispositivos.enabled=true`. Fuera de ese perfil se requiere activación explícita equivalente. Se verifica lectura del modelo, UNIQUE token_hash, UNIQUE id_dispositivo, FK y ausencia de legacy antes de habilitar emisión/autenticación. Si faltan, arranque falla y el servicio no emite credenciales. No se reparan datos automáticamente. Para BD nueva usar el mismo control, que debe encontrar cero legacy.

### Regresión corregida: preparación respondía como recurso estático

El controller y servicio estaban condicionados a `diafacil.dispositivos.enabled=true`, pero ninguna configuración local lo activaba. Security permitía pasar el POST público de preparación aunque el servicio no existiera; MVC acababa intentando resolver un recurso estático. La corrección activa E3 explícitamente en local y comprueba disponibilidad del servicio antes de permitir continuar la preparación pública. Con E3 desactivado responde 503 `SERVICIO_NO_DISPONIBLE`. La preparación sigue sin requerir una sesión del cuidador ni comprobar existencia del código.

Las pruebas HTTP cubren ahora la configuración desactivada real, sin simular un servicio que debería estar ausente, además de la configuración activada. Se conserva el flujo preparar → guardar secreto pendiente → consultar/vincular → restaurar, sin cambios visuales ni nuevas rutas.

Una transición puede permanecer viva por recursos del contexto no-web; se puede detener después del mensaje final. Nunca arrancar en modo web con el argumento de transición. Los argumentos de conexión/secretos continúan en la configuración privada existente.

### Fecha de renovación y resultado esperado para la BD confirmada

La transición es técnica y **no escribe `fecha_renovacion_codigo`**: NULL sigue NULL y cualquier fecha histórica se conserva. No representa una solicitud del cuidador ni inicia un cooldown nuevo. La renovación normal de E1 sigue estableciendo su fecha real y aplicando 60 segundos, sin cambios en su política.

Antes de la transición el usuario confirmó seis perfiles activos: cinco legacy y
uno vigente. Esas cantidades fueron una fotografía histórica, no una condición
actual ni un dato que deba fijarse en código. La auditoría incluye toda la tabla,
también inactivos, como la guarda E3. Para aquella fotografía se esperaban cinco
cambios y conservación exacta del código/fecha ya vigentes. En otra BD/reanudación
usar las cantidades reales; no forzar total=6 ni cambiados=5.

El mensaje de comprobación de códigos superada confirma solo ese requisito. El arranque normal de E3 continúa verificando por separado tabla, UNIQUE y FK de credenciales. No se desactiva ni se relaja su guarda. Este ajuste de código y documentación **no ha ejecutado la migración sobre la BD local**.

## Validación

Pruebas de servicio, cadena Security/MockMvc, límites, esquema y comando de transición no necesitan BD local. `DispositivoMySqlTest` es opt-in con `DIAFACIL_TEST_MYSQL_URL`, `DIAFACIL_TEST_MYSQL_USER`, `DIAFACIL_TEST_MYSQL_PASSWORD`: exige una BD de pruebas existente `diafacil_test_*`, sin DDL. Comprueba transacción/rollback, concurrencia con el mismo secreto, múltiples dispositivos, conteo, renovación y revocación aislada. Nunca apuntarlo a la BD de desarrollo. La prueba limpia exclusivamente sus fixtures.

E2 sigue pendiente; ejecución de actividades ya está implementada en su módulo.
Salir del perfil conserva el dispositivo ACTIVO y pausa solo esta instalación.

### Resultado histórico de validación de E3, previo a Mi agenda

Java 21 + JUnit Platform aislado: **260 pruebas correctas, 7 pruebas MySQL omitidas** (267 descubiertas). Incluye configuración HTTP activada/desactivada, consultas HQL compiladas contra el modelo real sin JDBC, guarda de arranque transaccional, metadatos, auditoría/transición idempotente, conservación de fechas, colisiones, rollback, límites, autenticación y HTTP. El runner excluye `BackendApplicationTests`, que requiere el contexto/datasource real. Maven Wrapper no pudo ejecutar la suite por el fallo preexistente de su script PowerShell; se reutilizaron las dependencias disponibles en `target/isolated-test-libs`. javac terminó con código 0 y generó las clases, aunque el entorno emitió un diagnóstico de acceso al cerrar un archivo ZIP. Esto no sustituye las pruebas de integración MySQL ni la prueba física pendiente.

Frontend: TypeScript y lint correctos, **128 pruebas correctas** y export Android correcta. No se ejecutó DDL, transición de códigos ni cambio de configuración sobre la BD local. Los scripts y documentación quedan preparados para revisión/ejecución explícita en ese entorno.

En la revisión funcional se activó E3 en la copia privada de configuración local, sin arrancar Hibernate ni aplicar escrituras sobre la BD. La auditoría directa de solo lectura se intentó nuevamente, pero terminó en `CommunicationsException`; no se pudo confirmar desde este entorno el esquema ni las cantidades de códigos legacy. Antes de la prueba física, reiniciar el backend con el perfil local y comprobar que la guarda de arranque finalice correctamente.
