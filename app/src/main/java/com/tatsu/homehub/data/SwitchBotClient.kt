package com.tatsu.homehub.data

import android.util.Base64
import com.tatsu.homehub.model.SwitchBotDevice
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
                        name = roomAwareName(d.optString("deviceName", d.getString("deviceId"))),
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
                        name = roomAwareName(d.optString("deviceName", d.getString("deviceId"))),
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

    private fun roomAwareName(raw: String): String {
        val clean = raw.trim()
        val match = ROOM_SUFFIX.matchEntire(clean) ?: return clean
        val baseName = match.groupValues[1].trim()
        val room = match.groupValues[2].trim()
        if (baseName.isBlank() || room.isBlank()) return clean
        return "${room}の${baseName}"
    }

    companion object {
        private const val BASE_URL = "https://api.switch-bot.com/v1.1"
        private val ROOM_SUFFIX = Regex("""^(.+?)\s*[（(]([^()（）]+)[）)]\s*$""")
    }
}
