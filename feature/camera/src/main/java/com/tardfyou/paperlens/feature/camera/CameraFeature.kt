package com.tardfyou.paperlens.feature.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tardfyou.paperlens.core.file.DocumentImportManager
import com.tardfyou.paperlens.core.ui.PLPrimaryButton
import com.tardfyou.paperlens.core.ui.PLScaffold
import com.tardfyou.paperlens.core.ui.PaperLensUiTags
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CameraUiState(
    val hasPermission: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val documentImportManager: DocumentImportManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    fun onPermissionResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(hasPermission = granted, error = null)
    }

    fun createCaptureFile(): File = documentImportManager.createCaptureFile()

    fun showError(message: String) {
        _uiState.value = _uiState.value.copy(error = message)
    }
}

@Composable
fun CameraRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onImageCaptured: (String) -> Unit,
    viewModel: CameraViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
        onResult = viewModel::onPermissionResult,
    )
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.onPermissionResult(true)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    CameraScreen(
        modifier = modifier,
        uiState = uiState,
        onBack = onBack,
        onCapture = onImageCaptured,
        createFile = viewModel::createCaptureFile,
        onError = viewModel::showError,
    )
}

@Composable
fun CameraScreen(
    uiState: CameraUiState,
    onBack: () -> Unit,
    onCapture: (String) -> Unit,
    createFile: () -> File,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!uiState.hasPermission) {
        PLScaffold(modifier = modifier) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .testTag(PaperLensUiTags.CameraPermissionState),
            ) {
                Text(
                    "\u9700\u8981\u76f8\u673a\u6743\u9650\u624d\u80fd\u62cd\u7167\u8bc6\u5b57\u3002",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        val listener = Runnable {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            imageCapture = capture
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                capture,
            )
        }
        cameraProviderFuture.addListener(listener, executor)
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { previewView },
        )
        CameraGuideOverlay()
        IconButton(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            onClick = onBack,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "\u8fd4\u56de",
                tint = Color.White,
            )
        }
        PLPrimaryButton(
            text = "\u8bc6\u522b\u5e76\u9605\u8bfb",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
                .testTag(PaperLensUiTags.CameraCaptureButton),
            onClick = {
                captureImage(
                    imageCapture = imageCapture,
                    executor = ContextCompat.getMainExecutor(context),
                    outputFile = createFile(),
                    onCaptured = onCapture,
                    onError = onError,
                )
            },
        )
        uiState.error?.let { message ->
            Text(
                text = message,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp),
                color = Color.White,
            )
        }
    }
}

@Composable
private fun CameraGuideOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width * 0.82f
        val height = size.height * 0.58f
        val left = (size.width - width) / 2f
        val top = (size.height - height) / 2f
        drawRoundRect(
            color = Color.White.copy(alpha = 0.18f),
            topLeft = Offset(left, top),
            size = Size(width, height),
            cornerRadius = CornerRadius(28f, 28f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f),
        )
    }
}

private fun captureImage(
    imageCapture: ImageCapture?,
    executor: Executor,
    outputFile: File,
    onCaptured: (String) -> Unit,
    onError: (String) -> Unit,
) {
    if (imageCapture == null) {
        onError("\u76f8\u673a\u8fd8\u6ca1\u51c6\u5907\u597d\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5\u3002")
        return
    }
    val options = ImageCapture.OutputFileOptions.Builder(outputFile).build()
    imageCapture.takePicture(
        options,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onCaptured(outputFile.absolutePath)
            }

            override fun onError(exception: ImageCaptureException) {
                onError(exception.message ?: "\u62cd\u7167\u5931\u8d25\u3002")
            }
        },
    )
}
