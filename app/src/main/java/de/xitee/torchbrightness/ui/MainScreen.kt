package de.xitee.torchbrightness.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.xitee.torchbrightness.BuildConfig
import de.xitee.torchbrightness.R
import de.xitee.torchbrightness.curve.CurveAccess
import de.xitee.torchbrightness.service.ControlStatus
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val canWrite by vm.canWrite.collectAsStateWithLifecycle()
    val access by vm.curveAccess.collectAsStateWithLifecycle()
    val curve by vm.curve.collectAsStateWithLifecycle()
    val torchOn by vm.torchOn.collectAsStateWithLifecycle()
    val written by vm.writtenValue.collectAsStateWithLifecycle()
    val frontLux by vm.frontLux.collectAsStateWithLifecycle()
    val rearLux by vm.rearLux.collectAsStateWithLifecycle()
    val system by vm.systemBrightness.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val itemColors = ListItemDefaults.colors(containerColor = Color.Transparent)

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { StatusCard(settings.enabled, status, vm::setEnabled) }

            item { SectionHeader(stringResource(R.string.section_setup)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.setup_write_settings)) },
                    supportingContent = {
                        Text(stringResource(if (canWrite) R.string.setup_granted else R.string.setup_write_settings_missing))
                    },
                    trailingContent = {
                        if (!canWrite) {
                            FilledTonalButton(onClick = { context.startActivity(vm.writeSettingsIntent()) }) {
                                Text(stringResource(R.string.setup_allow))
                            }
                        }
                    },
                    colors = itemColors,
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.setup_curve)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                when (access) {
                                    CurveAccess.DUMP_PERMISSION -> R.string.setup_curve_dump
                                    CurveAccess.SHIZUKU -> R.string.setup_curve_shizuku
                                    CurveAccess.SHIZUKU_NO_PERMISSION -> R.string.setup_curve_shizuku_missing
                                    CurveAccess.NONE -> R.string.setup_curve_none
                                },
                            ),
                        )
                    },
                    trailingContent = {
                        if (access == CurveAccess.SHIZUKU_NO_PERMISSION) {
                            FilledTonalButton(onClick = vm::requestShizukuPermission) {
                                Text(stringResource(R.string.setup_allow))
                            }
                        }
                    },
                    colors = itemColors,
                )
            }
            if (access == CurveAccess.NONE || access == CurveAccess.SHIZUKU_NO_PERMISSION) {
                item {
                    val command = "adb shell pm grant ${BuildConfig.APPLICATION_ID} android.permission.DUMP"
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SelectionContainer {
                                Text(command, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(onClick = {
                                context.getSystemService(ClipboardManager::class.java)
                                    .setPrimaryClip(ClipData.newPlainText("adb", command))
                            }) { Text(stringResource(R.string.setup_copy_command)) }
                        }
                    }
                }
            }
            curve?.let { c ->
                item {
                    InfoText(
                        stringResource(
                            R.string.setup_curve_read,
                            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(c.readAtMillis)),
                            c.pointCount,
                        ),
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.section_brightness)) }
            item {
                ValueSlider(
                    label = { stringResource(R.string.adjustment, if (it > 0) "+$it" else "$it") },
                    value = settings.adjustmentPercent,
                    range = -50f..100f,
                    step = 10,
                    onChange = vm::setAdjustmentPercent,
                )
            }
            item { InfoText(stringResource(R.string.adjustment_desc)) }

            item { SectionHeader(stringResource(R.string.section_live)) }
            item {
                Column {
                    val none = stringResource(R.string.value_none)
                    InfoText(stringResource(R.string.live_front, frontLux?.let { "%.1f".format(it) } ?: none))
                    InfoText(
                        stringResource(
                            R.string.live_rear,
                            if (!vm.hasRearSensor) stringResource(R.string.value_not_available)
                            else rearLux?.let { "%.1f".format(it) } ?: none,
                        ),
                    )
                    InfoText(stringResource(R.string.live_flashlight, stringResource(if (torchOn) R.string.value_on else R.string.value_off)))
                    InfoText(
                        stringResource(
                            R.string.live_adaptive,
                            stringResource(if (system.automatic) R.string.value_on else R.string.value_off),
                            system.value,
                        ),
                    )
                    val target = curve?.let { c -> frontLux?.let { c.settingFor(it, settings.adjustmentPercent / 100f) } }
                    InfoText(stringResource(R.string.live_target, target?.toString() ?: none))
                    written?.let { InfoText(stringResource(R.string.live_written, it)) }
                }
            }

            item { SectionHeader(stringResource(R.string.section_how)) }
            item { InfoText(stringResource(R.string.how_text)) }
        }
    }
}

@Composable
private fun StatusCard(enabled: Boolean, status: ControlStatus, onEnabled: (Boolean) -> Unit) {
    val ok = enabled && (status == ControlStatus.WAITING || status == ControlStatus.CONTROLLING)
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.status_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(
                        when {
                            !enabled -> R.string.status_off
                            else -> when (status) {
                                ControlStatus.OFF -> R.string.status_starting
                                ControlStatus.NO_PERMISSION -> R.string.status_no_permission
                                ControlStatus.NO_CURVE_ACCESS -> R.string.status_no_curve_access
                                ControlStatus.CURVE_UNSUPPORTED -> R.string.status_curve_unsupported
                                ControlStatus.WAITING -> R.string.status_waiting
                                ControlStatus.CONTROLLING -> R.string.status_controlling
                                ControlStatus.USER_OVERRIDE -> R.string.status_user_override
                                ControlStatus.SKIPPED_MANUAL -> R.string.status_skipped_manual
                            }
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabled)
        }
    }
}
