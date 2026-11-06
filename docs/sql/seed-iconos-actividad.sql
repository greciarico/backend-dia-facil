-- Manual, additive seed AFTER Hibernate expansion and schema verification.
-- Historical/manual reference. Current startup data comes ONLY from db/datos-base.json.
-- Select your reviewed database first. No startup hook, REPLACE or hardcoded IDs.
-- Pause writes; use a client that stops on errors, never --force.
START TRANSACTION;
INSERT INTO icono_actividad (codigo, nombre)
SELECT c.codigo, c.nombre FROM (
SELECT 'apple' AS codigo, 'Manzana' AS nombre
UNION ALL
SELECT 'backpack', 'Mochila'
UNION ALL
SELECT 'ball', 'Pelota'
UNION ALL
SELECT 'bandages', 'Tiritas'
UNION ALL
SELECT 'bed', 'Cama'
UNION ALL
SELECT 'blocks', 'Bloques'
UNION ALL
SELECT 'bread', 'Pan'
UNION ALL
SELECT 'cereal', 'Cereal'
UNION ALL
SELECT 'clean-tooth', 'Diente limpio'
UNION ALL
SELECT 'coffee', 'Café'
UNION ALL
SELECT 'cookies', 'Galletas'
UNION ALL
SELECT 'dental-cleaning', 'Limpieza dental'
UNION ALL
SELECT 'dish-of-food', 'Plato de comida'
UNION ALL
SELECT 'dress', 'Vestido'
UNION ALL
SELECT 'fold-clothes', 'Ropa doblada'
UNION ALL
SELECT 'food', 'Comida'
UNION ALL
SELECT 'glass-of-water', 'Vaso de agua'
UNION ALL
SELECT 'handwashing', 'Lavado de manos'
UNION ALL
SELECT 'juice', 'Jugo'
UNION ALL
SELECT 'laptop', 'Computadora portátil'
UNION ALL
SELECT 'night', 'Noche'
UNION ALL
SELECT 'notebooks', 'Cuadernos'
UNION ALL
SELECT 'notes', 'Notas'
UNION ALL
SELECT 'open-book', 'Libro abierto'
UNION ALL
SELECT 'order', 'Orden'
UNION ALL
SELECT 'pens', 'Bolígrafos'
UNION ALL
SELECT 'pill', 'Pastilla'
UNION ALL
SELECT 'read', 'Lectura'
UNION ALL
SELECT 'run', 'Correr'
UNION ALL
SELECT 'school', 'Escuela'
UNION ALL
SELECT 'school-uniform', 'Uniforme escolar'
UNION ALL
SELECT 'shampoo', 'Champú'
UNION ALL
SELECT 'shorts', 'Pantalón corto'
UNION ALL
SELECT 'shower', 'Ducha'
UNION ALL
SELECT 'sleep-mask', 'Antifaz para dormir'
UNION ALL
SELECT 'snacks', 'Bocadillos'
UNION ALL
SELECT 'soup', 'Sopa'
UNION ALL
SELECT 'step', 'Paso'
UNION ALL
SELECT 'sweep', 'Barrer'
UNION ALL
SELECT 'syrup', 'Jarabe'
UNION ALL
SELECT 'teddy-bear', 'Osito de peluche'
UNION ALL
SELECT 'toilet-paper', 'Papel higiénico'
UNION ALL
SELECT 'toothbrush', 'Cepillo de dientes'
UNION ALL
SELECT 'toothpaste', 'Pasta dental'
UNION ALL
SELECT 'tour', 'Paseo'
UNION ALL
SELECT 'towel', 'Toalla'
UNION ALL
SELECT 'traces', 'Huellas'
UNION ALL
SELECT 'walk', 'Caminar'
UNION ALL
SELECT 'water-bottle', 'Botella de agua'
UNION ALL
SELECT 'weights', 'Pesas'
UNION ALL
SELECT 'yoga-mat', 'Esterilla de yoga'
UNION ALL
SELECT 'yogurt', 'Yogur'
) c WHERE NOT EXISTS (SELECT 1 FROM icono_actividad i WHERE i.codigo = c.codigo);

INSERT INTO palabra_clave_icono_actividad (id_icono_actividad, palabra)
SELECT i.id_icono_actividad, c.palabra FROM (
SELECT 'bed' AS codigo, 'cama' AS palabra
UNION ALL
SELECT 'bed', 'descansar'
UNION ALL
SELECT 'bed', 'dormir'
UNION ALL
SELECT 'bread', 'cena'
UNION ALL
SELECT 'bread', 'cenar'
UNION ALL
SELECT 'bread', 'desayunar'
UNION ALL
SELECT 'bread', 'desayuno'
UNION ALL
SELECT 'bread', 'pan'
UNION ALL
SELECT 'dish-of-food', 'almorzar'
UNION ALL
SELECT 'dish-of-food', 'almuerzo'
UNION ALL
SELECT 'open-book', 'colegio'
UNION ALL
SELECT 'open-book', 'estudiar'
UNION ALL
SELECT 'open-book', 'estudio'
UNION ALL
SELECT 'open-book', 'leer'
UNION ALL
SELECT 'open-book', 'tarea'
UNION ALL
SELECT 'walk', 'caminar'
UNION ALL
SELECT 'walk', 'ejercicio'
UNION ALL
SELECT 'walk', 'pasear'
) c JOIN icono_actividad i ON i.codigo = c.codigo
WHERE NOT EXISTS (SELECT 1 FROM palabra_clave_icono_actividad k
  WHERE k.id_icono_actividad = i.id_icono_actividad AND k.palabra = c.palabra);
COMMIT;
