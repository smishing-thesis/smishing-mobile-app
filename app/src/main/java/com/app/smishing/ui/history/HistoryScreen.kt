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
import com.app.smishing.domain.mitigation.HistoryMark
import com.app.smishing.domain.mitigation.MitigationAction
import com.app.smishing.domain.mitigation.historyMarks
import com.app.smishing.domain.risk.RiskLevel
import com.app.smishing.ui.designsystem.components.HistoryRow
import com.app.smishing.ui.designsystem.components.SelectAction
import com.app.smishing.ui.designsystem.components.SelectionTopBar
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
 * sospecha como certeza (#11). Cada mensaje dice lo que se hizo con él (#28); la búsqueda no está.
 */
@Composable
fun HistoryScreen(
    records: List<AnalysisRecord>?,
    onOpen: (AnalysisRecord) -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
    /** Las acciones tomadas por registro (#28), para marcar cada mensaje con lo que se hizo. */
    actions: Map<Long, List<MitigationAction>> = emptyMap(),
    /** Si se están eligiendo registros para borrar (#52) y cuáles. Tocar una fila la marca o desmarca. */
    selecting: Boolean = false,
    selected: Set<Long> = emptySet(),
    onToggle: (AnalysisRecord) -> Unit = {},
    /** Empezar a elegir: desde el botón "Seleccionar" (sin registro) o manteniendo presionado uno (ese registro). */
    onStartSelecting: (AnalysisRecord?) -> Unit = {},
    onSelectAll: () -> Unit = {},
    onCancelSelection: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    /** Cómo se muestra un remitente: el nombre del contacto o, si no hay, el número (#53). */
    displayName: (String) -> String = { it },
) {
    Column(modifier = modifier.fillMaxSize().background(SmishingPalette.Background)) {
        if (selecting) {
            SelectionTopBar(
                count = selected.size,
                allSelected = !records.isNullOrEmpty() && selected.size >= records.size,
                style = TopBarStyle.Primary,
                onCancel = onCancelSelection,
                onSelectAll = onSelectAll,
                onDelete = onDeleteSelected,
            )
        } else {
            SmishingTopBar(
                title = stringResource(R.string.history_title),
                style = TopBarStyle.Primary,
                actions = { if (!records.isNullOrEmpty()) SelectAction(TopBarStyle.Primary) { onStartSelecting(null) } },
            )
        }
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
                        sender = displayName(record.sender),
                        date = formatHistoryDate(record.analyzedAtMillis, zone),
                        snippet = record.body,
                        onClick = { if (selecting) onToggle(record) else onOpen(record) },
                        selectionMode = selecting,
                        selected = record.id in selected,
                        onLongClick = { if (!selecting) onStartSelecting(record) },
                        onLongClickLabel = stringResource(R.string.history_select),
                        actions = marksText(historyMarks(record, actions[record.id].orEmpty())),
                        chip = {
                            // Marcado como seguro por la persona: el mismo chip verde, con la marca de manual (#102).
                            if (record.markedSafeAtMillis != null) StatusChip(Status.Safe, stringResource(R.string.history_chip_safe_manual))
                            else StatusChip(statusOf(record.assessment.level), stringResource(chipText(record.assessment.level)))
                        },
                    )
                }
            }
        }
    }
}

/** Las marcas de acciones en palabras, separadas con « · »; `null` si no hay ninguna. */
@Composable
private fun marksText(marks: List<HistoryMark>): String? =
    marks.takeIf { it.isNotEmpty() }?.map { stringResource(markText(it)) }?.joinToString(" · ")

private fun markText(mark: HistoryMark) = when (mark) {
    HistoryMark.MarkedSafe -> R.string.history_mark_safe
    HistoryMark.Blocked -> R.string.history_mark_blocked
    HistoryMark.Removed -> R.string.history_mark_removed
    HistoryMark.ActionFailed -> R.string.history_mark_failed
    HistoryMark.ActionPending -> R.string.history_mark_pending
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
