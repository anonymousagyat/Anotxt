package com.anotxt.anonymous

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class TextRecordingService : AccessibilityService() {

    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        startForegroundNotification()

        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = info.flags or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            return
        }

        val eventPackage = event.packageName?.toString() ?: return
        if (eventPackage == packageName) {
            return
        }

        val eventText = event.text
        val text = if (!eventText.isNullOrEmpty()) {
            eventText.joinToString(" ")
        } else {
            val source = event.source
            val nodeText = source?.text?.toString()
            nodeText
        } ?: return

        if (text.isBlank()) return

        val timestamp = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        executor.execute {
            saveToFile(eventPackage, timestamp, text)
        }
    }

    private fun getTargetDirectory(): File {
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = File(publicDownloads, "Anotxt/txt files")

        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()) {
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            targetDir
        } else {
            val fallbackDir = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir, "Anotxt/txt files")
            if (!fallbackDir.exists()) {
                fallbackDir.mkdirs()
            }
            fallbackDir
        }
    }

    private fun saveToFile(eventPackage: String, timestamp: String, text: String) {
        try {
            val dir = getTargetDirectory()
            val fileName = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()) + FILE_EXTENSION
            val file = File(dir, fileName)
            val isNew = !file.exists()

            FileWriter(file, true).use { writer ->
                writer.write("[$timestamp] $eventPackage: $text\n")
            }

            if (isNew) {
                MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath, dir.absolutePath), null, null)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun startForegroundNotification() {
        createNotificationChannel()

        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Anotxt Active")
            .setContentText("Monitoring text input in background")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background Recording Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Anotxt active in background"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        startForegroundNotification()
    }

    override fun onInterrupt() {
        // Handle interruption
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    companion object {
        private const val FILE_EXTENSION = ".txt"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "anotxt_background_channel"
    }
}
