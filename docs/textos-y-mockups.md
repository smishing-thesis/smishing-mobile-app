# Textos de la app y los mockups

Registro de #61. La referencia son los mockups de `designs/` (sobre todo `Mockups.png`). Esta página dice qué textos y pantallas siguen
el mockup, cuáles se apartan de él y por qué, y qué falta. Última revisión: 2026-11-11.

## Regla

1. **El texto del mockup, tal cual.**
2. **Salvo donde afirma certeza**, con el cambio mínimo (acuerdo con la diseñadora): "Mensaje Peligroso", "Seguro" y "Su teléfono está
   protegido". La tesis pide no presentar la sospecha ni la protección como certeza (#11), porque el modelo puede equivocarse.
3. **Lo que no está en ningún mockup se quita**, salvo lo mínimo que la app necesita para no fallar en silencio.

## Lo que se apartó del mockup, y por qué

| Dónde | Mockup | App | Motivo |
|---|---|---|---|
| Aviso y detalle, nivel alto | "Mensaje Peligroso" | "Posible estafa" | certeza |
| Historial, marcas | "Peligroso" / "Seguro" | "Posible estafa" / "Sin señales" ("Sospechoso" igual) | certeza |
| Inicio, tarjeta activa | "Su teléfono está protegido contra mensajes maliciosos…" | "La aplicación está protegiendo sus mensajes en este momento." | certeza (habla de lo que hace la app, no del estado del teléfono) |
| Paso 3, privacidad | "Su privacidad está garantizada." | "Todo el análisis se hace en su teléfono." | no se puede garantizar (el historial guarda el texto sin cifrar) |
| Detalle, razón de suplantación | "Finge ser de una entidad" | "Dice ser de una entidad" | afirma una intención que no se conoce |
| Detalle, encabezado de las razones | "¿Por qué es riesgoso?" | "¿Por qué podría ser riesgoso?" | afirma que lo es |
| Ajustes, título del grupo | "Cuando llegue un mensaje peligroso" | "Modo de protección" (antes "…con posible estafa") | certeza; el nuevo título no habla del mensaje |
| Onboarding, contador de pasos | "PASO 1 DE 3", "2 DE 3" y "3 DE 3" | "PASO 1 DE 2" y "2 DE 2"; el permiso de lectura no lleva contador | el rol concede ese permiso solo; la pantalla aparece únicamente si la persona lo quita después |
| Nuevo mensaje, campo "Para" | "Número o nombre de contacto" | "Número de teléfono" | no hay contactos todavía (#53) |
| Notificación de alerta, pantalla de bloqueo | muestra remitente y motivo | solo "Abra la aplicación para ver el detalle" | privacidad |
| Notificación, contenido | el mockup "Alerta entrante" muestra el inicio del texto | remitente y motivo, sin el texto | decisión tomada en #10 |

"Este mensaje es seguro", "Sí, es seguro" y "Mensaje seguro" se dejan como en el mockup donde es **la persona** quien lo afirma (el botón y
el diálogo). Donde lo diría la app, se usa "Marcado como seguro". La prueba de lenguaje excluye solo esos dos textos de la regla de certeza.

## Lo que se corrigió para seguir el mockup (#61)

- **Inicio:** se quitó "Lo que necesita la protección" y la nota de los 7 días. "Posibles estafas: N detectadas". Tarjeta apagada con el
  texto del mockup.
- **Detalle:** se quitó el resumen largo bajo el aviso. Las razones, la recomendación y los botones ("Bloquear este número",
  "Borrar mensaje", "Este mensaje es seguro") son los del mockup, en su orden. Se agregó el diálogo **"¿Está seguro?"** al marcar como
  seguro, y, tras revertir en modo automático, el panel **"¿Prefiere decidir antes de actuar?"** con **"Ir a Ajustes"**.
- **Ajustes:** se quitaron "Permiso para enviar mensajes" y la nota sobre el rol. Quedan los dos permisos del mockup y **Notificaciones**.
- **Onboarding:** la bienvenida es la del mockup. Se quitaron las pantallas de "ya concedido" de los pasos 2 y 3, que no están en el
  diseño y no se ven en el flujo.
- **Notificaciones de alerta:** "Le pide actuar con urgencia", "Le pide sus datos o claves".

## Lo que se dejó aunque no está en el mockup (para que lo decida la diseñadora)

- **"En el mensaje: «…»"** bajo cada razón del detalle, y **"Este análisis es automático y puede equivocarse"**: explican de dónde sale
  cada razón y dicen que el modelo puede fallar. Son de #11.
- **Notificaciones** en Ajustes y su explicación cuando no está concedida, y el enlace "Abrir ajustes" de los permisos negados: sin ellos,
  Inicio diría "Protección apagada" sin que se pueda ver por qué.
- Avisos de **acción fallida** ("No pudimos bloquear este número") y de **permiso negado**: para no fallar en silencio.
- **El contador de no leídos** de la lista y las **marcas de acciones** del historial ("Número bloqueado"…): son de #50 y #28.
- El estado vacío de las listas y los textos de error al enviar.

## Lo que el mockup tiene y la app todavía no

- La **búsqueda** (lupa) en Mensajes y en Historial.
- "**Cuidado: Posible estafa**" en la lista y "**Ver riesgo**" en la conversación: #60.
- "**Compartir reportes de estafas**" y su diálogo de privacidad: fase 2 (#18, #19, #26, #27).
- El **nombre del contacto** y el "+" para elegir contacto: #53.
- Los títulos "(manual)" y "(AUTOMÁTICO)" del detalle se leyeron como notas del mockup para distinguir variantes y no se pusieron en pantalla.
  Si son parte del diseño, se agregan.

## Dos versiones del mockup

`Mockups.png` y los mockups a color traen frases distintas del paso 3 ("buscando enlaces sospechosos o estafas conocidas" y
"identificando patrones de estafa") y del paso 2. Se tomó `Mockups.png`.
