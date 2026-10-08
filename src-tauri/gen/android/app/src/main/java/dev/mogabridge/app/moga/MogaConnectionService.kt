package dev.mogabridge.app.moga

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import dev.mogabridge.app.R

/**
 * Foreground service that keeps the process alive and shows a persistent notification while
 * a controller is connecting or connected, so the user can see the bridge is still running.
 */
class MogaConnectionService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                MogaAndroidPlugin.requestDisconnect()
                stopForegroundAndSelf()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                stopForegroundAndSelf()
                return START_NOT_STICKY
            }
        }

        val deviceName = intent?.getStringExtra(EXTRA_DEVICE_NAME) ?: "MOGA"
        val connected = intent?.getBooleanExtra(EXTRA_CONNECTED, false) ?: false
        try {
            val notification = buildNotification(deviceName, connected)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (error: Exception) {
            // Starting a foreground service can be refused (app in background, notification
            // permission policy). The bridge keeps working without the notification.
            Log.w(TAG, "Could not show the connection notification: ${error.message}")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun stopForegroundAndSelf() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(deviceName: String, connected: Boolean): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Conexión del mando",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "Se muestra mientras el mando MOGA está conectado." },
            )
        }

        val openApp = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val disconnect = PendingIntent.getService(
            this,
            1,
            Intent(this, MogaConnectionService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.drawable.ic_stat_moga)
            .setContentTitle(if (connected) "Mando conectado" else "Conectando mando…")
            .setContentText(deviceName)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(0xFF33B5E5.toInt())
            .setContentIntent(openApp)
            .addAction(Notification.Action.Builder(null, "Desconectar", disconnect).build())
            .build()
    }

    companion object {
        private const val TAG = "MogaConnection"
        private const val CHANNEL_ID = "moga_connection"
        private const val NOTIFICATION_ID = 7001
        private const val ACTION_DISCONNECT = "dev.mogabridge.app.action.DISCONNECT"
        private const val ACTION_STOP = "dev.mogabridge.app.action.STOP"
        private const val EXTRA_DEVICE_NAME = "deviceName"
        private const val EXTRA_CONNECTED = "connected"

        /** Show or update the notification. Call while the app is visible. */
        fun show(context: Context, deviceName: String, connected: Boolean) {
            val intent = Intent(context, MogaConnectionService::class.java)
                .putExtra(EXTRA_DEVICE_NAME, deviceName)
                .putExtra(EXTRA_CONNECTED, connected)
            try {
                context.startForegroundService(intent)
            } catch (error: Exception) {
                Log.w(TAG, "Could not start the connection service: ${error.message}")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MogaConnectionService::class.java))
        }
    }
}
