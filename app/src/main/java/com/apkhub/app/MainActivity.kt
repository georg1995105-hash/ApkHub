package com.apkhub.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.apkhub.app.ui.ApkHubViewModel
import com.apkhub.app.ui.screens.ApkHubRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val viewModel: ApkHubViewModel by viewModels {
        val app = application as ApkHubApplication
        ApkHubViewModel.Factory(app.container)
    }

    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                val jsonText = viewModel.exportBackupJson()
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(jsonText.toByteArray(Charsets.UTF_8))
                    }
                }
            }
        }
    }

    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                val rawJson = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                        it.readText()
                    }
                }
                if (!rawJson.isNullOrBlank()) {
                    viewModel.importBackupJson(rawJson)
                }
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshSystemState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ApkHubRoot(
                viewModel = viewModel,
                onExportBackup = {
                    exportBackupLauncher.launch("apkhub-backup.json")
                },
                onImportBackup = {
                    importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                },
                onOpenInstallPermissionSettings = {
                    startActivity(viewModel.getUnknownSourcesIntent())
                },
                onRequestNotificationPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSystemState()
    }
}
