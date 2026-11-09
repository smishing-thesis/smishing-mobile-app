package com.app.smishing.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.app.smishing.R
import com.app.smishing.domain.history.AnalysisRecord
import com.app.smishing.domain.history.formatHistoryDate
import com.app.smishing.domain.risk.RiskLevel
import com.app.smishing.ui.designsystem.components.HistoryRow
import com.app.smishing.ui.designsystem.components.SmishingTopBar
import com.app.smishing.ui.designsystem.components.StatusChip
import com.app.smishing.ui.designsystem.components.TopBarStyle
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.SmishingTypography
import com.app.smishing.ui.designsystem.theme.Spacing
import com.app.smishing.ui.designsystem.theme.Status
import java.time.ZoneId

/**
 * "Historial" (#12): los mensajes analizados, el más reciente primero, con el remitente, la fecha, el inicio del mensaje y una
 * marca de estado. Tocar uno abre su detalle, armado desde lo guardado. [records] es `null` mientras se leen.
 *
 * La marca dice "Posible estafa", "Sospechoso" o "Sin señales" y no "Peligroso" o "Seguro" del diseño, para no presentar la
 * sospecha como certeza (#11). Todavía no tiene la búsqueda ni las acciones tomadas (#13 a #16).
 */
@Composable
fun HistoryScreen(
    records: List<AnalysisRecord>?,
    onOpen: (AnalysisRecord) -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    Column(modifier = modifier.fillMaxSize().background(SmishingPalette.Background)) {
        SmishingTopBar(title = stringResource(R.string.history_title), style = TopBarStyle.Primary)
        when {
            records == null -> Unit
            records.isEmpty() -> Box(Modifier.fillMaxSize().padding(Spacing.Md), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.history_empty),
                    style = SmishingTypography.BodyMedium,
                    color = SmishingPalette.TextSecondary,
                )
            }
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(records, key = { it.id }) { record ->
                    HistoryRow(
                        sender = record.sender,
                        date = formatHistoryDate(record.analyzedAtMillis, zone),
                        snippet = record.body,
                        onClick = { onOpen(record) },
                        chip = { StatusChip(statusOf(record.assessment.level), stringResource(chipText(record.assessment.level))) },
                    )
                }
            }
        }
    }
}

private fun statusOf(level: RiskLevel) = when (level) {
    RiskLevel.High -> Status.Danger
    RiskLevel.Medium -> Status.Suspicious
    RiskLevel.Low -> Status.Safe
}

private fun chipText(level: RiskLevel) = when (level) {
    RiskLevel.High -> R.string.history_chip_high
    RiskLevel.Medium -> R.string.history_chip_medium
    RiskLevel.Low -> R.string.history_chip_low
}
