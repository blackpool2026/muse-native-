package com.blackpool2026.muse

import android.Manifest
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.blackpool2026.muse.ui.MuseRoot

class MainActivity : ComponentActivity() {
    private val vm: MuseViewModel by viewModels()
    private val handler = Handler(Looper.getMainLooper())
    private val rescan = Runnable { vm.onMediaChanged() }

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            handler.removeCallbacks(rescan)
            handler.postDelayed(rescan, 1500)
        }
    }

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.onPermission() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MuseRoot(vm, ::askPermissions, ::openAppSettings) }
    }

    override fun onStart() {
        super.onStart()
        try {
            contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
        } catch (_: Throwable) {}
    }

    override fun onStop() {
        try { contentResolver.unregisterContentObserver(observer) } catch (_: Throwable) {}
        handler.removeCallbacks(rescan)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (!vm.hasPerm) vm.onPermission()
    }

    private fun askPermissions() {
        val l = ArrayList<String>()
        l.add(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)
        if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS)
        permLauncher.launch(l.toTypedArray())
    }

    private fun openAppSettings() {
        try {
            startActivity(
                Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Throwable) {}
    }
}
