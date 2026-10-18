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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.tatsu.homehub.news.NewsWorker
import java.util.concurrent.TimeUnit
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import com.tatsu.homehub.admin.AdminReceiver
import com.tatsu.homehub.ui.HomeHubScreen
import com.tatsu.homehub.ui.HomeHubTheme
import com.tatsu.homehub.ui.HomeViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels()
    private var showNews by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        showNews = intent?.getBooleanExtra("open_news", false) == true
        scheduleNewsChecks()
        requestRuntimePermissionsIfNeeded()
        requestExactAlarmPermissionIfNeeded()

        setContent {
            HomeHubTheme {
                HomeHubScreen(viewModel, showNews)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showNews = intent.getBooleanExtra("open_news", false)
    }

    private fun scheduleNewsChecks() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED).build()
        val work = PeriodicWorkRequestBuilder<NewsWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "TatsuHomeNews", ExistingPeriodicWorkPolicy.KEEP, work)
    }

    override fun onResume() {
        super.onResume()
        enterLockTaskIfPermitted()
        startWakeWordIfPermitted()
    }

    override fun onPause() {
        viewModel.stopWakeWordListening()
        super.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) startWakeWordIfPermitted()
    }

    private fun startWakeWordIfPermitted() {
        if (
            ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.startWakeWordListening()
        }
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

    private fun requestRuntimePermissionsIfNeeded() {
        val permissions = mutableListOf<String>()
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        if (
            ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions += Manifest.permission.RECORD_AUDIO
        }
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1001)
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
