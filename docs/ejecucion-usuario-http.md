# Ejecución diaria de Usuario

Todas las rutas usan Bearer E3 y ROLE_USUARIO. `AccesoDispositivoService` resuelve
credencial, Dispositivo ACTIVO y PerfilUsuario ACTIVO; el cliente nunca aporta
un perfil/cuidador como autoridad. La actividad debe estar ACTIVA y pertenecer
al perfil autenticado. Inexistente, ajena o inactiva producen el mismo
404 `ACTIVIDAD_NO_ENCONTRADA`. Respuestas `Cache-Control: no-store`.

## Contrato

Base: `/api/usuario/actividades/{idActividad}`.

| Método y sufijo | Body | Resultado |
| --- | --- | --- |
| GET `/guia` | — | Guía del día; no crea ejecución |
| POST `/guia` | `{ "fecha": "2026-10-24" }` | Inicia o recupera la ejecución del día |
| GET `/ejecuciones/{idEjecucion}` | — | Recuperación exacta, incluso de otro día, solo lectura |
| PUT `/ejecuciones/{idEjecucion}/pasos/{idPaso}/completar` | — | Confirma el paso alcanzado y alcanza el siguiente |
| POST `/ejecuciones/{idEjecucion}/finalizar-guiada` | `{ "idPasoActual": 12 }` | Finalización GUIADA |
| POST `/finalizar-directa` | `{ "fecha": "2026-10-24" }` | Finalización DIRECTA |

Todos devuelven 200. Las cuatro primeras rutas devuelven:

```json
{
  "fecha": "2026-10-24",
  "zonaHoraria": "America/Lima",
  "actualizarEnSegundos": 3600,
  "vigente": true,
  "idEjecucion": 4,
  "estado": "EN_PROGRESO",
  "modoFinalizacion": null,
  "actividad": { "idActividad": 7, "nombre": "Desayunar", "guiaInicialCompletada": false },
  "pasos": [
    { "idPasoActividad": 12, "posicion": 1, "descripcion": "Lávate las manos", "pictograma": null, "alcanzado": true, "completado": false }
  ]
}
```

`pasos` contiene todos los pasos ACTIVO en orden, con posiciones lógicas 1..N;
el ejemplo abrevia una actividad de un paso. `pictograma` es null o el DTO existente
`idPictograma/nombre/fuente/idArasaac/urlImagen`. La URL ARASAAC se deriva del ID,
sin búsqueda, persistencia de URL ni modificaciones al piloto Desayunar.
Una guía sin ejecución tiene estado PENDIENTE, ID/modo null y ningún paso alcanzado.

Las finalizaciones devuelven únicamente `fecha`, `idEjecucion`,
`estado: COMPLETADA`, `modoFinalizacion` y `actividad` del contrato anterior.
No exponen entidades JPA, secretos ni relaciones internas.

## Reglas y transacciones

- GET nunca crea progreso. Iniciar crea/reutiliza una ejecución por actividad/fecha
  y registra únicamente el primer paso alcanzado (`completado=false`).
- Siguiente confirma el paso actual y alcanza el siguiente en una transacción.
  No permite saltar al futuro. Las repeticiones conservan el resultado existente.
- El máximo alcanzado se deriva del prefijo de ProgresoPaso; no existe otro campo.
  La posición visual es local: al reanudar se ofrece el último paso alcanzado.
- Primera guía: solo finaliza desde el último paso con los anteriores confirmados.
  Confirma el último, cambia `guiaInicialCompletada=true` y cierra GUIADA atómicamente.
- Guías posteriores: cierre anticipado GUIADA con confirmación en UI. Conserva
  el progreso real; no marca pasos no confirmados ni inventa visitas.
- DIRECTA exige la bandera inicial. No crea progreso y conserva el previo si existe.
- La primera finalización gana: un comando posterior no altera su modo o fecha.
- Escrituras READ_COMMITTED bloquean/revalidan perfil, actividad y ejecución.
  Reutilizan UNIQUE existentes de actividad/fecha y ejecución/paso. Sin DDL nuevo.
- Día autoritativo: `agendaUsuarioClock`, America/Lima por defecto. Comandos no
  modifican una ejecución histórica. GET exacto puede recuperarla con `vigente=false`.
  El cambio de día exige volver al detalle; nunca inicia hoy implícitamente.

## Errores y respuesta perdida

409: `GUIA_NO_DISPONIBLE` (definición/progreso inconsistente),
`DIA_AGENDA_CAMBIADO`, `GUIA_INICIAL_REQUERIDA`, `RECORRIDO_NO_PERMITIDO`.
404: `EJECUCION_NO_ENCONTRADA` para ejecución ajena/inexistente, sin revelar dueño.
Body incompleto: 400 de validación existente. Credencial inválida conserva el 401 E3.

El cliente bloquea doble toque. Ante fallo técnico o respuesta inválida realiza
una única lectura para comprobar el efecto; **no repite POST/PUT automáticamente**.
Si no puede consultar, bloquea nuevas escrituras hasta una recuperación GET explícita.
Si la lectura confirma que no se aplicó, mantiene el estado real y permite una nueva
acción explícita. El cierre confirmado muestra éxito solo en memoria, sin demoras.

## Validación

`EjecucionUsuarioServiceTest`, `EjecucionUsuarioControllerTest` y consultas HQL
cubren reglas, permisos, fechas, progreso y DTO. `EjecucionUsuarioMySqlTest` es
opt-in sobre `diafacil_test_*`: concurrencia, unicidad y rollback; nunca sobre la
BD de desarrollo ni ejecutando DDL. Frontend: `test-user-execution.cjs`.

Validación de este incremento: compilación Java 21 y runner JUnit Platform local,
284 pruebas correctas y 9 MySQL omitidas por falta de configuración. Se excluye
`BackendApplicationTests`, que requiere contexto/datasource completo. Se mantiene
el runner aislado por el fallo PowerShell del Maven Wrapper ya documentado;
la compilación devuelve 0, aunque este entorno emite un diagnóstico de cierre
ZIP de javac. No se afirma haber ejecutado las pruebas opt-in MySQL.
