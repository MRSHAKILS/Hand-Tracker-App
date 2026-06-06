package com.handtracker.fingerspark

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: FingerOverlayView
    private lateinit var permissionPanel: View
    private lateinit var switchCameraButton: ImageButton
    private lateinit var analysisExecutor: ExecutorService

    private var handLandmarker: HandLandmarker? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var currentLensFacing = CameraSelector.LENS_FACING_FRONT
    private var isDetecting = false

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                permissionPanel.visibility = View.GONE
                switchCameraButton.visibility = View.VISIBLE
                startCamera()
            } else {
                permissionPanel.visibility = View.VISIBLE
                switchCameraButton.visibility = View.GONE
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        analysisExecutor = Executors.newSingleThreadExecutor()
        createLayout()

        if (hasCameraPermission()) {
            permissionPanel.visibility = View.GONE
            switchCameraButton.visibility = View.VISIBLE
            startCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handLandmarker?.close()
        analysisExecutor.shutdown()
    }

    private fun createLayout() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(23, 34, 47))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        overlayView = FingerOverlayView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        switchCameraButton = createSwitchCameraButton()
        permissionPanel = createPermissionPanel()
        root.addView(previewView)
        root.addView(overlayView)
        root.addView(switchCameraButton)
        root.addView(permissionPanel)
        setContentView(root)
    }

    private fun createSwitchCameraButton(): ImageButton {
        return ImageButton(this).apply {
            visibility = View.GONE
            contentDescription = getString(R.string.switch_camera)
            setImageResource(R.drawable.ic_switch_camera)
            setBackgroundResource(R.drawable.round_button_yellow)
            scaleType = ImageView.ScaleType.CENTER
            setPadding(18, 18, 18, 18)
            setOnClickListener {
                switchCamera()
            }
            layoutParams = FrameLayout.LayoutParams(72, 72).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = 42
                marginEnd = 24
            }
        }
    }

    private fun createPermissionPanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 40, 40, 40)
            setBackgroundColor(Color.rgb(23, 34, 47))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val title = TextView(this).apply {
            text = getString(R.string.permission_title)
            setTextColor(Color.rgb(255, 248, 216))
            textSize = 44f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val message = TextView(this).apply {
            text = getString(R.string.permission_message)
            setTextColor(Color.rgb(255, 248, 216))
            textSize = 17f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 32)
        }

        val button = Button(this).apply {
            text = getString(R.string.start_camera)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(23, 34, 47))
            setBackgroundColor(Color.rgb(255, 216, 77))
            setOnClickListener {
                requestCameraPermission.launch(Manifest.permission.CAMERA)
            }
        }

        panel.addView(title)
        panel.addView(message)
        panel.addView(
            button,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 32
                marginEnd = 32
            }
        )

        return panel
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        setupHandLandmarker()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            {
                cameraProvider = cameraProviderFuture.get()
                bindCamera(currentLensFacing)
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun switchCamera() {
        val nextLensFacing = if (currentLensFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }

        bindCamera(nextLensFacing)
    }

    private fun bindCamera(lensFacing: Int) {
        val provider = cameraProvider ?: return
        val selector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        if (!provider.hasCamera(selector)) {
            return
        }

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also {
                it.setAnalyzer(analysisExecutor, ::analyzeFrame)
            }

        isDetecting = false
        overlayView.clearFingertip()
        provider.unbindAll()
        provider.bindToLifecycle(this, selector, preview, analysis)
        currentLensFacing = lensFacing
    }

    private fun setupHandLandmarker() {
        if (handLandmarker != null) {
            return
        }

        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET)
            .build()
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(1)
            .setMinHandDetectionConfidence(0.7f)
            .setMinHandPresenceConfidence(0.6f)
            .setMinTrackingConfidence(0.65f)
            .setResultListener { result, image ->
                isDetecting = false
                val fingertip = result.landmarks().firstOrNull()?.getOrNull(INDEX_FINGER_TIP)
                runOnUiThread {
                    if (fingertip == null) {
                        overlayView.clearFingertip()
                    } else {
                        overlayView.setFingertip(
                            fingertip.x(),
                            fingertip.y(),
                            image.width,
                            image.height
                        )
                    }
                }
            }
            .setErrorListener { error ->
                isDetecting = false
                Log.e(TAG, "Hand tracking error", error)
            }
            .build()

        handLandmarker = HandLandmarker.createFromOptions(this, options)
    }

    private fun analyzeFrame(imageProxy: ImageProxy) {
        if (isDetecting) {
            imageProxy.close()
            return
        }

        try {
            val bitmap = imageProxy.toBitmap()
            val orientedBitmap = bitmap.orientForLandmarker(imageProxy.imageInfo.rotationDegrees)
            val mpImage = BitmapImageBuilder(orientedBitmap).build()
            isDetecting = true
            handLandmarker?.detectAsync(mpImage, SystemClock.uptimeMillis())
        } catch (error: Exception) {
            isDetecting = false
            Log.e(TAG, "Could not analyze camera frame", error)
        } finally {
            imageProxy.close()
        }
    }

    private fun ImageProxy.toBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val buffer = planes[0].buffer
        buffer.rewind()
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    private fun Bitmap.orientForLandmarker(rotationDegrees: Int): Bitmap {
        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
            if (currentLensFacing == CameraSelector.LENS_FACING_FRONT) {
                postScale(-1f, 1f)
            }
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    companion object {
        private const val TAG = "FingerSpark"
        private const val MODEL_ASSET = "hand_landmarker.task"
        private const val INDEX_FINGER_TIP = 8
    }
}
