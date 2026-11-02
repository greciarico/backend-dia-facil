# Incremento 2 ? contrato backend push Android

## Registro (solo bearer de cuidador)
- PUT /api/push/registro
- JSON: {"idInstalacion":"UUID de instalacion", "expoToken":"ExpoPushToken[...]", "plataforma":"ANDROID"}
- 200: {"activo":true,"idRegistro":123,"generacion":1,"idCuidador":45,"proyectoEas":"UUID real configurado"}
- GET /api/push/registro?idInstalacion=UUID: 200 con el mismo DTO; sin registro propio: activo=false y idRegistro/generacion=null. No devuelve token ni datos del dueno anterior.
- GET devuelve ETag fuerte opaco. PUT y DELETE exigen If-Match y devuelven el nuevo ETag. Sin cabecera: 428; formato invalido: 400; revision anterior: 412. Consultar y reconciliar solo la intencion vigente; no repetir una activacion abandonada con revision nueva.
- DELETE /api/push/registro?idInstalacion=UUID: 204; solo modifica un token de su sesion. Avanza la revision incluso sin token o ya inactivo. Repetir la misma precondicion consumida da 412 sin reaplicar el comando.
- 400 datos invalidos; 401 autenticacion ausente/vencida; 409 conflicto concurrente despues de reintentos limitados (reintentar mismo PUT); 503 push no configurado/habilitado.
- Cache-Control: no-store en respuestas correctas.
- El proyecto se deriva de configuracion del backend, nunca del cliente.
- Un PUT aceptado sobre el mismo registro conserva generacion, pero avanza la revision independiente de comandos. Token/cuenta/sesion reactivada o cambiado incrementa generacion. No se duplican filas por token o instalacion.

## Datos de navegacion
{version:1,tipo:'NOTIFICACION_CUIDADOR',idNotificacion,idRegistro,generacion,idCuidador}
Canal Android: diafacil-cuidador-v1. GET /api/notificaciones/{id} valida propietario. Abrir push NO marca leido; solo tarjeta interna usa PUT /api/notificaciones/{id}/leida.

## Activacion manual
1. Se conserva ddl-auto=update en desarrollo. La guarda permite none/validate/update y rechaza recreacion destructiva.
2. Antes del primer arranque real: revisar vista previa, probar sobre esquema desechable, respaldar y obtener autorizacion explicita. No iniciar ni reiniciar contra la base real automaticamente.
3. Hibernate gestiona la ampliacion; docs/sql/add-push-android.sql es una referencia opcional, no un requisito de migracion manual. Verificar indices, defaults y acciones FK antes de habilitar push. No cambiar E3 ni autenticacion.
4. EXPO_PROJECT_ID=UUID real de EAS; EXPO_PUSH_ACCESS_TOKEN=token privado habilitado para proteccion de Expo Push Service. No guardar secretos en Git, logs o EXPO_PUBLIC_*.
5. diafacil.push.enabled=true habilita cola y worker. Default false. No hay backfill ni envio de historia al registrar.
6. Reiniciar solo con autorizacion del operador. Credenciales invalidas pausan worker hasta corregir configuracion y reiniciar; cola historica permanece inspeccionable.

## Firebase y EAS (a cargo del usuario/padre)
Firebase Dia Facil (dia-facil), app com.diafacil.app y FCM v1 ya fueron preparados por el responsable. Pendiente validar archivo local y vincular EAS sin duplicar proyectos; luego subir la clave de cuenta de servicio directamente a EAS. NO copiar esa clave al backend; Spring usa Expo HTTPS y el token privado Expo. Seguir guia frontend para build propio: Expo Go Android no soporta push remotas. Sin credenciales no hacer envios reales.

## Persistencia y operacion
NotificacionEventoService guarda historial y entregas en la transaccion de ayuda/finalizacion. No cambia cooldown/progreso. Unicidad notificacion+registro+generacion. Worker reclama una entrega por transaccion con arrendamiento UUID 2 minutos, hasta 20 por ciclo (10 segundos), HTTP fuera de transaccion y resultado condicionado al lease vigente.
Revalida sesion, cuidador, token, proyecto y generacion antes del envio y al aplicar respuesta. Sesiones revocadas/vencidas o eliminadas son inelegibles incluso si activo queda true; FK sesion SET NULL permite limpieza existente. Logout puede llamar DELETE pero no depende de ?l para seguridad.
8 intentos de envio, 24 horas, backoff 30s/2m/10m/30m/2h/6h/12h, Retry-After y jitter <=10%, nunca mas alla del vencimiento. TTL restante enviado a Expo. Receipts a 15m, repetidos hasta vencimiento; ausencia termina RESULTADO_DESCONOCIDO sin reenviar automaticamente.
ACEPTADA_EXPO y ACEPTADA_PROVEEDOR no equivalen a mostrado/leido. DeviceNotRegistered desactiva solo generacion vigente. Credenciales invalidas/MismatchSenderId pausan envios. No se guardan cuerpos de error/payloads completos en logs. Estados terminales no se limpian automaticamente en este incremento.
No garantia de exactamente una alerta: red incierta puede duplicar push. Historial permanece ?nico. Logout offline y alertas ya en transito no se pueden retirar del proveedor.

## Pruebas
Unitarias con HTTP simulado y metadatos sin JDBC. Integracion PushMySqlTest es opt-in, exclusivamente esquema diafacil_test_* preparado externamente con el esquema push y control_registro_push; no ejecuta DDL. La prueba de esquema B1 es separada y requiere un esquema desechable sin esa tabla. No levantar SpringBoot contra base real para probar.
Pendiente operativo externo: SQL aprobado/aplicado, credenciales reales, build EAS, Android foreground/background/cierre normal/toque; no se considera validado por ticket HTTP200.

Los receipts temporales respetan Retry-After y una espera minima de 15 minutos, sin programarse mas alla de la expiracion. Logout invalida la elegibilidad por revocacion de sesion; no necesita una llamada DELETE bajo credenciales nuevas. Consultar push-android-validacion.md para evidencia y limites actuales.
