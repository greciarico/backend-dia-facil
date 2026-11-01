# Sugerencia de plantilla y borrador de actividad

Actualización ARASAAC: el descriptor de pictograma admite fuente, idArasaac y
urlImagen calculada. Desayunar usa 2443, 5549, 36965, 2349 y 8688 tras el backfill aprobado. Conserva
idPictograma y nombre. Ver [contrato ARASAAC](pictogramas-arasaac.md). Las notas
inferiores conservan las decisiones del borrador B, ampliado posteriormente con
ARASAAC y [creación real C](actividades-http.md). El GET sigue siendo solo lectura.

`GET /api/plantillas-actividad/sugerencia?nombre=Desayunar`, con Bearer de cuidador.
200 y `Cache-Control: no-store`. El catálogo es global del sistema; no recibe
idCuidador ni modifica un perfil. La pantalla mantiene su consulta autorizada del
perfil ACTIVO mediante el GET existente. Crear una definición es un comando separado.

```json
{
  "plantilla": {
    "idPlantillaActividad": 4,
    "nombre": "Desayunar",
    "pasos": [
      {
        "orden": 1,
        "descripcion": "Lávate las manos",
        "pictograma": { "idPictograma": 9, "nombre": "enjabonar las manos", "fuente": "ARASAAC", "idArasaac": 2443, "urlImagen": "https://static.arasaac.org/pictograms/2443/2443_300.png" }
      }
    ]
  }
}
```

IDs ilustrativos: siempre se utilizan los reales de BD. Devuelve todos los pasos
ordenados; pictograma admite null. Sin coincidencia devuelve `{"plantilla":null}`.
No expone entidades ni IDs de PlantillaPaso; URL de ARASAAC calculada, no persistida.

## Detección

Consulta PlantillaActividad y PalabraClaveActividad en BD, nunca el JSON del seed.
Normaliza espacios, mayúsculas y acentos. Prioridad: nombre completo exacto,
keyword exacta, después nombre o keyword como palabras completas dentro de la
consulta. Desempata por nombre normalizado y después ID. No hay coincidencia por
prefijo ni IA: Des no carga Desayunar; Desayuno del domingo coincide por desayuno.
El catálogo base incluye Desayunar, Ir al colegio, Estudiar, Almorzar, Cenar y
Prepararse para dormir; sus variantes explícitas viven en datos-base.json.
Los 24 pasos tienen pictogramas curados; ver [datos base y transición](datos-base.md).
La opcionalidad null sigue admitida por el contrato y por los pasos propios.

Nombre ausente, vacío o menor de tres caracteres devuelve plantilla null. Más de
80 caracteres: 400 SOLICITUD_INVALIDA. Errores de sesión e infraestructura conservan
el contrato global. Solo se habilita el GET exacto para CUIDADOR con el filtro
Bearer existente. Repositorio proyectado y servicio transaccional de solo lectura.

## Formulario en memoria

La hora corresponde a LocalTime del modelo. El estado conserva minutos locales
desde medianoche (0–1439), inicialmente null. El selector nativo ya instalado
usa Date solo como adaptador, sin guardar fecha, UTC ni zona horaria. Cancelar
conserva el valor anterior. UI: Selecciona una hora; luego 07:00 a. m., etc.
Seleccionar hora no realiza escrituras; hora/pasos se envían únicamente al POST final.

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
su referencia al pictograma; pasos propios admiten selección ARASAAC o null.
Eliminar requiere confirmación. Drag & drop desde handle conserva claves y
recalcula órdenes; acciones accesibles Subir/Bajar continúan disponibles.

No se impone un máximo funcional inventado. La única barrera técnica de cantidad
es 65535 posiciones por SMALLINT UNSIGNED. Cero pasos es válido temporalmente en
el borrador; [creación C](actividades-http.md) exige mínimo un paso válido.

Los cinco predeterminados de Desayunar entregan referencias ARASAAC estables.
Las filas muestran miniatura y texto; si falta/falla imagen se conserva fallback
neutro. Agregar/Editar busca mediante el [contrato ARASAAC](pictogramas-arasaac.md),
sin catálogo completo, selección automática ni Cloudinary.

Hora y borrador viven en la pantalla y
se descartan al abandonarla; no hay SecureStore, autosave ni contexto de perfil
seleccionado. El POST C crea Actividad y PasoActividad propios sin sincronizar
actualizaciones futuras de plantillas. Esta consulta no modifica plantillas/datos base.

## Validación

Pruebas de matching, DTO y seguridad HTTP; validación Hibernate de las consultas;
parser completo, reducer independiente y protección permanente del borrador;
cancelación/debounce/respuestas tardías; confirmaciones, edición y movimientos;
selector horario Android y conversiones de hora local. Pruebas existentes de
autenticación/iconos/perfiles conservadas. TypeScript, lint y exportación Android.
La prueba visual y de accesibilidad en Android físico queda para validación manual.
