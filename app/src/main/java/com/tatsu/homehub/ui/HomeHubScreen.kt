package com.tatsu.homehub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tatsu.homehub.model.AcControlState
import com.tatsu.homehub.model.LocalAlarm
import com.tatsu.homehub.model.SwitchBotDevice
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun HomeHubScreen(viewModel: HomeViewModel) {
    val devices by viewModel.devices.collectAsState()
    val alarms by viewModel.alarms.collectAsState()
    val acStates by viewModel.acStates.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val message by viewModel.message.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showSettings by remember { mutableStateOf(false) }
    var showAlarmEditor by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        val text = message
        if (!text.isNullOrBlank()) {
            snackbarHostState.showSnackbar(text)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
        ) {
            val tabletLayout = maxWidth >= 700.dp

            Column(modifier = Modifier.fillMaxSize()) {
                Header(
                    loading = loading,
                    onRefresh = viewModel::refreshDevices,
                    onSettings = { showSettings = true }
                )
                Spacer(Modifier.height(16.dp))

                if (tabletLayout) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1.25f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            ClockCard()
                            AiStatusCard()
                            DeviceSection(
                                devices = devices,
                                acStates = acStates,
                                onPower = viewModel::power,
                                onAcChange = viewModel::setAirConditioner,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        AlarmSection(
                            alarms = alarms,
                            onAdd = { showAlarmEditor = true },
                            onToggle = viewModel::toggleAlarm,
                            onDelete = viewModel::deleteAlarm,
                            modifier = Modifier.weight(0.75f)
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item { ClockCard() }
                        item { AiStatusCard() }
                        item {
                            DeviceSection(
                                devices = devices,
                                acStates = acStates,
                                onPower = viewModel::power,
                                onAcChange = viewModel::setAirConditioner
                            )
                        }
                        item {
                            AlarmSection(
                                alarms = alarms,
                                onAdd = { showAlarmEditor = true },
                                onToggle = viewModel::toggleAlarm,
                                onDelete = viewModel::deleteAlarm
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSettings) {
        SwitchBotSettingsDialog(
            configured = viewModel.hasSwitchBotCredentials(),
            onDismiss = { showSettings = false },
            onSave = { token, secret ->
                viewModel.saveSwitchBotCredentials(token, secret)
                showSettings = false
            }
        )
    }

    if (showAlarmEditor) {
        AlarmEditorDialog(
            onDismiss = { showAlarmEditor = false },
            onSave = { hour, minute, label, repeatMask ->
                viewModel.saveAlarm(
                    hour = hour,
                    minute = minute,
                    label = label,
                    repeatMask = repeatMask
                )
                showAlarmEditor = false
            }
        )
    }
}

@Composable
private fun Header(
    loading: Boolean,
    onRefresh: () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Tatsu Home", style = MaterialTheme.typography.headlineMedium)
            Text(
                "AI・家電・アラームを1画面で管理",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.width(28.dp))
            Spacer(Modifier.width(12.dp))
        }
        OutlinedButton(onClick = onRefresh) {
            Text("同期")
        }
        Spacer(Modifier.width(8.dp))
        Button(onClick = onSettings) {
            Text("設定")
        }
    }
}

@Composable
private fun ClockCard() {
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1_000)
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                now.format(DateTimeFormatter.ofPattern("HH:mm")),
                style = MaterialTheme.typography.displayMedium
            )
            Text(
                now.format(DateTimeFormatter.ofPattern("yyyy/MM/dd E")),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun AiStatusCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("AI Voice PoC", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("Phase 0: Galaxyで音声モデルを先行評価")
            Text("未接続。ここに採用モデル、言語、会話セッションを統合予定")
        }
    }
}

@Composable
private fun DeviceSection(
    devices: List<SwitchBotDevice>,
    acStates: Map<String, AcControlState>,
    onPower: (SwitchBotDevice, Boolean) -> Unit,
    onAcChange: (SwitchBotDevice, AcControlState) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text("SwitchBot", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))

            if (devices.isEmpty()) {
                Text("デバイス未同期。設定からToken / Secretを登録")
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    devices.forEach { device ->
                        DeviceCard(
                            device = device,
                            acState = acStates[device.deviceId] ?: AcControlState(),
                            onPower = { onPower(device, it) },
                            onAcChange = { onAcChange(device, it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: SwitchBotDevice,
    acState: AcControlState,
    onPower: (Boolean) -> Unit,
    onAcChange: (AcControlState) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(device.name, style = MaterialTheme.typography.titleMedium)
            Text(
                device.type + if (device.infrared) " / IR" else "",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(10.dp))

            if (device.isAirConditioner) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (acState.power) "ON" else "OFF")
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = acState.power,
                        onCheckedChange = {
                            onAcChange(acState.copy(power = it))
                        }
                    )
                    Spacer(Modifier.width(20.dp))
                    Text(acState.temperature.toString() + "℃")
                }

                Slider(
                    value = acState.temperature.toFloat(),
                    onValueChange = {
                        onAcChange(acState.copy(temperature = it.toInt()))
                    },
                    valueRange = 16f..30f,
                    steps = 13
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1 to "Auto", 2 to "Cool", 3 to "Dry", 4 to "Fan", 5 to "Heat")
                        .forEach { mode ->
                            FilterChip(
                                selected = acState.mode == mode.first,
                                onClick = {
                                    onAcChange(acState.copy(mode = mode.first))
                                },
                                label = { Text(mode.second) }
                            )
                        }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onPower(true) }) {
                        Text("ON")
                    }
                    OutlinedButton(onClick = { onPower(false) }) {
                        Text("OFF")
                    }
                }
            }
        }
    }
}

@Composable
private fun AlarmSection(
    alarms: List<LocalAlarm>,
    onAdd: () -> Unit,
    onToggle: (LocalAlarm, Boolean) -> Unit,
    onDelete: (LocalAlarm) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "アラーム",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = onAdd) {
                    Text("追加")
                }
            }
            Spacer(Modifier.height(12.dp))

            if (alarms.isEmpty()) {
                Text("アラームなし")
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    alarms.forEach { alarm ->
                        AlarmCard(
                            alarm = alarm,
                            onToggle = { onToggle(alarm, it) },
                            onDelete = { onDelete(alarm) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlarmCard(
    alarm: LocalAlarm,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        String.format("%02d:%02d", alarm.hour, alarm.minute),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(alarm.label)
                    Text(
                        repeatLabel(alarm.repeatMask),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = alarm.enabled,
                    onCheckedChange = onToggle
                )
            }
            TextButton(onClick = onDelete) {
                Text("削除")
            }
        }
    }
}

@Composable
private fun SwitchBotSettingsDialog(
    configured: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var token by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SwitchBot API") },
        text = {
            Column {
                if (configured) {
                    Text("認証情報は登録済み。入力すると上書き")
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Token") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = { Text("Secret") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(token, secret) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    )
}

@Composable
private fun AlarmEditorDialog(
    onDismiss: () -> Unit,
    onSave: (Int, Int, String, Int) -> Unit
) {
    var hour by remember { mutableStateOf("7") }
    var minute by remember { mutableStateOf("00") }
    var label by remember { mutableStateOf("Wake up") }
    var repeatMask by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("アラーム追加") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hour,
                        onValueChange = { hour = it.filter(Char::isDigit).take(2) },
                        label = { Text("時") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = minute,
                        onValueChange = { minute = it.filter(Char::isDigit).take(2) },
                        label = { Text("分") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("名前") },
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))
                Text("繰り返し")
                val days = listOf("月", "火", "水", "木", "金", "土", "日")
                days.forEachIndexed { index, day ->
                    val selected = repeatMask and (1 shl index) != 0
                    FilterChip(
                        selected = selected,
                        onClick = {
                            repeatMask = if (selected) {
                                repeatMask and (1 shl index).inv()
                            } else {
                                repeatMask or (1 shl index)
                            }
                        },
                        label = { Text(day) }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        hour.toIntOrNull()?.coerceIn(0, 23) ?: 7,
                        minute.toIntOrNull()?.coerceIn(0, 59) ?: 0,
                        label,
                        repeatMask
                    )
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

private fun repeatLabel(mask: Int): String {
    if (mask == 0) return "1回"
    val days = listOf("月", "火", "水", "木", "金", "土", "日")
    return days.mapIndexedNotNull { index, day ->
        if (mask and (1 shl index) != 0) day else null
    }.joinToString(" ")
}
