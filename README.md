# Backend DiaFacil

El contrato de los endpoints de autenticacion y registro, sus DTOs y
respuestas HTTP esta documentado en [docs/auth-http.md](docs/auth-http.md).
Para validar esta capa sin JDBC ni SMTP real:

```powershell
.\mvnw.cmd '-Dtest=AutenticacionCuidadorControllerTest,SecurityConfigurationTest,AutenticacionConfigurationTest,EnvioCodigoAccesoSmtpTest,AutenticacionCuidadorServiceTest,AutenticacionCuidadorTransaccionalServiceTest,CriptografiaAutenticacionTest,AutenticacionTransaccionesTest,AuthenticationRepositoryTest,PersistenceModelTest' test
```

Las pruebas MVC conservan los filtros de seguridad reales y simulan el Service.

Backend con Spring Boot 4.1.1, Java 21, Spring Data JPA y MySQL Connector/J.
Las dependencias necesarias ya estan incluidas en `pom.xml`.

## Requisitos

- JDK 21 y `JAVA_HOME` configurado.
- MySQL 8.0.16 o superior iniciado, normalmente en `localhost:3306`.
- Usuario MySQL existente con permisos para conectarse, crear `diafacil` y
  ejecutar las operaciones DDL que Hibernate necesite.
- Acceso a las dependencias de Maven en el primer arranque. El proyecto incluye
  Maven Wrapper; no requiere instalar Maven por separado.

## Configuracion local

Ejecutar todos los comandos de esta guia desde `backend_dia_facil`.

1. Si no existe `application-local.properties`, copiar la plantilla:

   ```powershell
   Copy-Item application-local.example.properties application-local.properties
   ```

2. Editar exclusivamente la copia local. Configurar el usuario MySQL y sustituir
   el placeholder de la contrasena manualmente. El archivo local preparado usa
   `root`; la plantilla utiliza placeholders para ambos campos. No compartir
   este archivo ni sus valores.
3. Activar el perfil y arrancar:

   ```powershell
   $env:SPRING_PROFILES_ACTIVE = "local"
   .\mvnw.cmd spring-boot:run
   ```

En Linux/macOS, usar `SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run`.
En el IDE, establecer `backend_dia_facil` como directorio de trabajo y activar
el perfil `local`. Para ejecutar un JAR, mantener ese directorio de trabajo y
pasar `--spring.profiles.active=local`.

Spring Boot combina `src/main/resources/application.properties` con el archivo
externo `application-local.properties`. No carga la plantilla automaticamente.
El perfil local no esta activado por defecto. Los archivos locales estan fuera
de los recursos Maven para que las credenciales no se empaqueten en el JAR.
El `.gitignore` raiz excluye la copia local y permite versionar la configuracion
general y la plantilla. Ignorar un archivo en Git no protege archivos ya
versionados ni sustituye la revision de secretos antes de publicar.

## Creacion de la base y del esquema

La URL JDBC incluye `createDatabaseIfNotExist=true`: Connector/J intenta crear
`diafacil` si no existe y el usuario tiene permisos. No instala MySQL ni crea
usuarios. Mientras la contrasena sea un placeholder, la conexion no sera valida.

El SQL de referencia define `utf8mb4` y `utf8mb4_unicode_ci`. La creacion con
Connector/J puede heredar los valores predeterminados del servidor. Alinear
esos valores en los entornos locales y verificar posteriormente el charset y
la collation de la base y las tablas. Esta configuracion no modifica MySQL.

En `local`, `spring.jpa.hibernate.ddl-auto=update` permite crear o actualizar
tablas a partir de las 21 entidades implementadas. Sobre una base vacia,
Hibernate genera el esquema sin ejecutar manualmente el SQL de referencia.
Las tablas fijan utf8mb4/utf8mb4_unicode_ci y usan InnoDB; los defaults de la
base se deben comprobar por separado.
Fuera de `local`, `ddl-auto=none` impide la generacion del esquema.

Flyway permanece como dependencia, pero esta desactivado. Tambien esta
desactivada la inicializacion SQL de Spring Boot. No se debe copiar ni ejecutar
el script de referencia como `schema.sql`, `data.sql` o una migracion en esta
etapa.

Los datos base funcionales se inicializan automáticamente después de JPA desde
`src/main/resources/db/datos-base.json`. Está habilitado en todos los entornos por
defecto; `--diafacil.datos-base.enabled=false` lo deshabilita. No ejecuta data.sql
ni scripts de migración, y no modifica datos de usuarios.

**BD existente, antes del primer arranque de esta versión con update:** auditar
duplicados de posiciones de PlantillaPaso y aplicar la transición UNIQUE revisada.
Ver [inicialización y transición segura](docs/datos-base.md). Deshabilitar solo
el inicializador no deshabilita el DDL de Hibernate.

`update` es apropiado para el desarrollo inicial, pero no garantiza que bases
con historiales diferentes converjan. No gestiona de forma fiable renombrados,
eliminaciones ni transformaciones de datos; tampoco crea triggers o datos
iniciales por el solo hecho de mapear entidades. La evolucion reproducible se
abordara posteriormente mediante Flyway y `ddl-auto=validate`.

## Modelo de persistencia

Consultar [el modelo JPA](docs/persistence.md) para el inventario de las 21
tablas, relaciones, restricciones, diferencias de DDL y reglas pendientes.
No hay relaciones bidireccionales ni cascadas JPA. Se conservan las acciones
SQL de borrado y los dos CHECK mediante anotaciones Jakarta/Hibernate.

Iconos 3D de actividades y pictogramas de pasos tienen catálogos separados.
La [guía de transición](docs/iconos-actividad.md) describe expansión Hibernate,
seed manual de 52 códigos, traslado y limpieza con autorización separada.
No se ejecutan estos scripts al iniciar la aplicación.

El limite acordado es cuatro perfiles activos por cuidador y corresponde a
un futuro servicio con control de concurrencia. Las entidades no implementan
autenticacion, OTP, vinculacion, carga inicial ni otras operaciones funcionales.

## Verificacion

Sin credenciales reales, revisar ubicaciones, placeholders y exclusiones, y
comprobar que el JAR no contiene `application-local.properties`.
Para probar el mapeo sin MySQL y empaquetar sin ejecutar contextLoads:

```powershell
.\mvnw.cmd -B -ntp -Dtest=PersistenceModelTest package
```

Antes de inicializar Git, revisar las reglas del `.gitignore` raiz y de los
subdirectorios: solo la copia local debe quedar excluida. `git check-ignore`
requiere un repositorio, incluso con `--no-index`, por lo que esa comprobacion
queda pendiente hasta que se inicialice Git en una tarea posterior.

Cuando exista el repositorio, ejecutar desde la raiz de DiaFacil:

```powershell
git check-ignore --no-index backend_dia_facil/application-local.properties backend_dia_facil/application-local.example.properties backend_dia_facil/src/main/resources/application.properties
```

El resultado esperado es solo `backend_dia_facil/application-local.properties`.
Revisar tambien las reglas existentes en los subdirectorios; no ejecutar
`git init`, commits, push ni configurar remotos como parte de esta tarea.

Despues de completar manualmente las credenciales:

1. Arrancar contra una instancia desechable donde `diafacil` no exista.
2. Comprobar creacion de la base, conexion y carga del contexto.
3. Ejecutar las pruebas con el mismo perfil:

   ```powershell
   $env:SPRING_PROFILES_ACTIVE = "local"
   .\mvnw.cmd test
   ```

4. Comprobar fallos ante MySQL detenido, credenciales invalidas o permisos
   insuficientes, sin divulgar credenciales en los resultados.

La prueba `contextLoads()` necesita MySQL y credenciales validas; por si sola
no comprueba todas las restricciones. Spring Security esta incluido y un HTTP 401 no
implica por si mismo un fallo de conexion a MySQL.
Las pruebas de metadatos generan `target/jpa-schema.sql` sin ejecutar SQL ni
abrir conexiones JDBC. La verificacion real en una instancia MySQL desechable
esta detallada en [la guia de persistencia](docs/persistence.md).

## Correo OTP por SMTP

El adaptador `EnvioCodigoAccesoSmtp` implementa el contrato de envio de OTP.
Su bean se activa cuando existe `spring.mail.host`, independientemente del
perfil. Sin esa propiedad no se registra el adaptador. La configuracion
presente utiliza Gmail exclusivamente para desarrollo local; no define
infraestructura de produccion.

`spring-boot-starter-mail` ya esta incluido. Spring Boot configura
`JavaMailSender`; no se necesitan dependencias adicionales. El ensamblado
de los Services se activa separadamente mediante `diafacil.auth.enabled`,
como se explica en la seccion de autenticacion. Configurar SMTP por si solo
no activa ese modulo.

Para preparar Gmail:

1. Usar preferiblemente una cuenta dedicada a pruebas y activar la
   verificacion en dos pasos.
2. Crear una [contrasena de aplicacion](https://support.google.com/accounts/answer/185833?hl=en)
   identificada como DiaFacil local. Algunas cuentas organizacionales o con
   Proteccion Avanzada no permiten esta opcion; en ese caso detener la
   configuracion, sin utilizar la contrasena principal de Google.
3. La plantilla usa las variables `DIAFACIL_GMAIL_USERNAME` (direccion
   completa) y `DIAFACIL_GMAIL_APP_PASSWORD` (contrasena de aplicacion).
   La copia privada local puede guardar directamente los valores reales de
   `spring.mail.username` y `spring.mail.password`, sin depender de variables
   de entorno. No escribirlos en scripts, comandos versionados ni en el chat.
4. Si ya existe `application-local.properties`, conservar su configuracion y
   agregar solo el bloque SMTP de `application-local.example.properties`.
   No reemplazar una copia privada ya configurada con la plantilla.
   Si no existe, seguir las instrucciones de configuracion local anteriores.
5. Activar el perfil local como se indica arriba. El remitente sera la misma
   cuenta SMTP, con nombre visible DiaFacil. Si se conservan las referencias
   de la plantilla, se requieren ambas variables; no hay credenciales de respaldo.

La configuracion usa `smtp.gmail.com:587` con STARTTLS obligatorio,
autenticacion, validacion de identidad del servidor y timeouts de conexion,
lectura y escritura de 5 segundos. No se modifica la confianza TLS del JDK.
La codificacion es UTF-8 y las pruebas de conexion al arrancar estan
desactivadas: el arranque no comprueba las credenciales ni envia correo.

El mensaje es de texto plano, contiene el OTP de seis digitos y aclara que
vence diez minutos despues de solicitarlo. El codigo no aparece en el asunto.
El adaptador no reintenta ni oculta errores de autenticacion, conexion o envio.
Errores de preparacion se propagan como `MailPreparationException`.
Una aceptacion SMTP no garantiza entrega en la bandeja de entrada.

El Service envia despues del commit. Un error SMTP no deshace la emision
persistida y no recupera codigos anteriores. No registrar el OTP, las
credenciales, el cuerpo del correo ni volcar mensajes fallidos contenidos
en excepciones de Spring Mail. SMTP debug permanece desactivado.
La contrasena de aplicacion debe revocarse cuando deje de utilizarse y
actualizarse si Google la revoca al cambiar la contrasena de la cuenta.

Para ejecutar las pruebas de correo, autenticacion y metadatos sin SMTP
real ni JDBC, desde `backend_dia_facil`:

```powershell
.\mvnw.cmd '-Dtest=AutenticacionConfigurationTest,EnvioCodigoAccesoSmtpTest,AutenticacionCuidadorServiceTest,AutenticacionCuidadorTransaccionalServiceTest,CriptografiaAutenticacionTest,AutenticacionTransaccionesTest,AuthenticationRepositoryTest,PersistenceModelTest' test
```

Las pruebas usan `JavaMailSender` simulado y contextos limitados a correo.
No cargar el contexto completo ni ejecutar `contextLoads` para esta validacion.
No se realizaron envios reales ni se validaron las credenciales de Gmail.

## Ensamblado de autenticacion

`AutenticacionConfiguration` registra los dos Services, la criptografia,
`autenticacionClock` y `autenticacionSecureRandom` cuando
`diafacil.auth.enabled=true`. Por defecto el modulo esta desactivado; la
plantilla local lo activa. Esta activacion no depende de un nombre de perfil.

La configuracion compartida fija `diafacil.auth.zone=America/Lima`.
Se puede ajustar explicitamente la zona, que debe coincidir con la sesion
MySQL que genera los DATETIME. Esa coincidencia sigue pendiente de
validacion real; esta tarea no modifica la configuracion JDBC ni el esquema.

En la copia privada `application-local.properties` pueden guardarse los
secretos directamente, sin variables de entorno:

```properties
diafacil.auth.enabled=true
diafacil.auth.otp-secret=REEMPLAZAR_POR_BASE64_PRIVADO_OTP
diafacil.auth.registration-secret=REEMPLAZAR_POR_BASE64_PRIVADO_REGISTRO
```

Estos son placeholders, no claves utilizables. Generar independientemente
al menos 32 bytes aleatorios con un generador criptografico para cada clave
y codificarlos con Base64 estandar. Conservar los dos valores en un lugar
privado y reutilizarlos entre reinicios; no generar claves al arrancar.
La plantilla del equipo conserva referencias a `DIAFACIL_AUTH_OTP_SECRET`
y `DIAFACIL_AUTH_REGISTRATION_SECRET`, sin valores reales.

La copia privada sigue ignorada por Git. No reemplazar sus claves o
credenciales existentes al actualizar la plantilla ni compartir su contenido.
No publicar secretos en mensajes, logs, scripts o archivos versionados.
El arranque rechaza claves ausentes, vacias, con Base64 invalido, con menos
de 32 bytes decodificados o iguales, sin incluir sus valores en los errores.
Cambiar la clave OTP invalida hashes anteriores; cambiar la clave de registro
invalida los comprobantes anteriores.

Al activar autenticacion tambien se requieren ambos repositories, un gestor
transaccional existente y un `EnvioCodigoAcceso` configurado. Spring Boot/JPA
proporciona el gestor y el proxy del Service transaccional; la fachada recibe
ese proxy por inyeccion. Se conservan REQUIRED y el aislamiento predeterminado.
No hay adaptadores de respaldo ni excepciones funcionales para ocultar una
configuracion incompleta. Tener el modulo desactivado no desactiva la
autoconfiguracion JPA o SMTP del resto de la aplicacion.

`AutenticacionConfigurationTest` prueba el ensamblado con contextos Spring
acotados, claves ficticias, repositories y envio simulados y un gestor sin
JDBC. Comprueba el proxy, commit/rollback, envio posterior al commit y fallos
de configuracion. No lee secretos de la copia privada ni envia correos reales.
