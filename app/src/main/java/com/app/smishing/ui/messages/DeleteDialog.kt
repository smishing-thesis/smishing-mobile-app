package com.app.smishing.ui.messages

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.app.smishing.R
import com.app.smishing.ui.designsystem.components.SmishingDialog
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.SmishingTypography
import com.app.smishing.ui.designsystem.theme.Spacing

/** Qué se va a borrar (#51): un mensaje o toda la conversación con [address]. */
sealed interface DeleteTarget {
    data class Message(val message: com.app.smishing.domain.messaging.ConversationMessage) : DeleteTarget
    data class Conversation(val threadId: Long, val address: String) : DeleteTarget
}

/**
 * La confirmación de borrar (#51). Dice que se quita del teléfono y que no se puede deshacer, y deja decidir si también se borra el
 * análisis del historial: el historial guarda el texto del mensaje, y borrarlo de la bandeja no lo borra de ahí. La casilla viene
 * marcada, porque lo que se espera al borrar es que no quede el texto guardado; la línea de abajo dice qué pasa en cada caso.
 */
@Composable
fun DeleteDialog(
    target: DeleteTarget,
    onConfirm: (alsoHistory: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var alsoHistory by remember { mutableStateOf(true) }
    val conversation = target is DeleteTarget.Conversation
    SmishingDialog(
        title = stringResource(if (conversation) R.string.delete_conversation_title else R.string.delete_message_title),
        confirmLabel = stringResource(R.string.delete_confirm),
        onConfirm = { onConfirm(alsoHistory) },
        dismissLabel = stringResource(R.string.delete_cancel),
        onDismiss = onDismiss,
    ) {
        Text(
            when (target) {
                is DeleteTarget.Conversation -> stringResource(R.string.delete_conversation_text, target.address)
                is DeleteTarget.Message -> stringResource(R.string.delete_message_text)
            },
            style = SmishingTypography.BodyMedium,
            color = SmishingPalette.TextSecondary,
        )
        Row(
            modifier = Modifier.fillMaxWidth().toggleable(value = alsoHistory, role = Role.Checkbox, onValueChange = { alsoHistory = it }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = alsoHistory,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(checkedColor = SmishingPalette.Primary),
            )
            Text(
                stringResource(if (conversation) R.string.delete_history_many else R.string.delete_history_one),
                style = SmishingTypography.BodyMedium,
                color = SmishingPalette.TextPrimary,
                modifier = Modifier.padding(start = Spacing.Xs),
            )
        }
        Text(
            stringResource(if (alsoHistory) R.string.delete_history_on else R.string.delete_history_off),
            style = SmishingTypography.Note,
            color = SmishingPalette.TextSecondary,
        )
    }
}
