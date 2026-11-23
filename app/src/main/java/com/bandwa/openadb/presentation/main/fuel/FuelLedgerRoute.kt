package com.bandwa.openadb.presentation.main.fuel

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bandwa.openadb.component.DigitWheelRow
import com.bandwa.openadb.component.FuelAmountInput
import com.bandwa.openadb.component.SelectionText
import com.bandwa.openadb.component.SwitchGroup
import com.bandwa.openadb.component.TextButton
import com.bandwa.openadb.presentation.ui.AppTheme
import com.bandwa.openadb.presentation.ui.LiquidDialog
import com.bandwa.openadb.presentation.ui.PSAHelperThemePreview
import com.bandwa.openadb.preview.CombinedThemePreviews
import com.bandwa.openadb.util.FuelLedger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FuelLedgerRoute(
    viewModel: FuelLedgerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.reload() }

    FuelLedgerScreen(
        uiState = uiState,
        onPromptEnabledChange = viewModel::setPromptEnabled,
        onTankLitersChange = viewModel::setTankLiters,
        onDeleteEntry = viewModel::deleteEntry,
        onUpdateEntryAmount = viewModel::updateEntryAmount,
        onAddManualEntry = viewModel::addManualEntry,
    )
}

@Composable
fun FuelLedgerScreen(
    uiState: FuelLedgerUIState,
    onPromptEnabledChange: (Boolean) -> Unit,
    onTankLitersChange: (Int) -> Unit,
    onDeleteEntry: (FuelLedger.Entry) -> Unit,
    onUpdateEntryAmount: (FuelLedger.Entry, Int) -> Unit,
    onAddManualEntry: (Int) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<FuelLedger.Entry?>(null) }
    var editingEntry by remember { mutableStateOf<FuelLedger.Entry?>(null) }
    var showManualEntry by remember { mutableStateOf(false) }
    var showTankDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(top = 28.dp, bottom = 20.dp)
            .verticalScroll(scrollState)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "加油记录",
                style = AppTheme.typography.titleLarge,
                color = AppTheme.colors.textColorSecondary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "油箱容量",
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textColorSecondary,
            )
            Text(
                text = "  ${uiState.tankLiters} L",
                style = AppTheme.typography.titleLarge,
                color = AppTheme.colors.textColorPrimary,
            )
            TextButton(
                modifier = Modifier.padding(start = 12.dp),
                text = "修改",
                onclickListener = { showTankDialog = true },
            )
            Spacer(Modifier.width(20.dp))
            TextButton(text = "手动补录") { showManualEntry = true }
        }

        SwitchGroup(
            modifier = Modifier.padding(bottom = 32.dp),
            check = uiState.promptEnabled,
            title = "加油后弹窗提醒记录加油金额",
            onCheckedChange = onPromptEnabledChange
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            StatItem(
                modifier = Modifier.weight(1f),
                label = "累计加油",
                value = "¥${uiState.totalAmount}",
            )
            StatItem(
                modifier = Modifier.weight(1f),
                label = "加油次数",
                value = "${uiState.entries.size}",
            )
            StatItem(
                modifier = Modifier.weight(1f),
                label = "平均每次",
                value = if (uiState.entries.isEmpty()) "—" else "¥${uiState.averageAmount}",
            )
            StatItem(
                modifier = Modifier.weight(1f),
                label = "百公里油耗",
                value = uiState.averagePer100Km?.let {
                    String.format(Locale.getDefault(), "%.1f L", it)
                } ?: "—",
            )
            StatItem(
                modifier = Modifier.weight(1f),
                label = "每公里成本",
                value = uiState.averageCostPerKm?.let {
                    String.format(Locale.getDefault(), "¥%.2f", it)
                } ?: "—",
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (uiState.entries.isEmpty()) {
            Text(
                modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                text = "暂无加油记录\n加油后会自动提醒记账",
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textColorSecondary,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                modifier = Modifier.padding(bottom = 8.dp),
                text = "单击记录修改金额，长按删除",
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textColorSecondary,
            )
            uiState.entries.forEachIndexed { index, entry ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = AppTheme.colors.glassBorderColor
                    )
                }
                FuelEntryRow(
                    entry = entry,
                    tankLiters = uiState.tankLiters,
                    date = dateFormat.format(Date(entry.timestamp)),
                    segment = uiState.segments[entry.id],
                    onClick = { editingEntry = entry },
                    onLongPress = { pendingDelete = entry }
                )
            }
        }
    }

    pendingDelete?.let { entry ->
        LiquidDialog(
            onDismissRequest = { pendingDelete = null },
            title = "删除记录",
            content = "确定删除 ${dateFormat.format(Date(entry.timestamp))} 的${if (entry.skipped) "跳过" else " ¥${entry.amount} "}记录吗？",
            confirmText = "删除",
            onConfirm = {
                onDeleteEntry(entry)
                pendingDelete = null
            },
            cancelText = "取消",
            onCancel = { pendingDelete = null }
        )
    }

    editingEntry?.let { entry ->
        val known = entry.amount > 0 && !entry.skipped
        FuelAmountDialog(
            title = if (known) "修改加油金额" else "补充加油金额",
            subtitle = if (entry.manual) {
                dateFormat.format(Date(entry.timestamp))
            } else {
                "${dateFormat.format(Date(entry.timestamp))} · 油量 ${entry.percentBefore}% → ${entry.percentAfter}%"
            },
            initialAmount = if (known) entry.amount else 200,
            onDismiss = { editingEntry = null },
            onConfirm = { amount ->
                onUpdateEntryAmount(entry, amount)
                editingEntry = null
            },
        )
    }

    if (showTankDialog) {
        TankCapacityDialog(
            initialLiters = uiState.tankLiters,
            onDismiss = { showTankDialog = false },
            onConfirm = { liters ->
                onTankLitersChange(liters)
                showTankDialog = false
            },
        )
    }

    if (showManualEntry) {
        FuelAmountDialog(
            title = "补录加油金额",
            subtitle = "用于没有弹窗提醒或误触取消的情况",
            initialAmount = 200,
            onDismiss = { showManualEntry = false },
            onConfirm = { amount ->
                onAddManualEntry(amount)
                showManualEntry = false
            }
        )
    }
}

@Composable
private fun FuelAmountDialog(
    title: String,
    subtitle: String,
    initialAmount: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var amount by remember { mutableIntStateOf(initialAmount) }
    val colors = AppTheme.colors
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .width(560.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(colors.backgroundColor)
                .padding(horizontal = 36.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textColorPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                fontSize = 16.sp,
                color = colors.textColorSecondary,
            )
            Spacer(Modifier.height(24.dp))

            FuelAmountInput(
                initialAmount = initialAmount,
                onAmountChange = { amount = it },
            )

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.width(360.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = "取消",
                    onclickListener = onDismiss,
                )
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = "确认",
                    onclickListener = {
                        if (amount <= 0) {
                            Toast.makeText(context, "请输入加油金额", Toast.LENGTH_SHORT).show()
                        } else {
                            onConfirm(amount)
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FuelEntryRow(
    entry: FuelLedger.Entry,
    tankLiters: Int,
    date: String,
    segment: FuelLedger.Segment?,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (entry.skipped) "未记录金额" else "¥${entry.amount}",
                style = AppTheme.typography.headlineLarge,
                color = if (entry.skipped) AppTheme.colors.textColorSecondary else AppTheme.colors.textColorPrimary
            )
            Text(
                text = date,
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textColorSecondary
            )
            segment?.let {
                val cost = it.costPerKm?.let { cost -> String.format(Locale.getDefault(), "¥%.2f/km", cost) } ?: "成本未知"
                Text(
                    text = String.format(
                        Locale.getDefault(),
                        "行驶 %.0f km · %.1f L/100km · %s",
                        it.km,
                        it.per100Km,
                        cost,
                    ),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textColorAccent,
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            val percentText = when {
                entry.manual -> "手动补录"
                entry.skipped -> "已跳过 · ${entry.percentBefore}% → ${entry.percentAfter}%"
                else -> "油量 ${entry.percentBefore}% → ${entry.percentAfter}%"
            }
            val liters = FuelLedger.refueledLiters(entry, tankLiters)
            Text(
                text = liters?.let {
                    String.format(Locale.getDefault(), "%s（约 %.1f L）", percentText, it)
                } ?: percentText,
                style = AppTheme.typography.titleLarge,
                color = AppTheme.colors.textColorSecondary
            )
            entry.totalMileageKm?.let { mileage ->
                Text(
                    text = "总里程 ${mileage.toInt()} km",
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textColorSecondary
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SelectionText(text = label)
        Text(
            text = value,
            style = AppTheme.typography.headlineLarge,
            color = AppTheme.colors.textColorPrimary,
        )
    }
}

@Composable
private fun TankCapacityDialog(
    initialLiters: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var liters by remember { mutableIntStateOf(initialLiters) }
    val colors = AppTheme.colors

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .width(560.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(colors.backgroundColor)
                .padding(horizontal = 36.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "油箱容量",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textColorPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "用于按油量百分比换算实际油耗",
                fontSize = 16.sp,
                color = colors.textColorSecondary,
            )
            Spacer(Modifier.height(24.dp))

            DigitWheelRow(initialValue = initialLiters, onValueChange = { liters = it })

            Spacer(Modifier.height(12.dp))
            Text(
                text = "$liters L",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textColorAccent,
            )

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.width(360.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = "取消",
                    onclickListener = onDismiss,
                )
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = "确认",
                    onclickListener = { onConfirm(liters) },
                )
            }
        }
    }
}

@Composable
@CombinedThemePreviews
fun FuelLedgerScreenPreview() {
    PSAHelperThemePreview {
        FuelLedgerScreen(
            uiState = FuelLedgerUIState(),
            onPromptEnabledChange = {},
            onTankLitersChange = {},
            onDeleteEntry = {},
            onUpdateEntryAmount = { _, _ -> },
            onAddManualEntry = {},
        )
    }
}
