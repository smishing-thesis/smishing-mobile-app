package com.app.smishing.domain.tutorial

/**
 * Qué decidió la persona del recorrido guiado (#88). Se guarda en el teléfono y sobrevive a cerrar la aplicación.
 * [Pending] es lo que hay hasta que lo termina u omite: mientras tanto, la invitación vuelve a salir al abrir la aplicación.
 */
enum class TutorialState(val id: String) {
    Pending("pending"),
    Done("done"),
    Skipped("skipped");

    companion object {
        /** El estado de un `id` guardado. Si falta o no se reconoce, vale [Pending]: lo que no se sabe no se da por visto ni por omitido. */
        fun fromId(id: String?): TutorialState = entries.firstOrNull { it.id == id } ?: Pending
    }
}

/** Dónde se guarda [TutorialState]. Puede bloquear: no se llama desde el hilo principal. */
interface TutorialProgress {
    fun state(): TutorialState

    fun setState(state: TutorialState)
}

/** En qué punto va el recorrido. */
sealed interface TutorialPhase {
    /** No se muestra nada. */
    data object Hidden : TutorialPhase

    /** La invitación de Smishy: ver el recorrido o no. */
    data object Invite : TutorialPhase

    /** El paso [index] (desde 0). */
    data class Step(val index: Int) : TutorialPhase

    /** El cierre: dice dónde volver a ver el recorrido. */
    data object Closing : TutorialPhase
}

/** A dónde pasa el recorrido y, si cambia, qué estado se guarda. */
data class TutorialTransition(val phase: TutorialPhase, val save: TutorialState? = null)

/**
 * Las reglas del recorrido, sin pantalla: qué sigue a cada paso y cuándo se guarda la decisión. Solo se guarda al **terminarlo**
 * ([TutorialState.Done]) o al **omitirlo** ([TutorialState.Skipped]): aceptar la invitación o cerrar la aplicación a mitad no guardan
 * nada, así que el estado sigue pendiente y la invitación vuelve a salir.
 */
class TutorialFlow(private val stepCount: Int) {
    init {
        require(stepCount > 0) { "El recorrido necesita al menos un paso" }
    }

    /** Lo que sigue al botón principal: aceptar la invitación, "Siguiente" o "Empezar" en el cierre. */
    fun next(phase: TutorialPhase): TutorialTransition = when (phase) {
        TutorialPhase.Invite -> TutorialTransition(TutorialPhase.Step(0))
        is TutorialPhase.Step ->
            if (phase.index + 1 < stepCount) TutorialTransition(TutorialPhase.Step(phase.index + 1))
            else TutorialTransition(TutorialPhase.Closing)
        TutorialPhase.Closing -> TutorialTransition(TutorialPhase.Hidden, TutorialState.Done)
        TutorialPhase.Hidden -> TutorialTransition(TutorialPhase.Hidden)
    }

    /** "No, gracias" en la invitación u "Omitir recorrido" en cualquier paso: se omite por completo. */
    fun skip(phase: TutorialPhase): TutorialTransition =
        if (phase == TutorialPhase.Hidden) TutorialTransition(TutorialPhase.Hidden)
        else TutorialTransition(TutorialPhase.Hidden, TutorialState.Skipped)

    /** "Ver recorrido" en Ajustes: vuelve a pendiente y empieza de inmediato. */
    fun restart(): TutorialTransition = TutorialTransition(TutorialPhase.Step(0), TutorialState.Pending)
}
