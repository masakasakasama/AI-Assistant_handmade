package com.tatsu.homehub.voice

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler

/** Shared by wake detection and cloud transcription; USB routing must be confirmed, not assumed. */
class PreferredAudioInput(context: Context) {
    private val manager = context.applicationContext.getSystemService(AudioManager::class.java)
    private var lastUsbId: Int? = null

    @Synchronized private fun usbDevice(): AudioDeviceInfo? {
        val devices = manager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .filter { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
        val selected = devices.firstOrNull { it.id == lastUsbId } ?: devices.minByOrNull { it.id }
        lastUsbId = selected?.id
        return selected
    }

    fun open(): Capture {
        val usb = usbDevice()
        val source = if (usb != null) MediaRecorder.AudioSource.VOICE_RECOGNITION else MediaRecorder.AudioSource.VOICE_COMMUNICATION
        val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "マイクの録音形式を初期化できません" }
        val audio = AudioRecord(source, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minimum, 1280 * 4))
        var aec: AcousticEchoCanceler? = null
        try {
            check(audio.state == AudioRecord.STATE_INITIALIZED) { "マイクを初期化できません" }
            if (usb != null) check(audio.setPreferredDevice(usb)) {
                "USBマイク「${usb.productName}」を指定できませんでした"
            }
            if (usb == null && AcousticEchoCanceler.isAvailable()) {
                aec = runCatching { AcousticEchoCanceler.create(audio.audioSessionId) }.getOrNull()
                runCatching { aec?.setEnabled(true) }
            }
            return Capture(audio, usb, aec)
        } catch (error: Exception) {
            aec?.release()
            audio.release()
            throw error
        }
    }

    class Capture(val audio: AudioRecord, private val requested: AudioDeviceInfo?, private val aec: AcousticEchoCanceler?) {
        fun start() {
            audio.startRecording()
            check(audio.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "マイクの録音を開始できません" }
        }

        // Called after every successful read. An attached USB mic must never silently become a phone mic.
        fun verifyRoute() {
            val actual = audio.routedDevice
            check(actual != null) { "実際の入力マイクを確認できませんでした" }
            if (requested != null) check(actual.id == requested.id) {
                "USBマイクを使用できませんでした。入力は「${actual.productName}」になっています"
            }
        }

        fun diagnostic(): String {
            val actual = audio.routedDevice
            val kind = if (actual?.type == AudioDeviceInfo.TYPE_USB_DEVICE || actual?.type == AudioDeviceInfo.TYPE_USB_HEADSET) "USB" else "端末/その他"
            val echo = if (requested != null) "USB AECは出力経路次第（未確認）" else if (aec?.enabled == true) "Android AEC有効（効果未確認）" else "Android AEC利用不可"
            return "入力: $kind / ${actual?.productName ?: "未確認"} (id=${actual?.id}); エコー対策: $echo"
        }

        fun close() {
            runCatching { audio.stop() }
            runCatching { aec?.release() }
            audio.release()
        }
    }
}
