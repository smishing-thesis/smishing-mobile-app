# Sin internet y demora hasta el nivel de riesgo

Registro de #20 (RNF-03, sin internet) y #21 (RNF-01 y RNF-09, demora acotada). Última verificación: 2026-11-10, en el emulador de
API 29 (`Android SDK built for x86_64`, Android 10) sobre un computador de escritorio. **Ninguna cifra de este documento vale para un
teléfono de gama baja**: #21 pide el dispositivo objetivo y eso aún no se midió (ver abajo).

## #20: recibir, clasificar y alertar sin internet

**La app no tiene cómo usar la red.** El manifiesto fusionado no declara `INTERNET` (ni `ACCESS_WIFI_STATE`): solo `ACCESS_NETWORK_STATE`,
que llega de una biblioteca y no permite abrir conexiones. Se comprobó en el manifiesto de depuración, en el APK `qa` con `aapt` y,
con una prueba, en lo que el sistema reporta como permisos pedidos por la app.

**Prueba funcional** (`SmsRoleInstrumentedTest.thenWithoutAnyConnectionAMessageIsReceivedClassifiedAndAlerted`):

1. Apaga el Wi-Fi y los datos móviles (`svc wifi disable`, `svc data disable`) y **espera a que no haya ninguna red activa durante tres
   segundos seguidos**. Apagarlas no es instantáneo, y mientras una cae la otra puede tomar el relevo un momento: la primera versión
   de la prueba dio por apagada una red que volvía.
2. Entrega un SMS de riesgo alto por el receptor real.
3. Comprueba que el mensaje **está en el buzón**, que su análisis quedó guardado con **nivel alto** y que **aparece la alerta**, y que
   seguía sin haber red al terminar. Siempre vuelve a encender el Wi-Fi y los datos.

Resultado: pasa. Si la app declarara `INTERNET`, la prueba falla (se comprobó añadiéndolo a propósito; se revirtió).

El aporte opcional al corpus (#18, #26, #27) sí usará la red, solo con consentimiento activo; no está hecho. Cuando exista, esta prueba
debe seguir pasando: clasificar y alertar no pueden depender de él.

## #21: demora desde la recepción hasta el nivel de riesgo

**Qué se mide** (`SmsRoleInstrumentedTest.thenTheLatencyFromReceptionToTheRiskLevelIsMeasured`): el tiempo desde que el SMS entra al
receptor real hasta que **el análisis con su nivel de riesgo queda guardado**, pasando por guardar el mensaje en el buzón, analizarlo
(modelo y señales) y guardar el resultado. La alerta se publica justo después. Se usan los mensajes del conjunto controlado que el
modelo evalúa (77, sintéticos), tres veces cada uno: **231 mediciones**. Los mensajes
con tildes, signos o más de 70 caracteres se arman en UCS-2 y en varias partes (ver `SmsPdu`). Se mide con el modo "solo avisarme", para
que bloquear o retirar no cuente. La resolución es de aproximadamente 1 ms.

**Objetivo:** P95 menor a 1 segundo. La prueba **falla** si no se cumple (se comprobó metiendo a propósito 1,1 s de espera en el análisis:
P95 de 1197 ms, falla; se revirtió).

**Resultado en el emulador** (tres corridas; el reporte de la última está en `docs/reportes/latencia-api29.json`):

| | Recepción → nivel de riesgo | Solo el análisis (modelo y señales) |
|---|---|---|
| Mediana | 72 a 75 ms | 2,3 a 2,4 ms |
| **P95** | **127 a 161 ms** | 4,9 a 5,2 ms |
| P99 | 166 a 216 ms | 6,7 a 12 ms |
| Máximo | 186 a 244 ms | 8 a 16 ms |

El P95 cumple el objetivo con un margen de 6 a 8 veces **en este emulador**. El análisis (modelo y señales) es de unos 2 ms: la mayor parte de
la demora medida es otra cosa (guardar en el buzón y en la base, y pasar entre hilos). No se desglosó más.

### Lo que no se midió

- **El dispositivo objetivo.** Un emulador x86_64 en un computador de escritorio no representa un teléfono Android de gama baja (procesador,
  memoria y almacenamiento mucho más lentos). **#21 sigue abierto** hasta medirlo allí.
- **El arranque en frío:** la primera medición no lo es, porque otras pruebas del mismo proceso ya cargaron el modelo. La primera llegada
  tras abrir el teléfono o tras un reinicio puede tardar más (cargar el modelo y abrir la base).
- **Con el teléfono bajo carga**, con poca batería o en ahorro de energía.
- La demora hasta que el usuario **ve** la notificación, que depende del sistema.

### Cómo medirlo en un teléfono

Con el teléfono por USB y la depuración activada, y **antes de usar la app con datos reales** (la prueba vacía el historial de la app,
instala la app de depuración, entrega y borra mensajes sintéticos del remitente `+999…` y al terminar desinstala la app):

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.app.smishing.data.sms.SmsRoleInstrumentedTest#thenTheLatencyFromReceptionToTheRiskLevelIsMeasured'
```

El reporte (con el modelo del teléfono, la versión de Android y las cifras, sin mensajes) queda en
`app/build/outputs/connected_android_test_additional_output/`. Es una compilación de depuración, que es algo más lenta que la `qa`.
Guardar el reporte en `docs/reportes/` con el nombre del dispositivo.

Para el arranque en frío, a mano: reiniciar el teléfono, enviar el mensaje de riesgo alto desde otro teléfono y anotar cuánto tarda en
aparecer la alerta (sin un cronómetro preciso, solo para saber si es de segundos o de décimas).
