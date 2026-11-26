package com.app.smishing.ui.tutorial

import androidx.annotation.StringRes
import com.app.smishing.R

/** Los elementos que el recorrido señala. Se marcan con `Modifier.coachmarkTarget(TutorialTarget.X)`. */
enum class TutorialTarget {
    TabHome, TabMessages, TabHistory, TabSettings,
    ModeAutomatic, ModeAsk,
    SampleAlert, DetailBanner, DetailReasons,
}

/** El contenido de ejemplo que se muestra en lugar de la pantalla real. Es inventado: no toca el historial ni el modelo. */
enum class TutorialSample {
    /** Inicio con un aviso de ejemplo arriba. */
    Alert,

    /** El detalle de un mensaje de ejemplo. */
    Detail,
}

/** Un paso: qué elemento señala, en qué pestaña (0 Inicio, 1 Mensajes, 2 Historial, 3 Ajustes), qué dice y si usa un ejemplo. */
data class TutorialStep(
    val target: TutorialTarget,
    val tab: Int,
    @StringRes val title: Int,
    @StringRes val body: Int,
    val sample: TutorialSample? = null,
)

/** Los 9 pasos, en el orden del mockup. Para agregar o quitar uno basta tocar esta lista (y sus textos). */
val TutorialSteps: List<TutorialStep> = listOf(
    TutorialStep(TutorialTarget.TabHome, 0, R.string.tutorial_home_title, R.string.tutorial_home_body),
    TutorialStep(TutorialTarget.TabMessages, 1, R.string.tutorial_messages_title, R.string.tutorial_messages_body),
    TutorialStep(TutorialTarget.TabHistory, 2, R.string.tutorial_history_title, R.string.tutorial_history_body),
    TutorialStep(TutorialTarget.TabSettings, 3, R.string.tutorial_settings_title, R.string.tutorial_settings_body),
    TutorialStep(TutorialTarget.ModeAutomatic, 3, R.string.tutorial_automatic_title, R.string.tutorial_automatic_body),
    TutorialStep(TutorialTarget.ModeAsk, 3, R.string.tutorial_ask_title, R.string.tutorial_ask_body),
    TutorialStep(TutorialTarget.SampleAlert, 0, R.string.tutorial_alert_title, R.string.tutorial_alert_body, TutorialSample.Alert),
    TutorialStep(TutorialTarget.DetailBanner, 1, R.string.tutorial_level_title, R.string.tutorial_level_body, TutorialSample.Detail),
    TutorialStep(TutorialTarget.DetailReasons, 1, R.string.tutorial_reasons_title, R.string.tutorial_reasons_body, TutorialSample.Detail),
)
