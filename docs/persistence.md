# Modelo de persistencia JPA

## Alcance

19 entidades en `dia_facil.backend.persistence.entity` y 10 enums en
`dia_facil.backend.persistence.enums`. Hibernate 7.4.5.Final / Jakarta Persistence 3.2.
El SQL v7 sigue siendo la referencia, ahora ampliada con `cuidador.avatar_id`.
La ampliación se entrega también en docs/sql/add-cuidador-avatar.sql; no se ha
ejecutado contra una base real. Los servicios y endpoints de autenticación y
registro se documentan en auth-http.md. No se habilita Flyway en este incremento.

Sesiones de cuidador: dos tablas adicionales en docs/sql/add-cuidador-sessions.sql.
SesionCuidador almacena cuidador, creación UTC, inactividad, vencimiento absoluto y
revocación; CredencialSesionCuidador almacena generación, hashes BINARY(32) únicos,
access_hasta y consumida_en. FK solo hacia cuidador/sesion_cuidador, nunca dispositivo.
Fechas nuevas DATETIME(6) representan UTC explícito; no se modifica la zona del OTP.

Todos los nombres fisicos son explicitos. Las relaciones apuntan desde la fila
dependiente hacia su referencia: no hay colecciones inversas, cascadas JPA ni
orphan removal. Todas solicitan carga LAZY; es una indicacion al proveedor, no
una promesa de que cada relacion produzca siempre un proxy o cero consultas.

Las entidades tienen acceso por campos, constructor protegido sin argumentos
y getters/setters de Lombok. Los identificadores y timestamps generados por la
base no tienen setter. No se genera equals/hashCode/toString sobre asociaciones.
Los constructores de uso del futuro servicio se definiran con ese servicio;
esta etapa no crea factories ni operaciones funcionales.

## Columnas

`?` indica nullable. Las demas columnas son obligatorias.
Las FK estan en la siguiente tabla; todas son INT y se representan mediante
asociaciones. Todos los IDs son Integer. Las longitudes String se conservan.

| Tabla / entidad | PK | Campos restantes sin FK |
|---|---|---|
| `cuidador` → `Cuidador` | `id_cuidador` (IDENTITY) | `nombre`: String? [VARCHAR(100)], obligatorio para registrar; `avatarId`: String? [VARCHAR(32)]; `email`: String [VARCHAR(150)]; `estado`: EstadoCuidador; `fecha_creacion`: LocalDateTime |
| `codigo_acceso` → `CodigoAcceso` | `id_codigo_acceso` (IDENTITY) | `email`: String [VARCHAR(150)]; `codigo_hash`: String [CHAR(64)]; `fecha_expiracion`: LocalDateTime; `intentos`: Integer; `usado`: Boolean; `fecha_creacion`: LocalDateTime |
| `perfil_usuario` → `PerfilUsuario` | `id_perfil_usuario` (IDENTITY) | `nombre`: String [VARCHAR(60)]; `avatar`: String? [VARCHAR(255)]; `codigo_vinculacion`: String [VARCHAR(20)]; `fecha_renovacion_codigo`: LocalDateTime?; `estado`: EstadoPerfil; `fecha_creacion`: LocalDateTime |
| `configuracion_perfil` → `ConfiguracionPerfil` | `id_perfil_usuario` compartida (@MapsId) | `tamano_texto`: TamanoTexto; `alto_contraste`: Boolean; `lectura_voz`: Boolean; `recordatorio_minutos`: Integer; `fecha_actualizacion`: LocalDateTime |
| `pictograma` → `Pictograma` | `id_pictograma` (IDENTITY) | `nombre`: String [VARCHAR(80)]; `url_imagen`: String [VARCHAR(255)]; `fuente`: String? [VARCHAR(60)] |
| `palabra_clave_pictograma` → `PalabraClavePictograma` | `id_palabra_clave_pictograma` (IDENTITY) | `palabra`: String [VARCHAR(60)] |
| `plantilla_actividad` → `PlantillaActividad` | `id_plantilla_actividad` (IDENTITY) | `nombre`: String [VARCHAR(80)] |
| `palabra_clave_actividad` → `PalabraClaveActividad` | `id_palabra_clave_actividad` (IDENTITY) | `palabra`: String [VARCHAR(60)] |
| `plantilla_paso` → `PlantillaPaso` | `id_plantilla_paso` (IDENTITY) | `orden`: Integer; `descripcion`: String [VARCHAR(120)] |
| `actividad` → `Actividad` | `id_actividad` (IDENTITY) | `nombre`: String [VARCHAR(80)]; `hora`: LocalTime; `guia_inicial_completada`: Boolean; `estado`: EstadoActividad; `fecha_creacion`: LocalDateTime |
| `paso_actividad` → `PasoActividad` | `id_paso_actividad` (IDENTITY) | `orden`: Integer; `descripcion`: String [VARCHAR(120)]; `activo`: Boolean |
| `dispositivo` → `Dispositivo` | `id_dispositivo` (IDENTITY) | `nombre`: String [VARCHAR(80)]; `estado`: EstadoDispositivo; `fecha_vinculacion`: LocalDateTime; `fecha_ultima_actividad`: LocalDateTime?; `fecha_desvinculacion`: LocalDateTime? |
| `ejecucion_actividad` → `EjecucionActividad` | `id_ejecucion_actividad` (IDENTITY) | `fecha`: LocalDate; `estado`: EstadoEjecucion; `modo_finalizacion`: ModoFinalizacion?; `fecha_inicio`: LocalDateTime; `fecha_fin`: LocalDateTime? |
| `progreso_paso` → `ProgresoPaso` | `id_progreso_paso` (IDENTITY) | `completado`: Boolean; `fecha_completado`: LocalDateTime? |
| `solicitud_ayuda` → `SolicitudAyuda` | `id_solicitud_ayuda` (IDENTITY) | `fecha_solicitud`: LocalDateTime; `estado`: EstadoSolicitudAyuda; `fecha_atencion`: LocalDateTime? |
| `notificacion_cuidador` → `NotificacionCuidador` | `id_notificacion_cuidador` (IDENTITY) | `tipo`: TipoNotificacion; `titulo`: String [VARCHAR(120)]; `mensaje`: String [VARCHAR(255)]; `fecha_creacion`: LocalDateTime; `leida`: Boolean |
| `token_push` → `TokenPush` | `id_token_push` (IDENTITY) | `expo_token`: String [VARCHAR(255)]; `plataforma`: Plataforma?; `fecha_actualizacion`: LocalDateTime |

## Relaciones, unicidad e indices

Se conservan los nombres de todas las FK del SQL. ON DELETE es comportamiento
de MySQL configurado con `@OnDelete`, distinto de `CascadeType.REMOVE`.
No se propagan persist/merge/remove hacia padres o catalogos.
Despues de borrados fisicos con CASCADE/SET NULL, limpiar o refrescar el contexto
de persistencia antes de reutilizar objetos que puedan haber quedado obsoletos.
No se habilita cache de segundo nivel en esta etapa.

| Tabla | FK / relacion / borrado | UNIQUE | Indices |
|---|---|---|---|
| `cuidador` | Sin FK | `uq_cuidador_email(email)` | Sin indice explicito adicional |
| `codigo_acceso` | Sin FK | Sin UNIQUE adicional | `idx_codigo_acceso_email(email,fecha_creacion)` |
| `perfil_usuario` | `id_cuidador` → `cuidador` (obligatoria; ManyToOne; ON DELETE CASCADE) | `uq_perfil_codigo(codigo_vinculacion)`; `uq_perfil_cuidador(id_perfil_usuario,id_cuidador)` | `idx_perfil_cuidador_nombre(id_cuidador,nombre)` |
| `configuracion_perfil` | `id_perfil_usuario` → `perfil_usuario` (obligatoria; OneToOne; ON DELETE CASCADE) | Sin UNIQUE adicional | Sin indice explicito adicional |
| `pictograma` | Sin FK | `uq_pictograma_nombre(nombre)` | Sin indice explicito adicional |
| `palabra_clave_pictograma` | `id_pictograma` → `pictograma` (obligatoria; ManyToOne; ON DELETE CASCADE) | `uq_palabra_pictograma(id_pictograma,palabra)` | `idx_palabra_pictograma(palabra)` |
| `plantilla_actividad` | `id_pictograma` → `pictograma` (opcional; ManyToOne; ON DELETE SET NULL) | `uq_plantilla_nombre(nombre)` | Sin indice explicito adicional |
| `palabra_clave_actividad` | `id_plantilla_actividad` → `plantilla_actividad` (obligatoria; ManyToOne; ON DELETE CASCADE) | `uq_palabra_actividad(id_plantilla_actividad,palabra)` | `idx_palabra_actividad(palabra)` |
| `plantilla_paso` | `id_plantilla_actividad` → `plantilla_actividad` (obligatoria; ManyToOne; ON DELETE CASCADE); `id_pictograma` → `pictograma` (opcional; ManyToOne; ON DELETE SET NULL) | Sin UNIQUE adicional | `idx_plantilla_paso_orden(id_plantilla_actividad,orden)` |
| `actividad` | `id_perfil_usuario` → `perfil_usuario` (obligatoria; ManyToOne; ON DELETE CASCADE); `id_pictograma` → `pictograma` (opcional; ManyToOne; ON DELETE SET NULL) | Sin UNIQUE adicional | `idx_actividad_agenda(id_perfil_usuario,estado,hora)` |
| `paso_actividad` | `id_actividad` → `actividad` (obligatoria; ManyToOne; ON DELETE CASCADE); `id_pictograma` → `pictograma` (opcional; ManyToOne; ON DELETE SET NULL) | Sin UNIQUE adicional | `idx_paso_actividad_orden(id_actividad,orden)` |
| `dispositivo` | `id_perfil_usuario` → `perfil_usuario` (obligatoria; ManyToOne; ON DELETE CASCADE) | Sin UNIQUE adicional | `idx_dispositivo_perfil_estado(id_perfil_usuario,estado)` |
| `ejecucion_actividad` | `id_actividad` → `actividad` (obligatoria; ManyToOne; ON DELETE CASCADE) | `uq_ejecucion_actividad_fecha(id_actividad,fecha)` | `idx_ejecucion_fecha(fecha)` |
| `progreso_paso` | `id_ejecucion_actividad` → `ejecucion_actividad` (obligatoria; ManyToOne; ON DELETE CASCADE); `id_paso_actividad` → `paso_actividad` (obligatoria; ManyToOne; ON DELETE CASCADE) | `uq_progreso_ejecucion_paso(id_ejecucion_actividad,id_paso_actividad)` | `idx_progreso_paso(id_paso_actividad)` |
| `solicitud_ayuda` | `id_perfil_usuario` → `perfil_usuario` (obligatoria; ManyToOne; ON DELETE CASCADE); `id_actividad` → `actividad` (opcional; ManyToOne; ON DELETE SET NULL); `id_ejecucion_actividad` → `ejecucion_actividad` (opcional; ManyToOne; ON DELETE SET NULL); `id_paso_actividad` → `paso_actividad` (opcional; ManyToOne; ON DELETE SET NULL) | Sin UNIQUE adicional | `idx_ayuda_perfil_estado(id_perfil_usuario,estado,fecha_solicitud)`; `idx_ayuda_actividad(id_actividad,fecha_solicitud)` |
| `notificacion_cuidador` | `id_perfil_usuario,id_cuidador` → `perfil_usuario` (obligatoria; ManyToOne; ON DELETE CASCADE); `id_ejecucion_actividad` → `ejecucion_actividad` (opcional; ManyToOne; ON DELETE SET NULL); `id_solicitud_ayuda` → `solicitud_ayuda` (opcional; ManyToOne; ON DELETE SET NULL) | Sin UNIQUE adicional | `idx_notif_cuidador_fecha(id_cuidador,fecha_creacion)`; `idx_notif_cuidador_leida(id_cuidador,leida)` |
| `token_push` | `id_cuidador` → `cuidador` (obligatoria; ManyToOne; ON DELETE CASCADE) | `uq_token_push(expo_token)` | `idx_token_cuidador(id_cuidador)` |

El orden de `uq_perfil_cuidador` en el codigo es
`(id_cuidador,id_perfil_usuario)`, como se explica a continuacion; la tabla
anterior conserva el orden original de la referencia SQL.

La configuracion comparte el ID del perfil mediante `@MapsId`; no utiliza
autoincremento. Impide duplicados, pero no obliga a que cada perfil tenga una
fila de configuracion. La futura creacion del perfil debe resolver esa regla.

Las notificaciones usan una sola asociacion al perfil con FK compuesta. Esa
asociacion escribe tanto `id_perfil_usuario` como `id_cuidador`, evitando
dos campos o asociaciones escribibles que puedan contradecirse. El cuidador se
obtiene a traves del perfil; no se crea una FK independiente adicional.

## Defaults y timestamps

Los estados iniciales son ACTIVO (cuidador/perfil/dispositivo), ACTIVA
(actividad), EN_PROGRESO (ejecucion) y PENDIENTE (solicitud).
Tamano de texto MEDIANO; recordatorio 10; lectura de voz y paso activo true;
los otros booleanos false; intentos 0. Los enums se almacenan por nombre con
`@Enumerated(EnumType.STRING)`, nunca por ordinal.

`@ColumnDefault` reproduce defaults SQL. Los defaults no temporales tambien
se inicializan en Java; un null explicito no se reemplaza silenciosamente.
Los campos obligatorios sin default requieren un valor antes de persistir.

Todos los DATETIME se representan como LocalDateTime (sin zona horaria);
TIME como LocalTime; DATE como LocalDate. Se solicita precision temporal de
cero fracciones mediante `@Column(secondPrecision = 0)`.

MySQL genera los timestamps con DEFAULT CURRENT_TIMESTAMP:
fecha_creacion, fecha_vinculacion, fecha_inicio y fecha_solicitud.
Hibernate recupera esos valores con `@Generated(event = INSERT)`.
fecha_actualizacion de configuracion y token conserva tambien
ON UPDATE CURRENT_TIMESTAMP; Hibernate recupera su valor en INSERT y UPDATE.
No se usan callbacks, relojes Java ni triggers.

Las fechas opcionales y fecha_expiracion son valores normales que debera
proporcionar la futura capa funcional. No se actualizan por cambios de estado
en las entidades.

## Fidelidad y diferencias documentadas

Se priorizan las anotaciones de Jakarta/Hibernate. Solo se usa
`columnDefinition` donde las anotaciones por si solas no conservan el tipo:

- TINYINT UNSIGNED y SMALLINT UNSIGNED: Integer evita los limites de Byte/Short
  con signo; se conserva el rango SQL unsigned.
- codigo_hash CHAR(64): en Hibernate 7.4.5 con MySQL, JdbcTypeCode(CHAR) por si
  solo genero VARCHAR(64). Una definicion explicita conserva el tipo fijo.
- Las opciones de tabla fijan utf8mb4/utf8mb4_unicode_ci y el dialecto emite
  InnoDB. Esto no modifica los defaults de la base.

Las siguientes diferencias son deliberadas y semanticamente compatibles:

- Hibernate emite INTEGER en lugar de INT y DATETIME(0)/TIME(0) en lugar de
  DATETIME/TIME sin precision explicita.
- Los booleanos usan JdbcTypeCode(TINYINT), sin el ancho de visualizacion (1),
  que no limita el rango del entero. Java los representa como Boolean.
- Los enums nativos se emiten con sus etiquetas ordenadas por Hibernate.
  Los valores permitidos son los mismos; el orden numerico interno y un
  ORDER BY de un ENUM pueden diferir del SQL original. La futura aplicacion
  debe ordenar por criterio funcional explicito, no por ese orden interno.
- Hibernate reordena la FK compuesta de notificaciones a
  (id_cuidador,id_perfil_usuario). Se alinea `uq_perfil_cuidador` al mismo
  orden para que MySQL tenga una clave UNIQUE de respaldo valida. La
  unicidad y la comprobacion de pertenencia siguen siendo las mismas.
  Un indice UNIQUE con el orden contrario no sirve como respaldo de esa FK.

Los CHECK chk_config_recordatorio y chk_ejecucion_cierre se representan con
`@Table(check = @CheckConstraint(...))`. No se agregan checks funcionales nuevos.

Limites del modelo:

- LocalTime representa hora del dia, no duraciones negativas o superiores a
  24 horas que MySQL TIME puede almacenar; coincide con la hora de agenda.
- Boolean no representa todos los enteros que un TINYINT permite almacenar;
  el dominio documentado es verdadero/falso.
- DATETIME no contiene zona horaria; la politica de zona del negocio queda
  para la capa funcional.
- LAZY puede requerir consultas adicionales, especialmente en asociaciones
  hacia una clave compuesta distinta de la PK.
- `ddl-auto=update` no garantiza corregir FK, CHECK, collation o indices
  preexistentes. La equivalencia se acepta sobre una base vacia.
  La prueba real de MySQL sigue pendiente.

## Reglas funcionales pendientes

El limite acordado es **cuatro perfiles activos por cuidador**. No lo implementa
una entidad ni un CHECK entre filas. Un futuro servicio debera contemplar altas,
reactivaciones y cambios de cuidador con control transaccional de concurrencia.
El trigger SQL solo revisa INSERT y no distingue un NEW inactivo: no se copia.

EjecucionActividad no duplica el perfil: se obtiene a traves de Actividad.
PENDIENTE se deriva de ausencia de ejecucion; NECESITA_AYUDA se deriva de las
solicitudes. El enum persistido de ejecucion solo tiene EN_PROGRESO/COMPLETADA.

La coherencia de las combinaciones de ayuda y de actividad/ejecucion/paso,
la creacion de configuracion, la primera guia, OTP, vinculacion, copia de
plantillas, notificaciones y borrado logico requieren servicios futuros.
Los estados y el campo activo no interceptan automaticamente un remove().
El catalogo inicial y la plantilla Desayunar no se cargan en esta tarea.

## Pruebas sin MySQL ni Docker

Desde `backend_dia_facil`:

```powershell
.\mvnw.cmd -B -ntp -Dtest=PersistenceModelTest test
```

Las pruebas usan un proveedor JDBC que rechaza y cuenta cualquier conexion.
No cargan Spring Boot ni leen credenciales locales. Construyen metadatos y
SessionFactory, y exportan DDL solo a `target/jpa-schema.sql`.
Ese archivo es un artefacto de verificacion, no una migracion ni una instruccion
para ejecutar SQL manualmente.

`src/test/resources/persistence/sql-v7-model.tsv` es una instantanea no
ejecutable de la referencia SQL. Permite comparar tablas, columnas, tipos,
nulabilidad, defaults, PK, FK, acciones de borrado, UNIQUE, indices y CHECK.
La prueba comprueba ademas el orden de la UNIQUE que respalda la FK compuesta.

La prueba existente contextLoads necesita conexion real. No ejecutarla contra
la base de trabajo como parte de esta verificacion. Para empaquetar con las
pruebas de metadatos y sin esa prueba:

```powershell
.\mvnw.cmd -B -ntp -Dtest=PersistenceModelTest package
```

## Comprobacion posterior en MySQL desechable

No ejecutada en esta tarea. Usar una instancia aislada, con base diafacil vacia
y credenciales configuradas privadamente; no apuntar a la base de trabajo.

1. Arrancar el backend con perfil local. Hibernate debe crear 17 tablas,
   sin triggers, datos iniciales ni flyway_schema_history.
2. Inspeccionar SHOW CREATE TABLE e information_schema para verificar tipos,
   nulabilidad, enums, defaults, UNIQUE, indices, CHECK y acciones de FK.
   Verificar especialmente el respaldo de la FK compuesta.
3. Mediante EntityManager en pruebas transaccionales, persistir fixtures
   validos en orden de dependencia, sin repositories. Comprobar IDs generados,
   MapsId y timestamps de base. Para ON UPDATE usar cambios reales y diferenciar
   al menos un segundo, dada la precision temporal.
4. Comprobar rechazo de email/codigo duplicado, duplicados de ejecucion diaria
   y progreso, perfil/cuidador incompatible, FK inexistente, null obligatorio,
   recordatorio invalido y cierre de ejecucion incoherente.
5. Borrar un pictograma y comprobar SET NULL en sus referencias y CASCADE
   de palabras clave. Borrar padres de fixtures y verificar las cascadas
   indicadas; limpiar el contexto para leer el estado real de las filas.
6. Reiniciar y comprobar conservacion de fixtures. Repetir con otra instancia
   vacia para comparar los esquemas.
7. Los fixtures de prueba son datos temporales, no una carga inicial.
   No comprobar el limite de cuatro perfiles como restriccion de esta capa:
   su servicio y trigger estan fuera de alcance.

