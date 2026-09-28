package com.tatsu.homehub.screenshot

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

class CaptureScreenshotActivity : Activity() {

    private var captureStarted = false
    private var openedAccessibilitySettings = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDimAmount(0f)
    }

    override fun onResume() {
        super.onResume()
        maybeCapture()
    }

    private fun maybeCapture() {
        if (captureStarted) return

        if (!ScreenshotAccessibilityService.isAvailable()) {
            if (!openedAccessibilitySettings) {
                openedAccessibilitySettings = true
                Toast.makeText(
                    this,
                    "Tatsu Home のスクリーンショット機能を有効にして戻ってください",
                    Toast.LENGTH_LONG
                ).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            return
        }

        captureStarted = true
        ScreenshotAccessibilityService.capture { result ->
            runOnUiThread {
                result.onSuccess {
                    openChatGpt()
                }.onFailure { error ->
                    Toast.makeText(
                        this,
                        error.message ?: "スクリーンショットに失敗しました",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            }
        }
    }

    private fun openChatGpt() {
        val launchIntent = packageManager.getLaunchIntentForPackage(
            ScreenshotAccessibilityService.CHATGPT_PACKAGE
        )

        if (launchIntent == null) {
            Toast.makeText(
                this,
                "ChatGPT アプリが見つかりません",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(launchIntent)
        finish()
    }
}
