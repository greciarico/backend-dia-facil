# Catálogo y sugerencias de iconos de actividad

`GET /api/iconos-actividad`, con Bearer de cuidador, devuelve el catálogo completo
de BD (52 iconos base). `GET /api/iconos-actividad?nombre=Desayunar` devuelve hasta
tres sugerencias derivadas exclusivamente de PalabraClaveIconoActividad.

```json
{"iconos":[{"codigo":"bread","nombre":"Pan"}]}
```

200 y Cache-Control: no-store. El DTO solo contiene código estable y etiqueta;
frontend resuelve el código con su catálogo local de PNG, sin rutas enviadas por
backend. El catálogo es global, no pertenece a un perfil; no acepta idCuidador
como autoridad. Solo el GET exacto se permite al rol CUIDADOR mediante el filtro
Bearer existente. No se modifica emisión, renovación ni persistencia de sesión.

Sin parámetro o con nombre vacío tras normalizar espacios, devuelve todo el
catálogo ordenado por código. Con uno o dos caracteres devuelve lista vacía.
Con tres o más, compara sin mayúsculas ni acentos: keyword igual al nombre
completo, keyword igual a una palabra completa y finalmente prefijo de palabra
desde dos caracteres. Conserva la mejor prioridad por icono, elimina duplicados,
desempata por código y limita a tres. Sin coincidencias devuelve `{"iconos":[]}`;
no rellena con iconos ajenos. No utiliza IA, stemming ni keywords de plantillas.

Nombre de más de 80 caracteres devuelve 400 SOLICITUD_INVALIDA. Los errores de
sesión e infraestructura mantienen el contrato global seguro. Las consultas son
de solo lectura y no modifican datos base, actividades ni plantillas.

Con el seed actual, Desayunar sugiere bread; dish-of-food solo tiene las palabras
almorzar y almuerzo. Los iconos sin keywords siguen disponibles en el catálogo.

## Cliente: primer incremento de Nueva actividad

Desde Actividades del perfil se abre Nueva actividad, consultando el perfil real
por ID y conservando la política 404 común para inexistente, ajeno o INACTIVO.
Nombre obligatorio de máximo 80 caracteres. Catálogo completo y sugerencias
tienen estados y peticiones independientes. Mientras se escribe, desde tres
caracteres y tras debounce de 250 ms, se consulta el endpoint con nombre.
Se cancelan consultas anteriores y se ignoran respuestas obsoletas o posteriores
a salir de la pantalla/cambiar de sesión. Bajar de tres caracteres limpia las
sugerencias. Errores de catálogo y sugerencias pueden reintentarse por separado.

Ver más íconos abre siempre la galería completa, incluso sin coincidencias.
No incluye búsqueda interna. Ninguna sugerencia se selecciona automáticamente;
editar el nombre conserva la selección manual. No tener sugerencias no invalida
el nombre ni impide elegir cualquier icono. Códigos desconocidos o imágenes
fallidas quedan no seleccionables, sin inventar un reemplazo.

Nombre e icono solo viven en memoria de la pantalla. El incremento B añade
[hora y pasos en memoria](plantillas-actividad-http.md), sin modificar esta búsqueda
ni la selección de iconos. Crear actividad permanece deshabilitado; todavía no
hay POST ni agenda con datos. Flecha y Atrás de Android regresan a Actividades del
mismo perfil.
