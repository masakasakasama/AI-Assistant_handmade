package com.tatsu.homehub

import android.Manifest
import android.app.AlarmManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import com.tatsu.homehub.admin.AdminReceiver
import com.tatsu.homehub.ui.HomeHubScreen
import com.tatsu.homehub.ui.HomeHubTheme
import com.tatsu.homehub.ui.HomeViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        requestNotificationPermissionIfNeeded()
        requestAudioPermissionIfNeeded()
        requestExactAlarmPermissionIfNeeded()

        setContent {
            HomeHubTheme {
                HomeHubScreen(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        enterLockTaskIfPermitted()
    }

    private fun enterLockTaskIfPermitted() {
        val dpm = getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(this, AdminReceiver::class.java)

        if (dpm.isDeviceOwnerApp(packageName)) {
            runCatching {
                dpm.setLockTaskPackages(admin, arrayOf(packageName))
            }
        }

        if (dpm.isLockTaskPermitted(packageName)) {
            runCatching { startLockTask() }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
        }
    }

    private fun requestAudioPermissionIfNeeded() {
        if (
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                1002
            )
        }
    }

    private fun requestExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        val manager = getSystemService(AlarmManager::class.java)
        if (manager.canScheduleExactAlarms()) return

        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }
}
