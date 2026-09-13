package com.tatsu.homehub.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tatsu.homehub.alarm.AlarmScheduler
import com.tatsu.homehub.data.AlarmRepository
import com.tatsu.homehub.data.SecurePrefs
import com.tatsu.homehub.data.SwitchBotClient
import com.tatsu.homehub.model.AcControlState
import com.tatsu.homehub.model.LocalAlarm
import com.tatsu.homehub.model.SwitchBotDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val securePrefs = SecurePrefs(application)
    private val alarmRepo = AlarmRepository(application)

    private val _devices = MutableStateFlow<List<SwitchBotDevice>>(emptyList())
    val devices: StateFlow<List<SwitchBotDevice>> = _devices.asStateFlow()

    private val _alarms = MutableStateFlow(alarmRepo.all())
    val alarms: StateFlow<List<LocalAlarm>> = _alarms.asStateFlow()

    private val _acStates = MutableStateFlow<Map<String, AcControlState>>(emptyMap())
    val acStates: StateFlow<Map<String, AcControlState>> = _acStates.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun hasSwitchBotCredentials(): Boolean = securePrefs.hasSwitchBotCredentials()

    init {
        if (hasSwitchBotCredentials()) refreshDevices()
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

    fun refreshDevices() {
        val client = clientOrNull() ?: run {
            _message.value = "SwitchBot Token / Secretを設定してください"
            return
        }

        viewModelScope.launch {
            _loading.value = true
            client.getDevices()
                .onSuccess { list ->
                    _devices.value = list
                    _message.value = list.size.toString() + "台を同期しました"
                }
                .onFailure { error ->
                    _message.value = "SwitchBot同期失敗: " + (error.message ?: "unknown")
                }
            _loading.value = false
        }
    }

    fun power(device: SwitchBotDevice, on: Boolean) {
        val client = clientOrNull() ?: return
        viewModelScope.launch {
            val result = if (on) {
                client.turnOn(device.deviceId)
            } else {
                client.turnOff(device.deviceId)
            }
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
        alarmRepo.upsert(alarm)

        if (enabled) {
            AlarmScheduler.schedule(getApplication(), alarm)
        } else {
            AlarmScheduler.cancel(getApplication(), alarm.id)
        }
        reloadAlarms()
    }

    fun toggleAlarm(alarm: LocalAlarm, enabled: Boolean) {
        alarmRepo.setEnabled(alarm.id, enabled)
        if (enabled) {
            AlarmScheduler.schedule(getApplication(), alarm.copy(enabled = true))
        } else {
            AlarmScheduler.cancel(getApplication(), alarm.id)
        }
        reloadAlarms()
    }

    fun deleteAlarm(alarm: LocalAlarm) {
        AlarmScheduler.cancel(getApplication(), alarm.id)
        alarmRepo.delete(alarm.id)
        reloadAlarms()
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun reloadAlarms() {
        _alarms.value = alarmRepo.all()
            .sortedWith(compareBy<LocalAlarm> { it.hour }.thenBy { it.minute })
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
