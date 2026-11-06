# Incremento 2: reanudacion y validacion local

Fecha: 2026-11-01. Se retomo la implementacion existente sin reiniciarla desde cero. Se conservaron Incremento 1 y los cambios de Mi perfil/accesibilidad. No se instalaron paquetes ni se actualizaron dependencias durante esta reanudacion.

## Correcciones

- Operaciones push ligadas a revision de sesion antes/despues de credenciales, respuestas y reintentos.
- Cancelacion inmediata en logout, reutilizando la revocacion persistente offline. Ningun DELETE pendiente puede usar otra cuenta.
- Cambio de sesion del mismo cuidador y conciliacion pendiente por rotacion de token.
- Navegacion autorizada con descarte de errores definitivos, sin lectura automatica.
- Tarjeta seleccionada unica en la lista, incluso fuera de la pagina cargada; participa en visibilidad real para evitar alertas redundantes.
- Receipts temporales respetan Retry-After y expiracion; no reenvian el mensaje con ticket.
- Reparacion de adaptadores, expectativas HTTP, marca BOM/aserciones genericas y referencia de 24 entidades sin JDBC.

## Pruebas ejecutadas

Frontend (desde frontend-dia-facil):

~~~powershell
node --test scripts/test-*.cjs tests/*.test.cjs
.\node_modules\.bin\tsc.cmd --noEmit --incremental false
.\node_modules\.bin\eslint.cmd src scripts tests types app.config.ts --no-cache
~~~

258 pruebas frontend correctas. TypeScript correcto. ESLint correcto, sin errores ni advertencias. Las pruebas cubren autenticacion, vinculacion, perfiles, accesibilidad, Agenda, ejecucion, ayuda/notificaciones y los nuevos casos push con adaptadores. No sustituyen una validacion visual en Android.

Backend (desde backend-dia-facil):

~~~powershell
mvn.cmd -o '-Dtest=*Test,!*MySqlTest' test
~~~

338 pruebas correctas, 0 fallos y 0 errores. Incluye servicios, seguridad, controladores MockMvc, cliente Expo simulado, reintentos/receipts y modelo de persistencia que rechaza JDBC. No se ejecuto BackendApplicationTests ni se arranco el backend real.

## Pendiente externo; no validado

- Actualizacion: PushSchemaUpdateMySqlTest paso en diafacil_test_push_update_b2d9e3c9921c4f49bde2136dc49e861d. Se copio solo estructura de 23 tablas, nunca datos reales. Conservo dos tokens ficticios, verifico defaults, indices y FK y probo un segundo arranque sin SQL pendiente. PushMySqlTest: 6 pruebas correctas, incluida concurrencia, revocacion y los tres flujos por servicios reales (ayuda general/contextual y finalizacion), sin avanzar progreso al pedir ayuda ni duplicar historial. Los envios a Expo siguen simulados. Nunca apuntar estos tests a la base actual.
- El SQL del Incremento 2 no esta aplicado en MySQL actual: consulta de solo lectura verifico 0 columnas nuevas y ausencia de entrega_push. No se ejecutaron migraciones ni escrituras sobre esa base.
- La configuracion privada local conserva ddl-auto=update. La guarda ahora lo admite; los tres hashes declaran length=32 y no requieren ALTER. No se cambio configuracion privada ni se inicio/reinicio el backend. Falta respaldo y autorizacion explicita antes de un primer arranque real.
- Firebase Dia Facil (dia-facil), paquete Android com.diafacil.app y FCM v1 ya preparados por el responsable. EAS confirmado @jhonatanvasquez/diafacil, UUID 1795613d-b14f-45f4-947a-96dabcbf71a0. google-services.json ya fue validado y copiado al frontend ignorado por Git (project_id dia-facil, paquete com.diafacil.app). El responsable confirmo la clave privada fuera del repositorio. Falta confirmar su carga directa en EAS, configurar el archivo de build y compilar. No hay envios reales. Push permanece desactivado.
- Recepcion real en primer plano, segundo plano, cierre normal, permisos y toque requieren configuracion personal y Android fisico. Guia: ../../frontend-dia-facil/docs/push-android-configuracion.md.

## SQL para revision, no ejecutado

La propuesta generada con las entidades corregidas y conexion de solo lectura al esquema real esta en target/push-schema-real-preview.sql. Contiene 12 instrucciones para la nueva tabla, cinco columnas, dos restricciones unicas, un indice y tres FK. No contiene MODIFY de hashes, DROP ni cambios ajenos al incremento. El destino de generacion fue exclusivamente SCRIPT. La ejecucion en MySQL real y el arranque siguen sin autorizar.
