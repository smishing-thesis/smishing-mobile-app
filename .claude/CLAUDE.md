# smishing-mobile-app: contexto para sesiones de Claude

Repo de la app Android de la tesis. Este archivo se carga solo; el detalle está en:

- [architecture.md](architecture.md): componentes, almacenamiento y flujos de la app según la memoria.
- [model-contract.md](model-contract.md): qué se consume del repo del modelo y cómo validarlo.
- [backlog.md](backlog.md): las historias de usuario agrupadas por área y un orden sugerido.

## La tesis en breve

Detector de smishing en SMS en español que corre dentro del teléfono, sin internet, para Android de gama baja.
La app es además el gestor de SMS predeterminado: recibe, muestra, envía y analiza los mensajes, y puede avisar,
bloquear al remitente o retirar el mensaje del buzón, según el modo que elija la persona.
El modelo es un Char-CNN (reimplementación de Seo et al., 2024) exportado a TFLite/LiteRT; se entrena en otro repo.

- **Plataforma:** Android, minSdk 29, Kotlin, LiteRT (TFLite) para la inferencia.
- **Sin red para clasificar.** La red solo se usa para el aporte opcional al corpus, y solo con consentimiento activo.
- **Aporte opcional:** el mensaje se anonimiza en el teléfono antes de enviarse al backend por HTTPS. El backend no
  guarda nada que identifique al dispositivo, así que el consentimiento se verifica en el teléfono antes de enviar.
- **Mensajes reales:** nunca van al repositorio. Las pruebas usan mensajes sintéticos.

## Organización de GitHub (`smishing-thesis`)

| Repo | Contenido |
|---|---|
| `smishing-mobile-app` | este repo: la app Android |
| `charcnn-smishing-model` | modelo, experimentos, exportación TFLite; publica el modelo versionado (release v1.0.0) |
| `smishing-backend` | API FastAPI del aporte opcional (otro integrante del equipo) |
| `sms-anonymizer` | anonimización y etiquetado del corpus peruano; sus reglas son la referencia para anonimizar en el teléfono |

El backlog es el proyecto 1 de la organización (`smishing thesis`). Las historias son issues reales, sin prefijo
`HU-N` en el título; el número de issue es por repo. Los issues van en español, con tipo Task o Feature, etiquetas
en inglés y el campo Priority de la organización.

## Reglas de trabajo

- Explicar antes de aplicar cambios. No hacer commit ni push salvo que se pida. No agregar a Claude como coautor.
- Una rama y un PR por issue, nunca commits en `main`. Rama `<n>-<tema-corto>` y `Closes #<n>` en el PR.
- Ningún archivo versionado debe contener texto de mensajes reales, números ni nombres.
- No registrar el contenido de los SMS en logs, ni siquiera en depuración.
- Pedir solo los permisos que una historia justifique; cada permiso nuevo se documenta con su motivo.
- El preprocesamiento en Kotlin se valida con los vectores del modelo, no a ojo (ver `model-contract.md`).
- Registrar los resultados honestamente: si una prueba falla o no se pudo medir, decirlo.
- Respuestas cortas: la usuaria prefiere resúmenes breves.

## Pendiente de decidir (no asumir)

- Arquitectura de UI (Compose o vistas), inyección de dependencias y estructura de módulos.
- Cómo se implementa el límite de aportes en el teléfono y la autenticación hacia el backend (la define el backend).
