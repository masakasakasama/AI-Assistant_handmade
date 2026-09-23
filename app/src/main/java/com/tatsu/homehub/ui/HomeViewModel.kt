package com.tatsu.homehub.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tatsu.homehub.alarm.AlarmScheduler
import com.tatsu.homehub.data.AiBackendClient
import com.tatsu.homehub.data.AiDispatchResult
import com.tatsu.homehub.data.AiPipelineTimings
import com.tatsu.homehub.data.TemporaryRoomAssignments
import com.tatsu.homehub.data.RouterCompareResult
import com.tatsu.homehub.data.AlarmRepository
import com.tatsu.homehub.data.AppPrefs
import com.tatsu.homehub.data.NetworkMonitor
import com.tatsu.homehub.data.SecurePrefs
import com.tatsu.homehub.data.SwitchBotClient
import com.tatsu.homehub.data.WeatherClient
import com.tatsu.homehub.data.WeatherSnapshot
import com.tatsu.homehub.domain.ActionDecision
import com.tatsu.homehub.domain.ActionPlan
import com.tatsu.homehub.domain.ActionResolver
import com.tatsu.homehub.domain.ActionPolicyEngine
import com.tatsu.homehub.domain.DeviceIntent
import com.tatsu.homehub.domain.ResolvedAction
import com.tatsu.homehub.domain.SwitchBotActionAdapter
import com.tatsu.homehub.model.AcControlState
import com.tatsu.homehub.model.LocalAlarm
import com.tatsu.homehub.model.SwitchBotDevice
import com.tatsu.homehub.update.UpdateInfo
import com.tatsu.homehub.update.UpdateManager
import com.tatsu.homehub.voice.VoiceController
import com.tatsu.homehub.voice.AnswerComparisonSide
import com.tatsu.homehub.voice.AnswerComparisonState
import com.tatsu.homehub.voice.VoiceMode
import com.tatsu.homehub.voice.VoicePhase
import com.tatsu.homehub.voice.VoiceSessionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
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
    private val actionResolver = ActionResolver()
    private val actionPolicyEngine = ActionPolicyEngine()
    private val updateManager = UpdateManager(application)
    private val networkMonitor = NetworkMonitor(application) {
        if (hasSwitchBotCredentials()) refreshDevices(showMessage = false)
        refreshWeather(showError = false)
    }

    private val _devices = MutableStateFlow<List<SwitchBotDevice>>(emptyList())
    val devices: StateFlow<List<SwitchBotDevice>> = _devices.asStateFlow()

    private val _temporaryRoomAssignments = MutableStateFlow(appPrefs.loadTemporaryRoomAssignments())
    val temporaryRoomAssignments: StateFlow<Map<String, String>> = _temporaryRoomAssignments.asStateFlow()

    private val _alarms = MutableStateFlow<List<LocalAlarm>>(emptyList())
    val alarms: StateFlow<List<LocalAlarm>> = _alarms.asStateFlow()

    private val _acStates = MutableStateFlow(appPrefs.loadAcControlStates())
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

    private val _routerComparing = MutableStateFlow(false)
    val routerComparing: StateFlow<Boolean> = _routerComparing.asStateFlow()

    private val _routerCompareResult = MutableStateFlow<RouterCompareResult?>(null)
    val routerCompareResult: StateFlow<RouterCompareResult?> = _routerCompareResult.asStateFlow()

    private val _answerComparison = MutableStateFlow<AnswerComparisonState?>(null)
    val answerComparison: StateFlow<AnswerComparisonState?> = _answerComparison.asStateFlow()

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
    private var routerComparisonJob: Job? = null
    private var comparisonGeneration = 0L
    private var processedVoiceGeneration: Long? = null
    private var pendingActionPlan: ActionPlan? = null
    private var pendingActionExpiresAt: Long = 0L
    private val executedActionPlanIds = LinkedHashSet<String>()
    private var voiceSpeechEndedElapsedMs: Long? = null
    private val conversationHistory = mutableListOf<String>()
    private val executedVoiceOperations = LinkedHashMap<String, Long>()

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

        override fun onLanguageDetected(sessionId: Long, languageTag: String) {
            if (sessionId == _voiceState.value.generationId) {
                _voiceState.value = _voiceState.value.copy(detectedLanguageTag = languageTag)
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

        override fun onSpeakingChanged(sessionId: Long, speaking: Boolean) {
            val current = _voiceState.value
            if (sessionId != current.generationId) return
            _voiceState.value = if (speaking) {
                current.copy(phase = VoicePhase.SPEAKING, error = null)
            } else if (current.phase == VoicePhase.SPEAKING) {
                current.copy(phase = VoicePhase.IDLE)
            } else {
                current
            }
        }

        override fun onSpeechEnded(sessionId: Long, elapsedRealtimeMs: Long) {
            if (sessionId != _voiceState.value.generationId) return
            voiceSpeechEndedElapsedMs = elapsedRealtimeMs
            val timestamps = _voiceState.value.timings.timestamps + ("client_t0_speech_end" to elapsedRealtimeMs)
            _voiceState.value = _voiceState.value.copy(timings = _voiceState.value.timings.copy(timestamps = timestamps))
        }

        override fun onTtsLifecycle(sessionId: Long, event: String, elapsedRealtimeMs: Long) {
            if (sessionId != _voiceState.value.generationId) return
            val current = _voiceState.value.timings
            val timestampKey = when (event) {
                "requested" -> "client_t11_tts_request"
                "started" -> "client_t12_tts_playback_start"
                else -> "client_t13_tts_playback_complete"
            }
            val timestamps = current.timestamps + (timestampKey to elapsedRealtimeMs)
            val ttsRequest = current.timestamps["client_t11_tts_request"]
            val responseAssembledAt = current.timestamps["client_t10_response_assembled"]
            val ttsStartWait = if (event == "requested" && responseAssembledAt != null) elapsedRealtimeMs - responseAssembledAt else current.ttsStartWaitMs
            val ttsPreparation = if (event == "started" && ttsRequest != null) elapsedRealtimeMs - ttsRequest else current.ttsPreparationMs
            val total = if (event == "completed" && voiceSpeechEndedElapsedMs != null) elapsedRealtimeMs - voiceSpeechEndedElapsedMs!! else current.totalMs
            val afterRouting = total?.let { it - (current.sttMs ?: 0L) - (current.preRoutingWaitMs ?: 0L) - (current.routingMs ?: 0L) }
            val exclusiveIntervals = listOfNotNull(
                current.sttMs, current.preRoutingWaitMs, current.routingMs, current.postRoutingWaitMs,
                current.stateFetchMs, current.resolverMs, current.policyMs, current.deviceExecutionMs,
                current.answerStartWaitMs,
                if (current.answerStartWaitMs != null) current.answerTtftMs else null,
                current.answerGenerationMs, current.responseAssemblyMs,
                current.ttsStartWaitMs, current.ttsPreparationMs
            )
            val remainder = total?.minus(exclusiveIntervals.sum())
            _voiceState.value = _voiceState.value.copy(timings = current.copy(
                ttsStartWaitMs = ttsStartWait,
                ttsPreparationMs = ttsPreparation,
                totalMs = total,
                afterRoutingMs = afterRouting,
                unaccountedMs = remainder,
                timingError = current.timingError ?: when {
                    listOfNotNull(ttsStartWait, ttsPreparation, total, afterRouting, remainder).any { it < 0 } -> "Android timing interval is negative or exclusive intervals exceed totalMs"
                    else -> null
                },
                timestamps = timestamps
            ))
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
                    updateTemporaryRoomAssignments(list)
                    _devices.value = list
                    syncReadableAirConditionerStates(client, list)
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

    fun setTemporaryRoom(device: SwitchBotDevice, room: String) {
        if (room !in TemporaryRoomAssignments.choices) return
        pendingActionPlan = null
        pendingActionExpiresAt = 0L
        val next = _temporaryRoomAssignments.value.toMutableMap()
        next[device.deviceId] = if (room == TemporaryRoomAssignments.UNASSIGNED) "" else room
        appPrefs.saveTemporaryRoomAssignments(next)
        _temporaryRoomAssignments.value = next
    }

    private fun updateTemporaryRoomAssignments(devices: List<SwitchBotDevice>) {
        val defaults = TemporaryRoomAssignments.inferDefaults(devices)
        val saved = _temporaryRoomAssignments.value
        val next = defaults + saved
        if (next != _temporaryRoomAssignments.value) {
            appPrefs.saveTemporaryRoomAssignments(next)
            _temporaryRoomAssignments.value = next
        }
    }

    private fun devicesWithRooms(): List<SwitchBotDevice> = _devices.value.map { device ->
        val room = _temporaryRoomAssignments.value[device.deviceId]?.takeIf(String::isNotBlank)
        TemporaryRoomAssignments.applyRoom(device, room)
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

    fun setAirConditionerPower(device: SwitchBotDevice, on: Boolean) {
        val client = clientOrNull() ?: return
        val previous = _acStates.value[device.deviceId]
        val next = (previous ?: AcControlState()).copy(power = on)
        setOptimisticAcState(device.deviceId, next)
        viewModelScope.launch {
            val result = if (on) client.turnOn(device.deviceId) else client.turnOff(device.deviceId)
            result
                .onSuccess {
                    saveKnownAcState(device.deviceId, next)
                    _message.value = device.name + ": " + if (on) "ON" else "OFF"
                }
                .onFailure { error ->
                    restoreAcState(device.deviceId, previous)
                    _message.value = device.name + ": " + (error.message ?: "operation failed")
                }
        }
    }

    fun setAirConditioner(device: SwitchBotDevice, state: AcControlState) {
        val client = clientOrNull() ?: return
        // Temperature / mode / fan changes are sent as setAll and explicitly power the AC on.
        // This avoids the old bug where an unknown local default (power=false) sent "...off".
        val effectiveState = state.copy(
            temperature = state.temperature.coerceIn(16, 30),
            mode = state.mode.coerceIn(1, 5),
            fanSpeed = state.fanSpeed.coerceIn(1, 4),
            power = true
        )

        val previous = _acStates.value[device.deviceId]
        setOptimisticAcState(device.deviceId, effectiveState)
        viewModelScope.launch {
            client.setAirConditioner(
                deviceId = device.deviceId,
                temperature = effectiveState.temperature,
                mode = effectiveState.mode,
                fanSpeed = effectiveState.fanSpeed,
                power = true
            )
                .onSuccess {
                    saveKnownAcState(device.deviceId, effectiveState)
                    _message.value = device.name + ": " +
                        effectiveState.temperature.toString() + "℃ " + modeName(effectiveState.mode)
                }
                .onFailure { error ->
                    restoreAcState(device.deviceId, previous)
                    _message.value = device.name + ": " + (error.message ?: "operation failed")
                }
        }
    }

    private suspend fun syncReadableAirConditionerStates(
        client: SwitchBotClient,
        devices: List<SwitchBotDevice>
    ) {
        devices.filter { it.isAirConditioner && !it.infrared }.forEach { device ->
            client.getDeviceState(device).onSuccess { remote ->
                val previous = _acStates.value[device.deviceId] ?: AcControlState()
                val temperature = remote.temperature ?: return@onSuccess
                saveKnownAcState(
                    device.deviceId,
                    AcControlState(
                        temperature = temperature.coerceIn(16, 30),
                        mode = remote.mode ?: previous.mode,
                        fanSpeed = remote.fanSpeed ?: previous.fanSpeed,
                        power = remote.power ?: previous.power
                    )
                )
            }
        }
    }

    private fun setOptimisticAcState(deviceId: String, state: AcControlState) {
        _acStates.value = _acStates.value + (deviceId to state)
    }

    private fun restoreAcState(deviceId: String, previous: AcControlState?) {
        _acStates.value = if (previous == null) {
            _acStates.value - deviceId
        } else {
            _acStates.value + (deviceId to previous)
        }
    }

    private fun saveKnownAcState(deviceId: String, state: AcControlState) {
        val next = _acStates.value + (deviceId to state)
        _acStates.value = next
        appPrefs.saveAcControlStates(next)
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
        if (languageTag in setOf(AppPrefs.VOICE_LANGUAGE_AUTO, "ja-JP", "en-US", "de-DE")) {
            appPrefs.voiceLanguageTag = languageTag
        }
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

    fun compareRouters(text: String) {
        val query = text.trim()
        if (query.isBlank()) {
            _message.value = "比較する文を入力してください"
            return
        }
        val url = appPrefs.aiBackendUrl
        if (url.isBlank()) {
            _message.value = "設定からAI Backend URLを登録してください"
            return
        }

        val request = ++comparisonGeneration
        routerComparisonJob?.cancel()
        _routerCompareResult.value = null
        _answerComparison.value = null
        val job = viewModelScope.launch {
            _routerComparing.value = true
            try {
                val result = withTimeout(60_000) {
                    aiBackendClient.compareRouters(url, query).getOrThrow()
                }
                if (request == comparisonGeneration) {
                    _routerCompareResult.value = result
                    _aiBackendOnline.value = true
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                if (cancelled is kotlinx.coroutines.TimeoutCancellationException && request == comparisonGeneration) {
                    _message.value = "ルーター比較が60秒でタイムアウトしました"
                    return@launch
                }
                throw cancelled
            } catch (error: Throwable) {
                if (request == comparisonGeneration) {
                    _aiBackendOnline.value = false
                    _message.value = "ルーター比較失敗: ${error.message ?: "unknown"}"
                }
            } finally {
                if (request == comparisonGeneration) _routerComparing.value = false
            }
        }
        routerComparisonJob = job
    }

    fun compareAnswers(text: String) {
        val query = text.trim()
        if (query.isBlank()) {
            _message.value = "比較する文を入力してください"
            return
        }
        val url = appPrefs.aiBackendUrl
        if (url.isBlank()) {
            _message.value = "設定からAI Backend URLを登録してください"
            return
        }
        val runId = ++comparisonGeneration
        routerComparisonJob?.cancel()
        _routerCompareResult.value = null
        _answerComparison.value = null
        val job = viewModelScope.launch {
            startAnswerComparison(query, buildVoiceContext(), url, runId, voiceSessionGeneration = null)
        }
        routerComparisonJob = job
    }

    fun startVoiceSession() {
        startVoiceSessionInternal(VoiceMode.LUNA)
    }

    fun startVoiceSessionJev() {
        startVoiceSessionInternal(VoiceMode.JEV)
    }

    fun startVoiceRouterComparison() {
        startVoiceSessionInternal(VoiceMode.ROUTER_COMPARE)
    }

    fun startVoiceAnswerComparison() {
        startVoiceSessionInternal(VoiceMode.ANSWER_COMPARE)
    }

    fun speakComparisonAnswer(result: AiDispatchResult) {
        val text = when (result.route) {
            "device_action", "alarm_action" -> listOfNotNull(
                result.target?.let { "対象は $it。" },
                result.action?.let { comparisonActionText(it, result.language) },
                result.temperatureC?.let { "温度は ${it} 度。" },
                result.referenceTimeLocal?.let { "変更前は $it。" },
                result.timeLocal?.let { "変更後は $it。" },
                if (result.route == "device_action" || result.route == "alarm_action") {
                    localized(result.language, "比較用の提案で、操作は実行していません。", "This is a comparison proposal. Nothing was executed.", "Dies ist ein Vergleichsvorschlag. Es wurde nichts ausgeführt.")
                } else null
            ).joinToString(" ")
            "weather" -> weatherVoiceResponse(result.language)
            else -> result.answerText.orEmpty()
        }.ifBlank { localized(result.language, "返答文がありません", "No reply text is available.", "Es liegt kein Antworttext vor.") }
        val generation = voiceGeneration
        _voiceState.value = _voiceState.value.copy(
            phase = VoicePhase.ANSWER_READY,
            status = "比較した回答を読み上げます",
            responseText = text
        )
        voiceController.speak(generation, text, result.language, "comparison-$generation-${UUID.randomUUID()}")
    }

    private fun comparisonActionText(action: String, language: String): String = when (action) {
        "turn_on" -> localized(language, "電源を入れる案です。", "The proposal is to turn it on.", "Der Vorschlag ist, es einzuschalten.")
        "turn_off" -> localized(language, "電源を切る案です。", "The proposal is to turn it off.", "Der Vorschlag ist, es auszuschalten.")
        "set_ac" -> localized(language, "エアコンを設定する案です。", "The proposal is to set the air conditioner.", "Der Vorschlag ist, die Klimaanlage einzustellen.")
        "alarm_create" -> localized(language, "アラームを作成する案です。", "The proposal is to create an alarm.", "Der Vorschlag ist, einen Alarm zu erstellen.")
        "alarm_update" -> localized(language, "アラームを変更する案です。", "The proposal is to update the alarm.", "Der Vorschlag ist, den Alarm zu ändern.")
        "alarm_delete" -> localized(language, "アラームを削除する案です。", "The proposal is to delete the alarm.", "Der Vorschlag ist, den Alarm zu löschen.")
        else -> localized(language, "操作案です。", "This is an action proposal.", "Dies ist ein Handlungsvorschlag.")
    }

    private fun startVoiceSessionInternal(mode: VoiceMode) {
        val url = appPrefs.aiBackendUrl
        if (url.isBlank()) {
            _message.value = "設定からAI Backend URLを登録してください"
            return
        }

        val previousGeneration = voiceGeneration
        voiceGeneration += 1
        comparisonGeneration += 1
        if (previousGeneration > 0) voiceController.cancelSession(previousGeneration)
        currentVoiceJob?.cancel()
        routerComparisonJob?.cancel()
        routerComparisonJob = null
        processedVoiceGeneration = null
        voiceSpeechEndedElapsedMs = null
        _routerComparing.value = false
        _routerCompareResult.value = null
        _answerComparison.value = null
        _voiceState.value = VoiceSessionState(
            phase = VoicePhase.PREPARING,
            mode = mode,
            generationId = voiceGeneration,
            status = when {
                mode == VoiceMode.ANSWER_COMPARE -> "同じ質問への回答を比較します。家電・アラームは実行しません"
                mode == VoiceMode.ROUTER_COMPARE -> "Luna / Jevの判定を比較します"
                mode == VoiceMode.JEV -> "Jev経路で音声入力を準備しています"
                else -> "Luna経路で音声入力を準備しています"
            },
            diagnostic = "app=${com.tatsu.homehub.BuildConfig.VERSION_NAME} (${com.tatsu.homehub.BuildConfig.VERSION_CODE})",
            detectedLanguageTag = null
        )
        voiceController.startListening(voiceGeneration, appPrefs.voiceLanguageTag)
    }

    fun stopVoiceListening() {
        voiceController.stopListening()
    }

    fun cancelVoiceSession() {
        val previousGeneration = voiceGeneration
        voiceGeneration += 1
        comparisonGeneration += 1
        currentVoiceJob?.cancel()
        routerComparisonJob?.cancel()
        routerComparisonJob = null
        voiceController.cancelSession(previousGeneration)
        _routerComparing.value = false
        _routerCompareResult.value = null
        _answerComparison.value = null
        _voiceState.value = VoiceSessionState(
            phase = VoicePhase.IDLE,
            generationId = voiceGeneration
        )
    }

    private fun processVoiceText(text: String, generation: Long) {
        if (generation != voiceGeneration || text.isBlank() || processedVoiceGeneration == generation) return
        processedVoiceGeneration = generation
        val sttCompletedAt = android.os.SystemClock.elapsedRealtime()
        val speechEndedAt = voiceSpeechEndedElapsedMs
        val url = appPrefs.aiBackendUrl
        if (url.isBlank()) return
        val mode = _voiceState.value.mode
        val context = buildVoiceContext()
        val comparisonRunId = ++comparisonGeneration
        routerComparisonJob?.cancel()
        routerComparisonJob = null
        _routerComparing.value = false
        _routerCompareResult.value = null
        _answerComparison.value = null

        _voiceState.value = _voiceState.value.copy(
            phase = VoicePhase.THINKING,
            partialText = "",
            finalText = text,
            responseText = "",
            route = null,
            error = null,
            timings = _voiceState.value.timings.copy(
                sttMs = speechEndedAt?.let { sttCompletedAt - it },
                timingError = _voiceState.value.timings.timingError
                    ?: speechEndedAt?.let { if (sttCompletedAt - it < 0) "STT interval is negative" else null },
                timestamps = _voiceState.value.timings.timestamps + mapOf(
                    "client_t1_stt_complete" to sttCompletedAt
                )
            )
        )

        if (consumePendingAction(text, generation)) return

        if (mode == VoiceMode.ROUTER_COMPARE) {
            currentVoiceJob?.cancel()
            currentVoiceJob = viewModelScope.launch {
                _routerComparing.value = true
                val started = android.os.SystemClock.elapsedRealtime()
                try {
                    val result = withTimeout(60_000) {
                        aiBackendClient.compareRouters(url, text, context).getOrThrow()
                    }
                    if (generation != voiceGeneration) return@launch
                    _routerCompareResult.value = result
                    _aiBackendOnline.value = true
                    _voiceState.value = _voiceState.value.copy(
                        phase = VoicePhase.IDLE,
                        status = "Luna / Jev 判定比較完了",
                        responseText = "判定だけ比較しました。家電・アラームは実行していません。",
                        latencyMs = android.os.SystemClock.elapsedRealtime() - started,
                        error = null
                    )
                } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                    if (generation == voiceGeneration) _voiceState.value = _voiceState.value.copy(
                        phase = VoicePhase.ERROR,
                        error = "判定比較が60秒でタイムアウトしました"
                    )
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    if (generation == voiceGeneration) _voiceState.value = _voiceState.value.copy(
                        phase = VoicePhase.ERROR,
                        error = "ルーター比較失敗: ${error.message ?: "unknown"}"
                    )
                } finally {
                    if (generation == voiceGeneration) _routerComparing.value = false
                }
            }
            return
        }

        if (mode == VoiceMode.ANSWER_COMPARE) {
            currentVoiceJob?.cancel()
            currentVoiceJob = viewModelScope.launch {
                startAnswerComparison(text, context, url, comparisonRunId, voiceSessionGeneration = generation)
            }
            return
        }

        currentVoiceJob?.cancel()
        currentVoiceJob = viewModelScope.launch {
            val started = android.os.SystemClock.elapsedRealtime()
            val preRoutingWaitMs = started - sttCompletedAt
            _voiceState.value = _voiceState.value.copy(timings = _voiceState.value.timings.copy(
                preRoutingWaitMs = preRoutingWaitMs,
                timingError = _voiceState.value.timings.timingError
                    ?: if (preRoutingWaitMs < 0) "pre-routing wait interval is negative" else null,
                timestamps = _voiceState.value.timings.timestamps + ("client_t2_dispatch_start" to started)
            ))
            try {
                val result = withTimeout(60_000) {
                    if (mode == VoiceMode.JEV) aiBackendClient.dispatchJev(url, text, context)
                    else aiBackendClient.dispatch(url, text, context)
                }.getOrThrow()
                if (generation != voiceGeneration) return@launch
                _aiResult.value = result
                _aiBackendOnline.value = true
                val backendTimings = result.timings
                val receivedAt = android.os.SystemClock.elapsedRealtime()
                _voiceState.value = _voiceState.value.copy(timings = backendTimings.copy(
                    sttMs = _voiceState.value.timings.sttMs,
                    totalMs = speechEndedAt?.let { receivedAt - it },
                    timingError = _voiceState.value.timings.timingError ?: backendTimings.timingError
                        ?: if (speechEndedAt != null && receivedAt - speechEndedAt < 0) "speech-to-response interval is negative" else null,
                    timestamps = _voiceState.value.timings.timestamps + backendTimings.timestamps + mapOf(
                        "client_t3_backend_response_received" to receivedAt
                    )
                ))
                val postRoutingStartedAt = android.os.SystemClock.elapsedRealtime()
                val postRoutingWaitMs = postRoutingStartedAt - receivedAt
                _voiceState.value = _voiceState.value.copy(timings = _voiceState.value.timings.copy(
                    postRoutingWaitMs = postRoutingWaitMs,
                    timingError = _voiceState.value.timings.timingError
                        ?: if (postRoutingWaitMs < 0) "post-routing wait interval is negative" else null,
                    timestamps = _voiceState.value.timings.timestamps + ("client_t4_post_routing_start" to postRoutingStartedAt)
                ))
                val response = handleVoiceResult(result, generation, text)
                if (generation != voiceGeneration) return@launch
                if (response.isBlank()) error("返答が空でした")
                val assembledAt = android.os.SystemClock.elapsedRealtime()
                _voiceState.value = _voiceState.value.copy(timings = _voiceState.value.timings.copy(
                    totalMs = speechEndedAt?.let { assembledAt - it },
                    afterRoutingMs = (assembledAt - (speechEndedAt ?: started)) -
                        (_voiceState.value.timings.sttMs ?: 0L) - preRoutingWaitMs -
                        (_voiceState.value.timings.routingMs ?: 0L),
                    timingError = _voiceState.value.timings.timingError
                        ?: if (assembledAt - (speechEndedAt ?: started) -
                            (_voiceState.value.timings.sttMs ?: 0L) - preRoutingWaitMs -
                            (_voiceState.value.timings.routingMs ?: 0L) < 0) "after-routing interval is negative" else null,
                    timestamps = _voiceState.value.timings.timestamps + ("client_t10_response_assembled" to assembledAt)
                ))
                rememberConversation(text, response)
                _voiceState.value = _voiceState.value.copy(
                    phase = VoicePhase.ANSWER_READY,
                    status = "返答を表示しました",
                    responseText = response,
                    route = result.route,
                    latencyMs = android.os.SystemClock.elapsedRealtime() - started,
                    error = null
                )
                voiceController.speak(generation, response, result.language, "voice-$generation")
            } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                if (generation == voiceGeneration) {
                    _voiceState.value = _voiceState.value.copy(
                        phase = VoicePhase.ERROR,
                        error = "AIから60秒以内に応答がありませんでした。もう一度お試しください"
                    )
                }
                return@launch
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (generation == voiceGeneration) {
                    _aiBackendOnline.value = false
                    _voiceState.value = _voiceState.value.copy(
                        phase = VoicePhase.ERROR,
                        error = "AI処理失敗: ${error.message ?: "unknown"}"
                    )
                }
            }
        }
    }

    private suspend fun startAnswerComparison(
        text: String,
        context: String,
        url: String,
        runId: Long,
        voiceSessionGeneration: Long?
    ) {
        val active: () -> Boolean = {
            runId == comparisonGeneration &&
                (voiceSessionGeneration == null || voiceSessionGeneration == voiceGeneration)
        }
        _routerComparing.value = true
        _answerComparison.value = AnswerComparisonState(
            query = text,
            startedAtElapsedMs = android.os.SystemClock.elapsedRealtime()
        )
        if (voiceSessionGeneration != null) {
            _voiceState.value = _voiceState.value.copy(
                phase = VoicePhase.THINKING,
                status = "Luna経路とJev経路の返答を作成中",
                responseText = "",
                error = null
            )
        }
        try {
            coroutineScope {
                val luna = async {
                    runAnswerRoute(url, text, context, useJev = false, voiceSessionGeneration = voiceSessionGeneration).also { side ->
                        if (active()) _answerComparison.value = _answerComparison.value?.copy(luna = side)
                    }
                }
                val jev = async {
                    runAnswerRoute(url, text, context, useJev = true, voiceSessionGeneration = voiceSessionGeneration).also { side ->
                        if (active()) _answerComparison.value = _answerComparison.value?.copy(jev = side)
                    }
                }
                awaitAll(luna, jev)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } finally {
            if (active()) _routerComparing.value = false
        }
        if (active()) {
            val current = _answerComparison.value
            val completed = listOfNotNull(current?.luna, current?.jev).count { !it.pending }
            _aiBackendOnline.value = completed > 0
            if (voiceSessionGeneration != null) {
                _voiceState.value = _voiceState.value.copy(
                    phase = VoicePhase.IDLE,
                    status = if (completed == 2) "回答比較完了" else "比較終了。失敗した経路は再実行できます",
                    latencyMs = current?.let { android.os.SystemClock.elapsedRealtime() - it.startedAtElapsedMs },
                    error = if (completed == 0) "両方の経路で回答を取得できませんでした" else null
                )
            }
        }
    }

    private suspend fun runAnswerRoute(
        url: String,
        text: String,
        context: String,
        useJev: Boolean,
        voiceSessionGeneration: Long?
    ): AnswerComparisonSide {
        val started = android.os.SystemClock.elapsedRealtime()
        return try {
            val result = withTimeout(60_000) {
                if (useJev) aiBackendClient.dispatchJev(url, text, context)
                else aiBackendClient.dispatch(url, text, context)
            }.getOrThrow()
            val responseReceivedAt = android.os.SystemClock.elapsedRealtime()
            val clientTimes = _voiceState.value.timings.timestamps
            val speechEndedAt = if (voiceSessionGeneration != null) clientTimes["client_t0_speech_end"] else null
            val sttCompletedAt = if (voiceSessionGeneration != null) clientTimes["client_t1_stt_complete"] else null
            val preRoutingWaitMs = sttCompletedAt?.let { started - it }
            val postRoutingStartedAt = android.os.SystemClock.elapsedRealtime()
            val postRoutingWaitMs = postRoutingStartedAt - responseReceivedAt
            val base = result.timings
            val actionPlan = if (result.route == "device_action") resolveComparisonAction(result) else null
            val responseCompletedAt = android.os.SystemClock.elapsedRealtime()
            val totalMs = (speechEndedAt ?: started).let { responseCompletedAt - it }
            val sttMs = if (voiceSessionGeneration != null) _voiceState.value.timings.sttMs else null
            val unaccounted = computeUnaccounted(totalMs, base, actionPlan?.second, sttMs, preRoutingWaitMs, postRoutingWaitMs)
            val completedTimings = base.copy(
                totalMs = totalMs,
                sttMs = sttMs,
                preRoutingWaitMs = preRoutingWaitMs,
                postRoutingWaitMs = postRoutingWaitMs,
                afterRoutingMs = totalMs - (sttMs ?: 0L) - (preRoutingWaitMs ?: 0L) - (base.routingMs ?: 0L),
                stateFetchMs = actionPlan?.second?.stateFetchMs ?: base.stateFetchMs,
                resolverMs = actionPlan?.second?.resolverMs ?: base.resolverMs,
                policyMs = actionPlan?.second?.policyMs ?: base.policyMs,
                deviceExecutionMs = null,
                unaccountedMs = unaccounted,
                timingError = base.timingError ?: when {
                    preRoutingWaitMs != null && preRoutingWaitMs < 0 -> "pre-routing wait interval is negative"
                    postRoutingWaitMs < 0 -> "post-routing wait interval is negative"
                    totalMs - (sttMs ?: 0L) - (preRoutingWaitMs ?: 0L) - (base.routingMs ?: 0L) < 0 -> "after-routing interval is negative"
                    actionPlan?.second?.timingError != null -> actionPlan?.second?.timingError
                    unaccounted == null -> "exclusive measured intervals exceed totalMs"
                    else -> null
                },
                timestamps = base.timestamps + mapOf(
                    "client_t2_dispatch_start" to started,
                    "client_t3_backend_response_received" to responseReceivedAt,
                    "client_t4_post_routing_start" to postRoutingStartedAt,
                    "client_t10_response_assembled" to responseCompletedAt
                )
            )
            AnswerComparisonSide(
                result = result,
                clientLatencyMs = totalMs,
                timings = completedTimings,
                actionPlanJson = actionPlan?.first,
                pending = false
            )
        } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
            AnswerComparisonSide(
                error = "60秒で応答がありませんでした",
                clientLatencyMs = android.os.SystemClock.elapsedRealtime() - started,
                pending = false
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            AnswerComparisonSide(
                error = error.message ?: if (error is kotlinx.coroutines.TimeoutCancellationException) "60秒で応答がありませんでした" else "回答の取得に失敗しました",
                clientLatencyMs = android.os.SystemClock.elapsedRealtime() - started,
                pending = false
            )
        }
    }

    private suspend fun resolveComparisonAction(result: AiDispatchResult): Pair<String, ActionPlan>? {
        // Compare intent resolution with the real synchronized devices and real readable status.
        // This path only builds a proposal; the SwitchBot command adapter is never called here.
        val comparisonDevices = devicesWithRooms()
        val candidates = resolveDeviceCandidates(result, comparisonDevices)
        val device = candidates.singleOrNull()
        var state: com.tatsu.homehub.model.SwitchBotDeviceState? = null
        var stateFetchMs: Long? = null
        val client = clientOrNull()
        if (device != null && !device.infrared && client != null) {
            val stateStarted = android.os.SystemClock.elapsedRealtime()
            state = client.getDeviceState(device).getOrNull()
            stateFetchMs = android.os.SystemClock.elapsedRealtime() - stateStarted
        }
        val intent = DeviceIntent(result.route, result.target, result.targetType, result.action, result.goal,
            result.temperatureC ?: result.parameters["temperature"], result.confidence, result.language)
        val plan = actionPolicyEngine.evaluate(actionResolver.resolve(intent, comparisonDevices, state, stateFetchMs))
        return actionPlanDiagnostic(result, plan, null) to plan
    }

    private fun computeUnaccounted(
        totalMs: Long,
        server: AiPipelineTimings,
        plan: ActionPlan?,
        sttMs: Long?,
        preRoutingWaitMs: Long?,
        postRoutingWaitMs: Long?
    ): Long? {
        val exclusive = listOfNotNull(
            sttMs,
            preRoutingWaitMs,
            server.routingMs,
            postRoutingWaitMs,
            server.answerStartWaitMs,
            if (server.answerStartWaitMs != null) server.answerTtftMs else null,
            server.answerGenerationMs,
            server.responseAssemblyMs,
            plan?.stateFetchMs,
            plan?.resolverMs,
            plan?.policyMs
        ).sum()
        val remaining = totalMs - exclusive
        return if (remaining < 0) null else remaining
    }


    private suspend fun handleVoiceResult(result: AiDispatchResult, generation: Long, utterance: String): String {
        if (generation != voiceGeneration) return ""
        return when (result.route) {
            "simple_chat", "deep_reasoning", "clarify" ->
                result.answerText ?: localized(result.language, "もう一度お願いします", "Please try again.", "Bitte noch einmal.")
            "weather" -> weatherVoiceResponse(result.language)
            "device_action" -> resolveVoiceDeviceAction(result, generation, utterance, allowLunaFallback = true)
            "alarm_action" -> executeVoiceAlarmAction(result, generation)
            else -> localized(result.language, "その操作にはまだ対応していません", "That action is not supported yet.", "Diese Aktion wird noch nicht unterstützt.")
        }
    }

    private suspend fun resolveVoiceDeviceAction(
        result: AiDispatchResult,
        generation: Long,
        utterance: String,
        allowLunaFallback: Boolean
    ): String {
        if (generation != voiceGeneration) return ""
        val client = clientOrNull()
            ?: return localized(result.language, "SwitchBotの設定が必要です", "SwitchBot is not configured.", "SwitchBot ist nicht eingerichtet.")
        val candidates = resolveDeviceCandidates(result)
        val device = candidates.singleOrNull()
        val stateNeeded = result.goal in setOf("cooler", "warmer", "increase", "decrease", "brighter", "darker") ||
            result.action in setOf("turn_on", "turn_off")
        var deviceState: com.tatsu.homehub.model.SwitchBotDeviceState? = null
        var stateFetchMs: Long? = null
        var stateStartedAt: Long? = null
        var stateCompletedAt: Long? = null
        if (stateNeeded && device != null && !device.infrared) {
            val stateStarted = android.os.SystemClock.elapsedRealtime()
            stateStartedAt = stateStarted
            deviceState = client.getDeviceState(device).getOrNull()
            stateFetchMs = android.os.SystemClock.elapsedRealtime() - stateStarted
            stateCompletedAt = android.os.SystemClock.elapsedRealtime()
        }
        val resolvedAt = android.os.SystemClock.elapsedRealtime()
        val intent = DeviceIntent(
            route = result.route,
            target = result.target,
            targetType = result.targetType,
            action = result.action,
            goal = result.goal,
            temperatureC = result.temperatureC ?: result.parameters["temperature"],
            confidence = result.confidence,
            language = result.language
        )
        val resolvedPlan = actionResolver.resolve(intent, devicesWithRooms(), deviceState, stateFetchMs)
        val resolverCompletedAt = android.os.SystemClock.elapsedRealtime()
        val plan = actionPolicyEngine.evaluate(resolvedPlan)
        val policyCompletedAt = android.os.SystemClock.elapsedRealtime()
        _voiceState.value = _voiceState.value.copy(actionPlanJson = actionPlanDiagnostic(result, plan, null))
        _voiceState.value = _voiceState.value.copy(timings = _voiceState.value.timings.copy(
            stateFetchMs = stateFetchMs,
            resolverMs = plan.resolverMs,
            policyMs = plan.policyMs,
            timingError = plan.timingError,
                    timestamps = _voiceState.value.timings.timestamps + mapOf(
                "client_action_resolver_start" to resolvedAt,
                        "client_t5_state_fetch_start" to stateStartedAt,
                        "client_t6_state_fetch_complete" to stateCompletedAt,
                        "client_t7_action_resolver_complete" to resolverCompletedAt,
                        "client_t8_policy_complete" to policyCompletedAt
            )
        ))
        when (plan.decision) {
            ActionDecision.CONFIRM -> {
                if (plan.device != null && plan.action != null) {
                    pendingActionPlan = plan
                    pendingActionExpiresAt = android.os.SystemClock.elapsedRealtime() + PENDING_ACTION_TTL_MS
                }
                return plan.response
            }
            ActionDecision.FALLBACK -> {
                if (plan.policy == com.tatsu.homehub.domain.ActionPolicy.BLOCKED) return plan.response
                if (allowLunaFallback && utterance.isNotBlank() && appPrefs.aiBackendUrl.isNotBlank()) {
                    val luna = aiBackendClient.dispatch(appPrefs.aiBackendUrl, utterance, buildVoiceContext()).getOrNull()
                    if (generation != voiceGeneration) return ""
                    if (luna != null) {
                        if (luna.route == "device_action") {
                            val sameExplicitAction = result.action != null &&
                                luna.action == result.action &&
                                luna.temperatureC == result.temperatureC &&
                                normalizeName(luna.target.orEmpty()) == normalizeName(result.target.orEmpty())
                            if (!sameExplicitAction) return plan.response
                            return resolveVoiceDeviceAction(luna, generation, utterance, allowLunaFallback = false)
                        }
                        if (luna.answerText != null) return luna.answerText
                    }
                }
                return plan.response
            }
            ActionDecision.NOOP -> return plan.response
            ActionDecision.EXECUTE -> Unit
        }
        if (!markVoiceOperationOnce(result)) {
            return localized(result.language, "同じ操作は重複実行しませんでした", "I blocked a duplicate action.", "Die doppelte Aktion wurde blockiert.")
        }
        val adapter = SwitchBotActionAdapter(
            client,
            getKnownAcState = { id -> _acStates.value[id] ?: AcControlState() },
            saveKnownAcState = { id, state -> saveKnownAcState(id, state) }
        )
        val adapterStarted = android.os.SystemClock.elapsedRealtime()
        _voiceState.value = _voiceState.value.copy(timings = _voiceState.value.timings.copy(
            timestamps = _voiceState.value.timings.timestamps + ("client_t9_device_adapter_start" to adapterStarted)
        ))
        val execution = adapter.execute(plan)
        val adapterCompleted = android.os.SystemClock.elapsedRealtime()
        _voiceState.value = _voiceState.value.copy(timings = _voiceState.value.timings.copy(
            deviceExecutionMs = execution.elapsedMs,
            totalMs = voiceSpeechEndedElapsedMs?.let { adapterCompleted - it },
            timestamps = _voiceState.value.timings.timestamps + mapOf(
                "client_t10_device_result_received" to adapterCompleted,
                "client_device_command_complete" to adapterCompleted
            )
        ))
        _voiceState.value = _voiceState.value.copy(actionPlanJson = actionPlanDiagnostic(result, plan, execution))
        if (!execution.accepted) {
            return localized(result.language, "家電への指示送信に失敗しました: ${execution.error ?: "不明なエラー"}", "The device command failed: ${execution.error ?: "unknown error"}.", "Der Gerätebefehl ist fehlgeschlagen: ${execution.error ?: "unbekannter Fehler"}.")
        }
        val target = plan.device?.name ?: return plan.response
        return when (plan.action?.type) {
            "power_on" -> localized(result.language, "${target}へON指示を送信しました", "Sent ON to ${target}.", "EIN an ${target} gesendet.")
            "power_off" -> localized(result.language, "${target}へOFF指示を送信しました", "Sent OFF to ${target}.", "AUS an ${target} gesendet.")
            else -> localized(result.language, "${target}へ${plan.action?.temperatureC}℃の設定を送信しました", "Sent the ${plan.action?.temperatureC}°C setting to ${target}.", "Die Einstellung ${plan.action?.temperatureC}°C wurde an ${target} gesendet.")
        }
    }

    private fun resolveDeviceCandidates(
        result: AiDispatchResult,
        devices: List<SwitchBotDevice> = devicesWithRooms()
    ): List<SwitchBotDevice> = com.tatsu.homehub.domain.DeviceTargetResolver.resolve(
        result.target, result.targetType, devices
    )

    private fun actionPlanDiagnostic(
        result: AiDispatchResult?,
        plan: ActionPlan,
        execution: com.tatsu.homehub.domain.DeviceExecutionResult?
    ): String = org.json.JSONObject()
        .put("requestId", result?.requestId ?: org.json.JSONObject.NULL)
        .put("jevRaw", result?.rawIntentJson ?: org.json.JSONObject.NULL)
        .put("target", plan.device?.name ?: org.json.JSONObject.NULL)
        .put("deviceId", plan.device?.deviceId ?: org.json.JSONObject.NULL)
        .put("temporaryRoom", plan.device?.let { _temporaryRoomAssignments.value[it.deviceId] } ?: org.json.JSONObject.NULL)
        .put("deviceState", plan.currentState?.rawJson?.let { org.json.JSONObject(it) } ?: org.json.JSONObject.NULL)
        .put("goal", plan.intent.goal ?: org.json.JSONObject.NULL)
        .put("actionPlan", org.json.JSONObject()
            .put("type", plan.action?.type ?: org.json.JSONObject.NULL)
            .put("temperatureC", plan.action?.temperatureC ?: org.json.JSONObject.NULL))
        .put("decision", plan.decision.name.lowercase())
        .put("policy", plan.policy.name)
        .put("response", plan.response)
        .put("reason", plan.reason)
        .put("timings", org.json.JSONObject()
            .put("stateFetchMs", plan.stateFetchMs ?: org.json.JSONObject.NULL)
            .put("resolverMs", plan.resolverMs)
            .put("policyMs", plan.policyMs)
            .put("deviceExecutionMs", execution?.elapsedMs ?: org.json.JSONObject.NULL))
            .put("deviceResult", execution?.let {
            org.json.JSONObject().put("accepted", it.accepted).put("adapter", it.adapter)
                .put("command", it.command).put("error", it.error ?: org.json.JSONObject.NULL)
                .put("timingError", it.timingError ?: org.json.JSONObject.NULL)
        } ?: org.json.JSONObject.NULL)
        .toString()

    private fun consumePendingAction(text: String, generation: Long): Boolean {
        val pending = pendingActionPlan ?: return false
        if (android.os.SystemClock.elapsedRealtime() > pendingActionExpiresAt) {
            pendingActionPlan = null
            pendingActionExpiresAt = 0L
            return false
        }
        val normalized = normalizeName(text)
        val isYes = normalized in setOf("はい", "うん", "お願い", "いいよ", "そうして", "yes", "yeah", "ok", "okay", "ja", "bitte")
        val isNo = normalized in setOf("いいえ", "いや", "やめて", "やめる", "no", "nope", "nein", "nicht")
        val requestedTemperature = Regex("(?:^|\\D)(1[6-9]|2[0-9]|30)(?=\\s*(?:度|°?c)?(?:にして|に|$))", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.toIntOrNull()
        if (!isYes && !isNo && requestedTemperature == null) {
            pendingActionPlan = null
            pendingActionExpiresAt = 0L
            return false
        }

        pendingActionPlan = null
        pendingActionExpiresAt = 0L
        currentVoiceJob?.cancel()
        currentVoiceJob = viewModelScope.launch {
            val response = when {
                isNo -> localized(pending.intent.language, "わかりました。操作しません", "Okay, I won't change it.", "Okay, ich ändere nichts.")
                requestedTemperature != null -> {
                    if (requestedTemperature !in 16..30 || pending.device?.isAirConditioner != true) {
                        localized(pending.intent.language, "16〜30度のエアコン設定を指定してください", "Please specify an air-conditioner temperature from 16 to 30 degrees.", "Bitte gib eine Klimaanlagentemperatur zwischen 16 und 30 Grad an.")
                    } else {
                        runPendingActionPlan(pending.copy(
                            action = ResolvedAction("set_temperature", requestedTemperature, power = true),
                            decision = ActionDecision.EXECUTE,
                            policy = com.tatsu.homehub.domain.ActionPolicy.SAFE_AUTO,
                            reason = "user supplied an explicit temperature to the pending plan"
                        ), generation)
                    }
                }
                else -> runPendingActionPlan(pending.copy(
                    decision = ActionDecision.EXECUTE,
                    policy = com.tatsu.homehub.domain.ActionPolicy.SAFE_AUTO,
                    reason = "user confirmed the pending action"
                ), generation)
            }
            if (generation != voiceGeneration) return@launch
            rememberConversation(text, response)
            _voiceState.value = _voiceState.value.copy(
                phase = VoicePhase.ANSWER_READY,
                status = "確認した操作の結果",
                responseText = response,
                error = null
            )
            voiceController.speak(generation, response, pending.intent.language, "pending-action-$generation-${UUID.randomUUID()}")
        }
        return true
    }

    private suspend fun runPendingActionPlan(plan: ActionPlan, generation: Long): String {
        if (generation != voiceGeneration) return ""
        val device = plan.device ?: return localized(plan.intent.language, "対象を指定してください", "Please specify the device.", "Bitte gib das Gerät an.")
        val action = plan.action ?: return localized(plan.intent.language, "操作を決められませんでした。対象と操作をもう一度指定してください", "I couldn't resolve the action. Please specify the device and action again.", "Ich konnte die Aktion nicht bestimmen. Bitte nenne Gerät und Aktion erneut.")
        if (!executedActionPlanIds.add(plan.id)) {
            return localized(plan.intent.language, "同じ操作は重複実行しませんでした", "I blocked a duplicate action.", "Die doppelte Aktion wurde blockiert.")
        }
        val client = clientOrNull()
            ?: return localized(plan.intent.language, "SwitchBotの設定が必要です", "SwitchBot is not configured.", "SwitchBot ist nicht eingerichtet.")
        if (plan.currentState != null && !device.infrared) {
            val latest = client.getDeviceState(device).getOrElse {
                return localized(plan.intent.language, "現在状態を再確認できなかったため実行しませんでした", "I couldn't recheck the device state, so I didn't execute the action.", "Ich konnte den Gerätezustand nicht erneut prüfen und habe nichts ausgeführt.")
            }
            val original = plan.currentState
            if (latest.power != original.power || latest.temperature != original.temperature || latest.mode != original.mode || latest.fanSpeed != original.fanSpeed) {
                return localized(plan.intent.language, "確認中に家電の状態が変わったため操作しませんでした。もう一度指示してください", "The device state changed while you were confirming, so I didn't act. Please try again.", "Der Gerätezustand hat sich während der Bestätigung geändert. Bitte versuche es erneut.")
            }
        }
        val adapter = SwitchBotActionAdapter(
            client,
            getKnownAcState = { id -> _acStates.value[id] ?: AcControlState() },
            saveKnownAcState = { id, state -> saveKnownAcState(id, state) }
        )
        val execution = adapter.execute(plan)
        _voiceState.value = _voiceState.value.copy(actionPlanJson = actionPlanDiagnostic(null, plan, execution))
        if (!execution.accepted) {
            return localized(plan.intent.language, "家電への指示送信に失敗しました: ${execution.error ?: "不明なエラー"}", "The device command failed: ${execution.error ?: "unknown error"}.", "Der Gerätebefehl ist fehlgeschlagen: ${execution.error ?: "unbekannter Fehler"}.")
        }
        val target = plan.device?.name ?: return plan.response
        return localized(plan.intent.language, "${target}への指示を送信しました", "Sent the action to ${target}.", "Die Aktion wurde an ${target} gesendet.")
    }

    private suspend fun executeVoiceAlarmAction(result: AiDispatchResult, generation: Long): String {
        if (generation != voiceGeneration) return ""
        if (!markVoiceOperationOnce(result)) {
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

    private fun markVoiceOperationOnce(result: AiDispatchResult): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        val expired = executedVoiceOperations.filterValues { now - it > VOICE_OPERATION_DEDUPE_WINDOW_MS }.keys
        expired.forEach(executedVoiceOperations::remove)
        val key = listOf(
            result.action.orEmpty(),
            result.target.orEmpty(),
            result.targetType.orEmpty(),
            result.goal.orEmpty(),
            result.temperatureC?.toString().orEmpty(),
            result.timeLocal.orEmpty(),
            result.referenceTimeLocal.orEmpty()
        ).joinToString("|")
        if (key in executedVoiceOperations) return false
        executedVoiceOperations[key] = now
        return true
    }

    private fun buildVoiceContext(): String {
        val referenceTime = java.time.ZonedDateTime.now().toOffsetDateTime().toString()
        val timeZone = java.time.ZoneId.systemDefault().id
        val contextDevices = devicesWithRooms()
        val devices = contextDevices.distinctBy { it.deviceId }.joinToString("\n") { device ->
            val actualName = _devices.value.firstOrNull { it.deviceId == device.deviceId }?.name
            "- ${device.name} | type=${device.type} | id=${device.deviceId}" +
                (actualName?.let { " | actualName=$it" } ?: "") +
                " | room=${device.room.orEmpty()}"
        }.ifBlank { "- none" }
        val alarms = _alarms.value.joinToString("\n") {
            "- ${it.label} | ${String.format("%02d:%02d", it.hour, it.minute)} | id=${it.id} | enabled=${it.enabled}"
        }.ifBlank { "- none" }
        val history = conversationHistory.takeLast(8).joinToString("\n").ifBlank { "- none" }
        return """
Known devices:
$devices

Current local date and time: $referenceTime
Current time zone: $timeZone

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
        value.lowercase().replace(Regex("""[\s　・_\-:：/、。！？!?.,「」『』（）()]+"""), "")

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
        private const val PENDING_ACTION_TTL_MS = 30_000L
        private const val VOICE_OPERATION_DEDUPE_WINDOW_MS = 30_000L

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
