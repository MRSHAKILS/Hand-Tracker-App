package com.handtracker.fingerspark

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.text.InputFilter
import android.text.TextUtils
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.EditorInfo
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnLayout
import com.airbnb.lottie.FontAssetDelegate
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
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
    private lateinit var sparkBackgroundView: SparkBackgroundView
    private lateinit var galaxyCircleView: GalaxyCircleView
    private lateinit var handAnimationView: LottieAnimationView
    private lateinit var logoSplashView: LottieAnimationView
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: FingerOverlayView
    private lateinit var namePanel: View
    private lateinit var homePanel: View
    private lateinit var homeArtwork: FrameLayout
    private lateinit var nameArtwork: FrameLayout
    private lateinit var homeContent: LinearLayout
    private lateinit var nameContent: LinearLayout
    private lateinit var modeChoices: LinearLayout
    private var homeLayoutSpec: HomeLayoutSpec? = null
    private lateinit var nameTitle: TextView
    private lateinit var nameMessage: TextView
    private lateinit var nameInput: EditText
    private lateinit var nameSaveButton: Button
    private lateinit var nameLanguageButton: Button
    private lateinit var homeTitle: TextView
    private lateinit var homeMessage: TextView
    private lateinit var paintModeCard: ModeTileView
    private lateinit var gameModeCard: ModeTileView
    private lateinit var homeLanguageButton: Button
    private lateinit var creatorName: TextView
    private lateinit var creatorInfo: TextView
    private lateinit var controlTray: LinearLayout
    private lateinit var backHomeButton: ImageButton
    private lateinit var captureButton: ImageButton
    private lateinit var exitButton: ImageButton
    private lateinit var soundButton: ImageButton
    private lateinit var switchCameraButton: ImageButton
    private lateinit var analysisExecutor: ExecutorService
    private lateinit var captureExecutor: ExecutorService

    private var handLandmarker: HandLandmarker? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var toneGenerator: ToneGenerator? = null
    private val controlsHandler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable { hideCameraControls() }
    private var currentLensFacing = CameraSelector.LENS_FACING_FRONT
    private var isCameraActive = false
    private val trackingSession = TrackingSession()
    private var capturePending = false
    private var pendingCaptureRunnable: Runnable? = null
    private var splashFinished = false
    private var isSoundEnabled = true
    private var currentPlayMode = FingerOverlayView.PlayMode.PAINT
    private var language = Language.ENGLISH
    private val splashFallbackRunnable = Runnable { finishSplashScreen() }

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (isFinishing || isDestroyed) return@registerForActivityResult
            if (!splashFinished) finishSplashScreen()
            if (granted) {
                showCameraScreen()
            } else {
                showEntryScreen()
                showPermissionHelp()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        analysisExecutor = Executors.newSingleThreadExecutor()
        captureExecutor = Executors.newSingleThreadExecutor()
        currentPlayMode = savedInstanceState?.getString("play_mode")?.let {
            runCatching { FingerOverlayView.PlayMode.valueOf(it) }.getOrNull()
        } ?: FingerOverlayView.PlayMode.PAINT
        toneGenerator = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull()
        language = savedLanguage()
        createLayout()
        overlayView.onSparkCaptured = ::playSparkSound
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
        showSplashScreen()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("play_mode", currentPlayMode.name)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        super.onStop()
        controlsHandler.removeCallbacks(splashFallbackRunnable)
        logoSplashView.pauseAnimation()
        if (!isFinishing && splashFinished) {
            showEntryScreen()
        }
        handAnimationView.pauseAnimation()
        galaxyCircleView.visibility = View.GONE
    }

    override fun onStart() {
        super.onStart()
        if (!splashFinished) {
            if (isMotionEnabled()) {
                logoSplashView.resumeAnimation()
                controlsHandler.postDelayed(splashFallbackRunnable, SPLASH_FALLBACK_MS)
            } else finishSplashScreen()
        }
        if (splashFinished && (homePanel.visibility == View.VISIBLE || namePanel.visibility == View.VISIBLE)) {
            galaxyCircleView.visibility = View.VISIBLE
            playHomeMotion()
        }
    }

    override fun onPause() {
        if (isCameraActive) showEntryScreen()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        controlsHandler.removeCallbacksAndMessages(null)
        stopCameraSession()
        handAnimationView.cancelAnimation()
        logoSplashView.removeAllAnimatorListeners()
        logoSplashView.cancelAnimation()
        toneGenerator?.release()
        analysisExecutor.execute { handLandmarker?.close() }
        analysisExecutor.shutdown()
        captureExecutor.shutdown()
    }

    private fun createLayout() {
        rootView = FrameLayout(this).apply {
            setBackgroundColor(SparkStyle.surface)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        sparkBackgroundView = SparkBackgroundView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        galaxyCircleView = GalaxyCircleView(this).apply {
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                dp(112),
                dp(112)
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = 0
            }
        }

        handAnimationView = LottieAnimationView(this).apply {
            visibility = View.GONE
            setAnimation("hand-lottie.json")
            repeatCount = LottieDrawable.INFINITE
            repeatMode = LottieDrawable.RESTART
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(
                dp(132),
                dp(132)
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = dp(88)
            }
        }

        logoSplashView = LottieAnimationView(this).apply {
            visibility = View.GONE
            setBackgroundColor(Color.BLACK)
            setAnimation("Loading Anmation.lottie")
            setFontAssetDelegate(
                object : FontAssetDelegate() {
                    override fun fetchFont(fontFamily: String?): Typeface {
                        return Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    }
                }
            )
            setFailureListener {
                Log.e(TAG, "Could not load splash animation", it)
                finishSplashScreen()
            }
            repeatCount = 0
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(38), dp(38), dp(38), dp(38))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        previewView = PreviewView(this).apply {
            visibility = View.GONE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        overlayView = FingerOverlayView(this).apply {
            visibility = View.GONE
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
        rootView.addView(sparkBackgroundView)
        rootView.addView(previewView)
        rootView.addView(overlayView)
        rootView.addView(controlTray)
        rootView.addView(homePanel)
        rootView.addView(namePanel)
        rootView.addView(logoSplashView)
        setContentView(rootView)
        WindowCompat.getInsetsController(window, rootView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            view.setPadding(safe.left, safe.top, safe.right, maxOf(safe.bottom, keyboard))
            view.post { updateResponsiveLayout() }
            insets
        }
        ViewCompat.requestApplyInsets(rootView)
        rootView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateResponsiveLayout() }
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
        soundButton = createControlButton(
            description = "Sound on",
            icon = R.drawable.ic_sound_on,
            background = R.drawable.round_button_mint,
            onClick = {
                toggleSound()
                showCameraControls()
            }
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
            addView(soundButton)
            addView(exitButton)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                marginStart = dp(16)
                marginEnd = dp(16)
                bottomMargin = dp(16)
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
            setColorFilter(if (background == R.drawable.round_button_blue) SparkStyle.ink
                else if (background == R.drawable.round_button_coral) SparkStyle.coral else Color.WHITE)
            this.background = SparkStyle.touchSurface(
                when (background) {
                    R.drawable.round_button_blue -> SparkStyle.mint
                    else -> Color.TRANSPARENT
                },
                dp(8).toFloat(),
                lightRipple = background != R.drawable.round_button_blue
            )
            androidx.core.view.ViewCompat.setTooltipText(this, description)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setOnClickListener {
                onClick()
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            }
        }
    }

    private fun createModeCard(
        title: String,
        subtitle: String,
        isPaint: Boolean,
        onClick: () -> Unit
    ): ModeTileView = ModeTileView(this, isPaint).apply {
        bindText(title, subtitle)
        setOnClickListener { onClick() }
    }

    private fun createHomePanel(): View {
        val text = copy()
        val container = FrameLayout(this).apply {
            visibility = View.GONE
            isClickable = true
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val scrollPanel = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = true
            setPadding(0, dp(88), 0, 0)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), 0, dp(24), dp(24))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        homeContent = panel

        homeTitle = TextView(this).apply {
            setTextColor(SparkStyle.ink)
            textSize = 28f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.START
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this,
                24,
                28,
                2,
                TypedValue.COMPLEX_UNIT_SP
            )
        }

        homeMessage = TextView(this).apply {
            setTextColor(SparkStyle.secondary)
            textSize = 15f
            gravity = Gravity.START
            setPadding(0, dp(6), 0, dp(20))
        }

        paintModeCard = createModeCard(
            title = text.paintMode,
            subtitle = text.paintModeInfo,
            isPaint = true,
            onClick = { startMode(FingerOverlayView.PlayMode.PAINT) }
        )

        gameModeCard = createModeCard(
            title = text.gameMode,
            subtitle = text.gameModeInfo,
            isPaint = false,
            onClick = { startMode(FingerOverlayView.PlayMode.GAME) }
        )

        val creatorPanel = createCreatorPanel()

        homeLanguageButton = Button(this).apply {
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(SparkStyle.ink)
            setBackgroundResource(R.drawable.button_language_circle)
            minWidth = 0
            minHeight = 0
            includeFontPadding = false
            isAllCaps = false
            letterSpacing = 0f
            setPadding(0, 0, 0, 0)
            setOnClickListener {
                toggleLanguage()
            }
        }

        homeArtwork = createArtworkSlot()
        panel.addView(homeArtwork)
        panel.addView(homeTitle, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) })
        panel.addView(homeMessage)
        modeChoices = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            isBaselineAligned = false
            addView(paintModeCard, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            addView(gameModeCard, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = dp(12)
            })
        }
        panel.addView(modeChoices, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        panel.addView(View(this).apply { setBackgroundColor(SparkStyle.rule) }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
        ).apply { topMargin = dp(24); bottomMargin = dp(10) })
        panel.addView(
            creatorPanel,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(6)
                marginEnd = dp(6)
            }
        )

        scrollPanel.addView(panel)
        container.addView(scrollPanel)
        addBrandLabel(container)
        container.addView(
            homeLanguageButton,
            FrameLayout.LayoutParams(dp(54), dp(54)).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = dp(16)
                marginEnd = dp(24)
            }
        )
        return container
    }

    private fun createCreatorPanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }

        val photoSlot = CircleImageView(this).apply {
            contentDescription = "Creator photo"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setImageResource(R.drawable.creator_photo)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        val textStack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }

        creatorName = TextView(this).apply {
            setTextColor(SparkStyle.ink)
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

        creatorInfo = TextView(this).apply {
            setTextColor(SparkStyle.secondary)
            textSize = 12f
        }

        textStack.addView(creatorName)
        textStack.addView(
            creatorInfo,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(2)
            }
        )

        panel.addView(
            photoSlot,
            LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                marginEnd = dp(12)
            }
        )
        panel.addView(
            textStack,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        return panel
    }

    private fun createNamePanel(): View {
        val container = FrameLayout(this).apply {
            visibility = View.GONE
            isClickable = true
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val scrollPanel = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = true
            setPadding(0, dp(88), 0, 0)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), 0, dp(24), dp(24))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        nameContent = panel

        nameLanguageButton = Button(this).apply {
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(SparkStyle.ink)
            setBackgroundResource(R.drawable.button_language_circle)
            minWidth = 0
            minHeight = 0
            includeFontPadding = false
            isAllCaps = false
            letterSpacing = 0f
            setPadding(0, 0, 0, 0)
            setOnClickListener {
                toggleLanguage()
            }
        }

        nameTitle = TextView(this).apply {
            setTextColor(SparkStyle.ink)
            textSize = 28f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.START
            maxLines = 2
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this,
                24,
                28,
                2,
                TypedValue.COMPLEX_UNIT_SP
            )
        }

        nameMessage = TextView(this).apply {
            setTextColor(SparkStyle.secondary)
            textSize = 15f
            gravity = Gravity.START
            setPadding(0, dp(8), 0, dp(24))
        }

        nameInput = EditText(this).apply {
            textSize = 18f
            setSingleLine(true)
            setTextColor(Color.rgb(11, 18, 29))
            setHintTextColor(Color.rgb(110, 122, 137))
            setBackgroundResource(R.drawable.input_name)
            minHeight = dp(56)
            maxLines = 1
            filters = arrayOf(InputFilter.LengthFilter(64))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_DONE) {
                    submitName()
                    true
                } else false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            }
        }

        nameSaveButton = Button(this).apply {
            textSize = 17f
            isAllCaps = false
            letterSpacing = 0f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.button_primary)
            minHeight = dp(56)
            minWidth = 0
            setOnClickListener { submitName() }
        }

        nameArtwork = createArtworkSlot()
        panel.addView(nameArtwork)
        panel.addView(nameTitle)
        panel.addView(nameMessage)
        panel.addView(
            nameInput,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(18)
            }
        )
        panel.addView(
            nameSaveButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        scrollPanel.addView(panel)
        container.addView(scrollPanel)
        addBrandLabel(container)
        container.addView(
            nameLanguageButton,
            FrameLayout.LayoutParams(dp(54), dp(54)).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = dp(16)
                marginEnd = dp(24)
            }
        )
        return container
    }

    private fun createArtworkSlot(): FrameLayout = FrameLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(176)
        )
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    private fun attachArtwork(slot: FrameLayout) {
        (galaxyCircleView.parent as? ViewGroup)?.removeView(galaxyCircleView)
        (handAnimationView.parent as? ViewGroup)?.removeView(handAnimationView)
        slot.addView(galaxyCircleView)
        slot.addView(handAnimationView)
    }

    private fun addBrandLabel(container: FrameLayout) {
        val wordmark = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        wordmark.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_game_mode)
            setColorFilter(SparkStyle.paintAccent)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginEnd = dp(8) })
        wordmark.addView(TextView(this).apply {
            setText(R.string.app_name)
            textSize = 20f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(SparkStyle.ink)
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this, 12, 20, 1, TypedValue.COMPLEX_UNIT_SP
            )
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        container.addView(wordmark, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(54)
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            topMargin = dp(16)
            marginStart = dp(24)
            marginEnd = dp(88)
        })
    }

    private fun submitName() {
        val name = nameInput.text.toString().trim()
        if (name.isBlank()) {
            nameInput.error = copy().nameNeeded
            nameInput.requestFocus()
            return
        }
        nameInput.error = null
        saveUserName(name)
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(nameInput.windowToken, 0)
        nameInput.clearFocus()
        showHomeScreen()
    }

    private fun updateResponsiveLayout() {
        val density = resources.displayMetrics.density
        val width = ((rootView.width - rootView.paddingLeft - rootView.paddingRight) / density).toInt()
        val height = ((rootView.height - rootView.paddingTop - rootView.paddingBottom) / density).toInt()
        if (width <= 0 || height <= 0) return
        val spec = HomeLayoutPolicy.forWindow(width, height, resources.configuration.fontScale)
        if (spec == homeLayoutSpec) return
        homeLayoutSpec = spec
        homeContent.setPadding(dp(spec.sidePadding), 0, dp(spec.sidePadding), dp(20))
        nameContent.setPadding(dp(spec.sidePadding), 0, dp(spec.sidePadding), dp(24))
        listOf(homeArtwork, nameArtwork).forEach {
            it.layoutParams = (it.layoutParams as LinearLayout.LayoutParams).apply { this.height = dp(spec.artworkHeight) }
        }
        val compactArtwork = spec.artworkHeight < 160
        val ringSize = if (compactArtwork) 80 else 96
        val handSize = if (compactArtwork) 96 else 112
        galaxyCircleView.layoutParams = FrameLayout.LayoutParams(dp(ringSize), dp(ringSize)).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
        handAnimationView.layoutParams = FrameLayout.LayoutParams(dp(handSize), dp(handSize)).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dp(spec.artworkHeight - handSize)
        }
        modeChoices.orientation = if (spec.stackModes) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        listOf(paintModeCard, gameModeCard).forEachIndexed { index, card ->
            card.setCompact(spec.stackModes)
            card.layoutParams = if (spec.stackModes) {
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = if (index == 1) dp(12) else 0
                }
            } else {
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    marginStart = if (index == 1) dp(12) else 0
                }
            }
        }
        controlTray.layoutParams = (controlTray.layoutParams as FrameLayout.LayoutParams).apply {
            this.width = dp(spec.toolbarWidth)
        }
    }

    private fun playHomeMotion() {
        val enabled = isMotionEnabled()
        galaxyCircleView.motionEnabled = enabled
        if (enabled) handAnimationView.playAnimation() else {
            handAnimationView.pauseAnimation()
            handAnimationView.progress = 0.4f
        }
    }

    private fun isMotionEnabled(): Boolean {
        val animationsEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
        val batterySaver = (getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode
        return animationsEnabled && !batterySaver
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startMode(mode: FingerOverlayView.PlayMode) {
        currentPlayMode = mode
        if (hasCameraPermission()) {
            showCameraScreen()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun showPermissionHelp() {
        val english = language == Language.ENGLISH
        val canRetry = shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        AlertDialog.Builder(this)
            .setTitle(if (english) "Camera access" else "ক্যামেরার অনুমতি")
            .setMessage(if (english) "Allow camera access to use Paint and Game. Frames are processed on this phone."
                else "Paint এবং Game-এর জন্য ক্যামেরার অনুমতি দাও। ছবি এই ফোনেই প্রসেস হয়।")
            .setNegativeButton(if (english) "Cancel" else "বাতিল", null)
            .setPositiveButton(if (canRetry) {
                if (english) "Try again" else "আবার চেষ্টা করো"
            } else {
                if (english) "Settings" else "সেটিংস"
            }) { _, _ ->
                if (canRetry) startMode(currentPlayMode)
                else startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
            .show()
    }

    private fun showSplashScreen() {
        splashFinished = false
        stopCameraSession()
        sparkBackgroundView.visibility = View.GONE
        galaxyCircleView.visibility = View.GONE
        handAnimationView.pauseAnimation()
        handAnimationView.visibility = View.GONE
        previewView.visibility = View.GONE
        overlayView.visibility = View.GONE
        namePanel.visibility = View.GONE
        homePanel.visibility = View.GONE
        hideCameraControls(immediate = true)

        logoSplashView.alpha = 1f
        logoSplashView.visibility = View.VISIBLE
        logoSplashView.bringToFront()
        logoSplashView.removeAllAnimatorListeners()
        logoSplashView.addAnimatorListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    finishSplashScreen()
                }
            }
        )
        if (isMotionEnabled()) {
            logoSplashView.playAnimation()
            controlsHandler.postDelayed(splashFallbackRunnable, SPLASH_FALLBACK_MS)
        } else finishSplashScreen()
    }

    private fun finishSplashScreen() {
        if (splashFinished || isFinishing || isDestroyed) {
            return
        }

        splashFinished = true
        controlsHandler.removeCallbacks(splashFallbackRunnable)
        logoSplashView.animate().cancel()
        logoSplashView.cancelAnimation()
        logoSplashView.visibility = View.GONE
        logoSplashView.alpha = 1f
        showEntryScreen()
    }

    private fun showEntryScreen() {
        if (savedUserName().isNullOrBlank()) {
            showNameScreen()
        } else {
            showHomeScreen()
        }
    }

    private fun showHomeScreen() {
        stopCameraSession()
        attachArtwork(homeArtwork)
        logoSplashView.visibility = View.GONE
        sparkBackgroundView.visibility = View.VISIBLE
        galaxyCircleView.visibility = View.VISIBLE
        handAnimationView.visibility = View.VISIBLE
        playHomeMotion()
        previewView.visibility = View.GONE
        overlayView.visibility = View.GONE
        namePanel.visibility = View.GONE
        updateLanguageText()
        homePanel.visibility = View.VISIBLE
        homePanel.bringToFront()
        hideCameraControls(immediate = true)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun showNameScreen() {
        stopCameraSession()
        attachArtwork(nameArtwork)
        logoSplashView.visibility = View.GONE
        sparkBackgroundView.visibility = View.VISIBLE
        galaxyCircleView.visibility = View.VISIBLE
        handAnimationView.visibility = View.VISIBLE
        playHomeMotion()
        previewView.visibility = View.GONE
        overlayView.visibility = View.GONE
        updateLanguageText()
        homePanel.visibility = View.GONE
        namePanel.visibility = View.VISIBLE
        namePanel.bringToFront()
        hideCameraControls(immediate = true)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun showCameraScreen() {
        logoSplashView.visibility = View.GONE
        sparkBackgroundView.visibility = View.GONE
        galaxyCircleView.visibility = View.GONE
        handAnimationView.pauseAnimation()
        handAnimationView.visibility = View.GONE
        previewView.visibility = View.VISIBLE
        overlayView.visibility = View.VISIBLE
        overlayView.setPlayMode(currentPlayMode)
        namePanel.visibility = View.GONE
        homePanel.visibility = View.GONE
        overlayView.resetGame()
        hideCameraControls(immediate = true)
        controlTray.bringToFront()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        startCamera()
    }

    private fun showCameraControls() {
        if (!isCameraActive) return
        controlsHandler.removeCallbacks(hideControlsRunnable)
        controlTray.animate().cancel()
        controlTray.bringToFront()
        controlTray.visibility = View.VISIBLE
        controlTray.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(160L)
            .start()
        val accessibility = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        if (!accessibility.isTouchExplorationEnabled) {
            val timeout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                accessibility.getRecommendedTimeoutMillis(CONTROLS_VISIBLE_MS.toInt(),
                    AccessibilityManager.FLAG_CONTENT_CONTROLS or AccessibilityManager.FLAG_CONTENT_ICONS).toLong()
            } else CONTROLS_VISIBLE_MS
            controlsHandler.postDelayed(hideControlsRunnable, maxOf(CONTROLS_VISIBLE_MS, timeout))
        }
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
        trackingSession.stop()
        pendingCaptureRunnable?.let {
            controlsHandler.removeCallbacks(it)
            pendingCaptureRunnable = null
            capturePending = false
        }
        overlayView.clearFingertip()
        cameraProvider?.unbindAll()
    }

    private fun exitApp() {
        stopCameraSession()
        finishAndRemoveTask()
    }

    private fun captureScreen() {
        if (!isCameraActive || capturePending) return
        val session = trackingSession.currentSession() ?: return
        capturePending = true
        hideCameraControls(immediate = true)
        val capture = Runnable {
            pendingCaptureRunnable = null
            if (!trackingSession.isCurrent(session) || isFinishing) {
                capturePending = false
                return@Runnable
            }
            val screenshot = try {
                buildScreenshot()
            } catch (error: Exception) {
                capturePending = false
                Log.e(TAG, "Could not compose capture", error)
                Toast.makeText(this, copy().captureFailed, Toast.LENGTH_SHORT).show()
                return@Runnable
            }
            captureExecutor.execute {
                val saved = try {
                    check(trackingSession.isCurrent(session))
                    saveScreenshot(screenshot, session)
                    true
                } catch (error: Exception) {
                    Log.e(TAG, "Could not save screenshot", error)
                    false
                } finally {
                    screenshot.recycle()
                }
                runOnUiThread {
                    capturePending = false
                    if (!isDestroyed && !isFinishing && trackingSession.isCurrent(session)) {
                        val message = if (!saved) copy().captureFailed
                            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) copy().captureSaved
                            else copy().captureSavedLegacy
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        pendingCaptureRunnable = capture
        controlsHandler.postDelayed(capture, CAPTURE_DELAY_MS)
    }

    private fun buildScreenshot(): Bitmap {
        val previewBitmap = previewView.bitmap ?: throw IllegalStateException("Camera preview is not ready")
        try {
            val screenshot = Bitmap.createBitmap(previewView.width, previewView.height, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(screenshot)
                val srcWidth = previewBitmap.width.toFloat()
                val srcHeight = previewBitmap.height.toFloat()
                val scale = maxOf(previewView.width / srcWidth, previewView.height / srcHeight)
                val drawnWidth = srcWidth * scale
                val drawnHeight = srcHeight * scale
                val left = (previewView.width - drawnWidth) / 2f
                val top = (previewView.height - drawnHeight) / 2f
                val matrix = Matrix().apply {
                    postScale(scale, scale)
                    postTranslate(left, top)
                }
                canvas.drawBitmap(previewBitmap, matrix, null)
                overlayView.draw(canvas)
                return screenshot
            } catch (error: Exception) {
                screenshot.recycle()
                throw error
            }
        } finally {
            previewBitmap.recycle()
        }
    }

    private fun saveScreenshot(bitmap: Bitmap, session: Long) {
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

            try {
                resolver.openOutputStream(uri)?.use { output ->
                    val cancellable = CaptureOutputStream(output) { trackingSession.isCurrent(session) }
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, cancellable))
                } ?: throw IllegalStateException("Could not open image output stream")
                check(trackingSession.isCurrent(session))
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                check(resolver.update(uri, values, null, null) == 1) { "Could not publish capture" }
            } catch (error: Exception) {
                runCatching { resolver.delete(uri, null, null) }.exceptionOrNull()?.let(error::addSuppressed)
                throw error
            }
            return
        }

        val pictures = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: throw IllegalStateException("App picture storage is unavailable")
        val directory = File(pictures, "Finger Spark")
        check(directory.isDirectory || directory.mkdirs()) { "Could not create picture directory" }
        val file = File(directory, filename)
        try {
            FileOutputStream(file).use { output ->
                val cancellable = CaptureOutputStream(output) { trackingSession.isCurrent(session) }
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, cancellable))
            }
            check(trackingSession.isCurrent(session))
        } catch (error: Exception) {
            file.delete()
            throw error
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

    private fun savedLanguage(): Language {
        val savedCode = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, Language.ENGLISH.code)

        return Language.entries.firstOrNull { it.code == savedCode } ?: Language.ENGLISH
    }

    private fun saveLanguage() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.code)
            .apply()
    }

    private fun toggleLanguage() {
        language = if (language == Language.ENGLISH) {
            Language.BANGLA
        } else {
            Language.ENGLISH
        }
        saveLanguage()
        updateLanguageText()
    }

    private fun updateLanguageText() {
        val text = copy()
        val userName = savedUserName()
        overlayView.scoreLabel = if (language == Language.ENGLISH) "Spark" else "স্পার্ক"

        nameLanguageButton.text = text.languageButton
        nameLanguageButton.contentDescription = if (language == Language.ENGLISH) "Switch to Bangla" else "ইংরেজিতে পরিবর্তন করো"
        nameTitle.text = text.nameTitle
        nameMessage.text = text.nameMessage
        nameInput.hint = text.nameHint
        nameInput.contentDescription = text.nameHint
        if (nameInput.error != null) nameInput.error = text.nameNeeded
        nameSaveButton.text = text.saveName

        homeLanguageButton.text = text.languageButton
        homeLanguageButton.contentDescription = nameLanguageButton.contentDescription
        homeTitle.text = userName?.let { text.homeGreeting.format(it) } ?: text.appName
        homeMessage.text = text.homeMessage
        updateModeCardText(paintModeCard, text.paintMode, text.paintModeInfo)
        updateModeCardText(gameModeCard, text.gameMode, text.gameModeInfo)
        creatorName.text = text.creatorName
        creatorInfo.text = text.creatorInfo

        backHomeButton.contentDescription = text.backHome
        switchCameraButton.contentDescription = text.switchCamera
        captureButton.contentDescription = text.captureScreen
        exitButton.contentDescription = text.exitApp
        updateSoundButton(text)
        listOf(backHomeButton, switchCameraButton, captureButton, exitButton, soundButton).forEach {
            ViewCompat.setTooltipText(it, it.contentDescription)
        }
    }

    private fun toggleSound() {
        isSoundEnabled = !isSoundEnabled
        updateSoundButton(copy())
        if (isSoundEnabled) {
            playSparkSound()
        }
    }

    private fun updateSoundButton(text: UiCopy) {
        if (!::soundButton.isInitialized) {
            return
        }

        soundButton.setImageResource(
            if (isSoundEnabled) R.drawable.ic_sound_on else R.drawable.ic_sound_off
        )
        soundButton.contentDescription = if (isSoundEnabled) text.soundOn else text.soundOff
        ViewCompat.setTooltipText(soundButton, soundButton.contentDescription)
    }

    private fun updateModeCardText(card: ModeTileView, title: String, subtitle: String) {
        card.bindText(title, subtitle)
    }

    private fun playSparkSound() {
        if (!isSoundEnabled) {
            return
        }

        toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 85)
    }

    private fun copy(): UiCopy {
        return when (language) {
            Language.ENGLISH -> UiCopy(
                languageButton = "Bn",
                appName = "Finger Spark",
                nameTitle = "What should we call you?",
                nameMessage = "Let's start with a hello.",
                nameHint = "Your name",
                saveName = "Let's play",
                nameNeeded = "Please enter a name",
                homeGreeting = "Hi, %s!",
                homeMessage = "What shall we make today?",
                paintMode = "Paint",
                paintModeInfo = "Air canvas",
                gameMode = "Game",
                gameModeInfo = "Spark chase",
                creatorName = "Built by Shakil",
                creatorInfo = "Engineer, NSU",
                switchCamera = "Switch camera",
                captureScreen = "Capture screen",
                soundOn = "Sound on",
                soundOff = "Sound off",
                captureSaved = "Saved to Pictures/Finger Spark",
                captureSavedLegacy = "Saved to the app's Pictures folder",
                captureFailed = "Could not save screenshot",
                backHome = "Back to home",
                exitApp = "Exit app"
            )

            Language.BANGLA -> UiCopy(
                languageButton = "En",
                appName = "ফিঙ্গার স্পার্ক",
                nameTitle = "তোমার নাম কী?",
                nameMessage = "আগে একটু পরিচয় হোক।",
                nameHint = "তোমার নাম",
                saveName = "চলো খেলি",
                nameNeeded = "দয়া করে নাম লিখো",
                homeGreeting = "হাই, %s!",
                homeMessage = "আজ কী বানাবে?",
                paintMode = "Paint",
                paintModeInfo = "আকাশে আঁকা",
                gameMode = "Game",
                gameModeInfo = "স্পার্কের খেলা",
                creatorName = "শাকিলের তৈরি",
                creatorInfo = "NSUer ইঞ্জিনিয়ার, তোমার জন্য বানিয়েছি ।",
                switchCamera = "ক্যামেরা বদলাও",
                captureScreen = "ছবি তোলো",
                soundOn = "সাউন্ড চালু",
                soundOff = "সাউন্ড বন্ধ",
                captureSaved = "Pictures/Finger Spark-এ সেভ হয়েছে",
                captureSavedLegacy = "অ্যাপের Pictures ফোল্ডারে সেভ হয়েছে",
                captureFailed = "ছবি সেভ করা যায়নি",
                backHome = "হোমে ফিরে যাও",
                exitApp = "অ্যাপ বন্ধ করো"
            )
        }
    }

    private fun startCamera() {
        val session = trackingSession.start()
        isCameraActive = true
        previewView.doOnLayout {
            if (!trackingSession.isCurrent(session)) return@doOnLayout
            try {
                val future = ProcessCameraProvider.getInstance(this)
                future.addListener({
                    if (!trackingSession.isCurrent(session) || isFinishing || isDestroyed) return@addListener
                    try {
                        val provider = future.get()
                        cameraProvider = provider
                        val preferred = CameraSelector.Builder().requireLensFacing(currentLensFacing).build()
                        val lens = if (provider.hasCamera(preferred)) currentLensFacing
                            else if (currentLensFacing == CameraSelector.LENS_FACING_FRONT) CameraSelector.LENS_FACING_BACK
                            else CameraSelector.LENS_FACING_FRONT
                        analysisExecutor.execute {
                            if (!trackingSession.isCurrent(session)) return@execute
                            try {
                                setupHandLandmarker()
                                runOnUiThread {
                                    if (trackingSession.isCurrent(session)) {
                                        try {
                                            bindCamera(lens, session)
                                        } catch (error: Exception) {
                                            cameraFailed(error)
                                        }
                                    }
                                }
                            } catch (error: Exception) {
                                runOnUiThread {
                                    if (trackingSession.isCurrent(session)) cameraFailed(error)
                                }
                            }
                        }
                    } catch (error: Exception) {
                        cameraFailed(error)
                    }
                }, ContextCompat.getMainExecutor(this))
            } catch (error: Exception) {
                cameraFailed(error)
            }
        }
    }

    private fun switchCamera() {
        val nextLensFacing = if (currentLensFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }

        try {
            val selector = CameraSelector.Builder().requireLensFacing(nextLensFacing).build()
            if (cameraProvider?.hasCamera(selector) == true) {
                bindCamera(nextLensFacing, trackingSession.start())
            }
        } catch (error: Exception) {
            cameraFailed(error)
        }
    }

    private fun cameraFailed(error: Exception) {
        Log.e(TAG, "Could not start camera tracking", error)
        showEntryScreen()
        Toast.makeText(this, if (language == Language.ENGLISH) "Camera unavailable. Please try again."
            else "ক্যামেরা চালু করা যায়নি। আবার চেষ্টা করো।", Toast.LENGTH_LONG).show()
    }

    private fun bindCamera(lensFacing: Int, session: Long) {
        val provider = cameraProvider ?: return
        val selector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        check(provider.hasCamera(selector)) { "Requested camera is unavailable" }

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also {
                it.setAnalyzer(analysisExecutor) { frame -> analyzeFrame(frame, session, lensFacing) }
            }

        overlayView.clearFingertip()
        provider.unbindAll()
        val group = UseCaseGroup.Builder()
            .setViewPort(checkNotNull(previewView.viewPort) { "Preview layout is not ready" })
            .addUseCase(preview)
            .addUseCase(analysis)
            .build()
        provider.bindToLifecycle(this, selector, group)
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
                val session = trackingSession.completeFrame() ?: return@setResultListener
                val landmarks = result.landmarks().firstOrNull()
                val fingertip = landmarks?.getOrNull(INDEX_FINGER_TIP)
                val imageWidth = image.width
                val imageHeight = image.height
                runOnUiThread {
                    if (!trackingSession.isCurrent(session)) return@runOnUiThread
                    if (landmarks == null || fingertip == null) {
                        overlayView.clearFingertip()
                    } else {
                        overlayView.setHandState(
                            fingertip.x(),
                            fingertip.y(),
                            imageWidth,
                            imageHeight,
                            isFingerExtended(landmarks, INDEX_FINGER_TIP, INDEX_FINGER_PIP),
                            isFingerExtended(landmarks, MIDDLE_FINGER_TIP, MIDDLE_FINGER_PIP),
                            isFingerExtended(landmarks, RING_FINGER_TIP, RING_FINGER_PIP),
                            isFingerExtended(landmarks, PINKY_TIP, PINKY_PIP),
                            handSize(landmarks)
                        )
                    }
                }
            }
            .setErrorListener { error ->
                trackingSession.completeFrame()
                Log.e(TAG, "Hand tracking error", error)
            }
            .build()

        handLandmarker = HandLandmarker.createFromOptions(this, options)
    }

    private fun isFingerExtended(
        landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>,
        tipIndex: Int,
        pipIndex: Int
    ): Boolean {
        return landmarks[tipIndex].y() < landmarks[pipIndex].y()
    }

    private fun handSize(
        landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>
    ): Float {
        val wrist = landmarks[WRIST]
        val middleMcp = landmarks[MIDDLE_FINGER_MCP]
        val dx = wrist.x() - middleMcp.x()
        val dy = wrist.y() - middleMcp.y()
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun analyzeFrame(imageProxy: ImageProxy, session: Long, lensFacing: Int) {
        if (!trackingSession.beginFrame(session)) {
            imageProxy.close()
            return
        }

        try {
            val bitmap = imageProxy.toBitmap()
            val crop = imageProxy.cropRect
            val croppedBitmap = Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.width(), crop.height())
            val orientedBitmap = croppedBitmap.orientForLandmarker(imageProxy.imageInfo.rotationDegrees, lensFacing)
            if (croppedBitmap !== orientedBitmap) croppedBitmap.recycle()
            if (bitmap !== croppedBitmap && bitmap !== orientedBitmap) bitmap.recycle()
            val mpImage = BitmapImageBuilder(orientedBitmap).build()
            val tracker = checkNotNull(handLandmarker)
            tracker.detectAsync(mpImage, SystemClock.uptimeMillis())
        } catch (error: Exception) {
            trackingSession.completeFrame()
            Log.e(TAG, "Could not analyze camera frame", error)
        } finally {
            imageProxy.close()
        }
    }

    private fun Bitmap.orientForLandmarker(rotationDegrees: Int, lensFacing: Int): Bitmap {
        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
            if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                postScale(-1f, 1f)
            }
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    companion object {
        private const val TAG = "FingerSpark"
        private const val MODEL_ASSET = "hand_landmarker.task"
        private const val WRIST = 0
        private const val INDEX_FINGER_TIP = 8
        private const val INDEX_FINGER_PIP = 6
        private const val MIDDLE_FINGER_MCP = 9
        private const val MIDDLE_FINGER_TIP = 12
        private const val MIDDLE_FINGER_PIP = 10
        private const val RING_FINGER_TIP = 16
        private const val RING_FINGER_PIP = 14
        private const val PINKY_TIP = 20
        private const val PINKY_PIP = 18
        private const val CONTROLS_VISIBLE_MS = 2600L
        private const val SPLASH_FALLBACK_MS = 7000L
        private const val CAPTURE_DELAY_MS = 120L
        private const val PREFS_NAME = "finger_spark_prefs"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_LANGUAGE = "language"
    }
}

private enum class Language(val code: String) {
    ENGLISH("en"),
    BANGLA("bn")
}

private data class UiCopy(
    val languageButton: String,
    val appName: String,
    val nameTitle: String,
    val nameMessage: String,
    val nameHint: String,
    val saveName: String,
    val nameNeeded: String,
    val homeGreeting: String,
    val homeMessage: String,
    val paintMode: String,
    val paintModeInfo: String,
    val gameMode: String,
    val gameModeInfo: String,
    val creatorName: String,
    val creatorInfo: String,
    val switchCamera: String,
    val captureScreen: String,
    val soundOn: String,
    val soundOff: String,
    val captureSaved: String,
    val captureSavedLegacy: String,
    val captureFailed: String,
    val backHome: String,
    val exitApp: String
)
