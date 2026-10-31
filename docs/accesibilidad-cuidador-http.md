# Accesibilidad propia del Cuidador

Implementado el 31 de octubre de 2026. Esta configuración pertenece a la cuenta,
no a PerfilUsuario, Dispositivo ni ConfiguracionPerfil. No contiene lectura de voz.

## Contrato privado

Bearer de sesión vigente y ROLE_CUIDADOR. La identidad procede exclusivamente de
SesionCuidadorService.Identidad; se comprueba que el cuidador continúe ACTIVO.
Una credencial E3 no permite acceder. No se reciben IDs de propietario.

| Método | Ruta | Solicitud |
| --- | --- | --- |
| GET | `/api/cuidador/accesibilidad` | Sin body |
| PATCH | `/api/cuidador/accesibilidad` | Uno o ambos campos de preferencias |
| POST | `/api/cuidador/accesibilidad/restablecer` | Sin body |

Las tres respuestas son 200, `Cache-Control: no-store`, con el mismo DTO:

```json
{"tamanoTexto":"MEDIANO","altoContraste":false}
```

Tamaño: PEQUENO, MEDIANO o GRANDE. Contraste: booleano. PATCH rechaza body vacío,
campos desconocidos (también idCuidador y lecturaVoz), null y tipos incorrectos:
400 SOLICITUD_INVALIDA mediante ProblemDetail existente. Autenticación/autorización
mantienen los errores globales existentes. No se modifica el contrato de sesión.

## Persistencia y concurrencia

ConfiguracionCuidador usa PK compartida id_cuidador y relación @MapsId 1:1.
Defaults MEDIANO/false. GET devuelve esos defaults sin insertar si falta la fila;
la primera actualización o reset la crea. Los cuidadores existentes no requieren
backfill ni modificación del registro/autenticación.

PATCH/reset son transacciones READ_COMMITTED: bloquean y refrescan el Cuidador
con PESSIMISTIC_WRITE, vuelven a comprobar ACTIVO, refrescan la configuración
existente y modifican solo lo solicitado. El bloqueo del padre serializa también
la primera inserción. PK impide dos configuraciones para una cuenta. PATCH usa
valores absolutos, no operaciones toggle; campos distintos concurrentes se
conservan, y sobre el mismo campo prevalece la última escritura serializada.
No hay sincronización push entre instalaciones: cada una vuelve a consultar.

## Esquema y transición explícita

SQL v7 y JPA incluyen configuracion_cuidador: id_cuidador INT PK/FK a cuidador
ON DELETE CASCADE, tamano_texto ENUM NOT NULL DEFAULT MEDIANO y alto_contraste
TINYINT NOT NULL DEFAULT 0 con CHECK IN (0,1). Sin índices redundantes ni seeds.

- BD nueva con Hibernate update: crea la estructura desde JPA.
- BD existente local con update: Hibernate puede añadir la tabla; verificar
  SHOW CREATE TABLE y los defaults/FK/CHECK resultantes. No confiar en update
  para reparar una tabla previa incompatible.
- Con ddl-auto=none: antes de usar los nuevos endpoints, ejecutar manualmente
  [create-configuracion-cuidador.sql](sql/create-configuracion-cuidador.sql)
  sobre la base elegida, tras la inspección indicada en el propio script.
  Si la tabla ya existe, detenerse y comparar, no recrearla ni borrar datos.

El script es aditivo, no se ejecuta al arrancar y no toca cuentas, sesiones,
perfiles, actividades ni configuraciones de Usuario. **No se ejecutó SQL ni se
inspeccionó la BD personal durante esta implementación.**

## Validación

AccesibilidadCuidadorServiceTest cubre defaults sin escritura, validación,
actualización parcial, bloqueo, reset, identidad inactiva y commit/rollback.
AccesibilidadCuidadorControllerTest usa Security real para roles, principal,
contrato, CSRF de APIs y no-store. PersistenceModelTest contrasta JPA/SQL v7.
AccesibilidadCuidadorMySqlTest es opt-in: requiere DIAFACIL_TEST_MYSQL_* y una
base exclusiva diafacil_test_* con esquema ya preparado, nunca la BD personal;
verifica escrituras concurrentes y primera fila. Quedó omitido sin esas variables.

UI y guardado: [Mi perfil del Cuidador](../../frontend_dia_facil/docs/mi-perfil-cuidador.md).
