package com.cafq.accessibility

import androidx.activity.compose.rememberLauncherForActivityResult
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    var systemStateText by remember { mutableStateOf("Modo Activo (Fluido)") }
    var lastGestureText by remember { mutableStateOf("Esperando gesto...") }

    // 🎯 Variables de Control de Precisión y Fluidez
    var lastExecutionTime by remember { mutableStateOf(0L) }
    val cooldownMillis = 1200L // Enfriamiento ágil entre acciones

    // Variables para el filtro de suavizado por racha de fotogramas (Evita falsos positivos)
    var pendingGesture by remember { mutableStateOf("") }
    var consecutiveCount by remember { mutableIntStateOf(0) }
    val requiredConsecutiveFrames = 3 // El gesto debe repetirse en 3 fotogramas seguidos para validarse

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            CameraPreviewContent(
                modifier = Modifier.fillMaxSize(),
                onGestureDetected = { gestureName, score ->
                    // Ajuste de confianza óptimo en campo (0.65f equilibra precisión y agilidad)
                    val confidenceThreshold = 0.65f

                    if (gestureName == pendingGesture && score >= confidenceThreshold) {
                        consecutiveCount++
                    } else {
                        pendingGesture = gestureName
                        consecutiveCount = 1
                    }

                    // Mostramos en tiempo real el estado actual en pantalla
                    lastGestureText = if (score >= confidenceThreshold) {
                        "$gestureName (${(score * 100).toInt()}% - Racha: $consecutiveCount)"
                    } else {
                        "Analizando... ($gestureName)"
                    }

                    // Validamos si el gesto superó la racha consecutiva requerida y el umbral de confianza
                    if (consecutiveCount >= requiredConsecutiveFrames && score >= confidenceThreshold) {
                        val currentTime = System.currentTimeMillis()

                        if (currentTime - lastExecutionTime > cooldownMillis) {
                            val service = CAFQAccessibilityService.instance
                            if (service != null) {
                                var actionExecuted = false

                                when (pendingGesture) {
                                    "Thumb_Up" -> {
                                        service.performClick(500f, 500f)
                                        actionExecuted = true
                                    }
                                    "Open_Hand" -> {
                                        service.performHomeAction()
                                        actionExecuted = true
                                    }
                                    "Victory" -> {
                                        service.performBackAction()
                                        actionExecuted = true
                                    }
                                    "Closed_Fist" -> {
                                        service.performRecentsAction()
                                        actionExecuted = true
                                    }
                                }

                                if (actionExecuted) {
                                    lastExecutionTime = currentTime
                                    // Reiniciamos contador para exigir una nueva pose clara para la siguiente acción
                                    consecutiveCount = 0
                                }
                            }
                        }
                    }
                }
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                    Text(text = "Conceder permiso de cámara")
                }
            }
        }

        OverlayUI(
            statusText = systemStateText,
            gestureText = lastGestureText,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun CameraPreviewContent(
    modifier: Modifier = Modifier,
    onGestureDetected: (String, Float) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    // Instanciamos nuestro analizador matemático de landmarks (CAFQHandGestureAnalyzer)
    val handAnalyzer = remember {
        CAFQHandGestureAnalyzer(
            context = context,
            screenWidth = context.resources.displayMetrics.widthPixels,
            screenHeight = context.resources.displayMetrics.heightPixels,
            listener = object : CAFQGestureListener {
                override fun onScroll(direction: ScrollDirection, deltaY: Float) {
                    val service = CAFQAccessibilityService.instance
                    service?.let {
                        val action = if (direction == ScrollDirection.DOWN) {
                            android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                        } else {
                            android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                        }
                        it.rootInActiveWindow?.performAction(action)
                    }
                }

                override fun onClick(x: Float, y: Float) {
                    val service = CAFQAccessibilityService.instance
                    service?.performClick(x, y)
                }

                override fun onZoom(scaleFactor: Float) {
                    // Manejo opcional de zoom
                }
            }
        )
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analyzer ->
                    // Conectamos CameraX directamente con nuestro analizador matemático
                    analyzer.setAnalyzer(ContextCompat.getMainExecutor(ctx), handAnalyzer)
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (exc: Exception) {
                exc.printStackTrace()
            }
            previewView
        }
    )
}

@Composable
fun OverlayUI(statusText: String, gestureText: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.7f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Estado: $statusText",
                        color = Color.Green,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Último Gesto: $gestureText",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val service = CAFQAccessibilityService.instance
                    if (service != null) {
                        service.performClick(500f, 500f)
                        Toast.makeText(context, "¡Clic simulado enviado!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(
                            context,
                            "Activa el servicio en Ajustes > Accesibilidad",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Probar Clic de Accesibilidad")
            }
        }
    }
}