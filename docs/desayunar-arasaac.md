# Desayunar — predeterminados aprobados y backfill

Selección visual confirmada por la usuaria. Un paso = un pictograma; no se guardan
alternativas, términos de búsqueda ni posiciones de resultados.

| PK existente | Texto del paso (sin cambios) | idArasaac | Etiqueta del recurso |
|---|---|---|---|
| 2 | Lávate las manos | 2443 | enjabonar las manos |
| 3 | Prepara la mesa | 5549 | poner la mesa |
| 4 | Sirve la comida | 36965 | servir |
| 5 | Come | 2349 | comer |
| 6 | Limpia y recoge | 8688 | secar |

La etiqueta de Pictograma identifica la imagen, no sustituye PlantillaPaso.descripcion.
Por eso el último recurso se llama secar y el paso continúa siendo Limpia y recoge.
Las keywords locales existentes se conservan, sin añadir vocabulario ni ranking.

## BD nueva

datos-base.json contiene cinco recursos con fuente ARASAAC e idArasaac, sin URL.
Los pasos referencian arasaac:ID; los IDs locales son los generados por JPA, no
necesariamente 2–6. El inicializador crea filas faltantes y no duplica al repetirse.
SQL v7 refleja los mismos IDs y referencias por id_arasaac como documentación del
esquema/seed; no debe ejecutarse entero sobre una BD existente.

## BD existente auditada con PK 2–6

La transición estructural ya fue ejecutada y validada por la usuaria; no repetirla
por esta fase. Este cambio prepara el backfill de datos, no vuelve a cambiar esquema.

1. Detener backend y escrituras, respaldar la BD y seleccionar la base correcta.
2. Abrir y ejecutar **docs/sql/backfill-desayunar-arasaac.sql** en el cliente MySQL
   habitual. No configurar continuar ante errores. Requiere CREATE ROUTINE.
3. El script verifica estructura UNIQUE/InnoDB, presencia de las PK, sus firmas
   legacy o finales exactas, propiedad de los IDs externos, cinco referencias de
   Desayunar con texto/orden aprobados y ausencia de otros usos de los legacy.
   Si falla, revierte el DML y reporta el conflicto; no fusiona, elimina ni repara.
4. Actualiza exclusivamente nombre, fuente, id_arasaac y url_imagen de esas filas.
   Conserva PK/FK, keywords y todos los pasos. url_imagen queda NULL; no guarda
   la URL oficial derivable ni elimina la columna de compatibilidad.
5. El resultado debe mostrar PK 2–6 con los cinco IDs de esta tabla, fuente ARASAAC
   y URL NULL. Se puede repetir: si ya está migrado, no reescribe las filas.
6. Arrancar backend con el nuevo JSON. Si antes estaban completos los datos base,
   debe informar cero creados y ningún conflicto. Volver a solicitar la plantilla
   desde la app; el borrador que ya estaba abierto no se actualiza forzosamente.

No arrancar intencionadamente con el nuevo seed antes del backfill. Como protección,
el inicializador detecta referencias existentes cuyo recurso ARASAAC todavía falta:
las conserva, omite crear reemplazos y advierte transicion-pendiente/dependencias.
No hace la migración automáticamente y no duplica esos cinco recursos.

El script maneja transaccionalmente el DML. La creación/eliminación del procedimiento
es DDL explícito del cliente; no invocar dentro de otra transacción propia ni usarlo
como inicializador. Si algo falla, conservar el error y revisar antes de repetir.
No toca cuidadores, perfiles, actividades reales, pasos reales, ejecuciones o sesiones.

## Flujo resultante

GET de plantilla lee BD y entrega sus cinco pictogramas con URL calculada:
`https://static.arasaac.org/pictograms/{id}/{id}_300.png`.
Copiar/usar pasos sugeridos carga esas referencias en memoria; no busca pictogramas
individuales. Agregar/Editar sigue utilizando búsqueda dinámica y selección manual.
La [creación real posterior](actividades-http.md) persiste esos pasos como copias
independientes. La fase del backfill no cambió atribución/licencia.
La herramienta de revisión __DEV__ y sus pruebas exclusivas se eliminaron.

## Validación y límites

Pruebas unitarias verifican IDs/textos originales, ausencia de URLs en JSON,
idempotencia, protección antes del backfill y DTO/borrador con cinco miniaturas.
DesayunarArasaacMySqlTest es opt-in: requiere BD VACÍA exclusiva
diafacil_test_arasaac_* y variables DIAFACIL_TEST_ARASAAC_MYSQL_URL,
DIAFACIL_TEST_ARASAAC_MYSQL_USER y DIAFACIL_TEST_ARASAAC_MYSQL_PASSWORD.
Prueba rollback ante conflictos/NULL, IDs ocupados, texto cambiado y uso real;
preservación de FK/usuarios; repetición del backfill y del inicializador sin duplicados.
Solo crea/elimina objetos de esa BD desechable; nunca usa credenciales locales.

No se ejecutó el backfill sobre la BD local de la usuaria ni la integración MySQL
opt-in en este entorno. La aprobación visual de las imágenes procede de la usuaria,
no de una nueva búsqueda por posición ni de IDs de pruebas.

Resultado local: 182 pruebas backend y 91 frontend correctas; TypeScript y lint
correctos. Cuatro pruebas MySQL omitidas por falta de BD desechable configurada.
Backend compilado/ejecutado con javac/JUnit y dependencias locales existentes;
log de resultados en target/arasaac-phase2-validation.log.
