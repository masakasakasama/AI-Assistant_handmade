package com.tatsu.homehub.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tatsu.homehub.alarm.AlarmScheduler
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

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

    fun hasSwitchBotCredentials(): Boolean = securePrefs.hasSwitchBotCredentials()

    fun weatherSettings(): WeatherSettings = WeatherSettings(
        label = appPrefs.weatherLabel,
        latitude = appPrefs.weatherLatitude,
        longitude = appPrefs.weatherLongitude
    )

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

        if (System.currentTimeMillis() - appPrefs.lastUpdateCheckMillis > 12 * 60 * 60 * 1000L) {
            checkForUpdate(showMessage = false)
        }
    }

    fun saveSwitchBotCredentials(token: String, secret: String) {
        if (token.isBlank() || secret.isBlank()) {
            _message.value = "Token / Secretを両方入力してください"
            return
        }
        securePrefs.put(SecurePrefs.KEY_SWITCHBOT_TOKEN, token.trim())
        securePrefs.put(SecurePrefs.KEY_SWITCHBOT_SECRET, secret.trim())
        _message.value = "SwitchBot認証情報を端末Keystoreで保存しました"
        refreshDevices()
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
