package com.app.smishing.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.app.smishing.R
import com.app.smishing.ui.designsystem.components.LinkButton
import com.app.smishing.ui.designsystem.components.SmishingDialog
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.SmishingTypography
import com.app.smishing.ui.designsystem.theme.Spacing

/**
 * El aviso que sale al activar "Compartir mensajes de forma anónima" (#18): explica qué se guarda y qué nunca se comparte. El interruptor
 * solo se activa con [onAccept]; con [onDeny] (o tocando fuera) queda apagado. "Ver términos y condiciones" todavía no lleva a ninguna
 * parte: no hay un documento al que enlazar.
 */
@Composable
fun ContributionDialog(onAccept: () -> Unit, onDeny: () -> Unit) {
    SmishingDialog(
        title = stringResource(R.string.contribution_dialog_title),
        confirmLabel = stringResource(R.string.contribution_dialog_accept),
        onConfirm = onAccept,
        dismissLabel = stringResource(R.string.contribution_dialog_deny),
        onDismiss = onDeny,
    ) {
        Column(modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
            Text(stringResource(R.string.contribution_dialog_intro), style = SmishingTypography.BodyMedium, color = SmishingPalette.TextSecondary)
            Text(stringResource(R.string.contribution_dialog_never), style = SmishingTypography.BodyMediumBold, color = SmishingPalette.TextPrimary)
            // Las viñetas van juntas en un solo texto: así quedan más pegadas y el aviso cabe en pantalla.
            Text(
                listOf(
                    R.string.contribution_dialog_never_name,
                    R.string.contribution_dialog_never_phone,
                    R.string.contribution_dialog_never_contacts,
                    R.string.contribution_dialog_never_conversations,
                    R.string.contribution_dialog_never_personal,
                ).map { stringResource(it) }.joinToString("\n") { "•  $it" },
                style = SmishingTypography.BodyMedium,
                color = SmishingPalette.TextSecondary,
                modifier = Modifier.padding(start = Spacing.Xs),
            )
            Text(stringResource(R.string.contribution_dialog_internet), style = SmishingTypography.BodyMedium, color = SmishingPalette.TextSecondary)
            Text(stringResource(R.string.contribution_dialog_retry), style = SmishingTypography.BodyMedium, color = SmishingPalette.TextSecondary)
        }
        // El enlace va fuera del área desplazable: así siempre se ve.
        LinkButton(text = stringResource(R.string.contribution_dialog_terms), onClick = {})
    }
}
