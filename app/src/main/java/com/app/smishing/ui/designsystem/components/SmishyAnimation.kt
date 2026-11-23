package com.app.smishing.ui.designsystem.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** Un instante de la animación de Smishy investigando. */
data class SmishyFrame(
    /** Cuánto se desplazan las pupilas en horizontal, en unidades del dibujo (120 de ancho). 2 es la mirada normal. */
    val gazeX: Float,
    /** 1 = ojos abiertos, cerca de 0 = cerrados (parpadeo). */
    val eyeOpen: Float,
    /** Opacidad de los lentes y las cejas, que aparecen y desaparecen juntos. */
    val accessories: Float,
    /** 0 = boca recta, 1 = sonrisa. */
    val smile: Float,
)

/**
 * La línea de tiempo de "Smishy investigando" (5 s, una sola vez). Es la misma de la vista previa
 * `designs/smishy/animacion_investigando.html`; si se cambia una, se cambia la otra.
 *
 * 0 a 0,5 s aparecen lentes y cejas; 0,5 a 3,9 s mira al frente, a la derecha y a la izquierda; 3,9 a 4,4 s vuelve al frente,
 * parpadea y sonríe; 4,4 a 5 s lentes y cejas desaparecen.
 */
object SmishyInvestigatingTimeline {
    const val DURATION_MS = 5000

    private val gaze = listOf(
        0f to 2f, 500f to 0f, 1000f to 0f, 1500f to 5f, 2000f to 5f, 2900f to -5f, 3400f to -5f, 3900f to 2f, 5000f to 2f,
    )

    private fun ease(x: Float): Float =
        if (x < 0.5f) 4f * x * x * x else 1f - ((-2f * x + 2f).let { it * it * it }) / 2f

    private fun unit(x: Float) = x.coerceIn(0f, 1f)

    private fun gazeAt(t: Float): Float {
        for (i in 1 until gaze.size) {
            val (t1, v1) = gaze[i]
            if (t <= t1) {
                val (t0, v0) = gaze[i - 1]
                return v0 + (v1 - v0) * ease((t - t0) / (t1 - t0))
            }
        }
        return gaze.last().second
    }

    fun frameAt(timeMs: Float): SmishyFrame {
        val t = timeMs.coerceIn(0f, DURATION_MS.toFloat())
        val blink = if (t > 3950f && t < 4210f) {
            val k = (t - 3950f) / 260f
            1f - 0.9f * (if (k < 0.5f) k * 2f else (1f - k) * 2f)
        } else {
            1f
        }
        val appear = ease(unit(t / 500f))
        val disappear = 1f - ease(unit((t - 4400f) / 600f))
        val smile = when {
            t < 500f -> 1f - ease(t / 500f)
            t < 3900f -> 0f
            else -> ease(unit((t - 3900f) / 450f))
        }
        return SmishyFrame(gazeX = gazeAt(t), eyeOpen = blink, accessories = minOf(appear, disappear), smile = smile)
    }

    /** El último cuadro: el Smishy normal, sonriendo y sin lentes. */
    val last: SmishyFrame get() = frameAt(DURATION_MS.toFloat())
}

/** `false` si la persona desactivó las animaciones del sistema (accesibilidad): entonces Smishy no se anima. */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}

/**
 * Smishy con lentes, investigando: la animación de la pantalla de carga. Se reproduce una vez y avisa con [onFinished].
 * Si las animaciones del sistema están desactivadas, muestra de inmediato el Smishy normal y avisa al instante.
 * Está dibujado sobre fondo azul (borde claro), como `smishy_splash`.
 */
@Composable
fun SmishyInvestigating(modifier: Modifier = Modifier, onFinished: () -> Unit = {}) {
    val animate = rememberAnimationsEnabled()
    val end = SmishyInvestigatingTimeline.DURATION_MS.toFloat()
    val time = remember { Animatable(if (animate) 0f else end) }
    LaunchedEffect(animate) {
        if (animate) time.animateTo(end, tween(SmishyInvestigatingTimeline.DURATION_MS, easing = LinearEasing))
        onFinished()
    }
    Canvas(modifier.aspectRatio(VIEW_WIDTH / VIEW_HEIGHT)) {
        drawSmishy(SmishyInvestigatingTimeline.frameAt(time.value))
    }
}

/**
 * Tres puntos que suben y bajan uno tras otro, como una ola, para decir que está cargando. Con las animaciones desactivadas, quedan
 * quietos (más claros hacia la derecha, para que no parezcan una fila de puntos cualquiera).
 */
@Composable
fun SmishyLoadingDots(modifier: Modifier = Modifier, color: Color = Color.White) {
    val animate = rememberAnimationsEnabled()
    // Una vuelta completa de la ola (1,2 s). El punto i va 0,15 s detrás del anterior, es decir, 0,125 de vuelta.
    val phase = if (animate) {
        rememberInfiniteTransition(label = "dots").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(LOADING_WAVE_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "phase",
        )
    } else {
        null
    }
    val density = LocalDensity.current
    val rise = with(density) { LOADING_WAVE_RISE.dp.toPx() }
    // La fila mide lo que sube el punto más lo que mide un punto, para que el texto de arriba no se mueva con la ola.
    Row(
        modifier.height((LOADING_DOT + LOADING_WAVE_RISE).dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        repeat(3) { i ->
            Canvas(
                Modifier
                    .size(LOADING_DOT.dp)
                    .graphicsLayer {
                        val p = phase?.value
                        // Sube durante el 60 % de la vuelta (con forma de seno) y descansa el resto.
                        val t = if (p == null) 1f else ((p - i * 0.125f) % 1f + 1f) % 1f / 0.6f
                        translationY = if (t < 1f) -rise * sin(PI.toFloat() * t) else 0f
                    },
            ) {
                drawCircle(if (animate) color else color.copy(alpha = listOf(1f, 0.6f, 0.3f)[i]))
            }
        }
    }
}

private const val LOADING_WAVE_MS = 1200
private const val LOADING_DOT = 12
private const val LOADING_WAVE_RISE = 14

// --- Dibujo: las mismas formas de `designs/smishy/*.svg`, en un cuadro de 120 x 146. ---

private const val VIEW_WIDTH = 120f
private const val VIEW_HEIGHT = 146f

private val Ink = Color(0xFF202124)
private val Floor = Color(0xFF1557B0)
private val RimLeft = Color(0xFFB5D0F8)
private val RimRight = Color(0xFFE8F0FE)
private val FaceLeft = Color(0xFF4F94EE)
private val FaceRight = Color(0xFF8AB4F8)

private val leftHalf = Path().apply {
    moveTo(60f, 8f); lineTo(12f, 26f); lineTo(12f, 68f); cubicTo(12f, 100f, 34f, 122f, 60f, 134f); close()
}
private val rightHalf = Path().apply {
    moveTo(60f, 8f); lineTo(108f, 26f); lineTo(108f, 68f); cubicTo(108f, 100f, 86f, 122f, 60f, 134f); close()
}

private val lineStroke = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.drawSmishy(frame: SmishyFrame) {
    val unit = size.width / VIEW_WIDTH
    scale(unit, unit, pivot = Offset.Zero) {
        drawOval(Floor, topLeft = Offset(26f, 136f), size = Size(68f, 8f))
        drawPath(leftHalf, RimLeft)
        drawPath(rightHalf, RimRight)
        scale(0.84f, 0.84f, pivot = Offset(60f, 72f)) {
            drawPath(leftHalf, FaceLeft)
            drawPath(rightHalf, FaceRight)
        }
        // Ojos: el parpadeo los aplasta hacia su centro.
        scale(1f, frame.eyeOpen, pivot = Offset(60f, 64f)) {
            drawCircle(Color.White, radius = 11f, center = Offset(46f, 64f))
            drawCircle(Color.White, radius = 11f, center = Offset(74f, 64f))
            drawCircle(Ink, radius = 5.5f, center = Offset(46f + frame.gazeX, 66f))
            drawCircle(Ink, radius = 5.5f, center = Offset(74f + frame.gazeX, 66f))
        }
        // Lentes y cejas: una sola opacidad, para que aparezcan y desaparezcan al mismo tiempo.
        if (frame.accessories > 0f) {
            val c = Ink.copy(alpha = frame.accessories)
            drawCircle(c, radius = 14f, center = Offset(46f, 64f), style = lineStroke)
            drawCircle(c, radius = 14f, center = Offset(74f, 64f), style = lineStroke)
            drawLine(c, Offset(32f, 62f), Offset(23f, 58f), strokeWidth = 3.5f, cap = StrokeCap.Round)
            drawLine(c, Offset(88f, 62f), Offset(97f, 58f), strokeWidth = 3.5f, cap = StrokeCap.Round)
            // Una ceja alta y arqueada y la otra baja: pensativo.
            drawPath(Path().apply { moveTo(35f, 41f); quadraticTo(46f, 34f, 57f, 40f) }, c, style = lineStroke)
            drawLine(c, Offset(64f, 46f), Offset(85f, 42f), strokeWidth = 3.5f, cap = StrokeCap.Round)
        }
        // Boca: de recta a sonrisa.
        val mouthX = 52f - 2f * frame.smile
        val mouthY = 89f - 3f * frame.smile
        val mouthControlY = 89f + 7f * frame.smile
        drawPath(
            Path().apply { moveTo(mouthX, mouthY); quadraticTo(60f, mouthControlY, VIEW_WIDTH - mouthX, mouthY) },
            Ink,
            style = lineStroke,
        )
    }
}
