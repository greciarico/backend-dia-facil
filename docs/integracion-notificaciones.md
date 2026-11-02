# Integraci?n de ayuda y notificaciones

Integraci?n Git local en integration-notification, desde develop 5886ba5 y origin/feature/creation-of-notifications 4e0755c. Backend se fusion? sin conflictos textuales. No se modificaron workflow, pom.xml, datos base, matching de iconos, configuraci?n privada ni BD. No se inici? el backend ni se realiz? despliegue.

## C?digo integrado

Solicitudes generales/contextuales, historial del cuidador, eventos de finalizaci?n, registro push por instalaci?n/sesi?n, precondiciones ETag y outbox con tickets/receipts. Los controladores utilizan las identidades autenticadas actuales; no se mezcla Dispositivo E3 con sesiones del cuidador. Push permanece desactivado por defecto.

## Esquema pendiente: no arrancar contra BD real sin preparaci?n

No son solo tablas nuevas:

- Nuevas: control_registro_push y entrega_push.
- solicitud_ayuda: clave_operacion, posicion_paso, descripcion_paso, UNIQUE e ?ndice contextual.
- notificacion_cuidador: UNIQUE por solicitud y por ejecuci?n.
- token_push: instalaci?n, sesi?n, activo, generaci?n y proyecto EAS; UNIQUE y FK nueva.
- Credenciales: length=32 aclara el mapeo existente BINARY(32), sin cambio intencional de identidad/hash.

Los scripts docs/sql/add-ayuda-notificaciones-internas.sql, add-push-android.sql y add-control-registro-push.sql son referencias revisables, no migraciones ejecutadas. Conservar filas existentes. Preflight de duplicados antes de agregar UNIQUE; respaldo, prueba MySQL aislada y aprobaci?n expl?cita antes de cambios reales. MySQL DDL no es una transacci?n reversible.

application.properties conserva ddl-auto=none. La plantilla local usa update; no iniciar local contra la BD habitual sin revisar su efecto. El procesador nuevo rechaza recreaci?n destructiva pero no aplica migraciones. Desactivar push no exime del esquema necesario para ayuda/historial y eventos de finalizaci?n.

## Validaciones y l?mites

Se intent? compilar/empaquetar y ejecutar las pruebas sin MySQL ni BackendApplicationTests. El wrapper no pudo iniciar con la resoluci?n local de este entorno. Maven disponible pudo resolver el modelo offline usando la cach?, pero el JDK 21 fall? con java.nio.file.AccessDeniedException al cerrar JARs mediante ZipFileSystem. La compilaci?n aislada con cach? copiada al workspace y javac separado reprodujo el problema. No se alter? pom.xml para evitarlo; los auxiliares est?n en carpetas ignoradas.

La compilaci?n/pruebas backend NO quedan certificadas por esta sesi?n. Los errores derivados de clases ausentes en la compilaci?n de tests aislada no acreditan un fallo funcional: hubo una compilaci?n previa interrumpida por el problema de acceso. Repetir con el entorno local normal: mvnw.cmd package "-Dtest=*Test,!*MySqlTest,!BackendApplicationTests". No declarar verdes los resultados hist?ricos de Jonathan como evidencia de esta integraci?n.

Las pruebas MySQL requieren un esquema desechable diafacil_test_* y autorizaci?n separada. No configurar DIAFACIL_TEST_MYSQL_URL apuntando a la BD habitual ni a producci?n.

## Preparaci?n manual posterior

1. Resolver el entorno de compilaci?n y pasar pruebas/paquete backend; verificar seguridad y regresi?n de ejecuciones/notificaciones.
2. Aprobar/probar la transici?n de esquema, defaults, ?ndices y FK antes de cualquier arranque real.
3. Firebase/FCM: registrar com.rico999.diafacil y configurar credenciales en el proyecto EAS 9625ba7e-e2ca-4f45-8ba6-f24f2abdb39e (@rico999/diafacil). No adoptar recursos de la aplicaci?n de Jonathan.
4. Backend: EXPO_PROJECT_ID debe coincidir con ese UUID; EXPO_PUSH_ACCESS_TOKEN es secreto solo del servidor. DIAFACIL_PUSH_ENABLED=false hasta completar la preparaci?n. Con push activo se requiere token v?lido y proyecto configurado.
5. Frontend/EAS: GOOGLE_SERVICES_JSON como archivo cliente del paquete correcto y EXPO_PUBLIC_PUSH_ENABLED=true solo cuando se autorice. No colocar la cuenta de servicio privada en el frontend o Spring.
6. Validar push en Android f?sico. Integrar en develop y desplegar son acciones posteriores separadas: el workflow actual despliega al hacer push a develop. No usarlo para probar esta integraci?n.

Ver [gu?a frontend](../../frontend_dia_facil/docs/push-android-configuracion.md). No se configuraron AWS, variables remotas, secretos ni credenciales en esta integraci?n.
