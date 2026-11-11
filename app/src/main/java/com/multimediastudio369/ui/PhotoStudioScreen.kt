package com.multimediastudio369.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.multimediastudio369.photo.AdjustmentParams
import com.multimediastudio369.photo.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoStudioScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var params by remember { mutableStateOf(AdjustmentParams()) }
    var isProcessing by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }

    // Bitmap ownership: defer recycle until after composition
    val pendingRecycle = remember { mutableStateListOf<Bitmap>() }
    LaunchedEffect(processedBitmap) {
        pendingRecycle.forEach { if (!it.isRecycled) it.recycle() }
        pendingRecycle.clear()
    }

    fun replaceProcessed(newBitmap: Bitmap?) {
        processedBitmap?.let { pendingRecycle.add(it) }
        processedBitmap = newBitmap
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedUri = uri
        replaceProcessed(null)
    }

    val writePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // retry export if needed
        }
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    fun performExport() {
        val bmp = processedBitmap ?: return
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                writePermissionLauncher.launch(permission)
                return
            }
        }
        scope.launch {
            val uri = withContext(Dispatchers.IO) {
                ImageProcessor.exportBitmap(context, bmp, "Studio369_${System.currentTimeMillis()}")
            }
            exportMessage = if (uri != null) "Exportado correctamente" else "Error al exportar"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Photo Studio 369") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = { imagePicker.launch("image/*") }) {
                Text("Seleccionar imagen")
            }

            Spacer(Modifier.height(16.dp))

            processedBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                )
            }

            // Sliders for adjustments (simplified)
            Text("Brillo: ${params.brightness}")
            Slider(
                value = params.brightness,
                onValueChange = { params = params.copy(brightness = it) },
                valueRange = -1f..1f
            )

            Button(
                onClick = {
                    selectedUri?.let { uri ->
                        isProcessing = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                ImageProcessor.applyAdjustments(context, uri, params)
                            }
                            replaceProcessed(result)
                            isProcessing = false
                        }
                    }
                },
                enabled = selectedUri != null && !isProcessing
            ) {
                Text(if (isProcessing) "Procesando..." else "Aplicar ajustes")
            }

            Spacer(Modifier.height(8.dp))

            Button(onClick = { performExport() }, enabled = processedBitmap != null) {
                Text("Exportar")
            }

            exportMessage?.let { Text(it) }
        }
    }
}
