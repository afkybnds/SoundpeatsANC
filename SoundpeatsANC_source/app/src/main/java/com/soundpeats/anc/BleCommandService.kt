package com.soundpeats.anc

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import java.io.IOException
import java.util.UUID

class BleCommandService : Service() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_ANC = 0
        const val MODE_NORMAL = 1
        const val MODE_TRANSPARENCY = 2

        const val PREFS = "AncWidget"
        const val KEY_MAC = "device_mac"
        const val DEFAULT_MAC = "" // set automatically from paired devices on first launch

        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

        val CMD_ANC          = byteArrayOf(0xFF.toByte(), 0x04, 0x00, 0x01, 0x00, 0x0A, 0x03, 0x11, 0x01)
        val CMD_NORMAL       = byteArrayOf(0xFF.toByte(), 0x04, 0x00, 0x01, 0x00, 0x0A, 0x03, 0x11, 0x00)
        val CMD_TRANSPARENCY = byteArrayOf(0xFF.toByte(), 0x04, 0x00, 0x01, 0x00, 0x0A, 0x03, 0x11, 0x02)

        private const val TAG = "SoundpeatsANC"
        private const val CHANNEL_ID = "anc_ble"
        private const val NOTIF_ID = 1
        private const val TIMEOUT_MS = 6_000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable { Log.w(TAG, "Timeout"); finish() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Connecting…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val cmd = when (intent?.getIntExtra(EXTRA_MODE, -1)) {
            MODE_ANC          -> CMD_ANC
            MODE_NORMAL       -> CMD_NORMAL
            MODE_TRANSPARENCY -> CMD_TRANSPARENCY
            else              -> { finish(); return START_NOT_STICKY }
        }
        handler.postDelayed(timeoutRunnable, TIMEOUT_MS)
        Thread { connectAndSend(cmd) }.start()
        return START_NOT_STICKY
    }

    private fun connectAndSend(cmd: ByteArray) {
        // Check BLUETOOTH_CONNECT permission (required on Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "BLUETOOTH_CONNECT permission not granted"); finish(); return
        }

        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) { finish(); return }

        val mac = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MAC, "")?.takeIf { it.isNotEmpty() }
            ?: run { Log.e(TAG, "No device MAC saved — open the app first"); finish(); return }

        val device = try { adapter.getRemoteDevice(mac) }
                     catch (e: Exception) { Log.e(TAG, "Bad MAC: $e"); finish(); return }

        val sock = try { device.createInsecureRfcommSocketToServiceRecord(SPP_UUID) }
                   catch (e: IOException) { Log.e(TAG, "createRfcomm: $e"); finish(); return }

        try {
            sock.connect()
            updateNotification("Sending…")
            sock.outputStream.write(cmd)
            sock.outputStream.flush()
            Log.d(TAG, "Sent: ${cmd.joinToString(" ") { "%02X".format(it) }}")
            Thread.sleep(400)
        } catch (e: IOException) {
            Log.e(TAG, "SPP error: $e")
        } finally {
            try { sock.close() } catch (_: Exception) {}
            handler.post { finish() }
        }
    }

    private fun finish() {
        handler.removeCallbacks(timeoutRunnable)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() { finish(); super.onDestroy() }

    private fun updateNotification(t: String) =
        (getSystemService(NotificationManager::class.java)).notify(NOTIF_ID, buildNotification(t))

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "ANC Control", NotificationManager.IMPORTANCE_LOW)
        ch.setSound(null, null)
        (getSystemService(NotificationManager::class.java)).createNotificationChannel(ch)
    }

    private fun buildNotification(text: String): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Soundpeats ANC")
            .setContentText(text)
            .setOngoing(true)
            .build()
}
