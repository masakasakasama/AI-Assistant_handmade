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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tatsu.homehub.BuildConfig
import com.tatsu.homehub.data.AiDispatchResult
import com.tatsu.homehub.data.WeatherClient
import com.tatsu.homehub.data.WeatherSnapshot
import com.tatsu.homehub.model.AcControlState
import com.tatsu.homehub.model.LocalAlarm
import com.tatsu.homehub.model.SwitchBotDevice
import com.tatsu.homehub.update.UpdateInfo
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private enum class DashboardTab(val label: String, val icon: String) {
    HOME("ホーム", "⌂"),
    DEVICES("家電", "◉"),
    ALARMS("アラーム", "◷"),
    AI("AI", "✦")
}

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
    val aiTesting by viewModel.aiTesting.collectAsState()
    val aiResult by viewModel.aiResult.collectAsState()
    val aiBackendOnline by viewModel.aiBackendOnline.collectAsState()

    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    var tab by remember { mutableStateOf(DashboardTab.HOME) }
    var showSettings by remember { mutableStateOf(false) }
    var showAlarmEditor by remember { mutableStateOf(false) }
    var editingAlarm by remember { mutableStateOf<LocalAlarm?>(null) }

    LaunchedEffect(message) {
        val text = message
        if (!text.isNullOrBlank()) {
            snackbarHostState.showSnackbar(text)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) }
    ) { inner ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(inner)
        ) {
            val tablet = maxWidth >= 720.dp
            val edge = if (tablet) 28.dp else 16.dp

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = edge),
                verticalArrangement = Arrangement.spacedBy(if (tablet) 18.dp else 14.dp)
            ) {
                item {
                    Spacer(Modifier.height(8.dp))
                    DashboardHeader(
                        weather = weather,
                        tablet = tablet,
                        loading = loading,
                        onRefresh = {
                            viewModel.refreshDevices()
                            viewModel.refreshWeather()
                            viewModel.checkAiBackend(showMessage = false)
                        },
                        onSettings = { showSettings = true }
                    )
                }

                item {
                    CategoryBar(tab = tab, onSelect = { tab = it })
                }

                when (tab) {
                    DashboardTab.HOME -> {
                        item {
                            SummaryGrid(
                                devices = devices,
                                alarms = alarms,
                                weather = weather,
                                switchBotConfigured = switchBotConfigured,
                                aiOnline = aiBackendOnline,
                                tablet = tablet,
                                onPower = viewModel::power
                            )
                        }
                        val ac = devices.firstOrNull { it.isAirConditioner }
                        if (ac != null) {
                            item {
                                AirConditionerCard(
                                    device = ac,
                                    state = acStates[ac.deviceId] ?: AcControlState(),
                                    onApply = { viewModel.setAirConditioner(ac, it) }
                                )
                            }
                        }
                        item {
                            AiCard(
                                online = aiBackendOnline,
                                testing = aiTesting,
                                result = aiResult,
                                backendUrl = viewModel.aiBackendUrl(),
                                onTest = viewModel::testAi,
                                onCheck = { viewModel.checkAiBackend() },
                                onSettings = { showSettings = true }
                            )
                        }
                        item {
                            AlarmList(
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
                            UpdateRow(
                                updateInfo = updateInfo,
                                onCheck = { viewModel.checkForUpdate() },
                                onInstall = viewModel::installUpdate
                            )
                        }
                    }

                    DashboardTab.DEVICES -> {
                        item {
                            DeviceList(
                                devices = devices,
                                acStates = acStates,
                                onPower = viewModel::power,
                                onAcChange = viewModel::setAirConditioner
                            )
                        }
                    }

                    DashboardTab.ALARMS -> {
                        item {
                            AlarmList(
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
                    }

                    DashboardTab.AI -> {
                        item {
                            AiCard(
                                online = aiBackendOnline,
                                testing = aiTesting,
                                result = aiResult,
                                backendUrl = viewModel.aiBackendUrl(),
                                onTest = viewModel::testAi,
                                onCheck = { viewModel.checkAiBackend() },
                                onSettings = { showSettings = true }
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(30.dp)) }
            }
        }
    }

    if (showSettings) {
        val weatherSettings = viewModel.weatherSettings()
        SettingsDialog(
            switchBotConfigured = switchBotConfigured,
            switchBotTokenSuffix = switchBotTokenSuffix,
            initialWeatherLabel = weatherSettings.label,
            initialLatitude = weatherSettings.latitude,
            initialLongitude = weatherSettings.longitude,
            initialAiBackendUrl = viewModel.aiBackendUrl(),
            onDismiss = { showSettings = false },
            onSave = { token, secret, label, latitude, longitude, backendUrl ->
                if (viewModel.saveSwitchBotCredentials(token, secret)) {
                    viewModel.saveWeatherSettings(label, latitude, longitude)
                    viewModel.saveAiBackendUrl(backendUrl)
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
                viewModel.saveAlarm(id, hour, minute, label, repeatMask, enabled)
                showAlarmEditor = false
            }
        )
    }
}

@Composable
private fun DashboardHeader(
    weather: WeatherSnapshot?,
    tablet: Boolean,
    loading: Boolean,
    onRefresh: () -> Unit,
    onSettings: () -> Unit
) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1_000)
        }
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    "⌂",
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Tatsu Home",
                    style = if (tablet) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "家を、ひとつの画面で",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (tablet) {
                HeaderValue(
                    now.format(DateTimeFormatter.ofPattern("HH:mm")),
                    now.format(DateTimeFormatter.ofPattern("M/d E"))
                )
                Spacer(Modifier.width(22.dp))
                HeaderValue(
                    weather?.let { String.format("%.0f℃", it.temperatureC) } ?: "--℃",
                    weather?.let { WeatherClient.weatherLabel(it.weatherCode) } ?: "天気"
                )
                Spacer(Modifier.width(14.dp))
            }

            if (loading) {
                CircularProgressIndicator(modifier = Modifier.width(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            TextButton(onClick = onRefresh) { Text("同期") }
            TextButton(onClick = onSettings) { Text("設定") }
        }

        if (!tablet) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniStatus(
                    modifier = Modifier.weight(1f),
                    title = now.format(DateTimeFormatter.ofPattern("HH:mm")),
                    subtitle = now.format(DateTimeFormatter.ofPattern("M/d E"))
                )
                MiniStatus(
                    modifier = Modifier.weight(1f),
                    title = weather?.let { String.format("%.0f℃", it.temperatureC) } ?: "--℃",
                    subtitle = weather?.let { WeatherClient.weatherLabel(it.weatherCode) } ?: "天気"
                )
            }
        }
    }
}

@Composable
private fun HeaderValue(title: String, subtitle: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MiniStatus(modifier: Modifier, title: String, subtitle: String) {
    Surface(modifier = modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CategoryBar(tab: DashboardTab, onSelect: (DashboardTab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DashboardTab.entries.forEach { item ->
            Surface(
                onClick = { onSelect(item) },
                shape = RoundedCornerShape(20.dp),
                color = if (item == tab) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(item.icon, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(item.label, fontWeight = if (item == tab) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun SummaryGrid(
    devices: List<SwitchBotDevice>,
    alarms: List<LocalAlarm>,
    weather: WeatherSnapshot?,
    switchBotConfigured: Boolean,
    aiOnline: Boolean?,
    tablet: Boolean,
    onPower: (SwitchBotDevice, Boolean) -> Unit
) {
    val nextAlarm = alarms.filter { it.enabled }
        .minWithOrNull(compareBy<LocalAlarm> { it.hour }.thenBy { it.minute })

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("お気に入り", "よく使う情報と操作")

        val tiles = mutableListOf<@Composable () -> Unit>()
        devices.take(if (tablet) 4 else 2).forEach { device ->
            tiles += {
                FavoriteDeviceTile(device = device, onPower = { onPower(device, it) })
            }
        }
        tiles += {
            InfoTile(
                icon = "◷",
                title = "次のアラーム",
                value = nextAlarm?.let { String.format("%02d:%02d", it.hour, it.minute) } ?: "なし",
                subtitle = nextAlarm?.label ?: "未設定",
                accent = MaterialTheme.colorScheme.tertiary
            )
        }
        tiles += {
            InfoTile(
                icon = "☁",
                title = weather?.label ?: "天気",
                value = weather?.let { String.format("%.0f℃", it.temperatureC) } ?: "--℃",
                subtitle = weather?.let { WeatherClient.weatherLabel(it.weatherCode) } ?: "取得中",
                accent = MaterialTheme.colorScheme.primary
            )
        }
        tiles += {
            InfoTile(
                icon = "□",
                title = "SwitchBot",
                value = if (switchBotConfigured) "接続済み" else "未設定",
                subtitle = if (switchBotConfigured) devices.size.toString() + "台" else "設定が必要",
                accent = if (switchBotConfigured) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
            )
        }
        tiles += {
            InfoTile(
                icon = "✦",
                title = "AI",
                value = if (aiOnline == true) "接続済み" else "未接続",
                subtitle = "Luna → Sol",
                accent = MaterialTheme.colorScheme.primary
            )
        }

        val columns = if (tablet) 4 else 2
        tiles.chunked(columns).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { tile ->
                    Column(modifier = Modifier.weight(1f)) { tile() }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun FavoriteDeviceTile(device: SwitchBotDevice, onPower: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(deviceIcon(device), fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(
                device.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                device.type,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { onPower(true) }, modifier = Modifier.weight(1f)) { Text("ON") }
                OutlinedButton(onClick = { onPower(false) }, modifier = Modifier.weight(1f)) { Text("OFF") }
            }
        }
    }
}

@Composable
private fun InfoTile(icon: String, title: String, value: String, subtitle: String, accent: Color) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(icon, fontSize = 24.sp, color = accent)
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AirConditionerCard(
    device: SwitchBotDevice,
    state: AcControlState,
    onApply: (AcControlState) -> Unit
) {
    var draft by remember(device.deviceId, state) { mutableStateOf(state) }

    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("エアコン", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(device.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = draft.power, onCheckedChange = { draft = draft.copy(power = it) })
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { draft = draft.copy(temperature = (draft.temperature - 1).coerceAtLeast(16)) }) {
                    Text("−", fontSize = 28.sp)
                }
                Text(
                    draft.temperature.toString() + "℃",
                    modifier = Modifier.weight(1f),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = { draft = draft.copy(temperature = (draft.temperature + 1).coerceAtMost(30)) }) {
                    Text("+", fontSize = 26.sp)
                }
            }

            Text("運転モード", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(1 to "Auto", 2 to "冷房", 3 to "除湿", 4 to "送風", 5 to "暖房").forEach { pair ->
                    FilterChip(
                        selected = draft.mode == pair.first,
                        onClick = { draft = draft.copy(mode = pair.first) },
                        label = { Text(pair.second) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("風量", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(1 to "Auto", 2 to "Low", 3 to "Mid", 4 to "High").forEach { pair ->
                    FilterChip(
                        selected = draft.fanSpeed == pair.first,
                        onClick = { draft = draft.copy(fanSpeed = pair.first) },
                        label = { Text(pair.second) }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { onApply(draft) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) { Text("適用") }
        }
    }
}

@Composable
private fun DeviceList(
    devices: List<SwitchBotDevice>,
    acStates: Map<String, AcControlState>,
    onPower: (SwitchBotDevice, Boolean) -> Unit,
    onAcChange: (SwitchBotDevice, AcControlState) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("家電", "SwitchBot OpenAPI v1.1")
        if (devices.isEmpty()) {
            EmptyCard("SwitchBotが未接続", "設定からOpen Token / Secret Keyを登録")
        } else {
            devices.forEach { device ->
                if (device.isAirConditioner) {
                    AirConditionerCard(
                        device = device,
                        state = acStates[device.deviceId] ?: AcControlState(),
                        onApply = { onAcChange(device, it) }
                    )
                } else {
                    FavoriteDeviceTile(device = device, onPower = { onPower(device, it) })
                }
            }
        }
    }
}

@Composable
private fun AiCard(
    online: Boolean?,
    testing: Boolean,
    result: AiDispatchResult?,
    backendUrl: String,
    onTest: (String) -> Unit,
    onCheck: () -> Unit,
    onSettings: () -> Unit
) {
    var prompt by remember { mutableStateOf("エアコンを26度にして") }

    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        "✦",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 22.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("AI Assistant", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Wake/STT → Luna → 必要時だけ Sol",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    if (online == true) "● 接続" else "● 未接続",
                    color = if (online == true) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(14.dp))

            if (backendUrl.isBlank()) {
                Text("AI Backend URLが未設定", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onSettings) { Text("設定する") }
            } else {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("AIルーティングテスト") },
                    supportingText = { Text("常時Liveは使わず、Lunaが分類してDeepだけSolへ昇格") },
                    minLines = 2
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCheck) { Text("接続確認") }
                    Button(onClick = { onTest(prompt) }, enabled = !testing) {
                        if (testing) {
                            CircularProgressIndicator(modifier = Modifier.width(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (testing) "判定中" else "試す")
                    }
                }
            }

            result?.let {
                Spacer(Modifier.height(14.dp))
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(it.routerModel + " → " + it.route, fontWeight = FontWeight.SemiBold)
                        Text(
                            "confidence " + String.format("%.2f", it.confidence) + " / " + it.latencyMs + "ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        it.action?.let { action ->
                            Text("action: " + action + (it.target?.let { target -> " / " + target } ?: ""))
                        }
                        it.answerModel?.let { model ->
                            Spacer(Modifier.height(6.dp))
                            Text(model + " にEscalate", color = MaterialTheme.colorScheme.primary)
                        }
                        it.answerText?.let { text ->
                            Spacer(Modifier.height(6.dp))
                            Text(text)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlarmList(
    alarms: List<LocalAlarm>,
    onAdd: () -> Unit,
    onEdit: (LocalAlarm) -> Unit,
    onToggle: (LocalAlarm, Boolean) -> Unit,
    onDelete: (LocalAlarm) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("アラーム", "Room DB + Exact Alarm", Modifier.weight(1f))
            Button(onClick = onAdd, shape = RoundedCornerShape(16.dp)) { Text("追加") }
        }

        if (alarms.isEmpty()) {
            EmptyCard("アラームなし", "追加するとここに表示されます")
        } else {
            alarms.forEach { alarm ->
                Surface(
                    onClick = { onEdit(alarm) },
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("◷", fontSize = 24.sp, color = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                String.format("%02d:%02d", alarm.hour, alarm.minute),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                alarm.label + " · " + repeatLabel(alarm.repeatMask),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = alarm.enabled, onCheckedChange = { onToggle(alarm, it) })
                        TextButton(onClick = { onDelete(alarm) }) { Text("削除") }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateRow(updateInfo: UpdateInfo?, onCheck: () -> Unit, onInstall: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("✓", fontSize = 22.sp, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Tatsu Home v" + BuildConfig.VERSION_NAME, fontWeight = FontWeight.SemiBold)
                Text(
                    updateInfo?.let { "v" + it.version + " が利用可能" } ?: "更新状態を確認できます",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (updateInfo == null) {
                TextButton(onClick = onCheck) { Text("更新確認") }
            } else {
                Button(onClick = onInstall) { Text("更新") }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyCard(title: String, subtitle: String) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsDialog(
    switchBotConfigured: Boolean,
    switchBotTokenSuffix: String?,
    initialWeatherLabel: String,
    initialLatitude: Double,
    initialLongitude: Double,
    initialAiBackendUrl: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Double, Double, String) -> Unit
) {
    var token by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var weatherLabel by remember { mutableStateOf(initialWeatherLabel) }
    var latitude by remember { mutableStateOf(initialLatitude.toString()) }
    var longitude by remember { mutableStateOf(initialLongitude.toString()) }
    var backendUrl by remember { mutableStateOf(initialAiBackendUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tatsu Home 設定") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("SwitchBot", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (switchBotConfigured) {
                    Text(
                        "保存済み" + (switchBotTokenSuffix?.let { " / Token …" + it } ?: ""),
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (switchBotConfigured) "Open Token（変更時のみ）" else "Open Token") },
                    placeholder = { if (switchBotConfigured) Text("保存済み") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (switchBotConfigured) "Secret Key（変更時のみ）" else "Secret Key") },
                    placeholder = { if (switchBotConfigured) Text("保存済み") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )

                Spacer(Modifier.height(20.dp))
                Text("AI Backend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "OpenAI APIキーはAPKへ入れずBackend側だけに保存",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = backendUrl,
                    onValueChange = { backendUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Backend URL") },
                    placeholder = { Text("https://...") },
                    singleLine = true
                )

                Spacer(Modifier.height(20.dp))
                Text("天気", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = weatherLabel,
                    onValueChange = { weatherLabel = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("表示名") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = latitude,
                        onValueChange = { latitude = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("緯度") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = longitude,
                        onValueChange = { longitude = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("経度") },
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    token,
                    secret,
                    weatherLabel,
                    latitude.toDoubleOrNull() ?: initialLatitude,
                    longitude.toDoubleOrNull() ?: initialLongitude,
                    backendUrl
                )
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }
    )
}

@Composable
private fun AlarmEditorDialog(
    initial: LocalAlarm?,
    onDismiss: () -> Unit,
    onSave: (String?, Int, Int, String, Int, Boolean) -> Unit
) {
    var hour by remember(initial?.id) { mutableStateOf((initial?.hour ?: 7).toString()) }
    var minute by remember(initial?.id) { mutableStateOf(String.format("%02d", initial?.minute ?: 0)) }
    var label by remember(initial?.id) { mutableStateOf(initial?.label ?: "Wake up") }
    var repeatMask by remember(initial?.id) { mutableIntStateOf(initial?.repeatMask ?: 0) }
    var enabled by remember(initial?.id) { mutableStateOf(initial?.enabled ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "アラーム追加" else "アラーム編集") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
                    modifier = Modifier.fillMaxWidth(),
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
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    initial?.id,
                    hour.toIntOrNull()?.coerceIn(0, 23) ?: 7,
                    minute.toIntOrNull()?.coerceIn(0, 59) ?: 0,
                    label,
                    repeatMask,
                    enabled
                )
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )
}

private fun deviceIcon(device: SwitchBotDevice): String = when {
    device.isAirConditioner -> "▰"
    device.type.contains("Light", ignoreCase = true) -> "●"
    device.type.contains("Fan", ignoreCase = true) -> "✣"
    device.type.contains("Humid", ignoreCase = true) -> "◆"
    else -> "□"
}

private fun repeatLabel(mask: Int): String {
    if (mask == 0) return "1回"
    val days = listOf("月", "火", "水", "木", "金", "土", "日")
    return days.mapIndexedNotNull { index, day ->
        if (mask and (1 shl index) != 0) day else null
    }.joinToString(" ")
}
