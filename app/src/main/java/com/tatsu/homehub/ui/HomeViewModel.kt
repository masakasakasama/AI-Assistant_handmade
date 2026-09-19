package com.tatsu.homehub.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tatsu.homehub.alarm.AlarmScheduler
import com.tatsu.homehub.data.AiBackendClient
import com.tatsu.homehub.data.AiDispatchResult
import com.tatsu.homehub.data.AlarmRepository
import com.tatsu.homehub.data.AppPrefs
import com.tatsu.homehub.data.NetworkMonitor
import com.tatsu.homehub.data.SecurePrefs
import com.tatsu.homehub.data.SwitchBotClient
import com.tatsu.homehub.data.WeatherClient
import com.tatsu.homehub.data.WeatherSnapshot
import com.tatsu.homehub.model.AcControlState
import com.tatsu.homehub.model.LocalAlarm
import com.tatsu.homehub.model.SwitchBotDevice
import com.tatsu.homehub.update.UpdateInfo
import com.tatsu.homehub.update.UpdateManager
import com.tatsu.homehub.voice.VoiceController
import com.tatsu.homehub.voice.VoicePhase
import com.tatsu.homehub.voice.VoiceSessionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

data class WeatherSettings(
    val label: String,
    val latitude: Double,
    val longitude: Double
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val securePrefs = SecurePrefs(application)
    private val appPrefs = AppPrefs(application)
    private val alarmRepo = AlarmRepository(application)
    private val weatherClient = WeatherClient()
    private val aiBackendClient = AiBackendClient()
    private val updateManager = UpdateManager(application)
    private val networkMonitor = NetworkMonitor(application) {
        if (hasSwitchBotCredentials()) refreshDevices(showMessage = false)
        refreshWeather(showError = false)
    }

    private val _devices = MutableStateFlow<List<SwitchBotDevice>>(emptyList())
    val devices: StateFlow<List<SwitchBotDevice>> = _devices.asStateFlow()

    private val _alarms = MutableStateFlow<List<LocalAlarm>>(emptyList())
    val alarms: StateFlow<List<LocalAlarm>> = _alarms.asStateFlow()

    private val _acStates = MutableStateFlow<Map<String, AcControlState>>(emptyMap())
    val acStates: StateFlow<Map<String, AcControlState>> = _acStates.asStateFlow()

    private val _weather = MutableStateFlow<WeatherSnapshot?>(appPrefs.loadWeatherCache())
    val weather: StateFlow<WeatherSnapshot?> = _weather.asStateFlow()

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _aiTesting = MutableStateFlow(false)
    val aiTesting: StateFlow<Boolean> = _aiTesting.asStateFlow()

    private val _aiResult = MutableStateFlow<AiDispatchResult?>(null)
    val aiResult: StateFlow<AiDispatchResult?> = _aiResult.asStateFlow()

    private val _aiBackendOnline = MutableStateFlow<Boolean?>(null)
    val aiBackendOnline: StateFlow<Boolean?> = _aiBackendOnline.asStateFlow()

    private val _switchBotConfigured = MutableStateFlow(securePrefs.hasSwitchBotCredentials())
    val switchBotConfigured: StateFlow<Boolean> = _switchBotConfigured.asStateFlow()

    private val _switchBotTokenSuffix = MutableStateFlow(securePrefs.switchBotTokenSuffix())
    val switchBotTokenSuffix: StateFlow<String?> = _switchBotTokenSuffix.asStateFlow()

    private val _voiceState = MutableStateFlow(VoiceSessionState())
    val voiceState: StateFlow<VoiceSessionState> = _voiceState.asStateFlow()
    val voiceLanguageTag: String get() = appPrefs.voiceLanguageTag

    private var voiceGeneration = 0L
    private var currentVoiceJob: Job? = null
    private val conversationHistory = mutableListOf<String>()
    private val executedVoiceOperations = LinkedHashSet<String>()

    private val voiceController = VoiceController(application, object : VoiceController.Listener {
        override fun onListeningChanged(sessionId: Long, listening: Boolean) {
            val current = _voiceState.value
            if (sessionId != current.generationId) return
            if (listening) {
                _voiceState.value = current.copy(phase = VoicePhase.LISTENING, error = null)
            } else if (current.phase == VoicePhase.LISTENING) {
                _voiceState.value = current.copy(phase = VoicePhase.IDLE)
            }
        }

        override fun onStatus(sessionId: Long, status: String) {
            if (sessionId != _voiceState.value.generationId) return
            val phase = when (status) {
                "音声入力を準備しています", "音声入力を切り替えています", "音声を確認しています" -> VoicePhase.PREPARING
                "話してください", "準備できました。もう一度話してください" -> VoicePhase.LISTENING
                else -> _voiceState.value.phase
            }
            _voiceState.value = _voiceState.value.copy(status = status, phase = phase)
        }

        override fun onDiagnostic(sessionId: Long, diagnostic: String) {
            if (sessionId == _voiceState.value.generationId) {
                val current = _voiceState.value.diagnostic
                val appInfo = "app=${com.tatsu.homehub.BuildConfig.VERSION_NAME} (${com.tatsu.homehub.BuildConfig.VERSION_CODE})"
                val history = if (current.startsWith(appInfo)) current.removePrefix(appInfo).trimStart(';', ' ') else current
                val combined = listOf(appInfo, history, diagnostic).filter { it.isNotBlank() }.joinToString("; ")
                _voiceState.value = _voiceState.value.copy(diagnostic = combined.takeLast(1600))
            }
        }

        override fun onPartialText(sessionId: Long, text: String) {
            if (sessionId == _voiceState.value.generationId) {
                _voiceState.value = _voiceState.value.copy(partialText = text)
            }
        }

        override fun onFinalText(sessionId: Long, text: String) {
            processVoiceText(text, sessionId)
        }

        override fun onSpeakingChanged(speaking: Boolean) {
            val current = _voiceState.value
            _voiceState.value = if (speaking) {
                current.copy(phase = VoicePhase.SPEAKING, error = null)
            } else if (current.phase == VoicePhase.SPEAKING) {
                current.copy(phase = VoicePhase.IDLE)
            } else {
                current
            }
        }

        override fun onError(sessionId: Long, message: String) {
            val current = _voiceState.value
            if (sessionId != 0L && sessionId != current.generationId) return
            if (sessionId == 0L && current.generationId != 0L && current.phase != VoicePhase.SPEAKING) return
            _voiceState.value = _voiceState.value.copy(phase = VoicePhase.ERROR, error = message)
        }
    })

    fun hasSwitchBotCredentials(): Boolean = _switchBotConfigured.value

    fun weatherSettings(): WeatherSettings = WeatherSettings(
        label = appPrefs.weatherLabel,
        latitude = appPrefs.weatherLatitude,
        longitude = appPrefs.weatherLongitude
    )

    fun aiBackendUrl(): String = appPrefs.aiBackendUrl

    init {
        viewModelScope.launch {
            alarmRepo.observeAll().collect { list ->
                _alarms.value = list
            }
        }

        viewModelScope.launch {
            while (isActive) {
                refreshWeatherInternal(showError = _weather.value == null)
                delay(30 * 60 * 1000L)
            }
        }

        networkMonitor.start()

        if (hasSwitchBotCredentials()) refreshDevices(showMessage = false)
        if (appPrefs.aiBackendUrl.isNotBlank()) checkAiBackend(showMessage = false)

        viewModelScope.launch {
            while (isActive) {
                if (
                    System.currentTimeMillis() - appPrefs.lastUpdateCheckMillis >
                    12 * 60 * 60 * 1000L
                ) {
                    checkForUpdate(showMessage = false)
                }
                delay(60 * 60 * 1000L)
            }
        }
    }

    fun saveSwitchBotCredentials(token: String, secret: String): Boolean {
        val cleanToken = token.trim()
        val cleanSecret = secret.trim()

        if (cleanToken.isBlank() && cleanSecret.isBlank()) {
            return true
        }

        if (cleanToken.isBlank() || cleanSecret.isBlank()) {
            _message.value = "Open TokenとSecret Keyを両方入力してください"
            return false
        }

        return runCatching {
            val tokenSaved = securePrefs.put(SecurePrefs.KEY_SWITCHBOT_TOKEN, cleanToken)
            val secretSaved = securePrefs.put(SecurePrefs.KEY_SWITCHBOT_SECRET, cleanSecret)
            check(tokenSaved && secretSaved) { "端末ストレージへの保存に失敗しました" }
            check(securePrefs.hasSwitchBotCredentials()) { "保存後の読み戻しに失敗しました" }

            _switchBotConfigured.value = true
            _switchBotTokenSuffix.value = securePrefs.switchBotTokenSuffix()
            _message.value = "SwitchBot認証情報を保存しました"
            refreshDevices()
            true
        }.getOrElse { error ->
            _message.value = "SwitchBot認証情報の保存失敗: " + (error.message ?: "unknown")
            false
        }
    }

    fun refreshDevices(showMessage: Boolean = true) {
        val client = clientOrNull() ?: run {
            if (showMessage) {
                _message.value = "SwitchBot Token / Secretを設定してください"
            }
            return
        }

        viewModelScope.launch {
            _loading.value = true
            client.getDevices()
                .onSuccess { list ->
                    _devices.value = list
                    if (showMessage) {
                        _message.value = list.size.toString() + "台を同期しました"
                    }
                }
                .onFailure { error ->
                    if (showMessage) {
                        _message.value = "SwitchBot同期失敗: " + (error.message ?: "unknown")
                    }
                }
            _loading.value = false
        }
    }

    fun power(device: SwitchBotDevice, on: Boolean) {
        val client = clientOrNull() ?: return
        viewModelScope.launch {
            val result = if (on) client.turnOn(device.deviceId) else client.turnOff(device.deviceId)
            result
                .onSuccess {
                    _message.value = device.name + ": " + if (on) "ON" else "OFF"
                }
                .onFailure { error ->
                    _message.value = device.name + ": " + (error.message ?: "operation failed")
                }
        }
    }

    fun setAirConditioner(device: SwitchBotDevice, state: AcControlState) {
        val client = clientOrNull() ?: return
        _acStates.value = _acStates.value + (device.deviceId to state)

        viewModelScope.launch {
            client.setAirConditioner(
                deviceId = device.deviceId,
                temperature = state.temperature,
                mode = state.mode,
                fanSpeed = state.fanSpeed,
                power = state.power
            )
                .onSuccess {
                    _message.value = device.name + ": " +
                        state.temperature.toString() + "℃ " + modeName(state.mode)
                }
                .onFailure { error ->
                    _message.value = device.name + ": " + (error.message ?: "operation failed")
                }
        }
    }

    fun saveAlarm(
        id: String? = null,
        hour: Int,
        minute: Int,
        label: String,
        repeatMask: Int,
        enabled: Boolean = true
    ) {
        val alarm = LocalAlarm(
            id = id ?: UUID.randomUUID().toString(),
            hour = hour.coerceIn(0, 23),
            minute = minute.coerceIn(0, 59),
            label = label.ifBlank { "Alarm" },
            repeatMask = repeatMask,
            enabled = enabled
        )

        viewModelScope.launch {
            alarmRepo.upsert(alarm)
            if (enabled) {
                AlarmScheduler.schedule(getApplication(), alarm)
            } else {
                AlarmScheduler.cancel(getApplication(), alarm.id)
            }
        }
    }

    fun toggleAlarm(alarm: LocalAlarm, enabled: Boolean) {
        viewModelScope.launch {
            alarmRepo.setEnabled(alarm.id, enabled)
            if (enabled) {
                AlarmScheduler.schedule(getApplication(), alarm.copy(enabled = true))
            } else {
                AlarmScheduler.cancel(getApplication(), alarm.id)
            }
        }
    }

    fun deleteAlarm(alarm: LocalAlarm) {
        viewModelScope.launch {
            AlarmScheduler.cancel(getApplication(), alarm.id)
            alarmRepo.delete(alarm.id)
        }
    }

    fun saveWeatherSettings(label: String, latitude: Double, longitude: Double) {
        appPrefs.weatherLabel = label.ifBlank { "Weather" }
        appPrefs.weatherLatitude = latitude.coerceIn(-90.0, 90.0)
        appPrefs.weatherLongitude = longitude.coerceIn(-180.0, 180.0)
        refreshWeather()
    }

    fun saveAiBackendUrl(url: String) {
        appPrefs.aiBackendUrl = url.trim().trimEnd('/')
        checkAiBackend()
    }

    fun saveVoiceLanguage(languageTag: String) {
        if (languageTag in setOf("ja-JP", "en-US", "de-DE")) appPrefs.voiceLanguageTag = languageTag
    }

    fun checkAiBackend(showMessage: Boolean = true) {
        val url = appPrefs.aiBackendUrl
        if (url.isBlank()) {
            _aiBackendOnline.value = false
            if (showMessage) _message.value = "AI Backend URLを設定してください"
            return
        }

        viewModelScope.launch {
            aiBackendClient.health(url)
                .onSuccess { online ->
                    _aiBackendOnline.value = online
                    if (showMessage) {
                        _message.value = if (online) "AI Backend接続OK" else "AI Backend応答異常"
                    }
                }
                .onFailure { error ->
                    _aiBackendOnline.value = false
                    if (showMessage) {
                        _message.value = "AI Backend接続失敗: " + (error.message ?: "unknown")
                    }
                }
        }
    }

    fun testAi(text: String) {
        val query = text.trim()
        if (query.isBlank()) {
            _message.value = "テスト文を入力してください"
            return
        }

        val url = appPrefs.aiBackendUrl
        if (url.isBlank()) {
            _message.value = "設定からAI Backend URLを登録してください"
            return
        }

        viewModelScope.launch {
            _aiTesting.value = true
            aiBackendClient.dispatch(url, query)
                .onSuccess { result ->
                    _aiResult.value = result
                    _aiBackendOnline.value = true
                }
                .onFailure { error ->
                    _aiBackendOnline.value = false
                    _message.value = "AIテスト失敗: " + (error.message ?: "unknown")
                }
            _aiTesting.value = false
        }
    }

    fun startVoiceSession() {
        val url = appPrefs.aiBackendUrl
        if (url.isBlank()) {
            _message.value = "設定からAI Backend URLを登録してください"
            return
        }

        val previousGeneration = voiceGeneration
        voiceGeneration += 1
        if (previousGeneration > 0) voiceController.cancelListening(previousGeneration)
        currentVoiceJob?.cancel()
        voiceController.stopSpeaking()
        _voiceState.value = VoiceSessionState(
            phase = VoicePhase.PREPARING,
            generationId = voiceGeneration,
            status = "音声入力を準備しています",
            diagnostic = "app=${com.tatsu.homehub.BuildConfig.VERSION_NAME} (${com.tatsu.homehub.BuildConfig.VERSION_CODE})"
        )
        voiceController.startListening(voiceGeneration, appPrefs.voiceLanguageTag)
    }

    fun stopVoiceListening() {
        voiceController.stopListening()
    }

    fun cancelVoiceSession() {
        voiceGeneration += 1
        currentVoiceJob?.cancel()
        voiceController.cancelListening(voiceGeneration - 1)
        voiceController.stopSpeaking()
        _voiceState.value = VoiceSessionState(
            phase = VoicePhase.IDLE,
            generationId = voiceGeneration
        )
    }

    private fun processVoiceText(text: String, generation: Long) {
        if (generation != voiceGeneration || text.isBlank()) return
        val url = appPrefs.aiBackendUrl
        if (url.isBlank()) return

        _voiceState.value = _voiceState.value.copy(
            phase = VoicePhase.THINKING,
            partialText = "",
            finalText = text,
            responseText = "",
            route = null,
            error = null
        )

        currentVoiceJob?.cancel()
        currentVoiceJob = viewModelScope.launch {
            val started = android.os.SystemClock.elapsedRealtime()
            aiBackendClient.dispatch(url, text, buildVoiceContext())
                .onSuccess { result ->
                    if (generation != voiceGeneration) return@onSuccess
                    _aiResult.value = result
                    _aiBackendOnline.value = true
                    val response = handleVoiceResult(result, generation)
                    if (generation != voiceGeneration || response.isBlank()) return@onSuccess
                    rememberConversation(text, response)
                    val elapsed = android.os.SystemClock.elapsedRealtime() - started
                    _voiceState.value = _voiceState.value.copy(
                        phase = VoicePhase.THINKING,
                        responseText = response,
                        route = result.route,
                        latencyMs = elapsed,
                        error = null
                    )
                    voiceController.speak(response, result.language, "voice-$generation")
                }
                .onFailure { error ->
                    if (generation != voiceGeneration) return@onFailure
                    _aiBackendOnline.value = false
                    _voiceState.value = _voiceState.value.copy(
                        phase = VoicePhase.ERROR,
                        error = "AI処理失敗: " + (error.message ?: "unknown")
                    )
                }
        }
    }

    private suspend fun handleVoiceResult(result: AiDispatchResult, generation: Long): String {
        if (generation != voiceGeneration) return ""
        return when (result.route) {
            "simple_chat", "deep_reasoning", "clarify" ->
                result.answerText ?: localized(result.language, "もう一度お願いします", "Please try again.", "Bitte noch einmal.")
            "weather" -> weatherVoiceResponse(result.language)
            "device_action" -> executeVoiceDeviceAction(result, generation)
            "alarm_action" -> executeVoiceAlarmAction(result, generation)
            else -> localized(result.language, "その操作にはまだ対応していません", "That action is not supported yet.", "Diese Aktion wird noch nicht unterstützt.")
        }
    }

    private suspend fun executeVoiceDeviceAction(result: AiDispatchResult, generation: Long): String {
        if (generation != voiceGeneration) return ""
        if (!markVoiceOperationOnce(result, generation)) {
            return localized(result.language, "同じ操作は重複実行しませんでした", "I blocked a duplicate action.", "Die doppelte Aktion wurde blockiert.")
        }

        val target = result.target?.trim().orEmpty()
        if (target.isBlank()) {
            return localized(result.language, "どの家電を操作しますか？", "Which device should I control?", "Welches Gerät soll ich steuern?")
        }

        val normalizedTarget = normalizeName(target)
        val exact = _devices.value.filter { normalizeName(it.name) == normalizedTarget }
        val candidates = if (exact.isNotEmpty()) exact else _devices.value.filter {
            val name = normalizeName(it.name)
            name.contains(normalizedTarget) || normalizedTarget.contains(name)
        }

        if (candidates.size != 1) {
            return if (candidates.isEmpty()) {
                localized(result.language, "対象の家電が見つかりませんでした", "I couldn't find that device.", "Ich konnte dieses Gerät nicht finden.")
            } else {
                val names = candidates.take(3).joinToString("、") { it.name }
                localized(result.language, "対象が複数あります: $names", "I found multiple matching devices: $names", "Ich habe mehrere passende Geräte gefunden: $names")
            }
        }

        val client = clientOrNull()
            ?: return localized(result.language, "SwitchBotの設定が必要です", "SwitchBot is not configured.", "SwitchBot ist nicht eingerichtet.")
        val device = candidates.single()

        val operation = when (result.action) {
            "turn_on" -> client.turnOn(device.deviceId)
            "turn_off" -> client.turnOff(device.deviceId)
            "set_ac" -> {
                if (!device.isAirConditioner) {
                    return localized(result.language, "その機器は温度設定に対応していません", "That device does not support temperature control.", "Dieses Gerät unterstützt keine Temperatureinstellung.")
                }
                val temperature = result.temperatureC?.roundToInt()
                    ?: return localized(result.language, "温度を確認してください", "Please specify the temperature.", "Bitte nenne die Temperatur.")
                if (temperature !in 16..30) {
                    return localized(result.language, "温度は16〜30度で指定してください", "Please choose a temperature from 16 to 30 degrees.", "Bitte wähle eine Temperatur zwischen 16 und 30 Grad.")
                }
                val current = _acStates.value[device.deviceId] ?: AcControlState()
                val next = current.copy(temperature = temperature, power = true)
                client.setAirConditioner(device.deviceId, next.temperature, next.mode, next.fanSpeed, next.power)
                    .onSuccess { _acStates.value = _acStates.value + (device.deviceId to next) }
            }
            else -> return localized(result.language, "その家電操作にはまだ対応していません", "That device action is not supported yet.", "Diese Geräteaktion wird noch nicht unterstützt.")
        }

        return operation.fold(
            onSuccess = {
                when (result.action) {
                    "set_ac" -> localized(result.language, "${device.name}へ${result.temperatureC?.roundToInt()}度の設定を送信しました", "I sent ${result.temperatureC?.roundToInt()} degrees to ${device.name}.", "Ich habe ${result.temperatureC?.roundToInt()} Grad an ${device.name} gesendet.")
                    "turn_on" -> localized(result.language, "${device.name}へONを送信しました", "I sent ON to ${device.name}.", "Ich habe EIN an ${device.name} gesendet.")
                    else -> localized(result.language, "${device.name}へOFFを送信しました", "I sent OFF to ${device.name}.", "Ich habe AUS an ${device.name} gesendet.")
                }
            },
            onFailure = { error ->
                localized(result.language, "家電操作に失敗しました: ${error.message ?: "unknown"}", "Device control failed: ${error.message ?: "unknown"}", "Gerätesteuerung fehlgeschlagen: ${error.message ?: "unknown"}")
            }
        )
    }

    private suspend fun executeVoiceAlarmAction(result: AiDispatchResult, generation: Long): String {
        if (generation != voiceGeneration) return ""
        if (!markVoiceOperationOnce(result, generation)) {
            return localized(result.language, "同じ操作は重複実行しませんでした", "I blocked a duplicate action.", "Die doppelte Aktion wurde blockiert.")
        }

        return when (result.action) {
            "alarm_create" -> {
                val time = parseClock(result.timeLocal)
                    ?: return localized(result.language, "何時に設定するか確認してください", "What time should I set it for?", "Für welche Uhrzeit soll ich den Wecker stellen?")
                val alarm = LocalAlarm(
                    id = UUID.randomUUID().toString(),
                    hour = time.first,
                    minute = time.second,
                    label = result.target?.takeIf { it.isNotBlank() } ?: "Voice alarm",
                    repeatMask = 0,
                    enabled = true
                )
                alarmRepo.upsert(alarm)
                AlarmScheduler.schedule(getApplication(), alarm)
                localized(result.language, String.format("%02d:%02dにアラームを設定しました", alarm.hour, alarm.minute),
                    String.format("Alarm set for %02d:%02d.", alarm.hour, alarm.minute),
                    String.format("Wecker für %02d:%02d gestellt.", alarm.hour, alarm.minute))
            }

            "alarm_update", "alarm_delete" -> {
                val candidates = resolveAlarmCandidates(result)
                if (candidates.size != 1) {
                    return if (candidates.isEmpty()) {
                        localized(result.language, "変更するアラームが見つかりませんでした", "I couldn't find that alarm.", "Ich konnte diesen Wecker nicht finden.")
                    } else {
                        localized(result.language, "対象のアラームが複数あります。時刻か名前を指定してください", "Multiple alarms match. Please specify the time or name.", "Mehrere Wecker passen. Bitte nenne Uhrzeit oder Namen.")
                    }
                }
                val alarm = candidates.single()
                if (result.action == "alarm_delete") {
                    AlarmScheduler.cancel(getApplication(), alarm.id)
                    alarmRepo.delete(alarm.id)
                    localized(result.language, String.format("%02d:%02dのアラームを削除しました", alarm.hour, alarm.minute),
                        String.format("Deleted the %02d:%02d alarm.", alarm.hour, alarm.minute),
                        String.format("Der Wecker um %02d:%02d wurde gelöscht.", alarm.hour, alarm.minute))
                } else {
                    val time = parseClock(result.timeLocal)
                        ?: return localized(result.language, "新しい時刻を確認してください", "What should the new time be?", "Wie lautet die neue Uhrzeit?")
                    val updated = alarm.copy(hour = time.first, minute = time.second)
                    alarmRepo.upsert(updated)
                    AlarmScheduler.schedule(getApplication(), updated)
                    localized(result.language, String.format("アラームを%02d:%02dに変更しました", updated.hour, updated.minute),
                        String.format("Alarm changed to %02d:%02d.", updated.hour, updated.minute),
                        String.format("Wecker auf %02d:%02d geändert.", updated.hour, updated.minute))
                }
            }

            else -> localized(result.language, "そのアラーム操作にはまだ対応していません", "That alarm action is not supported yet.", "Diese Weckeraktion wird noch nicht unterstützt.")
        }
    }

    private fun resolveAlarmCandidates(result: AiDispatchResult): List<LocalAlarm> {
        var candidates = _alarms.value
        parseClock(result.referenceTimeLocal)?.let { time ->
            candidates = candidates.filter { it.hour == time.first && it.minute == time.second }
        }

        val target = result.target?.trim().orEmpty()
        if (target.isNotBlank() && !isGenericAlarmTarget(target)) {
            val normalized = normalizeName(target)
            candidates = candidates.filter {
                val label = normalizeName(it.label)
                label == normalized || label.contains(normalized) || normalized.contains(label)
            }
        }
        return candidates
    }

    private fun markVoiceOperationOnce(result: AiDispatchResult, generation: Long): Boolean {
        val key = listOf(
            generation.toString(),
            result.action.orEmpty(),
            result.target.orEmpty(),
            result.temperatureC?.toString().orEmpty(),
            result.timeLocal.orEmpty(),
            result.referenceTimeLocal.orEmpty()
        ).joinToString("|")
        if (!executedVoiceOperations.add(key)) return false
        while (executedVoiceOperations.size > 100) {
            val first = executedVoiceOperations.firstOrNull() ?: break
            executedVoiceOperations.remove(first)
        }
        return true
    }

    private fun buildVoiceContext(): String {
        val devices = _devices.value.joinToString("\n") {
            "- ${it.name} | type=${it.type} | id=${it.deviceId}"
        }.ifBlank { "- none" }
        val alarms = _alarms.value.joinToString("\n") {
            "- ${it.label} | ${String.format("%02d:%02d", it.hour, it.minute)} | id=${it.id} | enabled=${it.enabled}"
        }.ifBlank { "- none" }
        val history = conversationHistory.takeLast(8).joinToString("\n").ifBlank { "- none" }
        return """
Known devices:
$devices

Current alarms:
$alarms

Recent conversation:
$history
""".trim()
    }

    private fun rememberConversation(user: String, assistant: String) {
        conversationHistory += "User: $user"
        conversationHistory += "Assistant: $assistant"
        while (conversationHistory.size > 12) conversationHistory.removeAt(0)
    }

    private fun weatherVoiceResponse(language: String): String {
        val snapshot = _weather.value
            ?: return localized(language, "天気情報をまだ取得できていません", "Weather data is not available yet.", "Wetterdaten sind noch nicht verfügbar.")
        val condition = WeatherClient.weatherLabel(snapshot.weatherCode)
        val temperature = String.format("%.0f", snapshot.temperatureC)
        return localized(language, "${snapshot.label}は${temperature}度、${condition}です", "${snapshot.label}: ${temperature} degrees, ${condition}.", "${snapshot.label}: ${temperature} Grad, ${condition}.")
    }

    private fun parseClock(value: String?): Pair<Int, Int>? {
        val match = Regex("""^(\d{1,2}):(\d{2})$""").matchEntire(value?.trim().orEmpty()) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour to minute
    }

    private fun normalizeName(value: String): String =
        value.lowercase().replace(Regex("""[\s　・_\-:：/]+"""), "")

    private fun isGenericAlarmTarget(value: String): Boolean {
        val normalized = normalizeName(value)
        return normalized in setOf("alarm", "thealarm", "アラーム", "wecker", "derwecker")
    }

    private fun localized(language: String, ja: String, en: String, de: String): String = when (language) {
        "de" -> de
        "en" -> en
        else -> ja
    }

    fun refreshWeather(showError: Boolean = true) {
        viewModelScope.launch {
            refreshWeatherInternal(showError = showError)
        }
    }

    fun checkForUpdate(showMessage: Boolean = true) {
        viewModelScope.launch {
            updateManager.checkLatest()
                .onSuccess { info ->
                    appPrefs.lastUpdateCheckMillis = System.currentTimeMillis()
                    _updateInfo.value = info
                    if (showMessage) {
                        _message.value = if (info == null) {
                            "最新バージョンです"
                        } else {
                            "v" + info.version + " が利用可能です"
                        }
                    }
                }
                .onFailure { error ->
                    if (showMessage) {
                        _message.value = "更新確認失敗: " + (error.message ?: "unknown")
                    }
                }
        }
    }

    fun installUpdate() {
        val info = _updateInfo.value ?: run {
            _message.value = "利用可能な更新はありません"
            return
        }

        viewModelScope.launch {
            _message.value = "v" + info.version + " をダウンロード中"
            updateManager.downloadAndOpenInstaller(info)
                .onSuccess {
                    _message.value = "インストーラーを開きました"
                }
                .onFailure { error ->
                    _message.value = error.message ?: "更新に失敗しました"
                }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private suspend fun refreshWeatherInternal(showError: Boolean) {
        weatherClient.current(
            label = appPrefs.weatherLabel,
            latitude = appPrefs.weatherLatitude,
            longitude = appPrefs.weatherLongitude
        )
            .onSuccess {
                _weather.value = it
                appPrefs.saveWeatherCache(it)
            }
            .onFailure { error ->
                if (showError) {
                    _message.value = "天気取得失敗: " + (error.message ?: "unknown")
                }
            }
    }

    override fun onCleared() {
        currentVoiceJob?.cancel()
        voiceController.release()
        networkMonitor.stop()
        super.onCleared()
    }

    private fun clientOrNull(): SwitchBotClient? {
        val token = securePrefs.get(SecurePrefs.KEY_SWITCHBOT_TOKEN)
        val secret = securePrefs.get(SecurePrefs.KEY_SWITCHBOT_SECRET)
        if (token.isNullOrBlank() || secret.isNullOrBlank()) return null
        return SwitchBotClient(token, secret)
    }

    companion object {
        fun modeName(mode: Int): String = when (mode) {
            1 -> "Auto"
            2 -> "Cool"
            3 -> "Dry"
            4 -> "Fan"
            5 -> "Heat"
            else -> "Unknown"
        }

        fun fanName(speed: Int): String = when (speed) {
            1 -> "Auto"
            2 -> "Low"
            3 -> "Medium"
            4 -> "High"
            else -> "Unknown"
        }
    }
}
