package com.ryanrealaf.stemflow

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ryanrealaf.stemflow.processing.StemFlowProcessingService

class MainActivity : ComponentActivity() {

    private var statusMessage by mutableStateOf("Ready to process audio.")

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "Notification permission denied; service will run without progress notifications", Toast.LENGTH_SHORT).show()
        }
    }

    private val picker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            statusMessage = "No file selected."
            return@registerForActivityResult
        }

        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            // Some document providers do not support persistable permissions; fallback to transient intent grant.
        }

        try {
            val serviceIntent = StemFlowProcessingService.startIntent(this, uri)
            ContextCompat.startForegroundService(this, serviceIntent)
            statusMessage = "Processing started for selected audio file."
        } catch (e: Exception) {
            statusMessage = "Failed to start processing service: ${e.message}"
            Toast.makeText(this, statusMessage, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("STEMFLOW", style = MaterialTheme.typography.headlineLarge)
                    Text("Audio → stems → MIDI", modifier = Modifier.padding(vertical = 16.dp))
                    Text("Status: $statusMessage", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        try {
                            picker.launch(arrayOf("audio/*", "*/*"))
                        } catch (e: Exception) {
                            statusMessage = "Unable to launch document picker: ${e.message}"
                        }
                    }) {
                        Text("Select audio")
                    }
                }
            }
        }
    }
}
