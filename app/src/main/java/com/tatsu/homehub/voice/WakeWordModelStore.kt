package com.tatsu.homehub.voice

import android.content.Context
import android.net.Uri
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.io.File
import java.nio.FloatBuffer

class WakeWordModelStore(private val context: Context) {
    private val modelFile = File(context.filesDir, "custom-wake-word.onnx")
    fun hasModel() = modelFile.isFile
    fun modelBytes(): ByteArray = modelFile.inputStream().use { WakeWordModelContract.readModel(it) }

    /** Validate and run once before replacing the previous working model. No microphone/network use. */
    fun importModel(uri: Uri) {
        val bytes = requireNotNull(context.contentResolver.openInputStream(uri)) {
            "モデルファイルを開けません"
        }.use { WakeWordModelContract.readModel(it) }
        validate(bytes)
        val temporary = File(context.filesDir, "custom-wake-word.onnx.tmp")
        try {
            temporary.writeBytes(bytes)
            check(temporary.renameTo(modelFile)) { "モデルを保存できません" }
        } finally {
            temporary.delete()
        }
    }

    private fun validate(bytes: ByteArray) {
        val env = OrtEnvironment.getEnvironment()
        // The environment is shared with the active detector; do not close it here.
        OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(1)
            options.setInterOpNumThreads(1)
            env.createSession(bytes, options).use { session ->
                require(session.inputNames.size == 1 && session.outputNames.size == 1) {
                    "openWakeWord用の単一クラスONNXモデルを選んでください"
                }
                val name = session.inputNames.single()
                val info = session.inputInfo.getValue(name).info as? TensorInfo
                require(info != null && info.type == OnnxJavaType.FLOAT && WakeWordModelContract.acceptsShape(info.shape)) {
                    "このモデルの入力形式は対応していません（必要な形式: float [1,16,96]）"
                }
                OnnxTensor.createTensor(env, FloatBuffer.wrap(FloatArray(16 * 96)), longArrayOf(1, 16, 96)).use { input ->
                    session.run(mapOf(name to input)).use { result ->
                        val output = result[0] as? OnnxTensor
                        require(output != null && output.info.type == OnnxJavaType.FLOAT && output.info.numElements == 1L) {
                            "このモデルの出力形式は対応していません"
                        }
                        val score = output.floatBuffer.get(0)
                        require(score.isFinite() && score in 0f..1f) { "モデルから有効な検出スコアが得られません" }
                    }
                }
            }
        }
    }
}
