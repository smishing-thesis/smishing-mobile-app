# Pruebas del proyecto

Qué pruebas tiene la app y cuál es el criterio. Última revisión: 2026-11-11.

## Criterio

Las pruebas automáticas están para proteger **lo crítico del negocio**: lógica que, al cambiar el código, puede romper otros flujos sin que
nadie lo note. No están para comprobar que algo aparece en una pantalla ni que un texto es el esperado: eso se ve a ojo.

Al cambiar algo se corre **solo lo que ese cambio toca**.

## Tipos

| Tipo | Dónde | Qué protege |
|---|---|---|
| **Unitarias en la JVM** | `app/src/test` | la lógica del dominio, sin emulador: nivel de riesgo, preprocesamiento contra los vectores del modelo, señales, el orden "guardar, consultar el modo, actuar, avisar", mitigación (bloquear, retirar, revertir), estado de la protección, resumen semanal, no leídos, compuerta de permisos, reglas de copia de seguridad |
| **De lenguaje** | `ExplanationWordingTest` (JVM) | que ningún texto de la app afirme certeza (la tesis pide no presentar la sospecha como certeza) |
| **En el dispositivo, sin pantalla** | `app/src/androidTest` | lo que depende de Android: el modelo LiteRT contra Keras, la clasificación de punta a punta, Room (historial, acciones, migración) y las preferencias |
| **De punta a punta** | `SmsRoleInstrumentedTest`, `AnalysisSuspensionInstrumentedTest` | el flujo real con SMS entregados al receptor: rol, recepción, alerta, modos, bloqueo y restauración, sin internet, demora, notificaciones y lectura |

## Lo que se quitó (#61)

Se quitaron las **pruebas de pantallas con Compose** (11 archivos, unas 100 pruebas): comprobaban que cada pantalla mostraba ciertos
elementos y textos y que los botones respondían. Cada cambio de texto las rompía sin que hubiera un error real.

Las decisiones que esas pruebas fijaban **ya no tienen una prueba automática de pantalla**, y se vigilan a ojo:
- que Inicio no diga "activa" si falta un requisito (la **lógica** sigue en `ProtectionStatusTest`; la pantalla ya no);
- que el detalle nunca muestre el puntaje ni porcentajes;
- que marcar como seguro pida confirmación;
- que los botones tengan el área táctil de 48 dp y que las marcas del historial no usen "Peligroso" ni "Seguro".

Siguen cubiertas de punta a punta, con el sistema real: la alerta, el detalle desde la notificación, marcar como seguro con su confirmación,
la lista con no leídos y el historial.
