# Backlog del repo

Las historias son issues de este repo (proyecto 1 de la organización). Agrupadas por área, con un orden sugerido.
Revisar el tablero antes de empezar: el estado y la prioridad reales están allí, no aquí.

| Área | Issues |
|---|---|
| Base del proyecto | #1 entorno Android y LiteRT · #30 pruebas unitarias · #32 integrar el prototipo y generar un APK · #24 App Bundle firmado |
| Mensajería (gestor de SMS) | #2 app predeterminada · #3 permisos mínimos · #25 revisar permisos · #4 recibir SMS · #5 conversaciones · #6 redactar y enviar |
| Detección local | #33 preprocesamiento en Kotlin · #34 modelo LiteRT frente a Keras · #7 clasificación local · #8 señales · #9 nivel de riesgo · #20 sin internet · #21 demora acotada · #35 modelo versionado en actualización firmada |
| Alerta e historial | #10 alerta · #11 explicación sencilla · #12 historial · #17 estado de la protección |
| Mitigación y reporte | #13 modo manual, automático o desactivado · #14 bloquear remitente · #15 retirar del buzón · #16 marcar como seguro y revertir · #26 modo de reporte · #27 reportar · #28 y #29 acciones en el historial · #31 pantalla de ajustes |
| Aporte opcional | #18 activar o revocar el aporte · #19 desidentificar antes de compartir |
| Evaluación | #22 flujo, permisos y reversión · #23 clasificación y comprensión de alertas |
| Ayuda al usuario | #88 recorrido guiado con Smishy (ver `docs/recorrido-guiado.md`) |
| Reemplazo de la app de mensajería (fase 1) | #48 recibir y guardar MMS · #49 avisar de cada SMS recibido · #50 leídos y no leídos · #51 borrar mensajes y conversaciones · #52 borrar el historial y su retención · #53 nombre del contacto |

## Orden sugerido

1. **Base:** #1, luego #30 para tener pruebas desde el principio.
2. **Núcleo de detección** (no depende de la interfaz): #33, #34, #7. Con esto se valida que el modelo funciona en el
   teléfono antes de construir el resto.
3. **Mensajería:** #2, #3, #4, #5, #6.
4. **Alerta, historial y ajustes:** #8, #9, #10, #11, #12, #31, #17, #20, #21.
5. **Mitigación:** #13 a #16 y luego #26 a #29.
6. **Aporte opcional:** #19 y #18. El envío real depende del backend (`smishing-backend`), así que puede ir al final
   o probarse con un servidor simulado.
7. **Cierre:** #24, #32, #35, #22, #23.

## Objetivo: un demo en el emulador (decidido el 2026-11-11)

Las fases que siguen apuntan a un **demo de las funcionalidades completas en el emulador de Android**. El teléfono real queda para el final de
todo: #32 (protocolo en un dispositivo), #21 (demora en el teléfono objetivo), subir `targetSdk` y firmar el App Bundle (#24). **#48 (MMS) quedó
fuera de alcance**: la app no recibe MMS. Orden para el demo: #60 y #59, un modo de demostración con mensajes sintéticos, #51, #52, #53, #29 y el
aporte opcional (#18, #19, #26, #27) contra un servidor simulado.

## Alcance de la primera fase (prueba manual)

Se implementa esto y luego se detiene el desarrollo para probar todo en un teléfono; de lo que salga se crean más issues.

1. **Protección:** #13 + #31 (parcial: falta "Compartir reportes") + #25; luego #14, #15, #16, #28; luego #17 (estado de la protección
   y primer paso del onboarding).
2. **Base:** #32 (APK de prueba), #20 (sin internet), #21 (demora acotada).
3. **Reemplazo de la app de mensajería:** #48 a #53.
4. **Fase 2 (después de probar):** #18, #19, #26, #27, #29, #22, #23, #24, #35.

## Dependencias con otros repos

- #33, #34, #35 dependen de la release del modelo en `charcnn-smishing-model` (ver `model-contract.md`).
- #18 y #19 dependen del contrato de la API de aportes del backend y de las reglas de `sms-anonymizer`.
- #22 y #23 necesitan personas y dispositivos reales; no se pueden cerrar solo con pruebas automáticas.
