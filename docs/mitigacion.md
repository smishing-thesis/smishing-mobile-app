# Mitigación: bloquear, retirar, marcar como seguro y revertir

Registro de #14 (RF-07, bloquear al remitente), #15 (RF-08, retirar del buzón), #16 (RF-10, marcar como seguro y revertir) y #28
(acciones en el historial). Última verificación: 2026-11-09, en el emulador de API 29.

## Qué hace

Sobre un mensaje de **riesgo alto**, según el modo de [ajustes-y-modo.md](ajustes-y-modo.md):

| Modo | Qué pasa al llegar | Qué ofrece el detalle |
|---|---|---|
| Automático | bloquea al remitente y retira el mensaje del buzón, y avisa | lo hecho, y "Marcar como seguro" |
| Preguntar (por defecto) | avisa y no toca nada | "Bloquear número", "Quitar el mensaje de la bandeja" y "Marcar como seguro" |
| Solo avisarme | avisa y no toca nada | ningún botón de actuar |

Las dos acciones se piden también a mano desde el detalle. En medio y bajo no hay acciones, sea cual sea el modo.

### Reglas que se cumplen (y se prueban)

- **Solo si la configuración lo permite:** con "solo avisarme", o si no se puede leer el modo, no se hace nada ni se anota nada.
- **Solo si hay registro local:** sin el registro guardado no se actúa. Retirar un mensaje comprueba además que el historial guarda
  **el mismo remitente y texto**; si no, la acción queda *Fallida* (`NoLocalCopy`) y el mensaje **no se borra**.
- **Una acción fallida nunca figura como hecha.** Cada una pasa por *Solicitada → Completada | Fallida*, con su causa como dato
  (`NotSupported`, `NotAllowed`, `NoLocalCopy`, `Error`).
- **Si el número ya estaba bloqueado** antes, la app no lo toca (`AlreadyApplied`) y **tampoco lo desbloquea** al revertir: no lo puso ella.
- **Reintentar** crea un intento nuevo; para la pantalla cuenta el último de cada tipo.

### Marcar como seguro (#16)

Guarda la decisión de la persona en el registro y deshace lo completado: **desbloquea** el número y **restaura** el mensaje en su
conversación, con su texto y hora originales y **ya leído**. Al restaurarlo el buzón le da un id nuevo; el registro se actualiza para
seguir encontrándolo. Lo que no se pueda deshacer **sigue vigente**, queda anotado (`RevertFailed`) y la pantalla lo dice.
"Marcado como seguro" es lo que **la persona** declara, no una afirmación de la app sobre el mensaje.

## Cómo se guarda (#28)

Base Room **versión 2** (`app/schemas/.../2.json`): tabla `mitigation_action` (tipo, estado, motivo, fechas; borra en cascada con el
mensaje analizado) y la columna `markedSafeAtMillis` en el mensaje analizado. La migración 1→2 **conserva lo ya guardado**
(`HistoryMigrationTest`, con la base de la versión 1 armada desde el SQL exacto del esquema exportado). Las acciones no guardan el
texto del mensaje ni el remitente; se guardan ids estables, no nombres.

La lista del historial dice bajo cada mensaje lo que se hizo: "Número bloqueado", "Mensaje quitado", "Acción fallida", "Acción en
curso" o "Marcado como seguro".

## Cómo se hace en Android

| Qué | Cómo | Notas |
|---|---|---|
| Bloquear | `BlockedNumberContract` (`AndroidSenderBlocker`) | solo la app de mensajería predeterminada puede escribir; sin el rol `canCurrentUserBlockNumbers()` es `false` y la acción falla con `NotSupported`. **No requiere permiso de manifiesto** |
| Retirar | borrar `content://sms/<id>` (`TelephonyInboxMessages`) | **borrar por `content://sms/inbox/<id>` no funcionó** en el emulador (devolvía 0 filas); por `content://sms/<id>` sí |
| Restaurar | insertar en `Telephony.Sms.Inbox` leído, con la hora original | el mensaje restaurado no pasa por el receptor: no se vuelve a analizar |

No se agregó ningún permiso.

## Pruebas

- JVM (193 en total al cerrar esta parte): `MitigationExecutorTest` (permitido o no, registro local, estados, causas, ya bloqueado,
  reintento, revertir y lo que no se puede revertir) y `HistoryMarksTest`.
- Dispositivo: `HistoryMigrationTest`, `RoomMitigationLogInstrumentedTest`, `MessageDetailActionsTest` (quitada: ver [pruebas.md](pruebas.md)) (acciones, fallos con su causa,
  botones según el modo, marcado como seguro, reglas de lenguaje), la marca en `HistoryScreenTest` (quitada: ver [pruebas.md](pruebas.md)) y, de punta a punta con SMS reales
  entregados y el sistema de bloqueo real, `SmsRoleInstrumentedTest`: modo automático (se bloquea y se retira, la alerta abre el detalle
  desde lo guardado, marcar como seguro desbloquea y restaura leído, el historial lo dice) y modo preguntar / solo avisarme.
- Suites completas al cerrar: JVM 193 pruebas, 0 fallos (1 omitida por diseño); emulador (API 29) 157 pruebas, 0 fallos, 2 omitidas (los
  pasos del rol, porque ya estaba concedido tras la revisión a mano).
- Mutación: hacer que un bloqueo fallido figure como completado, quitar la protección del número ya bloqueado y quitar la
  comprobación de la copia local rompen cada una su prueba (revertidas).
- A mano: se ve el detalle con los botones al final, "Bloqueamos este número" tras tocar el botón y "Acciones revertidas" al marcar como seguro.

## Lo que no se verificó

- **Un fallo real de la plataforma** (bloquear sin el rol): se prueba con piezas falsas, no con el sistema; con el rol concedido el
  emulador siempre lo permitió.
- **Remitentes con letras** (por ejemplo, el nombre de un banco): no se comprobó que el sistema los bloquee.
- **Que el sistema deje de entregar los SMS** de un número bloqueado (el filtro es del sistema, no de la app) y qué hace un número
  bloqueado con la app de mensajería de otro fabricante.
- **Teléfono real, Android 13 o superior, doble SIM.** El mensaje restaurado queda en la conversación que el sistema le asigne.
- **Mensajes largos:** al restaurar se inserta un único mensaje con el texto completo; no se probó con uno que llegó en varias partes.
- No se deja de actuar sobre un remitente marcado como seguro: un mensaje nuevo suyo con riesgo alto volvería a actuar (es otra historia).
- "Reportar" (#27) y "Ir a Ajustes" del mockup de "acciones revertidas" no están.
