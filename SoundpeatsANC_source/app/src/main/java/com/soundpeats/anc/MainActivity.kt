package com.soundpeats.anc

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView

class MainActivity : Activity() {

    private val PREFS get() = getSharedPreferences(BleCommandService.PREFS, MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestPermissionsIfNeeded()

        mapOf(
            R.id.btn_anc    to BleCommandService.MODE_ANC,
            R.id.btn_normal to BleCommandService.MODE_NORMAL,
            R.id.btn_pass   to BleCommandService.MODE_TRANSPARENCY
        ).forEach { (id, mode) ->
            findViewById<ImageButton>(id).setOnClickListener { sendMode(mode) }
        }

        // Long-press pill → open widget picker
        findViewById<android.view.View>(R.id.pill_container).setOnLongClickListener {
            requestPinWidget()
            true
        }

        // GitHub link
        findViewById<TextView>(R.id.tv_github).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/afkybnds")))
        }
    }

    private fun requestPinWidget() {
        val awm = AppWidgetManager.getInstance(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && awm.isRequestPinAppWidgetSupported) {
            val provider = ComponentName(this, AncWidget::class.java)
            awm.requestPinAppWidget(provider, null, null)
        }
    }

    private fun autoDetectDevice() {
        if (!hasPermission(if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_CONNECT
                           else Manifest.permission.BLUETOOTH)) return
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter ?: return
        val device = adapter.bondedDevices?.firstOrNull { d ->
            val n = d.name ?: ""
            n.contains("SOUNDPEA", ignoreCase = true) ||
            n.contains("Air4", ignoreCase = true) ||
            n.contains("Air 4", ignoreCase = true)
        }
        if (device != null) {
            PREFS.edit().putString(BleCommandService.KEY_MAC, device.address).apply()
            setStatus("✓ ${device.name}  (${device.address})")
        } else {
            val saved = PREFS.getString(BleCommandService.KEY_MAC, null)
            setStatus(if (saved != null) "Using saved device: $saved"
                      else "⚠ No Soundpeats found — pair them first then reopen")
        }
    }

    private fun sendMode(mode: Int) {
        val svc = Intent(this, BleCommandService::class.java)
            .putExtra(BleCommandService.EXTRA_MODE, mode)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc)
        else startService(svc)
    }

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT))
                needed += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            if (!hasPermission(Manifest.permission.BLUETOOTH))
                needed += Manifest.permission.BLUETOOTH
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            if (!hasPermission(Manifest.permission.POST_NOTIFICATIONS))
                needed += Manifest.permission.POST_NOTIFICATIONS
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), 1)
        else autoDetectDevice()
    }

    override fun onRequestPermissionsResult(rc: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(rc, perms, results)
        if (results.all { it == PackageManager.PERMISSION_GRANTED }) autoDetectDevice()
        else setStatus("⚠ Bluetooth permission required")
    }

    private fun hasPermission(p: String) =
        checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun setStatus(msg: String) {
        findViewById<TextView>(R.id.tv_status).text = msg
    }
}
