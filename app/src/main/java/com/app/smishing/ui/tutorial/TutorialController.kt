package com.app.smishing.ui.tutorial

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.app.smishing.data.settings.DataStoreTutorialProgress
import com.app.smishing.domain.tutorial.TutorialFlow
import com.app.smishing.domain.tutorial.TutorialPhase
import com.app.smishing.domain.tutorial.TutorialProgress
import com.app.smishing.domain.tutorial.TutorialState
import com.app.smishing.domain.tutorial.TutorialTransition
import com.app.smishing.ui.designsystem.components.CoachmarkRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * El recorrido guiado (#88) en pantalla: en qué punto va ([phase]), qué decidió la persona ([saved], para Ajustes) y dónde está lo que
 * se señala ([registry]). Las reglas están en [TutorialFlow]; esto solo las aplica y guarda la decisión fuera del hilo principal.
 */
@Stable
class TutorialController(
    private val progress: TutorialProgress,
    private val scope: CoroutineScope,
    private val flow: TutorialFlow = TutorialFlow(TutorialSteps.size),
) {
    val registry = CoachmarkRegistry()

    var phase: TutorialPhase by mutableStateOf(TutorialPhase.Hidden)
        private set

    /** Lo guardado, o `null` mientras se lee. */
    var saved: TutorialState? by mutableStateOf(null)
        private set

    val active: Boolean get() = phase != TutorialPhase.Hidden

    /** El paso en curso, o `null` en la invitación, en el cierre y sin recorrido. */
    val step: TutorialStep? get() = (phase as? TutorialPhase.Step)?.let { TutorialSteps[it.index] }

    /** El ejemplo que el paso en curso muestra en lugar de la pantalla real, o `null`. */
    val sample: TutorialSample? get() = step?.sample

    /** Lee lo guardado y, si el recorrido sigue pendiente, ofrece la invitación. No hace nada si ya hay un recorrido en curso. */
    suspend fun offerIfPending() {
        val state = withContext(Dispatchers.IO) { progress.state() }
        saved = state
        if (state == TutorialState.Pending && phase == TutorialPhase.Hidden) phase = TutorialPhase.Invite
    }

    /** El botón principal: aceptar la invitación, "Siguiente" o "Empezar". */
    fun next() = move(flow.next(phase))

    /** "No, gracias" u "Omitir recorrido": se omite por completo y no vuelve a salir solo. */
    fun skip() = move(flow.skip(phase))

    /** "Ver recorrido" de Ajustes: vuelve a pendiente y empieza de inmediato. */
    fun restart() = move(flow.restart())

    /** Atrás del sistema: cierra el recorrido sin decidir nada; sigue pendiente y la invitación vuelve a salir. */
    fun pause() {
        phase = TutorialPhase.Hidden
    }

    private fun move(transition: TutorialTransition) {
        phase = transition.phase
        transition.save?.let { state ->
            saved = state
            scope.launch(Dispatchers.IO) { progress.setState(state) }
        }
    }
}

@Composable
fun rememberTutorialController(): TutorialController {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    return remember(context) { TutorialController(DataStoreTutorialProgress(context), scope) }
}
