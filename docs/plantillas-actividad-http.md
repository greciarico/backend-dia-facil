# Sugerencia de plantilla y borrador de actividad — incremento B

Actualización ARASAAC: el descriptor de pictograma admite fuente, idArasaac y
urlImagen calculada. Desayunar usa 2443, 5549, 36965, 2349 y 8688 tras el backfill aprobado. Conserva
idPictograma y nombre. Ver [contrato ARASAAC](pictogramas-arasaac.md). Las notas
inferiores sobre ausencia de imágenes describen el estado previo: el editor ahora
admite miniaturas y selección ARASAAC en memoria, sin POST de actividades.

`GET /api/plantillas-actividad/sugerencia?nombre=Desayunar`, con Bearer de cuidador.
200 y `Cache-Control: no-store`. El catálogo es global del sistema; no recibe
idCuidador ni modifica un perfil. La pantalla mantiene su consulta autorizada del
perfil ACTIVO mediante el GET existente. No existe POST de actividades en B.

```json
{
  "plantilla": {
    "idPlantillaActividad": 4,
    "nombre": "Desayunar",
    "pasos": [
      {
        "orden": 1,
        "descripcion": "Lávate las manos",
        "pictograma": { "idPictograma": 9, "nombre": "Lavarse las manos" }
      }
    ]
  }
}
```

IDs ilustrativos: siempre se utilizan los reales de BD. Devuelve todos los pasos
ordenados; pictograma admite null. Sin coincidencia devuelve `{"plantilla":null}`.
No expone entidades, URL de imagen ni IDs de PlantillaPaso.

## Detección

Consulta PlantillaActividad y PalabraClaveActividad en BD, nunca el JSON del seed.
Normaliza espacios, mayúsculas y acentos. Prioridad: nombre completo exacto,
keyword exacta, después nombre o keyword como palabras completas dentro de la
consulta. Desempata por nombre normalizado y después ID. No hay coincidencia por
prefijo ni IA: Des no carga Desayunar; Desayuno del domingo coincide por desayuno.
Keywords base actuales: desayuno, desayunar, desayunando.

Nombre ausente, vacío o menor de tres caracteres devuelve plantilla null. Más de
80 caracteres: 400 SOLICITUD_INVALIDA. Errores de sesión e infraestructura conservan
el contrato global. Solo se habilita el GET exacto para CUIDADOR con el filtro
Bearer existente. Repositorio proyectado y servicio transaccional de solo lectura.

## Formulario en memoria

La hora corresponde a LocalTime del modelo. El estado conserva minutos locales
desde medianoche (0–1439), inicialmente null. El selector nativo ya instalado
usa Date solo como adaptador, sin guardar fecha, UTC ni zona horaria. Cancelar
conserva el valor anterior. UI: Selecciona una hora; luego 07:00 a. m., etc.
No se envía hora ni pasos a backend en este incremento.

La detección usa su propia petición desde tres caracteres, debounce de 250 ms y
cancelación; es independiente de las sugerencias de iconos. Una respuesta inválida
no se aplica parcialmente. Error, ausencia de coincidencia o consulta corta nunca
borran un borrador. Se permite reintentar o agregar pasos propios.

La primera propuesta se copia automáticamente mientras no se hayan personalizado
pasos; puede cambiar si otra plantilla coincide. La misma propuesta no reinicia
las filas. Un editor/menú abierto suspende la aplicación automática. Cerrar sin
cambios permite reevaluar la propuesta vigente. Desde la primera modificación
efectiva (editar, agregar, eliminar o mover) la protección permanece activa durante
todo el borrador, incluso al eliminar todos los pasos o aplicar explícitamente
otra plantilla. Usar pasos sugeridos requiere confirmación para sustituir pasos
personalizados. La elección de icono, hora o nombre no altera esa marca.

Cada paso tiene clave local estable, orden consecutivo desde 1, descripción y
referencia opcional al pictograma. Agregar/editar utiliza un modal: trim exterior,
texto obligatorio, máximo 120 caracteres y Cancelar sin cambios. Editar conserva
su referencia al pictograma; los pasos propios usan null. Eliminar requiere
confirmación. Subir/Bajar en el menú de cada fila conserva claves y recalcula
órdenes; extremos deshabilitados y anuncio accesible. No hay drag & drop.

No se impone un máximo funcional inventado. La única barrera técnica de cantidad
es 65535 posiciones por SMALLINT UNSIGNED. Cero pasos es válido temporalmente en
el borrador; la política final de creación se resolverá en C.

Los pictogramas actuales tienen URLs relativas sin archivos renderizables locales.
Las filas muestran número y texto sin imágenes, placeholders ficticios ni emojis.
Se conservan las referencias para una integración visual posterior, sin ARASAAC,
Cloudinary ni imágenes externas en B.

Crear actividad permanece deshabilitado. Hora y borrador viven en la pantalla y
se descartan al abandonarla; no hay SecureStore, autosave ni contexto de perfil
seleccionado. En C se crearán Actividad y PasoActividad propios sin sincronizar
actualizaciones futuras de plantillas. No se modificó JPA, SQL ni datos base.

## Validación

Pruebas de matching, DTO y seguridad HTTP; validación Hibernate de las consultas;
parser completo, reducer independiente y protección permanente del borrador;
cancelación/debounce/respuestas tardías; confirmaciones, edición y movimientos;
selector horario Android y conversiones de hora local. Pruebas existentes de
autenticación/iconos/perfiles conservadas. TypeScript, lint y exportación Android.
La prueba visual y de accesibilidad en Android físico queda para validación manual.
