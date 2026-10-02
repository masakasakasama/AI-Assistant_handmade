package com.tatsu.homehub.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tatsu.homehub.voice.WakeWordChoice

@Composable
fun WakeWordSettingsPanel(viewModel: HomeViewModel) {
    val settings by viewModel.wakeWordSettings.collectAsState()
    val diagnostic by viewModel.wakeWordDiagnostic.collectAsState()
    val interruptionEnabled by viewModel.wakeInterruptionEnabled.collectAsState()
    val status by viewModel.wakeWordStatus.collectAsState()
    val customAvailable by viewModel.customWakeWordAvailable.collectAsState()
    val importing by viewModel.wakeWordImporting.collectAsState()
    val importMessage by viewModel.wakeWordImportMessage.collectAsState()
    var customPhrase by remember(settings.customPhrase) { mutableStateOf(settings.customPhrase) }
    var requestedPhrase by remember { mutableStateOf(customPhrase) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importWakeWordModel(uri, requestedPhrase)
    }
    Column {
        Text("ウェイクワード", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        Text("変更はすぐに反映・保存されます。アプリを開いている間、端末内で検出します。",
            style = MaterialTheme.typography.bodySmall)
        Text(diagnostic, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("返答中も呼びかけで割り込む", modifier = Modifier.weight(1f))
            Switch(checked = interruptionEnabled, onCheckedChange = viewModel::setWakeInterruptionEnabled)
        }
        Text("呼びかけで読み上げを止め、次の発話を受け付けます。返答に呼びかけの言葉が含まれる間は誤起動防止のため停止します。",
            style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WakeWordChoice.entries.forEach { choice ->
                FilterChip(
                    selected = settings.choice == choice,
                    enabled = !importing && (choice != WakeWordChoice.CUSTOM || customAvailable),
                    onClick = { viewModel.selectWakeWord(choice) },
                    label = { Text(if (choice == WakeWordChoice.CUSTOM && customAvailable) settings.customPhrase else choice.label) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("好きな呼びかけを使う場合", fontWeight = FontWeight.Medium)
        Text("その言葉を学習したopenWakeWord用ONNXモデルが必要です。文字を入力するだけでは検出する言葉は変わりません。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = customPhrase,
            onValueChange = { customPhrase = it.take(60) },
            enabled = !importing,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("モデルが検出する呼びかけ（表示名）") },
            placeholder = { Text("例: Hey Tatsu") },
            singleLine = true
        )
        OutlinedButton(enabled = !importing && customPhrase.isNotBlank(), onClick = {
            requestedPhrase = customPhrase
            picker.launch(arrayOf("*/*"))
        }) { Text(if (importing) "確認中…" else "ONNXモデルを読み込んで使う") }
        importMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
