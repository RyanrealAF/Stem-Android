package com.ryanrealaf.stemflow

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ryanrealaf.stemflow.processing.StemFlowProcessingService

class MainActivity : ComponentActivity() {
    private val picker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        startForegroundService(StemFlowProcessingService.startIntent(this, uri))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("STEMFLOW", style = MaterialTheme.typography.headlineLarge)
                    Text("Audio → stems → MIDI", modifier = Modifier.padding(vertical = 16.dp))
                    Button(onClick = { picker.launch(arrayOf("audio/*")) }) {
                        Text("Select audio")
                    }
                }
            }
        }
    }
}
