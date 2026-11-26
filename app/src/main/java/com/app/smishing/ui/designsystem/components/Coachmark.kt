package com.app.smishing.ui.designsystem.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.app.smishing.R
import com.app.smishing.ui.designsystem.theme.Elevation
import com.app.smishing.ui.designsystem.theme.SmishingMotion
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.SmishingShapes
import com.app.smishing.ui.designsystem.theme.SmishingTypography
import com.app.smishing.ui.designsystem.theme.Spacing
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * Coachmarks: un globo de Smishy que señala un elemento de la pantalla y oscurece el resto (#88).
 *
 * Es genérico: no sabe de qué trata el recorrido. Quien lo usa
 *   1. marca los elementos con `Modifier.coachmarkTarget(clave)` (la clave puede ser cualquier objeto, por ejemplo un enumerado),
 *   2. provee un [CoachmarkRegistry] con [LocalCoachmarkRegistry], y
 *   3. dibuja un [CoachmarkOverlay] con el [CoachmarkMessage] del paso actual (o `null` para ocultarlo).
 * Con `target = null` el mensaje es una tarjeta centrada (la invitación y el cierre). Cambiar de mensaje mueve el foco y el globo
 * con una animación continua, en vez de cortar de uno a otro.
 */

/** Dónde está, en la ventana, cada elemento que un coachmark puede señalar. Solo guarda posiciones; no sabe de pasos. */
@Stable
class CoachmarkRegistry {
    private val bounds = mutableStateMapOf<Any, Rect>()

    fun boundsOf(key: Any): Rect? = bounds[key]

    internal fun update(key: Any, rect: Rect) {
        if (bounds[key] != rect) bounds[key] = rect
    }

    internal fun remove(key: Any) {
        bounds.remove(key)
    }
}

/** El registro de la pantalla. `null` (lo normal) si no hay un recorrido: marcar un elemento entonces no cuesta nada. */
val LocalCoachmarkRegistry = staticCompositionLocalOf<CoachmarkRegistry?> { null }

/** Marca este elemento como algo que un coachmark puede señalar con la clave [key]. Con `key` en `null` no hace nada. */
@Composable
fun Modifier.coachmarkTarget(key: Any?): Modifier {
    val registry = LocalCoachmarkRegistry.current
    if (registry == null || key == null) return this
    DisposableEffect(registry, key) { onDispose { registry.remove(key) } }
    return onGloballyPositioned { registry.update(key, it.boundsInWindow()) }
}

/**
 * Lo que dice un coachmark. [target] es la clave del elemento que señala, o `null` para una tarjeta centrada. [counter] ("2 de 9") se
 * muestra solo junto a un elemento. [secondaryLabel] es el botón de baja prioridad ("Omitir recorrido", "No, gracias"); sin él, solo hay
 * el principal. [note] va al final de la tarjeta centrada.
 */
data class CoachmarkMessage(
    val title: String,
    val body: String,
    val primaryLabel: String,
    val target: Any? = null,
    val counter: String? = null,
    val secondaryLabel: String? = null,
    val note: String? = null,
)

/** El coachmark en pantalla completa: [message] en `null` lo oculta con un fundido. */
@Composable
fun CoachmarkOverlay(
    message: CoachmarkMessage?,
    registry: CoachmarkRegistry,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Mientras se desvanece, el mensaje ya es `null`: se sigue dibujando el último para que no desaparezca de golpe.
    val last = remember { arrayOfNulls<CoachmarkMessage>(1) }
    if (message != null) last[0] = message
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        enter = fadeIn(tween(SmishingMotion.FadeMillis + 50)),
        exit = fadeOut(tween(SmishingMotion.FadeMillis)),
    ) {
        last[0]?.let { Overlay(it, registry, onPrimary, onSecondary) }
    }
}

private val HolePadding = 4.dp
private val HoleRadius = 16.dp
private val CardGap = 18.dp
private val ArrowHalf = 9.dp

@Composable
private fun Overlay(
    message: CoachmarkMessage,
    registry: CoachmarkRegistry,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
) {
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    var cardHeight by remember { mutableIntStateOf(0) }
    val rootModifier = Modifier
        .fillMaxSize()
        .onGloballyPositioned {
            origin = it.positionInWindow()
            area = it.size
        }
        // Tocar el fondo oscuro no hace nada, pero tampoco llega a lo que hay debajo.
        .pointerInput(Unit) { detectTapGestures { } }
    // Hasta saber el tamaño no hay dónde animar: la primera medida se hace con el fondo vacío.
    if (area == IntSize.Zero) {
        Box(rootModifier)
        return
    }

    // Lo que acaba de cambiar de pantalla aún no registró su posición: mientras tanto el foco se queda donde estaba.
    val lastBounds = remember { arrayOfNulls<Rect>(1) }
    val found = message.target?.let { registry.boundsOf(it) }?.translate(-origin)
    if (message.target == null) lastBounds[0] = null else if (found != null) lastBounds[0] = found
    val target = if (message.target == null) null else found ?: lastBounds[0]

    val padding = with(density) { HolePadding.toPx() }
    val radius = with(density) { HoleRadius.toPx() }
    val gap = with(density) { CardGap.toPx() }
    val margin = with(density) { Spacing.Md.toPx() }
    val centered = target == null
    val move = tween<Float>(SmishingMotion.MoveMillis, easing = SmishingMotion.Emphasized)

    // El foco: se desliza (y se redimensiona) hasta el elemento nuevo; sin elemento, se cierra en el centro.
    val hole by animateRectAsState(
        targetValue = target?.inflate(padding) ?: Rect(Offset(area.width / 2f, area.height / 2f), 0f),
        animationSpec = tween(SmishingMotion.MoveMillis, easing = SmishingMotion.Emphasized),
        label = "coachmark-hole",
    )

    // El globo: debajo del elemento si cabe y, si no, arriba; la tarjeta centrada, en el medio. Respeta las barras del sistema.
    val topInset = WindowInsets.systemBars.getTop(density)
    val bottomInset = WindowInsets.systemBars.getBottom(density)
    val below = target != null && target.bottom + padding + gap + cardHeight <= area.height - bottomInset - margin
    val wantedY = when {
        target == null -> (area.height - cardHeight) / 2f
        below -> target.bottom + padding + gap
        else -> target.top - padding - gap - cardHeight
    }
    val minY = topInset + margin
    val maxY = max(minY, area.height - bottomInset - margin - cardHeight)
    val cardY by animateFloatAsState(wantedY.coerceIn(minY, maxY), move, label = "coachmark-y")
    val arrowX by animateFloatAsState(target?.center?.x ?: (area.width / 2f), move, label = "coachmark-arrow")
    val cardInset = with(density) { (if (centered) Spacing.Xl else Spacing.Md).toPx() }

    Box(rootModifier) {
        Canvas(Modifier.fillMaxSize()) {
            // El fondo oscuro con un hueco: relleno par-impar, sin capas fuera de pantalla (más barato en un teléfono de gama baja).
            val scrim = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                addRoundRect(RoundRect(hole, CornerRadius(radius)))
            }
            drawPath(scrim, SmishingPalette.TextPrimary.copy(alpha = 0.62f))
            if (hole.width > 0f) {
                drawRoundRect(Color.White, hole.topLeft, hole.size, CornerRadius(radius), style = Stroke(2.dp.toPx()))
            }
        }
        // La tarjeta aparece con un leve escalado la primera vez; después solo se mueve y cambia de texto.
        val entered = remember { MutableTransitionState(false).apply { targetState = true } }
        AnimatedVisibility(
            visibleState = entered,
            enter = fadeIn(tween(SmishingMotion.EnterMillis - 120)) +
                scaleIn(tween(SmishingMotion.EnterMillis, easing = SmishingMotion.EmphasizedDecelerate), initialScale = 0.92f),
        ) {
            Box(
                Modifier
                    .offset { IntOffset(0, cardY.roundToInt()) }
                    .padding(horizontal = with(density) { cardInset.toDp() })
                    .fillMaxWidth()
                    .onSizeChanged { cardHeight = it.height }
                    .shadow(Elevation.High, SmishingShapes.X2l, clip = false)
                    .drawBehind {
                        if (!centered) {
                            // La flecha que une el globo con el elemento.
                            val x = (arrowX - cardInset).coerceIn(ArrowHalf.toPx() * 2, size.width - ArrowHalf.toPx() * 2)
                            val half = ArrowHalf.toPx()
                            val edge = if (below) 0f else size.height
                            val tip = if (below) -half else size.height + half
                            drawPath(
                                Path().apply { moveTo(x - half, edge); lineTo(x, tip); lineTo(x + half, edge); close() },
                                Color.White,
                            )
                        }
                    }
                    .background(Color.White, SmishingShapes.X2l),
            ) {
                // Un texto se desvanece y entra el siguiente; el tamaño salta de una vez para no mover el globo a mitad de camino.
                AnimatedContent(
                    targetState = message,
                    transitionSpec = {
                        (fadeIn(tween(SmishingMotion.FadeMillis, delayMillis = 100)) togetherWith fadeOut(tween(120)))
                            .using(SizeTransform(clip = false) { _, _ -> snap() })
                    },
                    label = "coachmark-content",
                ) { shown ->
                    CoachmarkCard(shown, onPrimary, onSecondary)
                }
            }
        }
    }
}

/** El contenido de la tarjeta: centrada (con `target` en `null`) o junto a un elemento. */
@Composable
private fun CoachmarkCard(message: CoachmarkMessage, onPrimary: () -> Unit, onSecondary: () -> Unit) {
    val centered = message.target == null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(if (centered) Spacing.Xl else Spacing.Md)
            // Un lector de pantalla lee el mensaje nuevo cuando cambia.
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(Spacing.Xs),
    ) {
        if (centered) {
            CoachSmishy(Modifier.size(width = 80.dp, height = 98.dp))
            Text(message.title, style = SmishingTypography.TitleMedium, color = SmishingPalette.TextPrimary, textAlign = TextAlign.Center)
            Text(message.body, style = SmishingTypography.BodyLarge, color = SmishingPalette.TextPrimary, textAlign = TextAlign.Center)
            PrimaryButton(message.primaryLabel, onPrimary, modifier = Modifier.fillMaxWidth().padding(top = Spacing.Sm))
            message.secondaryLabel?.let { SecondaryButton(it, onSecondary, modifier = Modifier.fillMaxWidth()) }
            message.note?.let {
                Text(it, style = SmishingTypography.Note, color = SmishingPalette.TextSecondary, textAlign = TextAlign.Center)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                CoachSmishy(Modifier.size(width = 40.dp, height = 49.dp))
                Text(message.title, style = SmishingTypography.BodyLargeBold, color = SmishingPalette.TextPrimary, modifier = Modifier.weight(1f))
                message.counter?.let {
                    Text(
                        it,
                        style = SmishingTypography.Note,
                        color = SmishingPalette.TextSecondary,
                        modifier = Modifier
                            .border(1.dp, SmishingPalette.Border, SmishingShapes.Full)
                            .padding(horizontal = Spacing.Sm, vertical = 2.dp),
                    )
                }
            }
            Text(message.body, style = SmishingTypography.BodyMedium, color = SmishingPalette.TextPrimary)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.Xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (message.secondaryLabel != null) {
                    LinkButton(message.secondaryLabel, onSecondary, color = SmishingPalette.TextSecondary)
                } else {
                    Box(Modifier)
                }
                PrimaryButton(message.primaryLabel, onPrimary)
            }
        }
    }
}

/** Smishy, que flota apenas (3 dp) mientras habla. Si las animaciones del sistema están apagadas, se queda quieto. */
@Composable
private fun CoachSmishy(modifier: Modifier = Modifier) {
    val bob = if (rememberAnimationsEnabled()) {
        rememberInfiniteTransition(label = "coachmark-smishy").animateFloat(
            initialValue = 0f,
            targetValue = -3f,
            animationSpec = infiniteRepeatable(
                tween(durationMillis = 1300, easing = SmishingMotion.Emphasized),
                RepeatMode.Reverse,
            ),
            label = "coachmark-bob",
        )
    } else {
        null
    }
    // La imagen es decorativa: el texto ya dice todo.
    Image(
        painter = painterResource(R.drawable.smishy_active),
        contentDescription = null,
        // Leer el valor aquí, y no al componer, evita recomponer en cada cuadro.
        modifier = modifier.graphicsLayer { translationY = (bob?.value ?: 0f) * density },
    )
}
