package com.tatsu.homehub.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tatsu.homehub.BuildConfig
import com.tatsu.homehub.data.WeatherClient
import com.tatsu.homehub.data.WeatherSnapshot
import com.tatsu.homehub.model.AcControlState
import com.tatsu.homehub.model.LocalAlarm
import com.tatsu.homehub.model.SwitchBotDevice
import com.tatsu.homehub.update.UpdateInfo
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun HomeHubScreen(viewModel: HomeViewModel) {
    val devices by viewModel.devices.collectAsState()
    val alarms by viewModel.alarms.collectAsState()
    val acStates by viewModel.acStates.collectAsState()
    val weather by viewModel.weather.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val message by viewModel.message.collectAsState()
    val switchBotConfigured by viewModel.switchBotConfigured.collectAsState()
    val switchBotTokenSuffix by viewModel.switchBotTokenSuffix.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showSettings by remember { mutableStateOf(false) }
    var editingAlarm by remember { mutableStateOf<LocalAlarm?>(null) }
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
                    onRefresh = {
                        viewModel.refreshDevices()
                        viewModel.refreshWeather()
                    },
                    onSettings = { showSettings = true }
                )
                Spacer(Modifier.height(16.dp))

                if (tabletLayout) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier.weight(1.2f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item { ClockCard() }
                            item {
                                WeatherCard(
                                    weather = weather,
                                    onRefresh = viewModel::refreshWeather
                                )
                            }
                            item { AiStatusCard() }
                            item {
                                DeviceSection(
                                    devices = devices,
                                    acStates = acStates,
                                    onPower = viewModel::power,
                                    onAcChange = viewModel::setAirConditioner
                                )
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.weight(0.8f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                AlarmSection(
                                    alarms = alarms,
                                    onAdd = {
                                        editingAlarm = null
                                        showAlarmEditor = true
                                    },
                                    onEdit = {
                                        editingAlarm = it
                                        showAlarmEditor = true
                                    },
                                    onToggle = viewModel::toggleAlarm,
                                    onDelete = viewModel::deleteAlarm
                                )
                            }
                            item {
                                UpdateCard(
                                    updateInfo = updateInfo,
                                    onCheck = { viewModel.checkForUpdate() },
                                    onInstall = viewModel::installUpdate
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item { ClockCard() }
                        item {
                            WeatherCard(
                                weather = weather,
                                onRefresh = viewModel::refreshWeather
                            )
                        }
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
                                onAdd = {
                                    editingAlarm = null
                                    showAlarmEditor = true
                                },
                                onEdit = {
                                    editingAlarm = it
                                    showAlarmEditor = true
                                },
                                onToggle = viewModel::toggleAlarm,
                                onDelete = viewModel::deleteAlarm
                            )
                        }
                        item {
                            UpdateCard(
                                updateInfo = updateInfo,
                                onCheck = { viewModel.checkForUpdate() },
                                onInstall = viewModel::installUpdate
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSettings) {
        val weatherSettings = viewModel.weatherSettings()
        HomeSettingsDialog(
            switchBotConfigured = switchBotConfigured,
            switchBotTokenSuffix = switchBotTokenSuffix,
            initialWeatherLabel = weatherSettings.label,
            initialLatitude = weatherSettings.latitude,
            initialLongitude = weatherSettings.longitude,
            onDismiss = { showSettings = false },
            onSave = { token, secret, label, latitude, longitude ->
                val switchBotSaved = viewModel.saveSwitchBotCredentials(token, secret)
                if (switchBotSaved) {
                    viewModel.saveWeatherSettings(label, latitude, longitude)
                    showSettings = false
                }
            }
        )
    }

    if (showAlarmEditor) {
        AlarmEditorDialog(
            initial = editingAlarm,
            onDismiss = { showAlarmEditor = false },
            onSave = { id, hour, minute, label, repeatMask, enabled ->
                viewModel.saveAlarm(
                    id = id,
                    hour = hour,
                    minute = minute,
                    label = label,
                    repeatMask = repeatMask,
                    enabled = enabled
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
private fun WeatherCard(
    weather: WeatherSnapshot?,
    onRefresh: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "天気",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onRefresh) {
                    Text("更新")
                }
            }

            if (weather == null) {
                Text("取得中")
            } else {
                Text(
                    weather.label + "  " +
                        String.format("%.1f℃", weather.temperatureC),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    WeatherClient.weatherLabel(weather.weatherCode) +
                        " / 体感 " +
                        String.format("%.1f℃", weather.apparentTemperatureC)
                )
            }
        }
    }
}

@Composable
private fun AiStatusCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("AI Voice PoC", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("Phase 0: Galaxyで音声モデルを先行評価")
            Text("音声モデル接続は次フェーズ。家電・アラーム基盤を先に固定")
        }
    }
}

@Composable
private fun DeviceSection(
    devices: List<SwitchBotDevice>,
    acStates: Map<String, AcControlState>,
    onPower: (SwitchBotDevice, Boolean) -> Unit,
    onAcChange: (SwitchBotDevice, AcControlState) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("SwitchBot", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))

            if (devices.isEmpty()) {
                Text("未接続。設定からOpen Token / Secret Keyを登録")
            } else {
                devices.forEach { device ->
                    DeviceCard(
                        device = device,
                        acState = acStates[device.deviceId] ?: AcControlState(),
                        onPower = { onPower(device, it) },
                        onAcApply = { onAcChange(device, it) }
                    )
                    Spacer(Modifier.height(10.dp))
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
    onAcApply: (AcControlState) -> Unit
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
                var draft by remember(device.deviceId, acState) {
                    mutableStateOf(acState)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (draft.power) "ON" else "OFF")
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = draft.power,
                        onCheckedChange = { draft = draft.copy(power = it) }
                    )
                    Spacer(Modifier.width(20.dp))
                    Text(draft.temperature.toString() + "℃")
                }

                Slider(
                    value = draft.temperature.toFloat(),
                    onValueChange = {
                        draft = draft.copy(temperature = it.toInt())
                    },
                    valueRange = 16f..30f,
                    steps = 13
                )

                Text("モード", style = MaterialTheme.typography.bodySmall)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(1 to "Auto", 2 to "Cool", 3 to "Dry", 4 to "Fan", 5 to "Heat")
                        .forEach { mode ->
                            FilterChip(
                                selected = draft.mode == mode.first,
                                onClick = { draft = draft.copy(mode = mode.first) },
                                label = { Text(mode.second) }
                            )
                        }
                }

                Text("風量", style = MaterialTheme.typography.bodySmall)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(1 to "Auto", 2 to "Low", 3 to "Medium", 4 to "High")
                        .forEach { fan ->
                            FilterChip(
                                selected = draft.fanSpeed == fan.first,
                                onClick = { draft = draft.copy(fanSpeed = fan.first) },
                                label = { Text(fan.second) }
                            )
                        }
                }

                Button(onClick = { onAcApply(draft) }) {
                    Text("エアコンへ適用")
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
    onEdit: (LocalAlarm) -> Unit,
    onToggle: (LocalAlarm, Boolean) -> Unit,
    onDelete: (LocalAlarm) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
                alarms.forEach { alarm ->
                    AlarmCard(
                        alarm = alarm,
                        onEdit = { onEdit(alarm) },
                        onToggle = { onToggle(alarm, it) },
                        onDelete = { onDelete(alarm) }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun AlarmCard(
    alarm: LocalAlarm,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) {
                    Text("編集")
                }
                TextButton(onClick = onDelete) {
                    Text("削除")
                }
            }
        }
    }
}

@Composable
private fun UpdateCard(
    updateInfo: UpdateInfo?,
    onCheck: () -> Unit,
    onInstall: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("アプリ更新", style = MaterialTheme.typography.titleLarge)
            Text("現在 v" + BuildConfig.VERSION_NAME)

            if (updateInfo == null) {
                Text("新しいReleaseがあればここに表示")
                OutlinedButton(onClick = onCheck) {
                    Text("更新確認")
                }
            } else {
                Text("v" + updateInfo.version + " が利用可能")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCheck) {
                        Text("再確認")
                    }
                    Button(onClick = onInstall) {
                        Text("更新")
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSettingsDialog(
    switchBotConfigured: Boolean,
    switchBotTokenSuffix: String?,
    initialWeatherLabel: String,
    initialLatitude: Double,
    initialLongitude: Double,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Double, Double) -> Unit
) {
    var token by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var weatherLabel by remember { mutableStateOf(initialWeatherLabel) }
    var latitude by remember { mutableStateOf(initialLatitude.toString()) }
    var longitude by remember { mutableStateOf(initialLongitude.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("設定") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text("SwitchBot API", style = MaterialTheme.typography.titleMedium)
                if (switchBotConfigured) {
                    Text(
                        "保存済み" +
                            (switchBotTokenSuffix?.let { " / Open Token …" + it } ?: ""),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "保存済みの値は安全のため再表示しません。変更する場合だけ2項目とも入力してください",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        "Open TokenとSecret Keyを入力して保存してください",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(if (switchBotConfigured) "Open Token（変更時のみ）" else "Open Token") },
                    placeholder = {
                        if (switchBotConfigured) Text("保存済み")
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = { Text(if (switchBotConfigured) "Secret Key（変更時のみ）" else "Secret Key") },
                    placeholder = {
                        if (switchBotConfigured) Text("保存済み")
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )

                Spacer(Modifier.height(20.dp))
                Text("天気", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = weatherLabel,
                    onValueChange = { weatherLabel = it },
                    label = { Text("表示名") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = latitude,
                    onValueChange = { latitude = it },
                    label = { Text("緯度") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = longitude,
                    onValueChange = { longitude = it },
                    label = { Text("経度") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        token,
                        secret,
                        weatherLabel,
                        latitude.toDoubleOrNull() ?: initialLatitude,
                        longitude.toDoubleOrNull() ?: initialLongitude
                    )
                }
            ) {
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
    initial: LocalAlarm?,
    onDismiss: () -> Unit,
    onSave: (String?, Int, Int, String, Int, Boolean) -> Unit
) {
    var hour by remember(initial?.id) {
        mutableStateOf((initial?.hour ?: 7).toString())
    }
    var minute by remember(initial?.id) {
        mutableStateOf(String.format("%02d", initial?.minute ?: 0))
    }
    var label by remember(initial?.id) {
        mutableStateOf(initial?.label ?: "Wake up")
    }
    var repeatMask by remember(initial?.id) {
        mutableIntStateOf(initial?.repeatMask ?: 0)
    }
    var enabled by remember(initial?.id) {
        mutableStateOf(initial?.enabled ?: true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initial == null) "アラーム追加" else "アラーム編集")
        },
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("有効", modifier = Modifier.weight(1f))
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initial?.id,
                        hour.toIntOrNull()?.coerceIn(0, 23) ?: 7,
                        minute.toIntOrNull()?.coerceIn(0, 59) ?: 0,
                        label,
                        repeatMask,
                        enabled
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
