# App Bundle firmado y Google Play

Registro de #24 (RES-05): cómo se genera el App Bundle firmado, qué lleva, y qué hay que declarar y tener listo en Google Play.
Última verificación: 2026-11-23. **Publicar no está hecho**: el bundle se puede generar y firmar, pero la aprobación de Google se
registra aparte y depende de cosas que no están en este repositorio (ver "Lo que falta para publicar").

## Generar el bundle

```powershell
.\gradlew.bat bundleRelease
```

Sale en `app/build/outputs/bundle/release/app-release.aab`. Según la configuración de firma (siguiente sección) queda firmado o no.

### La clave de subida

Google Play pide el bundle firmado con una **clave de subida** (*upload key*) y lo vuelve a firmar con su propia clave de firma de la app
(*Play App Signing*): la que reciben los usuarios la guarda Google, no nosotros. La clave de subida se mantiene en secreto, pero si se
pierde o se compromete, Google permite pedir que se restablezca sin afectar a los usuarios ([fuente](https://support.google.com/googleplay/android-developer/answer/9842756)).

Se genera **una sola vez**, en una carpeta que no sea el repositorio, y se guarda una copia en un lugar seguro del equipo:

```powershell
keytool -genkeypair -v -keystore upload-key.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

`keytool` pregunta las contraseñas y los datos del certificado. Las contraseñas y el archivo **no van en Git** (`*.jks`, `*.keystore` y
`keystore.properties` están en `.gitignore`).

### Cómo la usa el build

`app/build.gradle.kts` firma `release` si encuentra los cuatro datos, en este orden: variables de entorno y, si no, `keystore.properties`
en la raíz del proyecto:

| Dato | Variable de entorno | Clave en `keystore.properties` |
|---|---|---|
| Archivo de la clave (ruta absoluta o relativa a la raíz) | `SMISHING_UPLOAD_STORE_FILE` | `storeFile` |
| Contraseña del archivo | `SMISHING_UPLOAD_STORE_PASSWORD` | `storePassword` |
| Nombre de la clave dentro del archivo | `SMISHING_UPLOAD_KEY_ALIAS` | `keyAlias` |
| Contraseña de la clave | `SMISHING_UPLOAD_KEY_PASSWORD` | `keyPassword` |

Ejemplo de `keystore.properties` (valores de ejemplo, no reales):

```properties
storeFile=C:/claves/upload-key.jks
storePassword=cambiar-esto
keyAlias=upload
keyPassword=cambiar-esto
```

**Sin esos datos, el bundle sale sin firmar**: compila igual (el CI no tiene la clave), pero Google Play no lo acepta.

### Qué se comprobó

- Con una clave **desechable** (generada para la prueba, ya descartada), el bundle salió firmado y `jarsigner -verify` dijo «jar verified».
- Sin clave, salió sin firma (sin entradas `META-INF/*.SF`) y del mismo tamaño que antes de agregar la configuración.
- **No se firmó con una clave de subida real**: no existe todavía. Quien la genere debe repetir `jarsigner -verify -keystore upload-key.jks app-release.aab`.

## Qué lleva el bundle

Comprobado en el bundle de `release` (2026-11-23): el modelo (`base/assets/smishing_charcnn.tflite`), su `preprocessing_config.json`, las
reglas de señales (`signal_rules.json`), el manifiesto, las bibliotecas nativas de `arm64-v8a`, `armeabi-v7a`, `x86` y `x86_64`, y el
`mapping` de R8 en `BUNDLE-METADATA` (Google Play lo usa para leer los errores del release). `minSdk` 29, `targetSdk` 36, `versionCode` 1,
`versionName` 1.0. Cada subida a Google Play necesita un `versionCode` mayor que el anterior: se sube a mano en `app/build.gradle.kts`.

## Minificado con R8

`release` (y `qa`, que parte de él) se compila con R8: recorta el código que no se usa, acorta los nombres y quita los recursos sin usar.

| | Sin R8 | Con R8 |
|---|---|---|
| App Bundle | 21,55 MB | **13,22 MB** |
| APK `qa` (las cuatro arquitecturas) | 55,14 MB | **22,81 MB** |
| Código (`dex`) dentro del bundle, comprimido | 12,14 MB | 1,48 MB |

El resto del bundle (unos 8 MB de bibliotecas nativas) no cambia. Lo que recibe cada teléfono es menos que el bundle, porque Google Play
entrega solo la arquitectura de ese teléfono; **no se midió** (haría falta `bundletool`).

**Qué se probó con la app minificada** (APK `qa` en el emulador, Android 16): abrir la app, la lista de mensajes, recibir un SMS y
analizarlo con el modelo (`Alerted`), el Historial, el detalle del análisis (señales y acciones) y Ajustes. No hubo ningún fallo.

**Qué no se probó**: el envío de aportes con una respuesta real del backend (la compilación local apunta a `https://localhost:8443`, así que solo
llegó hasta intentar conectar; falta probarlo con el APK de GitHub, que usa el backend de Azure), el bloqueo y borrado de mensajes, el envío de
SMS, ni en versiones de Android anteriores a la 16. Las pruebas de la JVM corren sobre `debug`, sin minificar.

**Qué no hace R8**: ocultar secretos. Acorta nombres, pero los textos del programa, como la clave del backend, siguen dentro del APK y se pueden
extraer. El mapping (`mapping.txt`) traduce los errores del release a los nombres reales; el workflow de GitHub lo sube como el artefacto
`app-qa-mapping` y Google Play lo recibe dentro del bundle.

## Declaración de permisos en Google Play

Google Play trata los permisos de SMS como de alto riesgo. Según su política ([fuente](https://support.google.com/googleplay/android-developer/answer/9047303)):

- Solo se permiten para una app que sea el **gestor de SMS predeterminado** (también teléfono o asistente). Debe estar registrada como tal
  **antes** de pedir los permisos, y dejar de usarlos si deja de serlo. Es el orden que ya sigue la app (rol primero, permisos después; ver
  [permisos-sms.md](permisos-sms.md)).
- Hay que completar en Play Console el **formulario de declaración de permisos**. Google revisa la funcionalidad principal de la app, la
  revisión puede tardar **varias semanas** y, si cambia el uso de los permisos, hay que volver a enviar el formulario. Sin formulario, la app
  puede ser retirada.

Los permisos de la app (manifiesto fusionado de `release`) y dónde se declara cada uno. La justificación completa está en [permisos-sms.md](permisos-sms.md).

| Permiso | Para qué | Dónde se declara |
|---|---|---|
| `READ_SMS`, `RECEIVE_SMS`, `SEND_SMS` | la app **es** la de mensajería: recibe, muestra y envía los SMS, y los analiza | formulario de declaración de permisos de SMS |
| `READ_CONTACTS` | mostrar el nombre del contacto en lugar del número (opcional) | no es de los permisos de SMS; **no se verificó** si la consola pide algo más |
| `POST_NOTIFICATIONS` | avisar de un mensaje riesgoso | permiso normal; **no se verificó** en la consola |
| `INTERNET`, `ACCESS_NETWORK_STATE` | enviar el aporte anónimo opcional, solo con consentimiento y por HTTPS | permisos normales; **no se verificó** en la consola |
| `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` | los agrega WorkManager para retomar el envío de aportes tras un reinicio | permisos normales; **no se verificó** en la consola |

### Borrador para el formulario (funcionalidad principal)

> La app es una aplicación de mensajería SMS y es el gestor de SMS predeterminado del teléfono. Recibe, muestra y envía los SMS, y analiza
> cada mensaje recibido **en el teléfono, sin conexión**, para avisar a la persona si parece un intento de estafa (smishing). Necesita leer
> y recibir SMS para analizarlos y mostrarlos, y enviar SMS para que la persona responda. No usa los permisos para otra cosa.

Es un borrador en español: el formulario y su revisión son de Google y pueden pedir cambios. Hay que revisarlo con quien lo presente.

## Seguridad de los datos y privacidad

Google Play exige completar el formulario de **seguridad de los datos** y una **URL pública de política de privacidad** (activa, sin PDF),
además de un enlace o texto de privacidad dentro de la app, aunque la app no recoja datos ([fuente](https://support.google.com/googleplay/android-developer/answer/10787469)).
Puntos que el formulario va a pedir y que hay que decidir con el equipo:

- **Qué se recoge y se comparte:** solo con el aporte opcional activado, el **texto de un mensaje peligroso o sospechoso**, anonimizado en el
  teléfono antes de salir, junto con la etiqueta y la versión del modelo. No se envía ningún identificador del dispositivo, nombre, número ni contacto.
- **Cómo viaja:** por HTTPS. **Es opcional** y se puede desactivar en Ajustes; al desactivarlo se vacía la cola de pendientes.
- **Lo que el backend hace con él:** revisión humana antes de reentrenar; la retención y el borrado dependen del backend (`smishing-backend`) y **no
  están definidos en este repositorio**. Como no guarda nada que identifique al dispositivo, no hay forma de buscar «los aportes de una persona»;
  esto tiene que quedar dicho en la política de privacidad.
- **Texto de privacidad dentro de la app:** el aviso de consentimiento ya lo describe y tiene un enlace «Ver términos y condiciones».

## Lo que falta para publicar

Nada de esto está hecho ni lo puede hacer este repositorio:

1. **Cuenta de Play Console** del equipo, y crear la app con su `applicationId` (`com.app.smishing`, que queda fijo una vez publicada).
2. **La clave de subida real** (ver arriba) y activar *Play App Signing*.
3. **URL pública de la política de privacidad**, y el formulario de seguridad de los datos.
4. **El formulario de declaración de permisos de SMS**, con la aprobación de Google (semanas). Esa aprobación se registra aparte, como dice #24.
5. **La ficha de la tienda:** descripción, ícono, capturas, clasificación de contenido y público objetivo.
6. **Pruebas internas o cerradas:** Google puede exigir un periodo de pruebas antes de publicar según el tipo de cuenta; **no se verificó** qué
   exige la de este equipo. Las pruebas en el carril interno no requieren el formulario de seguridad de los datos.
7. **Un teléfono real:** la app no se ha probado en uno (#32).

## Fuentes

- [Uso de los permisos de SMS y registro de llamadas](https://support.google.com/googleplay/android-developer/answer/9047303) (Google Play Console)
- [Sección de seguridad de los datos](https://support.google.com/googleplay/android-developer/answer/10787469) (Google Play Console)
- [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756) (Google Play Console)
