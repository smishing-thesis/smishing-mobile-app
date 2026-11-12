# Modo demo (#65)

Herramientas para entregar mensajes de ejemplo en el emulador y demostrar la app completa sin un teléfono real. **Solo existen en la
variante `debug`**: `qa` y `release` solo llevan una función vacía (`DemoSection`) y ninguna de las demás clases (verificado en las
clases compiladas de cada variante).

## Cómo se usa

1. Tener el archivo `demo-messages.csv` (ver "El archivo") y copiarlo al emulador. La carpeta de la app se crea al abrirla una vez:

   ```bash
   adb push demo-messages.csv /sdcard/Android/data/com.app.smishing/files/demo-messages.csv
   ```

   En Git Bash de Windows, anteponer `MSYS_NO_PATHCONV=1` al comando: si no, cambia la ruta del emulador.
2. Instalar la variante `debug` y dejar la app como la de SMS predeterminada (para escribir en el buzón hace falta serlo).
3. En **Ajustes**, al final, sección "Demo". **Cada toque entrega un solo mensaje**, para ver cómo llega y su aviso (no todos a la vez):
   - **Recibir un mensaje de estafa:** entrega el siguiente mensaje `smishing` del archivo, **en orden y sin repetir** hasta dar toda la vuelta (no es al azar);
     al llegar al último vuelve al primero. En qué mensaje va cada tipo se guarda (`demo-turns.txt`): sigue igual al salir de la
     pantalla o cerrar la app, y vuelve al primero al borrar los mensajes de demo.
   - **Recibir un mensaje legítimo:** lo mismo con los `legitimo`.
   - Cada mensaje llega como recibido ahora, por el mismo flujo que un SMS real (`ReceiveIncomingSms` y el análisis del receptor): se
     guarda una vez, se analiza, queda en el historial y avisa o actúa según el modo de mitigación. No hay texto de respuesta: la llegada
     del mensaje y su aviso son la respuesta (la sección solo escribe algo si hay un error).
   - **Borrar mensajes de demo (N):** quita del buzón los mensajes entregados, con sus registros del historial (y sus acciones) y sus
     avisos.
   Si falta el archivo, la sección lo dice y dónde copiarlo; con el archivo listo no muestra ningún texto de estado.

## El archivo

`demo-messages.csv`, UTF-8, con columnas `sender`, `body`, `minutes_ago` y `label`. Los textos con comas, saltos de línea o comillas van
entre comillas dobles. `label` (`smishing`, `legitimo`, `spam_sin_enlace`, `muy_corto`) solo sirve para comprobar los resultados; la
app no lo usa. Las tildes y la ñ se conservan (se lee como UTF-8; hay una prueba).

**No está en el repositorio ni debe estarlo**: tiene texto de mensajes reales. Se genera fuera, a partir del corpus real sin anonimizar,
y se copia con `adb push`. Los datos personales de terceros (nombres, números propios) se reemplazan por inventados al generarlo.
La versión actual tiene 25 mensajes: 10 de smishing, 12 legítimos, 2 ofertas de préstamo (spam sin enlace) y 1 muy corto.

Los textos nunca se escriben en logs: `DemoMessage` no muestra el texto en `toString`, y los errores dicen solo el tipo o un motivo propio.

## Dónde está

| Pieza | Archivo |
|---|---|
| Lector del CSV | `app/src/debug/.../demo/DemoCsv.kt` |
| Entregar y borrar | `app/src/debug/.../demo/DemoLoader.kt` |
| Sección de Ajustes | `app/src/debug/.../ui/settings/DemoSection.kt` (en `qa` y `release`: una versión vacía en `app/src/qa` y `app/src/release`) |
| Prueba | `app/src/testDebug/.../demo/DemoCsvTest.kt` (4 pruebas del lector; solo corre con la variante `debug`) |

Para que "Simular que llegan ahora" use el mismo análisis que el receptor, la parte del receptor que evalúa y avisa pasó a una función
interna (`analyzeIncoming` en `SmsDeliverReceiver.kt`); el receptor la sigue usando igual. `SettingsScreen` tiene un hueco (`extra`) al final
donde `SettingsRoute` pone `DemoSection()`.

## Resultado (emulador API 29, 2026-11-11)

- Un toque en "Recibir un mensaje de estafa": llegó 1 mensaje al buzón (0 → 1) y apareció la alerta "Posible estafa detectada" con
  "Ver detalles" en pantalla.
- Borrar: se fueron solo los mensajes de demo y su historial (con 25 de una versión anterior que entregaba todos juntos: 47 → 22).
- `qa` y `release` compilan y sus clases no incluyen `DemoLoader`, `DemoCsv` ni la sección real.
- Pruebas: 4 del lector del CSV, que pasan.
- Versiones anteriores que se descartaron: "Cargar como mensajes anteriores" (sin análisis) y "Comenzar demo", que entregaba los 25 juntos
  (las notificaciones se amontonaban y no se podían apreciar).

## Lo que no cubre

- **No desbloquea números.** Si se simula con el modo automático, la app bloquea los remitentes de los mensajes riesgosos; al borrar
  los mensajes de demo esos bloqueos quedan.
- **Si el registro de ids (`demo-ids.txt`, dentro de los datos de la app) se pierde
  (borrar los datos de la app), los mensajes cargados quedan en el buzón y hay que quitarlos a mano.
- **Se probó solo en el emulador API 29.** No en un teléfono real ni en Android 13 o posterior.
- **Con el modo automático no se probó** (se probó con el modo por defecto).
- **Ya no hay forma de demostrar mensajes sin analizar** (#59) con esta herramienta: todos pasan por el análisis. Los muy cortos y las
  ofertas de préstamo del archivo no tienen botón.
- **La columna `minutes_ago` del archivo ya no se usa**: todos llegan con la hora de ahora.
- **Que un mensaje sea de "estafa" lo dice la etiqueta del archivo, no el modelo**: puede salir "Sospechoso" o "Sin señales", y solo el
  nivel alto lanza la alerta con "Ver detalles".
- Los resultados del modelo en estos mensajes reales son lo que son: hay legítimos que salen "Sospechoso" y mensajes de smishing que
  salen solo "Sospechoso". El demo no los oculta.
