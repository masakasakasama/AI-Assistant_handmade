package com.tatsu.homehub.data

import android.util.Base64
import com.tatsu.homehub.model.HubEnvironmentState
import com.tatsu.homehub.model.SwitchBotDevice
import com.tatsu.homehub.model.SwitchBotDeviceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SwitchBotClient(
    private val token: String,
    private val secret: String
) {
    suspend fun getHubEnvironment(device: SwitchBotDevice): Result<HubEnvironmentState> = withContext(Dispatchers.IO) {
        runCatching {
            require(!device.infrared && device.type.equals("Hub 2", ignoreCase = true)) {
                "Environment status is only available for physical Hub 2 devices"
            }
            val json = request("GET", "/devices/" + device.deviceId + "/status", null).getJSONObject("body")
            val temperature = json.optDouble("temperature", Double.NaN)
            val humidity = json.optInt("humidity", -1)
            require(temperature.isFinite() && humidity in 0..100) {
                "Hub 2 environment values were not returned by SwitchBot"
            }
            HubEnvironmentState(
                temperatureC = temperature,
                humidityPercent = humidity,
                lightLevel = json.optInt("lightLevel", -1).takeIf { it in 1..20 },
                retrievedAtElapsedMs = android.os.SystemClock.elapsedRealtime()
            )
        }
    }

    suspend fun getDeviceState(device: SwitchBotDevice): Result<SwitchBotDeviceState> = withContext(Dispatchers.IO) {
        runCatching {
            require(!device.infrared) { "SwitchBot OpenAPI does not expose status for infrared remotes" }
            val json = request("GET", "/devices/${device.deviceId}/status", null).getJSONObject("body")
            val powerText = json.optString("power", json.optString("powerState")).uppercase()
            val rawTemperature = json.optDouble("temperature", Double.NaN)
            val temp = if (rawTemperature.isFinite()) rawTemperature.toInt() else null
            SwitchBotDeviceState(
                power = when (powerText) { "ON" -> true; "OFF" -> false; else -> null },
                temperature = temp,
                mode = json.optString("mode").toIntOrNull(),
                fanSpeed = json.optString("fanSpeed").toIntOrNull(),
                brightness = json.optString("brightness").toIntOrNull(),
                retrievedAtElapsedMs = android.os.SystemClock.elapsedRealtime(),
                rawJson = json.toString()
            )
        }
    }
    suspend fun getDevices(): Result<List<SwitchBotDevice>> = withContext(Dispatchers.IO) {
        runCatching {
            val json = request("GET", "/devices", null)
            val body = json.getJSONObject("body")
            val devices = mutableListOf<SwitchBotDevice>()

            body.optJSONArray("deviceList")?.let { list ->
                for (i in 0 until list.length()) {
                    val d = list.getJSONObject(i)
                    devices += SwitchBotDevice(
                        deviceId = d.getString("deviceId"),
                        name = switchBotDisplayName(
                            d.optString("deviceName", d.getString("deviceId")),
                            d.optString("deviceType", "Unknown")
                        ),
                        type = d.optString("deviceType", "Unknown"),
                        infrared = false,
                        hubDeviceId = d.optString("hubDeviceId").takeIf { it.isNotBlank() }
                    )
                }
            }

            body.optJSONArray("infraredRemoteList")?.let { list ->
                for (i in 0 until list.length()) {
                    val d = list.getJSONObject(i)
                    devices += SwitchBotDevice(
                        deviceId = d.getString("deviceId"),
                        name = switchBotDisplayName(
                            d.optString("deviceName", d.getString("deviceId")),
                            d.optString("remoteType", "Infrared")
                        ),
                        type = d.optString("remoteType", "Infrared"),
                        infrared = true,
                        hubDeviceId = d.optString("hubDeviceId").takeIf { it.isNotBlank() }
                    )
                }
            }

            devices.sortedBy { it.name.lowercase() }
        }
    }

    suspend fun turnOn(deviceId: String): Result<Unit> =
        sendCommand(deviceId, "turnOn", "default")

    suspend fun turnOff(deviceId: String): Result<Unit> =
        sendCommand(deviceId, "turnOff", "default")

    suspend fun setAirConditioner(
        deviceId: String,
        temperature: Int,
        mode: Int,
        fanSpeed: Int,
        power: Boolean
    ): Result<Unit> {
        val parameter = listOf(
            temperature.toString(),
            mode.toString(),
            fanSpeed.toString(),
            if (power) "on" else "off"
        ).joinToString(",")
        return sendCommand(deviceId, "setAll", parameter)
    }

    private suspend fun sendCommand(
        deviceId: String,
        command: String,
        parameter: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject()
                .put("command", command)
                .put("parameter", parameter)
                .put("commandType", "command")
            request("POST", "/devices/" + deviceId + "/commands", payload)
            Unit
        }
    }

    private fun request(method: String, path: String, body: JSONObject?): JSONObject {
        val timestamp = System.currentTimeMillis().toString()
        val nonce = UUID.randomUUID().toString()
        val sign = signature(token + timestamp + nonce)

        val connection = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Authorization", token)
            setRequestProperty("sign", sign)
            setRequestProperty("t", timestamp)
            setRequestProperty("nonce", nonce)
            setRequestProperty("Content-Type", "application/json; charset=utf8")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                outputStream.use { output ->
                    output.write(body.toString().toByteArray(Charsets.UTF_8))
                }
            }
        }

        val httpCode = connection.responseCode
        val stream = if (httpCode in 200..299) connection.inputStream else connection.errorStream
        val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()

        if (httpCode !in 200..299) {
            error("HTTP " + httpCode + ": " + responseText)
        }

        val json = JSONObject(responseText)
        if (json.optInt("statusCode", -1) != 100) {
            error(json.optString("message", "SwitchBot API error"))
        }
        return json
    }

    private fun signature(raw: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return Base64.encodeToString(
            mac.doFinal(raw.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP
        )
    }

    companion object {
        private const val BASE_URL = "https://api.switch-bot.com/v1.1"
    }
}

internal fun switchBotDisplayName(raw: String, _type: String): String {
    val clean = raw.trim()
    val match = ROOM_SUFFIX.matchEntire(clean)
    if (match != null) {
        val baseName = match.groupValues[1].trim()
        val room = match.groupValues[2].trim()
        if (baseName.isNotBlank() && room.isNotBlank()) {
            return "${room}の${baseName}"
        }
    }

    return clean
}

private val ROOM_SUFFIX = Regex("""^(.+?)\s*[（(]([^()（）]+)[）)]\s*$""")
