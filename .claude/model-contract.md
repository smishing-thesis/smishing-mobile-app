# Contrato con el modelo

El modelo se entrena y exporta en `charcnn-smishing-model`. La app no entrena: consume archivos versionados y debe
replicar el preprocesamiento exactamente. Fuente: pre-release `v1.0.0` de ese repo (modelo 1.0.0, preprocesamiento 1.1.0).

## Archivos que consume la app

| Archivo | Para qué |
|---|---|
| `smishing_charcnn.tflite` | el modelo (unos 120 KB), se empaqueta dentro de la app |
| `preprocessing_config.json` | todo lo que el cliente debe replicar: limpieza, normalización v2 con la tabla de confusables, reglas de enmascarado, alfabeto, `char2idx`, longitud máxima, umbral, política de entrada y versiones |
| `preprocessing_vectors.json` | mensajes de prueba con la entrada esperada del modelo y la decisión de la política de entrada |
| `parity_report.json` | puntaje y decisión del `.tflite` para esos mensajes, para comparar LiteRT en un dispositivo |

Verificar el SHA-256 del `.tflite` contra el de la release antes de empaquetarlo. La identidad del modelo es el
`.tflite`; el `.keras` no es reproducible byte a byte.

## Cómo usarlos

1. Leer `preprocessing_config.json` en tiempo de ejecución; no copiar sus valores al código (longitud, umbral,
   alfabeto, reglas). Si cambia el JSON, no debe hacer falta tocar Kotlin salvo que cambie la versión del
   preprocesamiento.
2. Aplicar, en orden, limpieza → normalización → enmascarado → política de entrada → codificación a índices.
3. Política de entrada: un mensaje con menos de 15 caracteres limpios no se clasifica (no se alerta ni se bloquea).
4. Decidir con el umbral del JSON, no con 0.5.
5. Guardar con cada resultado la versión del modelo (`model.version`) para la trazabilidad (RNF-07).

## Cómo validar

- **Preprocesamiento (#33):** una prueba unitaria recorre todos los casos de `preprocessing_vectors.json` y compara
  limpieza, normalización, enmascarado y entrada del modelo con lo esperado. Debe pasar el 100 %.
- **Modelo en dispositivo (#34):** correr el `.tflite` en un dispositivo o emulador con los mismos casos y comparar
  decisión (y puntaje, con tolerancia pequeña) con `parity_report.json`. Registrar el dispositivo y la versión de LiteRT.
- **Actualización (#35):** cuando salga otro modelo, cambia la versión del modelo y se vuelven a pasar las dos pruebas.

## Límites conocidos del modelo (no prometer más en la interfaz)

- Los mensajes cortos o conversacionales pueden marcarse como sospechosos; la política de entrada solo evita los muy cortos.
- Detecta peor el smishing de premios o de agencias que el de entidades bancarias.
- La referencia del paper para esta receta en mensajes peruanos externos es recall 0.618 ± 0.122 con FPR 1 % en validación.
  Son cifras de laboratorio: no son una garantía en el teléfono.
- Un baseline TF-IDF con regresión logística rindió igual o mejor en el corpus actual (decisión pendiente en el repo del modelo).

El detalle de la política de preprocesamiento está en `docs/preprocessing.md` del repo del modelo.
