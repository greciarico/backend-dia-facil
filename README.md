# Backend DiaFacil

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
tablas a partir de las entidades. Actualmente no existen entidades: esta etapa
solo prepara la conexion y la creacion de la base, no las 17 tablas del SQL.
Fuera de `local`, `ddl-auto=none` impide la generacion del esquema.

Flyway permanece como dependencia, pero esta desactivado. Tambien esta
desactivada la inicializacion SQL de Spring Boot. No se debe copiar ni ejecutar
el script de referencia como `schema.sql`, `data.sql` o una migracion en esta
etapa.

`update` es apropiado para el desarrollo inicial, pero no garantiza que bases
con historiales diferentes converjan. No gestiona de forma fiable renombrados,
eliminaciones ni transformaciones de datos; tampoco crea triggers o datos
iniciales por el solo hecho de mapear entidades. La evolucion reproducible se
abordara posteriormente mediante Flyway y `ddl-auto=validate`.

## Referencia para la futura tarea de entidades

Usar `../documentacion-dia-facil/scrip_diafacil_v7.sql` y la documentacion
funcional para preservar las 17 tablas, columnas, nulabilidad, tipos, claves,
indices, restricciones y relaciones. Revisar especialmente la configuracion
1:1 con clave compartida, la FK compuesta de notificaciones, las unicidades de
ejecucion y progreso, los OTP sin FK a cuidador y la independencia de plantillas
y actividades reales. Las cascadas de JPA no equivalen automaticamente a
`ON DELETE CASCADE` o `ON DELETE SET NULL`.

Resolver antes de implementar los comportamientos afectados:

- La documentacion limita a cuatro perfiles; el SQL habla de cuatro activos.
  El trigger solo comprueba inserciones, no reactivaciones, y puede bloquear
  inserciones inactivas cuando ya hay cuatro activos.
- El modelo conceptual incluye el perfil en la ejecucion; el SQL lo obtiene
  mediante la actividad.
- La documentacion exige una configuracion por perfil; la FK no obliga a crear
  esa fila.

El trigger, la validacion del limite con concurrencia, los datos iniciales y la
coherencia de solicitudes de ayuda requieren mecanismos adicionales. Tambien
requieren logica de aplicacion los OTP, la vinculacion, la copia de plantillas,
las transiciones de estado y el borrado logico. Revisar los dos `CHECK`, tipos
`UNSIGNED`, defaults, `ON UPDATE CURRENT_TIMESTAMP` y acciones referenciales
mediante mapeos explicitos y comprobacion del DDL; algunos pueden necesitar
extensiones de Hibernate o migraciones. Nada de esto se implementa en esta etapa.

## Verificacion

Sin credenciales reales, revisar ubicaciones, placeholders y exclusiones, y
comprobar que el JAR no contiene `application-local.properties`.
Para empaquetar sin ejecutar la prueba que necesita MySQL:

```powershell
.\mvnw.cmd -DskipTests package
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

La prueba `contextLoads()` necesita MySQL y credenciales validas; no confirma
todavia tablas o relaciones. Spring Security esta incluido y un HTTP 401 no
implica por si mismo un fallo de conexion a MySQL.
Cuando existan entidades, comparar dos bases vacias independientes, comprobar
conservacion de datos tras reiniciar y probar restricciones y relaciones.
