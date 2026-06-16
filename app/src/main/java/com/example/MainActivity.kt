package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.RecognizerIntent
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.util.Locale
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.Manifest
import com.example.db.HistoryItem
import com.example.ui.theme.ThemeMode
import android.media.MediaPlayer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
         super.onCreate(savedInstanceState)
         enableEdgeToEdge()
         
         setContent {
             val viewModel: TranslationViewModel = viewModel()
             val context = androidx.compose.ui.platform.LocalContext.current
             
             LaunchedEffect(intent) {
                 val action = intent?.action
                 val type = intent?.type

                 val processText = if (action == Intent.ACTION_PROCESS_TEXT) {
                     intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                 } else null

                 val sharedText = if (action == Intent.ACTION_SEND && type?.startsWith("text/") == true) {
                     intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                 } else null

                 val sharedImageUri = if (action == Intent.ACTION_SEND && type?.startsWith("image/") == true) {
                     intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
                 } else null

                 val sharedAudioUri = if (action == Intent.ACTION_SEND && type?.startsWith("audio/") == true) {
                     intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
                 } else null

                 if (!processText.isNullOrBlank()) {
                     viewModel.updateInputText(processText)
                 } else if (!sharedText.isNullOrBlank()) {
                     viewModel.updateInputText(sharedText)
                     viewModel.translate(context)
                 } else if (sharedImageUri != null) {
                     viewModel.translateImage(context, sharedImageUri, viewModel.sourceLanguage.value, viewModel.targetLanguage.value)
                 } else if (sharedAudioUri != null) {
                     viewModel.translateAudio(context, sharedAudioUri, viewModel.sourceLanguage.value, viewModel.targetLanguage.value)
                 }
             }
             
             val themeMode by viewModel.themeMode.collectAsState()
             MyApplicationTheme(themeMode = themeMode) {
                 Scaffold(
                     modifier = Modifier.fillMaxSize()
                 ) { innerPadding ->
                     Box(
                         modifier = Modifier
                             .fillMaxSize()
                             .background(MaterialTheme.colorScheme.background)
                             .padding(innerPadding)
                     ) {
                         TranslatorAppContent(viewModel = viewModel)
                     }
                 }
             }
         }
    }
}

enum class AppScreen {
    TRANSLATE, HISTORY, FAVORITES, CONVERSATION, PHRASEBOOK
}

enum class SpeechTarget {
    MAIN_INPUT, CONVERSATION_LEFT, CONVERSATION_RIGHT, CONVERSATION_AUTO
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun TranslatorAppContent(
    viewModel: TranslationViewModel = viewModel()
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val preferredVoiceGender by viewModel.preferredVoiceGender.collectAsState()
    val voicePitch by viewModel.voicePitch.collectAsState()
    val fontSizeScale by viewModel.fontSizeScale.collectAsState()
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    var speakingStateText by remember { mutableStateOf<String?>(null) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    
    val uiState by viewModel.uiState.collectAsState()
    val cameraUiState by viewModel.cameraUiState.collectAsState()
    val conversationTranscript by viewModel.conversationTranscript.collectAsState()
    
    // Haptic feedback when translation is successful
    LaunchedEffect(uiState) {
        if (uiState is TranslationUiState.Success) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
        }
    }
    
    LaunchedEffect(cameraUiState) {
        if (cameraUiState is CameraUiState.Success) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
        }
    }
    
    LaunchedEffect(conversationTranscript.size) {
        if (conversationTranscript.isNotEmpty()) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
        }
    }

    DisposableEffect(context) {
        val ttsInstance = try {
            TextToSpeech(context, { status -> }, "com.google.android.tts")
        } catch (e: Exception) {
            TextToSpeech(context) { status -> }
        }
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        ttsInstance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                handler.post { speakingStateText = utteranceId }
            }

            override fun onDone(utteranceId: String?) {
                handler.post { if (speakingStateText == utteranceId) speakingStateText = null }
            }

            @Deprecated("Deprecated")
            override fun onError(utteranceId: String?) {
                handler.post { if (speakingStateText == utteranceId) speakingStateText = null }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                handler.post { if (speakingStateText == utteranceId) speakingStateText = null }
            }
        })
        tts = ttsInstance
        onDispose {
            ttsInstance.stop()
            ttsInstance.shutdown()
        }
    }

    val speakOut: (String, String) -> Unit = { text, langCode ->
        val currentTts = tts
        if (currentTts != null) {
            if (speakingStateText == text) {
                currentTts.stop()
                speakingStateText = null
            } else {
                currentTts.stop()
                speakingStateText = text
                
                val locale = when (langCode.lowercase(Locale.ROOT)) {
                    "en" -> Locale.US
                    "hi" -> Locale("hi", "IN")
                    "es" -> Locale("es", "ES")
                    "fr" -> Locale.FRANCE
                    "de" -> Locale.GERMANY
                    "ja" -> Locale.JAPAN
                    "ar" -> Locale("ar", "SA")
                    "zh" -> Locale.SIMPLIFIED_CHINESE
                    "pt" -> Locale("pt", "PT")
                    "ru" -> Locale("ru", "RU")
                    "bn" -> Locale("bn", "IN")
                    "mr" -> Locale("mr", "IN")
                    "te" -> Locale("te", "IN")
                    "ta" -> Locale("ta", "IN")
                    "gu" -> Locale("gu", "IN")
                    "ur" -> Locale("ur", "PK")
                    "kn" -> Locale("kn", "IN")
                    "or" -> Locale("or", "IN")
                    "pa" -> Locale("pa", "IN")
                    "ml" -> Locale("ml", "IN")
                    "as" -> Locale("as", "IN")
                    "mai" -> Locale("mai", "IN")
                    "kok" -> Locale("kok", "IN")
                    "sa" -> Locale("sa", "IN")
                    "ne" -> Locale("ne", "NP")
                    "sd" -> Locale("sd", "IN")
                    "ks" -> Locale("ks", "IN")
                    "doi" -> Locale("doi", "IN")
                    "mni" -> Locale("mni", "IN")
                    "brx" -> Locale("brx", "IN")
                    "sat" -> Locale("sat", "IN")
                    else -> Locale.forLanguageTag(langCode)
                }

                val isAvailable = currentTts.isLanguageAvailable(locale)
                if (isAvailable == TextToSpeech.LANG_MISSING_DATA || isAvailable == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Fallback to simple Locale constructor if language tag is too specific
                    val fallbackLocale = Locale(langCode)
                    val isFallbackAvailable = currentTts.isLanguageAvailable(fallbackLocale)
                    if (isFallbackAvailable == TextToSpeech.LANG_MISSING_DATA || isFallbackAvailable == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Toast.makeText(context, "Voice not supported for this language", Toast.LENGTH_SHORT).show()
                        speakingStateText = null
                    } else {
                        setVoiceOrLanguage(currentTts, fallbackLocale, preferredVoiceGender, voicePitch)
                        val params = Bundle()
                        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, text)
                        currentTts.speak(text, TextToSpeech.QUEUE_FLUSH, params, text)
                    }
                } else {
                    setVoiceOrLanguage(currentTts, locale, preferredVoiceGender, voicePitch)
                    val params = Bundle()
                    params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, text)
                    currentTts.speak(text, TextToSpeech.QUEUE_FLUSH, params, text)
                }
            }
        } else {
            Toast.makeText(context, "Voice synthesizer is preparing...", Toast.LENGTH_SHORT).show()
        }
    }

    val inputText by viewModel.inputText.collectAsState()
    val sourceLanguage by viewModel.sourceLanguage.collectAsState()
    val targetLanguage by viewModel.targetLanguage.collectAsState()
    val historyState by viewModel.historyState.collectAsState()
    val favoriteHistoryState by viewModel.favoriteHistoryState.collectAsState()
    val isTransliterationEnabled by viewModel.isTransliterationEnabled.collectAsState()
    val isCompareMode by viewModel.isCompareMode.collectAsState()
    val compareTargetLanguages by viewModel.compareTargetLanguages.collectAsState()
    val translationTone by viewModel.translationTone.collectAsState()

    var speechTargetMode by remember { mutableStateOf<SpeechTarget?>(null) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.getOrNull(0)
            if (!spokenText.isNullOrBlank()) {
                when (speechTargetMode) {
                    SpeechTarget.CONVERSATION_LEFT -> {
                        viewModel.handleConversationSpeechReceived(spokenText, isLeft = true, context) { t, l -> speakOut(t, l) }
                    }
                    SpeechTarget.CONVERSATION_RIGHT -> {
                        viewModel.handleConversationSpeechReceived(spokenText, isLeft = false, context) { t, l -> speakOut(t, l) }
                    }
                    SpeechTarget.CONVERSATION_AUTO -> {
                        viewModel.handleConversationAutoDetectSpeech(spokenText, context) { t, l -> speakOut(t, l) }
                    }
                    else -> {
                        val currentText = viewModel.inputText.value
                        val updatedText = if (currentText.isBlank()) {
                            spokenText
                        } else {
                            "${currentText.trim()} $spokenText"
                        }
                        viewModel.updateInputText(updatedText)
                    }
                }
            }
        }
    }

    var cameraPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var audioRecordUri by remember { mutableStateOf<Uri?>(null) }
    var isRecordingAudio by remember { mutableStateOf(false) }
    var mediaRecorder: android.media.MediaRecorder? by remember { mutableStateOf(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val uri = cameraPhotoUri
            if (uri != null) {
                viewModel.translateImage(context, uri, sourceLanguage, targetLanguage)
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Microphone permission granted. Tap record again.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Microphone permission is required to record audio.", Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val photoFile = File(context.cacheDir, "camera_photo_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
            cameraPhotoUri = uri
            takePictureLauncher.launch(uri)
        } else {
            Toast.makeText(context, "Camera permission is required to analyze images.", Toast.LENGTH_SHORT).show()
        }
    }

    var activeScreen by remember { mutableStateOf(AppScreen.TRANSLATE) }

    // Interactive Toast indicator
    var isCopiedSuccessfully by remember { mutableStateOf(false) }

    LaunchedEffect(isCopiedSuccessfully) {
        if (isCopiedSuccessfully) {
            kotlinx.coroutines.delay(2000)
            isCopiedSuccessfully = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                })
            }
            .imePadding()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Premium Hero Header Bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh, // Swapper icon as translator representation
                        contentDescription = "App Logo",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Translator",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = "AI Translation powered by Gemini",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            // Settings Menu Button
            var showSettingsDialog by remember { mutableStateOf(false) }

            IconButton(
                onClick = { showSettingsDialog = true },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .testTag("settings_menu_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings Menu",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            if (showSettingsDialog) {
                SettingsDialog(
                    viewModel = viewModel,
                    themeMode = themeMode,
                    onDismiss = { showSettingsDialog = false }
                )
            }
        }

        Divider(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            thickness = 1.dp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        // --- Custom Pill-styled Segmented Switcher ---
        val scrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(4.dp)
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Translate Tab
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (activeScreen == AppScreen.TRANSLATE) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
                    .clickable { activeScreen = AppScreen.TRANSLATE }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("tab_translate"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Translate Tab",
                        tint = if (activeScreen == AppScreen.TRANSLATE) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Translate",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (activeScreen == AppScreen.TRANSLATE) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // History Tab
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (activeScreen == AppScreen.HISTORY) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
                    .clickable { activeScreen = AppScreen.HISTORY }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("tab_history"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "History Tab",
                        tint = if (activeScreen == AppScreen.HISTORY) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "History",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (activeScreen == AppScreen.HISTORY) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Favorites Tab
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (activeScreen == AppScreen.FAVORITES) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
                    .clickable { activeScreen = AppScreen.FAVORITES }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("tab_favorites"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Favorites Tab",
                        tint = if (activeScreen == AppScreen.FAVORITES) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Favorites",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (activeScreen == AppScreen.FAVORITES) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Conversation Tab
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (activeScreen == AppScreen.CONVERSATION) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
                    .clickable { activeScreen = AppScreen.CONVERSATION }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("tab_conversation"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Conversation Tab",
                        tint = if (activeScreen == AppScreen.CONVERSATION) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Conversation",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (activeScreen == AppScreen.CONVERSATION) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Phrasebook Tab
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (activeScreen == AppScreen.PHRASEBOOK) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
                    .clickable { activeScreen = AppScreen.PHRASEBOOK }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("tab_phrasebook"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "Phrasebook Tab",
                        tint = if (activeScreen == AppScreen.PHRASEBOOK) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Phrasebook",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (activeScreen == AppScreen.PHRASEBOOK) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }

        if (activeScreen == AppScreen.TRANSLATE) {
            val audioUiState by viewModel.audioUiState.collectAsState()
            if (audioUiState != AudioUiState.Idle) {
                AudioResultScreen(
                    state = audioUiState,
                    onClose = {
                        viewModel.resetAudioState()
                    },
                    fontSizeScale = fontSizeScale
                )
            } else if (cameraUiState != CameraUiState.Idle) {
                CameraResultScreen(
                    state = cameraUiState,
                    onRetake = {
                        viewModel.resetCameraState()
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            val photoFile = File(context.cacheDir, "camera_photo_${System.currentTimeMillis()}.jpg")
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
                            cameraPhotoUri = uri
                            takePictureLauncher.launch(uri)
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onSave = { oText, tText, sLang, tLang ->
                        viewModel.saveCameraResultToHistory(oText, tText, sLang, tLang)
                    },
                    onClose = {
                        viewModel.resetCameraState()
                    },
                    fontSizeScale = fontSizeScale
                )
            } else {
                // --- Language Pickers Card ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("language_selector_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                // Mode Toggle Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Translation Mode",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (!isCompareMode) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { viewModel.setCompareMode(false) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("mode_button_single"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Single",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!isCompareMode) Color.White else MaterialTheme.colorScheme.primary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isCompareMode) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { viewModel.setCompareMode(true) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("mode_button_compare"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Compare",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isCompareMode) Color.White else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                
                Divider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                if (!isCompareMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Source Language
                        Box(modifier = Modifier.weight(1f)) {
                            LanguageSelectorDropdown(
                                label = "From",
                                selectedLanguage = sourceLanguage,
                                onLanguageSelected = { viewModel.setSourceLanguage(it) },
                                includeAutoDetect = true
                            )
                        }

                        // Swapper Button
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                .clickable { viewModel.swapLanguages() }
                                .testTag("swap_languages_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Swap Languages",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Target Language
                        Box(modifier = Modifier.weight(1f)) {
                            LanguageSelectorDropdown(
                                label = "To",
                                selectedLanguage = targetLanguage,
                                onLanguageSelected = { viewModel.setTargetLanguage(it) },
                                includeAutoDetect = false
                            )
                        }
                    }
                } else {
                    // Compare Mode language pickers vertical layout
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                LanguageSelectorDropdown(
                                    label = "From (Source Language)",
                                    selectedLanguage = sourceLanguage,
                                    onLanguageSelected = { viewModel.setSourceLanguage(it) },
                                    includeAutoDetect = true
                                )
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                        Text(
                            text = "Translate to (Up to 3 languages):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        compareTargetLanguages.forEachIndexed { index, selectedLang ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    LanguageSelectorDropdown(
                                        label = "Target Language ${index + 1}",
                                        selectedLanguage = selectedLang,
                                        onLanguageSelected = { viewModel.updateCompareTargetLanguage(index, it) },
                                        includeAutoDetect = false
                                    )
                                }
                                
                                if (compareTargetLanguages.size > 1) {
                                    IconButton(
                                        onClick = { viewModel.removeCompareTargetLanguage(index) },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                                            .testTag("remove_compare_language_$index")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Remove Language",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (compareTargetLanguages.size < 3) {
                            var showAddDropdown by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { showAddDropdown = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("add_compare_language_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add target language",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Add Target Language",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                DropdownMenu(
                                    expanded = showAddDropdown,
                                    onDismissRequest = { showAddDropdown = false },
                                    modifier = Modifier.fillMaxWidth(0.85f)
                                ) {
                                    Language.values().filter { it != Language.AUTO && !compareTargetLanguages.contains(it) }.forEach { rLang ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(text = rLang.flag, fontSize = 16.sp)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(text = rLang.displayName, fontSize = 14.sp)
                                                }
                                            },
                                            onClick = {
                                                viewModel.addCompareTargetLanguage(rLang)
                                                showAddDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- Input Card ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_text_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Enter text to translate",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now to translate...")
                                    }
                                    try {
                                        speechLauncher.launch(intent)
                                    } catch (e: ActivityNotFoundException) {
                                        Toast.makeText(context, "Speech recognition is not supported on this device.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("mic_button")
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_mic),
                                    contentDescription = "Dictate text (Speech to Text)",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                        val photoFile = File(context.cacheDir, "camera_photo_${System.currentTimeMillis()}.jpg")
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
                                        cameraPhotoUri = uri
                                        takePictureLauncher.launch(uri)
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("camera_button")
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_camera),
                                    contentDescription = "Capture text to translate (Camera)",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        if (isRecordingAudio) {
                                            // Stop recording
                                            try {
                                                mediaRecorder?.stop()
                                                mediaRecorder?.release()
                                                mediaRecorder = null
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                            isRecordingAudio = false
                                            
                                            // Trigger API Translation
                                            audioRecordUri?.let { uri ->
                                                viewModel.translateAudio(context, uri, sourceLanguage, targetLanguage)
                                            }
                                        } else {
                                            // Start recording
                                            try {
                                                val audioFile = File(context.cacheDir, "audio_record_${System.currentTimeMillis()}.mp4")
                                                audioRecordUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", audioFile)
                                                
                                                val recorder = android.media.MediaRecorder().apply {
                                                    setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                                                    setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                                                    setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                                                    setOutputFile(audioFile.absolutePath)
                                                    prepare()
                                                    start()
                                                }
                                                mediaRecorder = recorder
                                                isRecordingAudio = true
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                Toast.makeText(context, "Failed to start recording", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } else {
                                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("audio_record_button")
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_mic),
                                    contentDescription = if (isRecordingAudio) "Stop Recording" else "Record Audio",
                                    tint = if (isRecordingAudio) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            if (inputText.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.updateInputText("") },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("clear_text_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear input text",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val keyboardController = LocalSoftwareKeyboardController.current
                    TextField(
                        value = inputText,
                        onValueChange = { viewModel.updateInputText(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp)
                            .testTag("input_text_field"),
                        placeholder = {
                            Text(
                                text = "Begin typing or select one of the templates below...",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                fontSize = (15 * fontSizeScale).sp,
                                fontFamily = FontFamily.SansSerif
                            )
                        },
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.SansSerif, fontSize = (16 * fontSizeScale).sp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 10,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                if (inputText.isNotBlank()) {
                                    viewModel.translate(context)
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                }
                            }
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Bottom row with Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${inputText.length} characters",
                            fontSize = 12.sp,
                            color = if (inputText.length > 800) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )

                        Text(
                            text = "${inputText.split("\\s+".toRegex()).filter { it.isNotBlank() }.size} words",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            // --- Inline Quick Suggestion Chips ---
            if (inputText.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Quick templates:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    val suggestions = listOf(
                        "Hello, how are you doing today? Just wanted to say hello!",
                        "Where is the nearest train station and how do I buy a ticket?",
                        "Thank you so much for your warm hospitality. I had an amazing time."
                    )

                    suggestions.forEach { suggestion ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updateInputText(suggestion) }
                                .testTag("suggestion_chip_${suggestion.take(15)}"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f)
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy() 
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Query Template",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = suggestion,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Start,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Tone Selection Toggle ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tone:",
                    fontSize = (14 * fontSizeScale).sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(end = 12.dp)
                )
                
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(modifier = Modifier.padding(2.dp)) {
                        TranslationTone.values().forEach { tone ->
                            val isSelected = translationTone == tone
                            Box(
                                modifier = Modifier
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { viewModel.setTranslationTone(tone) }
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tone.displayName,
                                    fontSize = (13 * fontSizeScale).sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // --- Action Translate Button ---
            Button(
                onClick = { viewModel.translate(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("translate_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check, // Confirmation icon
                        contentDescription = "Translate",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Translate Now",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // --- Output State Card Container ---
            AnimatedContent(
                targetState = uiState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) with fadeOut(animationSpec = tween(180))
                },
                modifier = Modifier.fillMaxWidth()
            ) { state ->
                when (state) {
                    is TranslationUiState.Idle -> {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("idle_state_card"),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Idle Instructions",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Text(
                                    text = "Awaiting translation input",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Enter text above, pick languages from the menus, and click the translate button to see high-fidelity results generated instantly.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }

                    is TranslationUiState.Loading -> {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("loading_state_card"),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "Translating text...",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                )
                            }
                        }
                    }

                    is TranslationUiState.Success -> {
                        if (state.isCompareMode) {
                            val copiedStates = remember { mutableStateMapOf<String, Boolean>() }
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                state.compareResults.forEach { result ->
                                    CompareResultCard(
                                        originalText = state.originalText,
                                        result = result,
                                        isTransliterationEnabled = isTransliterationEnabled,
                                        onTransliterationToggle = { viewModel.setTransliterationEnabled(it) },
                                        historyState = historyState,
                                        viewModel = viewModel,
                                        speakOut = { t, l -> speakOut(t, l) },
                                        speakingStateText = speakingStateText,
                                        context = context,
                                        copiedStates = copiedStates,
                                        latencyMs = state.latencyMs
                                    )
                                }
                            }
                        } else {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("success_state_card"),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy() 
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                if (state.isFromCache) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "Offline cached translation",
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Offline Mode: Cached Translation Loaded",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val detectedLanguage = remember(state.detectedLanguageName) {
                                            state.detectedLanguageName?.let { Language.fromDisplayNameOrName(it) }
                                        }
                                        if (detectedLanguage != null) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = detectedLanguage.flag,
                                                    fontSize = 14.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = state.detectedLanguageName ?: "",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = Icons.Default.ArrowForward,
                                                contentDescription = "to",
                                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = state.targetLang.flag,
                                                fontSize = 14.sp,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = state.targetLang.displayName,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    // Star Toggle Button & Latency Indicator
                                    val matchedItem = historyState.find {
                                        it.originalText == state.originalText &&
                                        it.translatedText == state.translatedText
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (!state.isFromCache && state.latencyMs > 0L) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                                    .testTag("latency_indicator")
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF4CAF50))
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "${state.latencyMs} ms",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }

                                        if (matchedItem != null) {
                                            IconButton(
                                                onClick = {
                                                    viewModel.toggleFavorite(matchedItem.id, !matchedItem.isFavorite)
                                                },
                                                modifier = Modifier.size(36.dp).testTag("translate_result_favorite_toggle")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Star,
                                                    contentDescription = if (matchedItem.isFavorite) "Starred. Tap to unstar." else "Unstarred. Tap to star.",
                                                    tint = if (matchedItem.isFavorite) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), thickness = 1.dp)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        Column {
                                            SelectionContainer {
                                                Text(
                                                    text = state.translatedText,
                                                    fontSize = (16 * fontSizeScale).sp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    fontWeight = FontWeight.Medium,
                                                    fontFamily = FontFamily.SansSerif,
                                                    lineHeight = (24 * fontSizeScale).sp,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .testTag("translated_result_text")
                                                )
                                            }
                                            if (isTransliterationEnabled && !state.transliteration.isNullOrBlank()) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                SelectionContainer {
                                                    Text(
                                                        text = state.transliteration,
                                                        fontSize = (14 * fontSizeScale).sp,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                        fontWeight = FontWeight.Normal,
                                                        fontFamily = FontFamily.SansSerif,
                                                        lineHeight = (20 * fontSizeScale).sp,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .testTag("transliteration_result_text")
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // Transliteration Toggle Button
                                        IconButton(
                                            onClick = { viewModel.setTransliterationEnabled(!isTransliterationEnabled) },
                                            modifier = Modifier
                                                .size(40.dp)
                                                .testTag("transliteration_toggle")
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isTransliterationEnabled) {
                                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                        } else {
                                                            Color.Transparent
                                                        }
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "Aa",
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isTransliterationEnabled) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isTransliterationEnabled) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                    }
                                                )
                                            }
                                        }

                                        // TTS Button
                                        IconButton(
                                            onClick = { speakOut(state.translatedText, state.targetLang.code) },
                                            modifier = Modifier
                                                .size(40.dp)
                                                .testTag("tts_button_translated")
                                        ) {
                                            Icon(
                                                imageVector = if (speakingStateText == state.translatedText) Icons.Default.Close else Icons.Default.PlayArrow,
                                                contentDescription = if (speakingStateText == state.translatedText) "Stop reading aloud" else "Read aloud in " + state.targetLang.displayName,
                                                tint = if (speakingStateText == state.translatedText) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }

                                Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f), thickness = 1.dp)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Clipboard Copy Button
                                    OutlinedButton(
                                        onClick = {
                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("Gemini Translation", state.translatedText)
                                            clipboard.setPrimaryClip(clip)
                                            isCopiedSuccessfully = true
                                            Toast.makeText(context, "Translation copied to clipboard!", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier.testTag("copy_button")
                                    ) {
                                        Icon(
                                            imageVector = if (isCopiedSuccessfully) Icons.Default.Done else Icons.Default.Check,
                                            contentDescription = "Copy to clipboard",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isCopiedSuccessfully) "Copied" else "Copy",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    // Share Button
                                    OutlinedButton(
                                        onClick = {
                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                            val shareIntent = Intent().apply {
                                                action = Intent.ACTION_SEND
                                                putExtra(Intent.EXTRA_TEXT, state.translatedText)
                                                type = "text/plain"
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share translation via"))
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier.testTag("share_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Share translation",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Share",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }

                    is TranslationUiState.Error -> {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("error_state_card"),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.Start,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Error notification",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (state.isNoInternet) "Connectivity Offline" else "Attention Required",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Text(
                                    text = state.message,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }
        } else if (activeScreen == AppScreen.HISTORY) {
            // --- History Layout ---
            if (historyState.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("history_empty_card"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = "Empty History list",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Text(
                            text = "No history yet",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Translations you make will be preserved locally right here on your device.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Translations (${historyState.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    TextButton(
                        onClick = { viewModel.clearAllHistory() },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.testTag("clear_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear all translation history",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                historyState.forEach { historyItem ->
                    val srcLang = remember(historyItem.sourceLanguageCode) { Language.fromCode(historyItem.sourceLanguageCode) }
                    val dstLang = remember(historyItem.targetLanguageCode) { Language.fromCode(historyItem.targetLanguageCode) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Load this item into current translation state to edit or view
                                viewModel.setSourceLanguage(srcLang)
                                viewModel.setTargetLanguage(dstLang)
                                viewModel.updateInputText(historyItem.originalText)
                                activeScreen = AppScreen.TRANSLATE
                            }
                            .testTag("history_item_${historyItem.id}"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Header banner: flags and delete button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = srcLang.flag, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = srcLang.displayName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = "Translated into",
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = dstLang.flag, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = dstLang.displayName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IconButton(
                                        onClick = { viewModel.toggleFavorite(historyItem.id, !historyItem.isFavorite) },
                                        modifier = Modifier
                                            .size(24.dp)
                                            .testTag("favorite_toggle_${historyItem.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = if (historyItem.isFavorite) "Remove from favorites" else "Add to favorites",
                                            tint = if (historyItem.isFavorite) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.deleteHistoryItem(historyItem.id) },
                                        modifier = Modifier
                                            .size(24.dp)
                                            .testTag("delete_item_button_${historyItem.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete this entry",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), thickness = 1.dp)

                            // Original text
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Original",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                                Text(
                                    text = historyItem.originalText,
                                    fontSize = (13 * fontSizeScale).sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    maxLines = 3,
                                    lineHeight = 18.sp
                                )
                            }

                            // Translated text
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Translation",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = historyItem.translatedText,
                                    fontSize = (14 * fontSizeScale).sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 3,
                                    lineHeight = (20 * fontSizeScale).sp
                                )
                            }

                            // Quick Actions row: Copy and Open
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Mini copy button
                                TextButton(
                                    onClick = {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Gemini Translation", historyItem.translatedText)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Translation copied!", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Copy text",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Mini speak button
                                TextButton(
                                    onClick = {
                                        speakOut(historyItem.translatedText, historyItem.targetLanguageCode)
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (speakingStateText == historyItem.translatedText) Icons.Default.Close else Icons.Default.PlayArrow,
                                        contentDescription = "Speak translation",
                                        modifier = Modifier.size(14.dp),
                                        tint = if (speakingStateText == historyItem.translatedText) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (speakingStateText == historyItem.translatedText) "Stop" else "Speak",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (speakingStateText == historyItem.translatedText) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Tap to use
                                TextButton(
                                    onClick = {
                                        viewModel.setSourceLanguage(srcLang)
                                        viewModel.setTargetLanguage(dstLang)
                                        viewModel.updateInputText(historyItem.originalText)
                                        activeScreen = AppScreen.TRANSLATE
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Load back text",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Open", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        } else if (activeScreen == AppScreen.FAVORITES) {
            // --- Favorites Layout ---
            if (favoriteHistoryState.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("favorites_empty_card"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFC107).copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Empty Favorites list",
                                tint = Color(0xFFFFC107),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Text(
                            text = "No favorites yet",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tap the star icon on any translation in the Translate tab or History to add a favorite.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Starred Translations (${favoriteHistoryState.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                favoriteHistoryState.forEach { favoriteItem ->
                    val srcLang = remember(favoriteItem.sourceLanguageCode) { Language.fromCode(favoriteItem.sourceLanguageCode) }
                    val dstLang = remember(favoriteItem.targetLanguageCode) { Language.fromCode(favoriteItem.targetLanguageCode) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setSourceLanguage(srcLang)
                                viewModel.setTargetLanguage(dstLang)
                                viewModel.updateInputText(favoriteItem.originalText)
                                activeScreen = AppScreen.TRANSLATE
                            }
                            .testTag("favorite_item_${favoriteItem.id}"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = srcLang.flag, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = srcLang.displayName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = "Translated into",
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = dstLang.flag, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = dstLang.displayName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.toggleFavorite(favoriteItem.id, false) },
                                    modifier = Modifier
                                        .size(24.dp)
                                        .testTag("unfavorite_item_button_${favoriteItem.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Remove from favorites",
                                        tint = Color(0xFFFFC107),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), thickness = 1.dp)

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Original",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                                Text(
                                    text = favoriteItem.originalText,
                                    fontSize = (13 * fontSizeScale).sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    maxLines = 3,
                                    lineHeight = 18.sp
                                )
                            }

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Translation",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = favoriteItem.translatedText,
                                    fontSize = (14 * fontSizeScale).sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 3,
                                    lineHeight = (20 * fontSizeScale).sp
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Gemini Translation", favoriteItem.translatedText)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Translation copied!", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Copy text",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Mini speak button
                                TextButton(
                                    onClick = {
                                        speakOut(favoriteItem.translatedText, favoriteItem.targetLanguageCode)
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (speakingStateText == favoriteItem.translatedText) Icons.Default.Close else Icons.Default.PlayArrow,
                                        contentDescription = "Speak translation",
                                        modifier = Modifier.size(14.dp),
                                        tint = if (speakingStateText == favoriteItem.translatedText) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (speakingStateText == favoriteItem.translatedText) "Stop" else "Speak",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (speakingStateText == favoriteItem.translatedText) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                TextButton(
                                    onClick = {
                                        viewModel.setSourceLanguage(srcLang)
                                        viewModel.setTargetLanguage(dstLang)
                                        viewModel.updateInputText(favoriteItem.originalText)
                                        activeScreen = AppScreen.TRANSLATE
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Load back text",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Open", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        } else if (activeScreen == AppScreen.PHRASEBOOK) {
            PhrasebookLayout(
                viewModel = viewModel,
                fontSizeScale = fontSizeScale,
                speakOut = speakOut,
                targetLanguage = targetLanguage
            )
        } else if (activeScreen == AppScreen.CONVERSATION) {
            // --- Conversation Layout ---
            ConversationLayout(
                viewModel = viewModel,
                sourceLang = sourceLanguage,
                targetLang = targetLanguage,
                speakOut = speakOut,
                speechLauncher = speechLauncher,
                onSpeechTargetChanged = { speechTargetMode = it },
                fontSizeScale = fontSizeScale
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- Standard Security Warning regarding API keys (MANDATORY) ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("security_warning_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Prototype security notice",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Developer Notice",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                     text = "Security Warning: I have included your API keys in the generated APK file for this prototype. Please be aware that Android APKs can be easily decompiled, and these keys can be extracted by anyone who has access to the file. Do not share this APK file publicly or with unauthorized individuals to prevent potential misuse.",
                     fontSize = 10.sp,
                     color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                     lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
fun LanguageSelectorDropdown(
    label: String,
    selectedLanguage: Language,
    onLanguageSelected: (Language) -> Unit,
    includeAutoDetect: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val languagesList = remember(includeAutoDetect) {
        if (includeAutoDetect) {
            Language.values().toList()
        } else {
            Language.values().filter { it != Language.AUTO }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .clickable { expanded = true }
                .padding(vertical = 10.dp, horizontal = 12.dp)
                .testTag("dropdown_${label.lowercase()}"),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = selectedLanguage.flag,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = selectedLanguage.displayName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        fontFamily = FontFamily.SansSerif
                    )
                }
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = "Expand language options",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { 
                    expanded = false
                    searchQuery = ""
                },
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search language...", fontSize = 13.sp, fontFamily = FontFamily.SansSerif) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("language_search_${label.lowercase()}"),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search icon",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    },
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontFamily = FontFamily.SansSerif),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                val filteredLanguages = remember(languagesList, searchQuery) {
                    if (searchQuery.isBlank()) {
                        languagesList
                    } else {
                        languagesList.filter {
                            it.displayName.contains(searchQuery, ignoreCase = true) ||
                            it.code.contains(searchQuery, ignoreCase = true)
                        }
                    }
                }

                val autoDetectLang = filteredLanguages.find { it == Language.AUTO }
                val internationalLangs = filteredLanguages.filter { !it.isIndian && it != Language.AUTO }
                val indianLangs = filteredLanguages.filter { it.isIndian && it != Language.AUTO }

                if (autoDetectLang != null) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(autoDetectLang.flag, fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = autoDetectLang.displayName,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        },
                        onClick = {
                            onLanguageSelected(autoDetectLang)
                            expanded = false
                            searchQuery = ""
                        },
                        modifier = Modifier.testTag("dropdown_item_${autoDetectLang.code}")
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (internationalLangs.isNotEmpty()) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "International Languages",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.SansSerif,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        },
                        onClick = {},
                        enabled = false
                    )

                    internationalLangs.forEach { language ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(language.flag, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = language.displayName,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                }
                            },
                            onClick = {
                                onLanguageSelected(language)
                                expanded = false
                                searchQuery = ""
                            },
                            modifier = Modifier.testTag("dropdown_item_${language.code}")
                        )
                    }
                }

                if (indianLangs.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Indian Languages",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.SansSerif,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        },
                        onClick = {},
                        enabled = false
                    )

                    indianLangs.forEach { language ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(language.flag, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = language.displayName,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                }
                            },
                            onClick = {
                                onLanguageSelected(language)
                                expanded = false
                                searchQuery = ""
                            },
                            modifier = Modifier.testTag("dropdown_item_${language.code}")
                        )
                    }
                }

                if (filteredLanguages.isEmpty()) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "No languages found",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontFamily = FontFamily.SansSerif,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        },
                        onClick = {},
                        enabled = false
                    )
                }
            }
        }
    }
}

// Visual text selection fallback wrapper to make text elegantly copyable manually
@Composable
fun SelectionContainer(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        content()
    }
}

@Composable
fun AudioResultScreen(
    state: AudioUiState,
    onClose: () -> Unit,
    fontSizeScale: Float = 1.0f
) {
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("audio_result_container")
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_mic),
                        contentDescription = "Audio Translation",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Audio Translation",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.SansSerif
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close audio translation",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            when (state) {
                is AudioUiState.Idle -> {}
                is AudioUiState.Recording -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Recording Audio...",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                is AudioUiState.Loading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Analyzing Voice...",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                is AudioUiState.Error -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                is AudioUiState.Success -> {
                    var isPlaying by remember { mutableStateOf(false) }
                    var mediaPlayer: MediaPlayer? by remember { mutableStateOf(null) }

                    DisposableEffect(Unit) {
                        onDispose {
                            mediaPlayer?.release()
                        }
                    }

                    // Content
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Transcription:",
                                fontSize = (12 * fontSizeScale).sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            IconButton(onClick = {
                                if (isPlaying) {
                                    mediaPlayer?.stop()
                                    mediaPlayer?.release()
                                    mediaPlayer = null
                                    isPlaying = false
                                } else {
                                    try {
                                        val mp = MediaPlayer().apply {
                                            setDataSource(context, state.audioUri)
                                            prepare()
                                            start()
                                            setOnCompletionListener {
                                                isPlaying = false
                                            }
                                        }
                                        mediaPlayer = mp
                                        isPlaying = true
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        Toast.makeText(context, "Failed to play audio", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Stop" else "Play",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        
                        Text(
                            text = state.originalText,
                            fontSize = (15 * fontSizeScale).sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = (22 * fontSizeScale).sp,
                            modifier = Modifier.testTag("audio_transcript")
                        )

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Text(
                            text = "Translation:",
                            fontSize = (12 * fontSizeScale).sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = state.translatedText,
                            fontSize = (17 * fontSizeScale).sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = (24 * fontSizeScale).sp,
                            modifier = Modifier.testTag("audio_translation")
                        )
                        
                        // Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Gemini Audio Translation", state.translatedText)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Copied translation to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Copy text",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CameraResultScreen(
    state: CameraUiState,
    onRetake: () -> Unit,
    onSave: (String, String, Language, Language) -> Unit,
    onClose: () -> Unit,
    fontSizeScale: Float = 1.0f
) {
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var isSaved by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("camera_result_container")
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_camera),
                        contentDescription = "Camera Translation",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Camera Translation",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.SansSerif
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close camera translation",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), thickness = 1.dp)

            when (state) {
                is CameraUiState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Analyzing photo with Gemini AI...",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = "Extracting and translating text in image...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
                is CameraUiState.Success -> {
                    // Show Language indicator
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val sourceLabel = if (state.sourceLang == Language.AUTO) {
                            if (state.detectedLanguageName != null) "Detected: ${state.detectedLanguageName}" else "Detected Language"
                        } else {
                            state.sourceLang.displayName
                        }
                        Text(
                            text = sourceLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontFamily = FontFamily.SansSerif
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "to",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .size(14.dp)
                        )
                        Text(
                            text = state.targetLang.displayName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontFamily = FontFamily.SansSerif
                        )
                    }

                    // Detected original text
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Detected Original Text:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontFamily = FontFamily.SansSerif
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .padding(14.dp)
                        ) {
                            Text(
                                text = state.originalText.ifEmpty { "(No text in photo detected)" },
                                fontSize = (15 * fontSizeScale).sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.SansSerif,
                                modifier = Modifier.testTag("camera_original_text")
                            )
                        }
                    }

                    // Translated text
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Gemini Translation:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.SansSerif
                            )
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Gemini Camera Translation", state.translatedText)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Copied translation to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Copy translation",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                                .padding(14.dp)
                        ) {
                            Text(
                                text = state.translatedText.ifEmpty { "(Translation was empty)" },
                                fontSize = (16 * fontSizeScale).sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.SansSerif,
                                modifier = Modifier.testTag("camera_translated_text")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Retake
                        Button(
                            onClick = onRetake,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("camera_retake_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Retake",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }

                        // Save to History
                        Button(
                            onClick = {
                                if (!isSaved) {
                                    val resolvedSrc = if (state.sourceLang == Language.AUTO && state.detectedLanguageName != null) {
                                        Language.fromDisplayNameOrName(state.detectedLanguageName) ?: Language.AUTO
                                    } else {
                                        state.sourceLang
                                    }
                                    onSave(state.originalText, state.translatedText, resolvedSrc, state.targetLang)
                                    isSaved = true
                                }
                            },
                            enabled = !isSaved && state.originalText.isNotEmpty(),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(48.dp)
                                .testTag("camera_save_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = if (isSaved) Icons.Default.Check else Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSaved) "Saved" else "Save to History",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                }
                is CameraUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = state.message,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            fontFamily = FontFamily.SansSerif,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onClose,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = onRetake,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Retake", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                is CameraUiState.Idle -> {}
            }
        }
    }
}

@Composable
fun ConversationLayout(
    viewModel: TranslationViewModel,
    sourceLang: Language,
    targetLang: Language,
    speakOut: (String, String) -> Unit,
    speechLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
    onSpeechTargetChanged: (SpeechTarget) -> Unit,
    fontSizeScale: Float = 1.0f
) {
    val context = LocalContext.current
    val transcript by viewModel.conversationTranscript.collectAsState()
    val isProcessing by viewModel.isConversationProcessing.collectAsState()
    val errorMsg by viewModel.conversationError.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("conversation_config_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with reset button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Conversation Mode",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Tap a mic relative to your side & speak",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
                
                if (transcript.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.clearConversationTranscript() },
                        modifier = Modifier.testTag("btn_clear_conversation")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear transcript",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // One single auto-detect microphone button
            Button(
                onClick = {
                    onSpeechTargetChanged(SpeechTarget.CONVERSATION_AUTO)
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        // Omitting EXTRA_LANGUAGE defaults to the system locale and broadens detection capabilities
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak in ${sourceLang.displayName} or ${targetLang.displayName}...")
                    }
                    try {
                        speechLauncher.launch(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Speech recognition is not supported on this device.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .testTag("mic_auto"),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = sourceLang.flag, fontSize = 28.sp)
                        Icon(
                            painter = painterResource(id = R.drawable.ic_mic),
                            contentDescription = "Auto Speak Button",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(text = targetLang.flag, fontSize = 28.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap to speak. Language will be auto-detected",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            if (isProcessing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Gemini is translating...",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (errorMsg != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = errorMsg ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.dismissConversationError() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss error",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Running Transcript section
    Text(
        text = "Running Transcript",
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxWidth()
    )

    if (transcript.isEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("conversation_empty_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "💬",
                    fontSize = 36.sp
                )
                Text(
                    text = "No messages yet",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Start speaking to build the transcript.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            transcript.forEach { item ->
                val isLeft = item.speakerCode == "left"
                
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (isLeft) Alignment.CenterStart else Alignment.CenterEnd
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .testTag("transcript_item_${item.id}"),
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isLeft) 4.dp else 16.dp,
                            bottomEnd = if (isLeft) 16.dp else 4.dp
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isLeft) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Header: Language flag arrow
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(text = item.sourceLang.flag, fontSize = 14.sp)
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = "Translated into",
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                    Text(text = item.targetLang.flag, fontSize = 14.sp)
                                }

                                // Quick button to replay TTS
                                IconButton(
                                    onClick = { speakOut(item.translatedText, item.targetLang.code) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Speak translation aloud",
                                        tint = if (isLeft) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        },
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Original Speech
                            Text(
                                text = item.originalText,
                                fontSize = (14 * fontSizeScale).sp,
                                color = if (isLeft) {
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                },
                                style = androidx.compose.ui.text.TextStyle(
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            )

                            // Translated Speech
                            Text(
                                text = item.translatedText,
                                fontSize = (16 * fontSizeScale).sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isLeft) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CompareResultCard(
    originalText: String,
    result: CompareResult,
    isTransliterationEnabled: Boolean,
    onTransliterationToggle: (Boolean) -> Unit,
    historyState: List<HistoryItem>,
    viewModel: TranslationViewModel,
    speakOut: (String, String) -> Unit,
    speakingStateText: String?,
    context: Context,
    copiedStates: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Boolean>,
    latencyMs: Long = 0L,
    fontSizeScale: Float = 1.0f
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val isCopied = copiedStates[result.targetLang.code] ?: false

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("compare_result_card_${result.targetLang.code}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        ),
        border = ButtonDefaults.outlinedButtonBorder.copy()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (result.isFromCache) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Offline cached translation",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Offline Mode: Cached Translation Loaded",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = result.targetLang.flag,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = result.targetLang.displayName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Star Toggle Button & Latency Indicator
                val matchedItem = historyState.firstOrNull {
                    it.originalText == originalText &&
                    it.translatedText == result.translatedText &&
                    it.targetLanguageCode == result.targetLang.code
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!result.isFromCache && latencyMs > 0L) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("compare_latency_indicator_${result.targetLang.code}")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$latencyMs ms",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (matchedItem != null) {
                        IconButton(
                            onClick = {
                                viewModel.toggleFavorite(matchedItem.id, !matchedItem.isFavorite)
                            },
                            modifier = Modifier.size(36.dp).testTag("compare_favorite_toggle_${result.targetLang.code}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = if (matchedItem.isFavorite) "Starred. Tap to unstar." else "Unstarred. Tap to star.",
                                tint = if (matchedItem.isFavorite) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), thickness = 1.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    Column {
                        SelectionContainer {
                            Text(
                                text = result.translatedText,
                                fontSize = (16 * fontSizeScale).sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.SansSerif,
                                lineHeight = (24 * fontSizeScale).sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("compare_result_text_${result.targetLang.code}")
                            )
                        }
                        if (isTransliterationEnabled && !result.transliteration.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            SelectionContainer {
                                Text(
                                    text = result.transliteration,
                                    fontSize = (14 * fontSizeScale).sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    fontWeight = FontWeight.Normal,
                                    fontFamily = FontFamily.SansSerif,
                                    lineHeight = (20 * fontSizeScale).sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("compare_transliteration_text_${result.targetLang.code}")
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Transliteration Toggle Button
                    IconButton(
                        onClick = { onTransliterationToggle(!isTransliterationEnabled) },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("compare_translit_toggle_${result.targetLang.code}")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isTransliterationEnabled) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    } else {
                                        Color.Transparent
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Aa",
                                fontSize = 13.sp,
                                fontWeight = if (isTransliterationEnabled) FontWeight.Bold else FontWeight.Normal,
                                color = if (isTransliterationEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                }
                            )
                        }
                    }

                    // TTS Button
                    IconButton(
                        onClick = { speakOut(result.translatedText, result.targetLang.code) },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("compare_tts_button_${result.targetLang.code}")
                    ) {
                        Icon(
                            imageVector = if (speakingStateText == result.translatedText) Icons.Default.Close else Icons.Default.PlayArrow,
                            contentDescription = if (speakingStateText == result.translatedText) "Stop reading aloud" else "Read aloud in " + result.targetLang.displayName,
                            tint = if (speakingStateText == result.translatedText) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f), thickness = 1.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Clipboard Copy Button
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Gemini Translation", result.translatedText)
                        clipboard.setPrimaryClip(clip)
                        copiedStates[result.targetLang.code] = true
                        Toast.makeText(context, "Translation to ${result.targetLang.displayName} copied!", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("compare_copy_${result.targetLang.code}")
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Default.Done else Icons.Default.Check,
                        contentDescription = "Copy to clipboard",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isCopied) "Copied" else "Copy",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Share Button
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, result.translatedText)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share translation via"))
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("compare_share_${result.targetLang.code}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share translation",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Share",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    viewModel: TranslationViewModel,
    themeMode: ThemeMode,
    onDismiss: () -> Unit
) {
    val isTransliterationEnabled by viewModel.isTransliterationEnabled.collectAsState()
    val isCompareMode by viewModel.isCompareMode.collectAsState()
    val preferredVoiceGender by viewModel.preferredVoiceGender.collectAsState()
    val voicePitch by viewModel.voicePitch.collectAsState()
    val defaultTargetLanguage by viewModel.defaultTargetLanguage.collectAsState()
    val fontSizeScale by viewModel.fontSizeScale.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Application Settings",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            val dialogScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(dialogScrollState)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "ACCESSIBILITY & THEME",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )

                // Theme Mode Selectors
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ThemeOptionRow(
                        title = "System Default",
                        description = "Matches device settings",
                        isSelected = themeMode == ThemeMode.SYSTEM,
                        onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                        testTag = "theme_option_system"
                    )
                    
                    ThemeOptionRow(
                        title = "Light Theme",
                        description = "Soft teal on white background",
                        isSelected = themeMode == ThemeMode.LIGHT,
                        onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                        testTag = "theme_option_light"
                    )

                    ThemeOptionRow(
                        title = "Dark Theme",
                        description = "Comfortable eye-safe teal on dark background",
                        isSelected = themeMode == ThemeMode.DARK,
                        onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                        testTag = "theme_option_dark"
                    )

                    ThemeOptionRow(
                        title = "High-Contrast Mode",
                        description = "Yellow/cyan on pure black for readability",
                        isSelected = themeMode == ThemeMode.HIGH_CONTRAST,
                        onClick = { viewModel.setThemeMode(ThemeMode.HIGH_CONTRAST) },
                        testTag = "theme_option_high_contrast"
                    )
                }

                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                Text(
                    text = "TEXT SIZE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Text Scale: ${String.format(Locale.US, "%.1fx", fontSizeScale)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TextButton(
                            onClick = { viewModel.setFontSizeScale(1.0f) },
                            modifier = Modifier.testTag("reset_font_size_button")
                        ) {
                            Text("Reset", fontSize = 12.sp)
                        }
                    }

                    Slider(
                        value = fontSizeScale,
                        onValueChange = { viewModel.setFontSizeScale(it) },
                        valueRange = 0.8f..2.0f,
                        steps = 11,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("font_size_slider")
                    )
                }

                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                Text(
                    text = "SPEECH VOICE GENDER",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    VoiceGenderOptionRow(
                        title = "Female Voice",
                        description = "Clear and fluent female vocal synthesis",
                        isSelected = preferredVoiceGender == VoiceGender.FEMALE,
                        onClick = { viewModel.setPreferredVoiceGender(VoiceGender.FEMALE) },
                        testTag = "voice_gender_female"
                    )

                    VoiceGenderOptionRow(
                        title = "Male Voice",
                        description = "Resonant and natural male vocal synthesis",
                        isSelected = preferredVoiceGender == VoiceGender.MALE,
                        onClick = { viewModel.setPreferredVoiceGender(VoiceGender.MALE) },
                        testTag = "voice_gender_male"
                    )
                }

                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                Text(
                    text = "SPEECH VOICE PITCH",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Pitch Level: ${String.format(Locale.US, "%.1fx", voicePitch)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TextButton(
                            onClick = { viewModel.setVoicePitch(1.0f) },
                            modifier = Modifier.testTag("reset_pitch_button")
                        ) {
                            Text("Reset", fontSize = 12.sp)
                        }
                    }

                    Slider(
                        value = voicePitch,
                        onValueChange = { viewModel.setVoicePitch(it) },
                        valueRange = 0.5f..2.0f,
                        steps = 14,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("voice_pitch_slider")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val context = LocalContext.current
                Button(
                    onClick = {
                        val installIntent = Intent()
                        installIntent.action = android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA
                        try {
                            context.startActivity(installIntent)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "TTS Settings not found", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("btn_offline_tts"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    contentPadding = PaddingValues(16.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Download Offline TTS Voices",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }

                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                Text(
                    text = "TRANSLATION FEATURES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Default Translation Language",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Set the language to use automatically for all translations",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LanguageSelectorDropdown(
                        label = "Default Language",
                        selectedLanguage = defaultTargetLanguage,
                        onLanguageSelected = { viewModel.setDefaultTargetLanguage(it) },
                        includeAutoDetect = false
                    )
                }

                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // Transliteration Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setTransliterationEnabled(!isTransliterationEnabled) }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Show Transliteration",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Show phonetic Roman script for native translation texts",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = isTransliterationEnabled,
                        onCheckedChange = { viewModel.setTransliterationEnabled(it) },
                        modifier = Modifier.testTag("settings_translit_switch")
                    )
                }

                // Compare Mode Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setCompareMode(!isCompareMode) }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Default Compare Mode",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Allow comparisons to multiple languages by default",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = isCompareMode,
                        onCheckedChange = { viewModel.setCompareMode(it) },
                        modifier = Modifier.testTag("settings_compare_switch")
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("settings_close_button")
            ) {
                Text(
                    text = "Done",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("settings_dialog")
    )
}

@Composable
fun ThemeOptionRow(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun VoiceGenderOptionRow(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

fun setVoiceOrLanguage(tts: TextToSpeech, locale: Locale, preferredGender: VoiceGender, pitch: Float) {
    try {
        tts.setPitch(pitch)
        val voices = tts.getVoices()
        if (!voices.isNullOrEmpty()) {
            val localeVoices = voices.filter { voice ->
                voice.locale.language.equals(locale.language, ignoreCase = true)
            }
            if (localeVoices.isNotEmpty()) {
                val selectedVoice = when (preferredGender) {
                    VoiceGender.FEMALE -> {
                        localeVoices.firstOrNull { voice ->
                            val nameLower = voice.name.lowercase(Locale.ROOT)
                            nameLower.contains("female") || 
                            nameLower.contains("fem") ||
                            nameLower.contains("f-f") ||
                            voice.features.any { feature -> 
                                feature.lowercase(Locale.ROOT).contains("female") || 
                                feature.lowercase(Locale.ROOT).contains("fem") 
                            }
                        } ?: localeVoices.firstOrNull { voice ->
                            !voice.name.lowercase(Locale.ROOT).contains("male")
                        } ?: localeVoices.firstOrNull()
                    }
                    VoiceGender.MALE -> {
                        localeVoices.firstOrNull { voice ->
                            val nameLower = voice.name.lowercase(Locale.ROOT)
                            nameLower.contains("male") || 
                            nameLower.contains("masc") ||
                            nameLower.contains("-m-") ||
                            voice.features.any { feature -> 
                                feature.lowercase(Locale.ROOT).contains("male") || 
                                feature.lowercase(Locale.ROOT).contains("masc") 
                            }
                        } ?: localeVoices.firstOrNull()
                    }
                }
                
                if (selectedVoice != null) {
                    val result = tts.setVoice(selectedVoice)
                    if (result == TextToSpeech.ERROR) {
                        tts.setLanguage(locale)
                    }
                } else {
                    tts.setLanguage(locale)
                }
            } else {
                tts.setLanguage(locale)
            }
        } else {
            tts.setLanguage(locale)
        }
    } catch (e: Exception) {
        tts.setLanguage(locale)
    }
}

