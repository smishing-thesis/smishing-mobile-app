package com.app.smishing.domain.mitigation

/**
 * Lo que "Este mensaje es seguro" va a **deshacer de verdad** (#102): las acciones completadas que [MitigationExecutor.markSafe]
 * revierte. Con riesgo medio, o con un modo que solo avisa, no hay ninguna, y el aviso de confirmación no debe prometer lo contrario.
 *
 * Un bloqueo que ya existía antes de la app (`AlreadyApplied`) no cuenta: la app no lo puso y no lo quita.
 */
fun undoneOnMarkSafe(actions: Map<ActionType, MitigationAction>): Set<ActionType> =
    actions.values
        .filter { it.status == ActionStatus.Completed && !(it.type == ActionType.BlockSender && it.note == ActionNote.AlreadyApplied) }
        .map { it.type }
        .toSet()
