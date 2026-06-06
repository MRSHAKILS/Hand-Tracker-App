package com.handtracker.fingerspark

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
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
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var rootView: FrameLayout
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: FingerOverlayView
    private lateinit var namePanel: View
    private lateinit var homePanel: View
    private lateinit var homeTitle: TextView
    private lateinit var controlTray: LinearLayout
    private lateinit var backHomeButton: ImageButton
    private lateinit var captureButton: ImageButton
    private lateinit var exitButton: ImageButton
    private lateinit var switchCameraButton: ImageButton
    private lateinit var analysisExecutor: ExecutorService

    private var handLandmarker: HandLandmarker? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val controlsHandler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable { hideCameraControls() }
    private var currentLensFacing = CameraSelector.LENS_FACING_FRONT
    private var isCameraActive = false
    private var isDetecting = false

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                showCameraScreen()
            } else {
                showHomeScreen()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        analysisExecutor = Executors.newSingleThreadExecutor()
        createLayout()
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isCameraActive) {
                        showHomeScreen()
                    } else if (namePanel.visibility == View.VISIBLE) {
                        exitApp()
                    } else {
                        exitApp()
                    }
                }
            }
        )
        if (savedUserName().isNullOrBlank()) {
            showNameScreen()
        } else {
            showHomeScreen()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing) {
            showHomeScreen()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handLandmarker?.close()
        analysisExecutor.shutdown()
    }

    private fun createLayout() {
        rootView = FrameLayout(this).apply {
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
            isClickable = true
            setOnClickListener {
                if (isCameraActive) {
                    showCameraControls()
                }
            }
        }

        controlTray = createControlTray()
        namePanel = createNamePanel()
        homePanel = createHomePanel()
        rootView.addView(previewView)
        rootView.addView(overlayView)
        rootView.addView(controlTray)
        rootView.addView(homePanel)
        rootView.addView(namePanel)
        setContentView(rootView)
    }

    private fun createControlTray(): LinearLayout {
        backHomeButton = createControlButton(
            description = getString(R.string.back_home),
            icon = R.drawable.ic_back_home,
            background = R.drawable.round_button_mint,
            onClick = ::showHomeScreen
        )
        switchCameraButton = createControlButton(
            description = getString(R.string.switch_camera),
            icon = R.drawable.ic_switch_camera,
            background = R.drawable.round_button_yellow,
            onClick = {
                switchCamera()
                showCameraControls()
            }
        )
        captureButton = createControlButton(
            description = getString(R.string.capture_screen),
            icon = R.drawable.ic_capture_screen,
            background = R.drawable.round_button_blue,
            onClick = ::captureScreen
        )
        exitButton = createControlButton(
            description = getString(R.string.exit_app),
            icon = R.drawable.ic_exit_app,
            background = R.drawable.round_button_coral,
            onClick = ::exitApp
        )

        return LinearLayout(this).apply {
            visibility = View.GONE
            alpha = 0f
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bottom_control_tray)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            addView(backHomeButton)
            addView(switchCameraButton)
            addView(captureButton)
            addView(exitButton)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(34)
            }
        }
    }

    private fun createControlButton(
        description: String,
        icon: Int,
        background: Int,
        onClick: () -> Unit
    ): ImageButton {
        return ImageButton(this).apply {
            contentDescription = description
            setImageResource(icon)
            setBackgroundResource(background)
            scaleType = ImageView.ScaleType.CENTER
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setOnClickListener {
                onClick()
            }
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56)).apply {
                marginStart = dp(6)
                marginEnd = dp(6)
            }
        }
    }

    private fun createHomePanel(): View {
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

        homeTitle = TextView(this).apply {
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
                if (hasCameraPermission()) {
                    showCameraScreen()
                } else {
                    requestCameraPermission.launch(Manifest.permission.CAMERA)
                }
            }
        }

        panel.addView(homeTitle)
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

    private fun createNamePanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(36), dp(40), dp(36), dp(40))
            setBackgroundColor(Color.rgb(23, 34, 47))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val title = TextView(this).apply {
            text = getString(R.string.name_title)
            setTextColor(Color.rgb(255, 248, 216))
            textSize = 38f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val message = TextView(this).apply {
            text = getString(R.string.name_message)
            setTextColor(Color.rgb(255, 248, 216))
            textSize = 17f
            gravity = Gravity.CENTER
            setPadding(0, dp(22), 0, dp(24))
        }

        val nameInput = EditText(this).apply {
            hint = getString(R.string.name_hint)
            textSize = 20f
            setSingleLine(true)
            setTextColor(Color.rgb(23, 34, 47))
            setHintTextColor(Color.rgb(84, 96, 111))
            setPadding(dp(18), dp(10), dp(18), dp(10))
            setBackgroundColor(Color.rgb(255, 248, 216))
        }

        val button = Button(this).apply {
            text = getString(R.string.save_name)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(23, 34, 47))
            setBackgroundColor(Color.rgb(255, 216, 77))
            setOnClickListener {
                val name = nameInput.text.toString().trim()

                if (name.isBlank()) {
                    Toast.makeText(this@MainActivity, R.string.name_needed, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                saveUserName(name)
                showHomeScreen()
            }
        }

        panel.addView(title)
        panel.addView(message)
        panel.addView(
            nameInput,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(12)
                marginEnd = dp(12)
                bottomMargin = dp(18)
            }
        )
        panel.addView(
            button,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(12)
                marginEnd = dp(12)
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

    private fun showHomeScreen() {
        stopCameraSession()
        namePanel.visibility = View.GONE
        homeTitle.text = savedUserName()?.let { getString(R.string.home_greeting, it) }
            ?: getString(R.string.permission_title)
        homePanel.visibility = View.VISIBLE
        hideCameraControls(immediate = true)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun showNameScreen() {
        stopCameraSession()
        homePanel.visibility = View.GONE
        namePanel.visibility = View.VISIBLE
        hideCameraControls(immediate = true)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun showCameraScreen() {
        namePanel.visibility = View.GONE
        homePanel.visibility = View.GONE
        hideCameraControls(immediate = true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        startCamera()
    }

    private fun showCameraControls() {
        controlsHandler.removeCallbacks(hideControlsRunnable)
        controlTray.visibility = View.VISIBLE
        controlTray.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(160L)
            .start()
        controlsHandler.postDelayed(hideControlsRunnable, CONTROLS_VISIBLE_MS)
    }

    private fun hideCameraControls(immediate: Boolean = false) {
        controlsHandler.removeCallbacks(hideControlsRunnable)

        if (immediate) {
            controlTray.animate().cancel()
            controlTray.alpha = 0f
            controlTray.translationY = dp(18).toFloat()
            controlTray.visibility = View.GONE
            return
        }

        controlTray.animate()
            .alpha(0f)
            .translationY(dp(18).toFloat())
            .setDuration(180L)
            .withEndAction {
                controlTray.visibility = View.GONE
            }
            .start()
    }

    private fun stopCameraSession() {
        isCameraActive = false
        isDetecting = false
        overlayView.clearFingertip()
        cameraProvider?.unbindAll()
    }

    private fun exitApp() {
        stopCameraSession()
        finishAndRemoveTask()
    }

    private fun captureScreen() {
        hideCameraControls(immediate = true)
        rootView.postDelayed(
            {
                try {
                    saveScreenshot(buildScreenshot())
                    Toast.makeText(this, R.string.capture_saved, Toast.LENGTH_SHORT).show()
                } catch (error: Exception) {
                    Log.e(TAG, "Could not save screenshot", error)
                    Toast.makeText(this, R.string.capture_failed, Toast.LENGTH_SHORT).show()
                }
            },
            CAPTURE_DELAY_MS
        )
    }

    private fun buildScreenshot(): Bitmap {
        val screenshot = Bitmap.createBitmap(rootView.width, rootView.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(screenshot)
        previewView.bitmap?.let { previewBitmap ->
            val srcWidth = previewBitmap.width.toFloat()
            val srcHeight = previewBitmap.height.toFloat()
            val scale = maxOf(rootView.width / srcWidth, rootView.height / srcHeight)
            val drawnWidth = srcWidth * scale
            val drawnHeight = srcHeight * scale
            val left = (rootView.width - drawnWidth) / 2f
            val top = (rootView.height - drawnHeight) / 2f
            val matrix = Matrix().apply {
                postScale(scale, scale)
                postTranslate(left, top)
            }
            canvas.drawBitmap(previewBitmap, matrix, null)
        } ?: rootView.draw(canvas)
        overlayView.draw(canvas)
        return screenshot
    }

    private fun saveScreenshot(bitmap: Bitmap) {
        val filename = "finger-spark-${System.currentTimeMillis()}.png"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Finger Spark")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create image entry")

            resolver.openOutputStream(uri)?.use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            } ?: throw IllegalStateException("Could not open image output stream")

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return
        }

        val directory = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Finger Spark")
        directory.mkdirs()
        FileOutputStream(File(directory, filename)).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun savedUserName(): String? {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_USER_NAME, null)
            ?.takeIf { it.isNotBlank() }
    }

    private fun saveUserName(name: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER_NAME, name)
            .apply()
    }

    private fun startCamera() {
        setupHandLandmarker()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            {
                cameraProvider = cameraProviderFuture.get()
                bindCamera(currentLensFacing)
                isCameraActive = true
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
        private const val CONTROLS_VISIBLE_MS = 2600L
        private const val CAPTURE_DELAY_MS = 120L
        private const val PREFS_NAME = "finger_spark_prefs"
        private const val KEY_USER_NAME = "user_name"
    }
}
