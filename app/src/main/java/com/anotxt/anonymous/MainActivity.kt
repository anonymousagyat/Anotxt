package com.anotxt.anonymous

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var settingsButton: Button
    private lateinit var storageStatusTextView: TextView
    private lateinit var storageButton: Button
    private lateinit var batteryStatusTextView: TextView
    private lateinit var batteryButton: Button
    private lateinit var appInfoButton: Button
    private lateinit var pathTextView: TextView

    private lateinit var filesHeaderTextView: TextView
    private lateinit var refreshFilesButton: Button
    private lateinit var emptyFilesTextView: TextView
    private lateinit var filesContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)
        settingsButton = findViewById(R.id.settingsButton)
        storageStatusTextView = findViewById(R.id.storageStatusTextView)
        storageButton = findViewById(R.id.storageButton)
        batteryStatusTextView = findViewById(R.id.batteryStatusTextView)
        batteryButton = findViewById(R.id.batteryButton)
        appInfoButton = findViewById(R.id.appInfoButton)
        pathTextView = findViewById(R.id.pathTextView)

        filesHeaderTextView = findViewById(R.id.filesHeaderTextView)
        refreshFilesButton = findViewById(R.id.refreshFilesButton)
        emptyFilesTextView = findViewById(R.id.emptyFilesTextView)
        filesContainer = findViewById(R.id.filesContainer)

        settingsButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        storageButton.setOnClickListener {
            requestStoragePermission()
        }

        batteryButton.setOnClickListener {
            requestIgnoreBatteryOptimization()
        }

        appInfoButton.setOnClickListener {
            openAppSettings()
        }

        refreshFilesButton.setOnClickListener {
            loadRecordedFiles()
        }

        requestNotificationPermission()
        ensureDirectoriesCreated()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
        updateStorageStatus()
        updateBatteryStatus()
        updatePathDisplay()
        ensureDirectoriesCreated()
        loadRecordedFiles()
    }

    private fun getStorageDirectory(): File {
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val primaryDir = File(publicDownloads, "Anotxt/txt files")
        val fallbackDir = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir, "Anotxt/txt files")

        return if (primaryDir.exists()) primaryDir else fallbackDir
    }

    private fun loadRecordedFiles() {
        filesContainer.removeAllViews()
        val dir = getStorageDirectory()

        val files = if (dir.exists()) {
            dir.listFiles { file -> file.isFile && file.extension.equals("txt", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()
        } else {
            emptyList()
        }

        if (files.isEmpty()) {
            filesHeaderTextView.text = "Recorded Text Files (0)"
            emptyFilesTextView.visibility = View.VISIBLE
        } else {
            filesHeaderTextView.text = "Recorded Text Files (${files.size})"
            emptyFilesTextView.visibility = View.GONE

            val inflater = LayoutInflater.from(this)
            for (file in files) {
                val cardView = inflater.inflate(R.layout.item_file_card, filesContainer, false)
                val nameView = cardView.findViewById<TextView>(R.id.fileNameTextView)
                val metaView = cardView.findViewById<TextView>(R.id.fileMetaTextView)
                val viewBtn = cardView.findViewById<Button>(R.id.viewFileButton)
                val shareBtn = cardView.findViewById<Button>(R.id.shareFileButton)
                val deleteBtn = cardView.findViewById<Button>(R.id.deleteFileButton)

                nameView.text = file.name
                val sizeStr = formatFileSize(file.length())
                val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
                metaView.text = "$sizeStr • Modified: $dateStr"

                cardView.setOnClickListener {
                    showFileContentDialog(file)
                }

                viewBtn.setOnClickListener {
                    showFileContentDialog(file)
                }

                shareBtn.setOnClickListener {
                    shareFile(file)
                }

                deleteBtn.setOnClickListener {
                    confirmDeleteFile(file)
                }

                filesContainer.addView(cardView)
            }
        }
    }

    private fun showFileContentDialog(file: File) {
        val inflater = LayoutInflater.from(this)
        val dialogView = inflater.inflate(R.layout.dialog_view_file, null)

        val titleView = dialogView.findViewById<TextView>(R.id.dialogTitleTextView)
        val metaView = dialogView.findViewById<TextView>(R.id.dialogMetaTextView)
        val contentView = dialogView.findViewById<TextView>(R.id.dialogContentTextView)
        val copyBtn = dialogView.findViewById<Button>(R.id.dialogCopyButton)
        val closeBtn = dialogView.findViewById<Button>(R.id.dialogCloseButton)

        titleView.text = file.name

        val textContent = try {
            file.readText()
        } catch (e: Exception) {
            "Error reading file: ${e.message}"
        }

        val lineCount = if (textContent.isEmpty()) 0 else textContent.lines().size
        val sizeStr = formatFileSize(file.length())
        metaView.text = "$lineCount lines • $sizeStr"
        contentView.text = if (textContent.isEmpty()) "(File is empty)" else textContent

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        copyBtn.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(file.name, textContent)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        closeBtn.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun shareFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share ${file.name}"))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not share file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDeleteFile(file: File) {
        AlertDialog.Builder(this)
            .setTitle("Delete File")
            .setMessage("Are you sure you want to delete \"${file.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                val deleted = file.delete()
                if (deleted) {
                    MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), null, null)
                    Toast.makeText(this, "Deleted ${file.name}", Toast.LENGTH_SHORT).show()
                    loadRecordedFiles()
                } else {
                    Toast.makeText(this, "Failed to delete file", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
            else -> "$bytes bytes"
        }
    }

    private fun ensureDirectoriesCreated() {
        if (isStoragePermissionGranted()) {
            val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val targetDir = File(publicDownloads, "Anotxt/txt files")
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            MediaScannerConnection.scanFile(this, arrayOf(targetDir.absolutePath), null, null)
        }
    }

    private fun updatePathDisplay() {
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = File(publicDownloads, "Anotxt/txt files")
        pathTextView.text = "Logs saved to:\nInternal storage > Download > Anotxt > txt files\n(${targetDir.absolutePath})"
    }

    private fun updateStorageStatus() {
        val hasStorageAccess = isStoragePermissionGranted()
        if (hasStorageAccess) {
            storageStatusTextView.text = "✓ Storage Access: Granted\nSaving to Download/Anotxt/txt files"
            storageButton.visibility = View.GONE
        } else {
            storageStatusTextView.text = "⚠ Storage Access: Restricted\nPermission needed to write to public Download folder"
            storageButton.visibility = View.VISIBLE
        }
    }

    private fun isStoragePermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                val fallbackIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(fallbackIntent)
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE),
                102
            )
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }

    private fun updateServiceStatus() {
        if (isAccessibilityServiceEnabled()) {
            statusTextView.text = "✓ Accessibility Service is ACTIVE\nRunning in background & recording text"
            settingsButton.text = "Open Accessibility Settings"
        } else {
            statusTextView.text = "⚠ Accessibility Service is OFF\nPlease enable Anotxt in Accessibility Settings"
            settingsButton.text = "Enable in Accessibility Settings"
        }
    }

    private fun updateBatteryStatus() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoring = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(packageName)
        } else {
            true
        }

        if (isIgnoring) {
            batteryStatusTextView.text = "✓ Battery Optimization: Unrestricted\nApp will not be killed in background"
            batteryButton.visibility = View.GONE
        } else {
            batteryStatusTextView.text = "⚠ Battery Optimization: Restricted\nAndroid may terminate background service"
            batteryButton.visibility = View.VISIBLE
        }
    }

    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(fallbackIntent)
            }
        }
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = ComponentName(this, TextRecordingService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledComponent = ComponentName.unflattenFromString(componentNameString)
            if (enabledComponent != null && enabledComponent == expectedComponentName) {
                return true
            }
        }
        return false
    }
}