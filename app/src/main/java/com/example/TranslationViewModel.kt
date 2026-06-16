package com.example

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.Content
import com.example.api.GenerateContentRequest
import com.example.api.GenerationConfig
import com.example.api.Part
import com.example.api.RetrofitClient
import com.example.api.InlineData
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import android.net.Uri
import com.example.db.HistoryDatabase
import com.example.db.HistoryItem
import com.example.db.HistoryRepository
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.HttpException

enum class VoiceGender {
    FEMALE, MALE
}

enum class TranslationTone(val displayName: String) {
    FORMAL("Formal"),
    CASUAL("Casual")
}

data class CompareResult(
    val targetLang: Language,
    val translatedText: String,
    val transliteration: String? = null,
    val isFromCache: Boolean = false
)

sealed interface TranslationUiState {
    object Idle : TranslationUiState
    object Loading : TranslationUiState
    data class Success(
        val originalText: String,
        val translatedText: String,
        val transliteration: String? = null,
        val sourceLang: Language,
        val targetLang: Language,
        val detectedLanguageName: String? = null,
        val isFromCache: Boolean = false,
        val isCompareMode: Boolean = false,
        val compareResults: List<CompareResult> = emptyList(),
        val latencyMs: Long = 0L
    ) : TranslationUiState
    data class Error(
        val message: String,
        val isNoInternet: Boolean = false
    ) : TranslationUiState
}

sealed interface CameraUiState {
    object Idle : CameraUiState
    object Loading : CameraUiState
    data class Success(
        val imageUri: Uri,
        val originalText: String,
        val translatedText: String,
        val sourceLang: Language,
        val targetLang: Language,
        val detectedLanguageName: String? = null,
        val isFromCache: Boolean = false
    ) : CameraUiState
    data class Error(val message: String) : CameraUiState
}

sealed interface AudioUiState {
    object Idle : AudioUiState
    object Recording : AudioUiState
    object Loading : AudioUiState
    data class Success(
        val audioUri: Uri,
        val originalText: String,
        val translatedText: String,
        val sourceLang: Language,
        val targetLang: Language,
        val detectedLanguageName: String? = null
    ) : AudioUiState
    data class Error(val message: String) : AudioUiState
}

class TranslationViewModel(application: Application) : AndroidViewModel(application) {

    private val database = HistoryDatabase.getDatabase(application)
    private val repository = HistoryRepository(database.historyDao())

    private val sharedPrefs = application.getSharedPreferences("translator_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        try {
            ThemeMode.valueOf(sharedPrefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        sharedPrefs.edit().putString("theme_mode", mode.name).apply()
    }

    private val _isTransliterationEnabled = MutableStateFlow(sharedPrefs.getBoolean("transliteration_enabled", false))
    val isTransliterationEnabled: StateFlow<Boolean> = _isTransliterationEnabled.asStateFlow()

    fun setTransliterationEnabled(enabled: Boolean) {
        _isTransliterationEnabled.value = enabled
        sharedPrefs.edit().putBoolean("transliteration_enabled", enabled).apply()
    }

    private val _isCompareMode = MutableStateFlow(sharedPrefs.getBoolean("compare_mode", false))
    val isCompareMode: StateFlow<Boolean> = _isCompareMode.asStateFlow()

    fun setCompareMode(enabled: Boolean) {
        _isCompareMode.value = enabled
        sharedPrefs.edit().putBoolean("compare_mode", enabled).apply()
    }

    private val _preferredVoiceGender = MutableStateFlow(
        try {
            VoiceGender.valueOf(sharedPrefs.getString("preferred_voice_gender", VoiceGender.FEMALE.name) ?: VoiceGender.FEMALE.name)
        } catch (e: Exception) {
            VoiceGender.FEMALE
        }
    )
    val preferredVoiceGender: StateFlow<VoiceGender> = _preferredVoiceGender.asStateFlow()

    fun setPreferredVoiceGender(gender: VoiceGender) {
        _preferredVoiceGender.value = gender
        sharedPrefs.edit().putString("preferred_voice_gender", gender.name).apply()
    }

    private val _voicePitch = MutableStateFlow(
        sharedPrefs.getFloat("voice_pitch", 1.0f)
    )
    val voicePitch: StateFlow<Float> = _voicePitch.asStateFlow()

    fun setVoicePitch(pitch: Float) {
        _voicePitch.value = pitch
        sharedPrefs.edit().putFloat("voice_pitch", pitch).apply()
    }

    private val _translationTone = MutableStateFlow(
        try {
            TranslationTone.valueOf(sharedPrefs.getString("translation_tone", TranslationTone.CASUAL.name) ?: TranslationTone.CASUAL.name)
        } catch (e: Exception) {
            TranslationTone.CASUAL
        }
    )
    val translationTone: StateFlow<TranslationTone> = _translationTone.asStateFlow()

    fun setTranslationTone(tone: TranslationTone) {
        _translationTone.value = tone
        sharedPrefs.edit().putString("translation_tone", tone.name).apply()
    }

    private val _defaultTargetLanguage = MutableStateFlow(
        Language.fromCode(sharedPrefs.getString("default_target_language", Language.ENGLISH.code) ?: Language.ENGLISH.code)
    )
    val defaultTargetLanguage: StateFlow<Language> = _defaultTargetLanguage.asStateFlow()

    fun setDefaultTargetLanguage(lang: Language) {
        _defaultTargetLanguage.value = lang
        sharedPrefs.edit().putString("default_target_language", lang.code).apply()
        // Force update the current target language immediately so it updates for the user
        _targetLanguage.value = lang
    }

    private val _fontSizeScale = MutableStateFlow(
        sharedPrefs.getFloat("font_size_scale", 1.0f)
    )
    val fontSizeScale: StateFlow<Float> = _fontSizeScale.asStateFlow()

    fun setFontSizeScale(scale: Float) {
        _fontSizeScale.value = scale
        sharedPrefs.edit().putFloat("font_size_scale", scale).apply()
    }

    private val _compareTargetLanguages = MutableStateFlow(loadCompareTargetLanguages())
    val compareTargetLanguages: StateFlow<List<Language>> = _compareTargetLanguages.asStateFlow()

    private fun loadCompareTargetLanguages(): List<Language> {
        val savedStr = sharedPrefs.getString("compare_target_languages", null)
        if (!savedStr.isNullOrBlank()) {
            try {
                val codes = savedStr.split(",")
                val list = codes.map { Language.fromCode(it) }.filter { it != Language.AUTO }
                if (list.isNotEmpty()) {
                    return list.take(3)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        return listOf(Language.HINDI, Language.SPANISH, Language.FRENCH)
    }

    private fun saveCompareTargetLanguages(langs: List<Language>) {
        val str = langs.joinToString(",") { it.code }
        sharedPrefs.edit().putString("compare_target_languages", str).apply()
    }

    fun updateCompareTargetLanguage(index: Int, lang: Language) {
        val current = _compareTargetLanguages.value.toMutableList()
        if (index in current.indices) {
            current[index] = lang
            _compareTargetLanguages.value = current
            saveCompareTargetLanguages(current)
        }
    }

    fun addCompareTargetLanguage(lang: Language) {
        val current = _compareTargetLanguages.value.toMutableList()
        if (current.size < 3 && !current.contains(lang) && lang != Language.AUTO) {
            current.add(lang)
            _compareTargetLanguages.value = current
            saveCompareTargetLanguages(current)
        }
    }

    fun removeCompareTargetLanguage(index: Int) {
        val current = _compareTargetLanguages.value.toMutableList()
        if (current.size > 1 && index in current.indices) {
            current.removeAt(index)
            _compareTargetLanguages.value = current
            saveCompareTargetLanguages(current)
        }
    }

    val historyState: StateFlow<List<HistoryItem>> = repository.allHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val favoriteHistoryState: StateFlow<List<HistoryItem>> = repository.favoriteHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun saveAsFavorite(originalText: String, translatedText: String, sourceLangCode: String, targetLangCode: String) {
        viewModelScope.launch {
            repository.insert(
                HistoryItem(
                    originalText = originalText,
                    translatedText = translatedText,
                    sourceLanguageCode = sourceLangCode,
                    targetLanguageCode = targetLangCode,
                    isFavorite = true
                )
            )
        }
    }

    private val _uiState = MutableStateFlow<TranslationUiState>(TranslationUiState.Idle)
    val uiState: StateFlow<TranslationUiState> = _uiState.asStateFlow()

    private val _cameraUiState = MutableStateFlow<CameraUiState>(CameraUiState.Idle)
    val cameraUiState: StateFlow<CameraUiState> = _cameraUiState.asStateFlow()

    private val _audioUiState = MutableStateFlow<AudioUiState>(AudioUiState.Idle)
    val audioUiState: StateFlow<AudioUiState> = _audioUiState.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _sourceLanguage = MutableStateFlow(
        Language.fromCode(sharedPrefs.getString("last_source_language", Language.AUTO.code) ?: Language.AUTO.code)
    )
    val sourceLanguage: StateFlow<Language> = _sourceLanguage.asStateFlow()

    private val _targetLanguage = MutableStateFlow(
        Language.fromCode(sharedPrefs.getString("last_target_language", sharedPrefs.getString("default_target_language", Language.ENGLISH.code) ?: Language.ENGLISH.code) ?: Language.ENGLISH.code)
    )
    val targetLanguage: StateFlow<Language> = _targetLanguage.asStateFlow()

    fun updateInputText(text: String) {
        _inputText.value = text
        // If user clears text, reset state back to idle
        if (text.isBlank()) {
            _uiState.value = TranslationUiState.Idle
        }
    }

    fun setSourceLanguage(lang: Language) {
        _sourceLanguage.value = lang
        sharedPrefs.edit().putString("last_source_language", lang.code).apply()
    }

    fun setTargetLanguage(lang: Language) {
        _targetLanguage.value = lang
        sharedPrefs.edit().putString("last_target_language", lang.code).apply()
    }

    fun swapLanguages() {
        val currentSource = _sourceLanguage.value
        val currentTarget = _targetLanguage.value
        
        // If current source is AUTO-DETECT, we cannot assign it to Target (which needs a specific language),
        // so we default Target to defaultTargetLanguage and Source to the previous Target.
        if (currentSource == Language.AUTO) {
            val newSource = currentTarget
            val defaultLang = _defaultTargetLanguage.value
            val newTarget = if (currentTarget == defaultLang) {
                if (defaultLang == Language.ENGLISH) Language.SPANISH else Language.ENGLISH
            } else {
                defaultLang
            }
            _sourceLanguage.value = newSource
            _targetLanguage.value = newTarget
            sharedPrefs.edit().putString("last_source_language", newSource.code).putString("last_target_language", newTarget.code).apply()
        } else {
            _sourceLanguage.value = currentTarget
            _targetLanguage.value = currentSource
            sharedPrefs.edit().putString("last_source_language", currentTarget.code).putString("last_target_language", currentSource.code).apply()
        }
    }

    // Conversation Mode Types and States
    data class ConversationItem(
        val id: String = java.util.UUID.randomUUID().toString(),
        val originalText: String,
        val translatedText: String,
        val sourceLang: Language,
        val targetLang: Language,
        val speakerCode: String, // "left" or "right"
        val timestamp: Long = System.currentTimeMillis()
    )

    private val _conversationTranscript = MutableStateFlow<List<ConversationItem>>(emptyList())
    val conversationTranscript: StateFlow<List<ConversationItem>> = _conversationTranscript.asStateFlow()

    private val _isConversationProcessing = MutableStateFlow(false)
    val isConversationProcessing: StateFlow<Boolean> = _isConversationProcessing.asStateFlow()

    private val _conversationError = MutableStateFlow<String?>(null)
    val conversationError: StateFlow<String?> = _conversationError.asStateFlow()

    fun clearConversationTranscript() {
        _conversationTranscript.value = emptyList()
        _conversationError.value = null
    }

    fun dismissConversationError() {
        _conversationError.value = null
    }

    fun handleConversationSpeechReceived(
        spokenText: String,
        isLeft: Boolean,
        context: Context,
        speakOut: (String, String) -> Unit
    ) {
        if (spokenText.isBlank()) return
        
        _isConversationProcessing.value = true
        _conversationError.value = null

        val sourceLang = if (isLeft) _sourceLanguage.value else _targetLanguage.value
        val targetLang = if (isLeft) _targetLanguage.value else _sourceLanguage.value

        viewModelScope.launch {
            val rawApiKey = BuildConfig.GEMINI_API_KEY
            val apiKey = rawApiKey.trim().removeSurrounding("\"").removeSurrounding("'")

            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                _conversationError.value = "Gemini API Key is missing or invalid. Please configure it in the Secrets panel."
                _isConversationProcessing.value = false
                return@launch
            }

            try {
                var detectedLangLabel: String? = null
                var finalSourceLang: Language = sourceLang

                if (sourceLang == Language.AUTO) {
                    val detectPrompt = "You are a professional language identification expert. " +
                            "Analyze the text below and return ONLY the English name of the language (for example: 'Hindi', 'Marathi', 'French', 'Spanish', 'Japanese'). " +
                            "Do NOT include any extra explanations, introductory comments, conversational replies, notes, or punctuation.\n\n" +
                            "Text to identify:\n$spokenText"

                    val detectRequest = GenerateContentRequest(
                        contents = listOf(
                            Content(parts = listOf(Part(text = detectPrompt)))
                        ),
                        generationConfig = GenerationConfig(temperature = 0.1f)
                    )

                    val detectResponse = RetrofitClient.service.generateContent(apiKey, detectRequest)
                    val rawDetect = detectResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    if (!rawDetect.isNullOrBlank()) {
                        val trimmedDetect = rawDetect.trim().removeSurrounding("\"").removeSurrounding("'").trim()
                        detectedLangLabel = trimmedDetect
                        val resolved = Language.fromDisplayNameOrName(trimmedDetect)
                        if (resolved != null && resolved != Language.AUTO) {
                            finalSourceLang = resolved
                        }
                    }
                }

                val resolvedSourceName = detectedLangLabel ?: if (finalSourceLang == Language.AUTO) "English" else finalSourceLang.displayName
                val targetName = if (targetLang == Language.AUTO) "English" else targetLang.displayName
                val finalTargetLang = if (targetLang == Language.AUTO) Language.ENGLISH else targetLang

                val toneStr = when (_translationTone.value) {
                    TranslationTone.FORMAL -> " Use a formal tone: Use respectful, polite phrasing suitable for elders or official writing (e.g., 'आप' rather than 'तुम' in Hindi)."
                    TranslationTone.CASUAL -> " Use a casual tone: Use everyday, friendly phrasing suitable for texting a friend."
                }

                val prompt = "You are a professional, high-fidelity translator. " +
                        "Translate the text below from $resolvedSourceName to $targetName.$toneStr " +
                        "Do NOT include any extra explanations, introductory comments, conversational replies, or notes. " +
                        "Return ONLY the direct raw translated text.\n\n" +
                        "Text:\n$spokenText"

                val request = GenerateContentRequest(
                    contents = listOf(
                        Content(parts = listOf(Part(text = prompt)))
                    ),
                    generationConfig = GenerationConfig(
                        temperature = 0.2f
                    ),
                    systemInstruction = Content(
                        parts = listOf(Part(text = "You are a state-of-the-art exact translator. You only return the translated text without extra text, symbols, formatting wrappers, quotes or conversational fluff."))
                    )
                )

                val response = RetrofitClient.service.generateContent(apiKey, request)
                val rawTranslation = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text

                if (rawTranslation != null) {
                    val cleanTranslation = rawTranslation.trim().removeSurrounding("\"").removeSurrounding("'")
                    
                    val newItem = ConversationItem(
                        originalText = spokenText,
                        translatedText = cleanTranslation,
                        sourceLang = if (sourceLang == Language.AUTO) finalSourceLang else sourceLang,
                        targetLang = finalTargetLang,
                        speakerCode = if (isLeft) "left" else "right"
                    )

                    _conversationTranscript.value = _conversationTranscript.value + newItem
                    
                    val speakLangCode = finalTargetLang.code
                    speakOut(cleanTranslation, if (speakLangCode == "auto") "en" else speakLangCode)

                    repository.insert(
                        HistoryItem(
                            originalText = spokenText,
                            translatedText = cleanTranslation,
                            sourceLanguageCode = if (finalSourceLang == Language.AUTO) "en" else finalSourceLang.code,
                            targetLanguageCode = if (finalTargetLang == Language.AUTO) "en" else finalTargetLang.code
                        )
                    )
                } else {
                    _conversationError.value = "Failed to translate speech. Empty response from Gemini API."
                }
            } catch (e: Exception) {
                Log.e("TranslationViewModel", "Conversation translation error", e)
                _conversationError.value = "Error: ${e.localizedMessage}"
            } finally {
                _isConversationProcessing.value = false
            }
        }
    }

    fun handleConversationAutoDetectSpeech(
        spokenText: String,
        context: Context,
        speakOut: (String, String) -> Unit
    ) {
        if (spokenText.isBlank()) return
        
        _isConversationProcessing.value = true
        _conversationError.value = null

        val sourceLang = _sourceLanguage.value
        val targetLang = _targetLanguage.value

        viewModelScope.launch {
            val rawApiKey = BuildConfig.GEMINI_API_KEY
            val apiKey = rawApiKey.trim().removeSurrounding("\"").removeSurrounding("'")

            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                _conversationError.value = "Gemini API Key is missing or invalid. Please configure it in the Secrets panel."
                _isConversationProcessing.value = false
                return@launch
            }

            try {
                // First, identify which of the two languages was spoken
                val sourceName = if (sourceLang == Language.AUTO) "English" else sourceLang.displayName
                val targetName = if (targetLang == Language.AUTO) "English" else targetLang.displayName
                
                val detectPrompt = "You are a professional language identification expert. " +
                        "Is the following text more likely '$sourceName' or '$targetName'? " +
                        "Answer ONLY with the exact language name. No other text.\n\nText: ${spokenText}"

                val detectRequest = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = detectPrompt)))),
                    generationConfig = GenerationConfig(temperature = 0.1f)
                )

                val detectResponse = RetrofitClient.service.generateContent(apiKey, detectRequest)
                var detectedLanguageName = detectResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()?.removeSurrounding("\"")?.removeSurrounding("'") ?: sourceName

                // Map the detected name back to a boolean: isLeft (which means sourceLang)
                var isLeft = true
                if (detectedLanguageName.equals(targetName, ignoreCase = true)) {
                    isLeft = false
                }
                
                // Now translate from the detected language to the OTHER language
                val actualSource = if (isLeft) sourceLang else targetLang
                val actualTarget = if (isLeft) targetLang else sourceLang
                val actualSourceName = if (isLeft) sourceName else targetName
                val actualTargetName = if (isLeft) targetName else sourceName
                
                val finalActualSource = if (actualSource == Language.AUTO) Language.ENGLISH else actualSource
                val finalActualTarget = if (actualTarget == Language.AUTO) Language.ENGLISH else actualTarget

                val toneStrAuto = when (_translationTone.value) {
                    TranslationTone.FORMAL -> " Use a formal tone: Use respectful, polite phrasing suitable for elders or official writing (e.g., 'आप' rather than 'तुम' in Hindi)."
                    TranslationTone.CASUAL -> " Use a casual tone: Use everyday, friendly phrasing suitable for texting a friend."
                }

                val translatePrompt = "You are a professional, high-fidelity translator. " +
                        "Translate the text below from $actualSourceName to $actualTargetName.$toneStrAuto " +
                        "Do NOT include any extra explanations, introductory comments, conversational replies, or notes. " +
                        "Return ONLY the direct raw translated text.\n\n" +
                        "Text:\n$spokenText"

                val translateRequest = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = translatePrompt)))),
                    generationConfig = GenerationConfig(temperature = 0.2f),
                    systemInstruction = Content(parts = listOf(Part(text = "You are a state-of-the-art exact translator. You only return the translated text without extra text, symbols, formatting wrappers, quotes or conversational fluff.")))
                )

                val translateResponse = RetrofitClient.service.generateContent(apiKey, translateRequest)
                val rawTranslation = translateResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text

                if (rawTranslation != null) {
                    val cleanTranslation = rawTranslation.trim().removeSurrounding("\"").removeSurrounding("'")
                    
                    val newItem = ConversationItem(
                        originalText = spokenText,
                        translatedText = cleanTranslation,
                        sourceLang = finalActualSource,
                        targetLang = finalActualTarget,
                        speakerCode = if (isLeft) "left" else "right"
                    )

                    _conversationTranscript.value = _conversationTranscript.value + newItem
                    
                    val speakLangCode = finalActualTarget.code
                    speakOut(cleanTranslation, if (speakLangCode == "auto") "en" else speakLangCode)

                    repository.insert(
                        HistoryItem(
                            originalText = spokenText,
                            translatedText = cleanTranslation,
                            sourceLanguageCode = finalActualSource.code,
                            targetLanguageCode = finalActualTarget.code
                        )
                    )
                } else {
                    _conversationError.value = "Failed to translate speech. Empty response from Gemini API."
                }
            } catch (e: Exception) {
                Log.e("TranslationViewModel", "Conversation translation error", e)
                _conversationError.value = "Error: ${e.localizedMessage}"
            } finally {
                _isConversationProcessing.value = false
            }
        }
    }

    fun deleteHistoryItem(id: Int) {
        viewModelScope.launch {
            repository.delete(id)
        }
    }

    fun toggleFavorite(id: Int, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.updateFavorite(id, isFavorite)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    fun translate(context: Context) {
        val textToTranslate = _inputText.value.trim()
        val sourceLang = _sourceLanguage.value
        val targetLang = _targetLanguage.value
        val isCompare = _isCompareMode.value
        val compareLangs = _compareTargetLanguages.value

        // 1. Check if input is empty
        if (textToTranslate.isEmpty()) {
            _uiState.value = TranslationUiState.Error(
                message = "The text to translate cannot be empty.",
                isNoInternet = false
            )
            return
        }

        // 2. Check internet connection
        if (!isNetworkAvailable(context)) {
            Log.d("TranslationViewModel", "No internet connection. Performing local offline Room DB cache check...")
            _uiState.value = TranslationUiState.Loading
            viewModelScope.launch {
                try {
                    if (isCompare) {
                        val compareResultsList = mutableListOf<CompareResult>()
                        var anyFound = false
                        for (targetL in compareLangs) {
                            val cached = repository.findCachedTranslation(
                                originalText = textToTranslate,
                                sourceLanguageCode = sourceLang.code,
                                targetLanguageCode = targetL.code
                            )
                            if (cached != null) {
                                compareResultsList.add(
                                    CompareResult(
                                        targetLang = targetL,
                                        translatedText = cached.translatedText,
                                        transliteration = null,
                                        isFromCache = true
                                    )
                                )
                                anyFound = true
                            }
                        }
                        if (anyFound) {
                            Log.d("TranslationViewModel", "Loaded $compareResultsList from offline Room DB cache.")
                            val first = compareResultsList.firstOrNull()
                            _uiState.value = TranslationUiState.Success(
                                originalText = textToTranslate,
                                translatedText = first?.translatedText ?: "",
                                transliteration = first?.transliteration,
                                sourceLang = sourceLang,
                                targetLang = first?.targetLang ?: compareLangs.first(),
                                detectedLanguageName = null,
                                isFromCache = true,
                                isCompareMode = true,
                                compareResults = compareResultsList
                            )
                        } else {
                            Log.d("TranslationViewModel", "Offline cache empty. Showing network error.")
                            _uiState.value = TranslationUiState.Error(
                                message = "No internet connection and no previously cached translations found for these languages. Please connect to the internet and try again.",
                                isNoInternet = true
                            )
                        }
                    } else {
                        val cached = repository.findCachedTranslation(
                            originalText = textToTranslate,
                            sourceLanguageCode = sourceLang.code,
                            targetLanguageCode = targetLang.code
                        )
                        if (cached != null) {
                            Log.d("TranslationViewModel", "Offline cache hit found in Room DB: '${cached.translatedText}'")
                            val resolvedSourceLang = Language.fromCode(cached.sourceLanguageCode)
                            _uiState.value = TranslationUiState.Success(
                                originalText = cached.originalText,
                                translatedText = cached.translatedText,
                                sourceLang = resolvedSourceLang,
                                targetLang = Language.fromCode(cached.targetLanguageCode),
                                detectedLanguageName = if (sourceLang == Language.AUTO) resolvedSourceLang.displayName else null,
                                isFromCache = true
                            )
                        } else {
                            Log.d("TranslationViewModel", "Offline cache miss. Showing network error.")
                            _uiState.value = TranslationUiState.Error(
                                message = "No internet connection and no previously cached translation found for this text. Please connect to the internet and try again.",
                                isNoInternet = true
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TranslationViewModel", "Failed to query offline cache", e)
                    _uiState.value = TranslationUiState.Error(
                        message = "No internet connection. Offline database query failed: ${e.localizedMessage}",
                        isNoInternet = true
                    )
                }
            }
            return
        }

        // 3. Initiate Loading state
        _uiState.value = TranslationUiState.Loading

        viewModelScope.launch {
            val rawApiKey = BuildConfig.GEMINI_API_KEY
            val apiKey = rawApiKey.trim().removeSurrounding("\"").removeSurrounding("'")
            
            Log.d("TranslationViewModel", "Preparing translation request.")
            Log.d("TranslationViewModel", "- Source: ${sourceLang.displayName} (${sourceLang.code})")
            if (isCompare) {
                Log.d("TranslationViewModel", "- Targets: ${compareLangs.map { it.displayName }}")
            } else {
                Log.d("TranslationViewModel", "- Target: ${targetLang.displayName} (${targetLang.code})")
            }
            Log.d("TranslationViewModel", "- Text length: ${textToTranslate.length} characters")

            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                Log.e("TranslationViewModel", "Gemini API validation failed: API key is empty or holds the default placeholder.")
                _uiState.value = TranslationUiState.Error(
                    message = "Gemini API Key is missing or invalid. Please configure your GEMINI_API_KEY securely in the Secrets panel in AI Studio.",
                    isNoInternet = false
                )
                return@launch
            }

            Log.d("TranslationViewModel", "Authenticated local verification. API Key prefixed: ${if (apiKey.length > 5) apiKey.take(5) else "short"}...")

            try {
                var detectedLangLabel: String? = null
                var finalSourceLang: Language = sourceLang

                if (sourceLang == Language.AUTO) {
                    val detectPrompt = "You are a professional language identification expert. " +
                            "Analyze the text below and return ONLY the English name of the language (for example: 'Hindi', 'Marathi', 'French', 'Spanish', 'Japanese'). " +
                            "Do NOT include any extra explanations, introductory comments, conversational replies, notes, or punctuation.\n\n" +
                            "Text to identify:\n$textToTranslate"

                    val detectRequest = GenerateContentRequest(
                        contents = listOf(
                            Content(parts = listOf(Part(text = detectPrompt)))
                        ),
                        generationConfig = GenerationConfig(temperature = 0.1f)
                    )

                    Log.d("TranslationViewModel", "Executing preliminary language detection API Call...")
                    val detectResponse = RetrofitClient.service.generateContent(apiKey, detectRequest)
                    val rawDetect = detectResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    Log.d("TranslationViewModel", "Detected language response from Gemini: '$rawDetect'")
                    if (!rawDetect.isNullOrBlank()) {
                        val trimmedDetect = rawDetect.trim().removeSurrounding("\"").removeSurrounding("'").trim()
                        detectedLangLabel = trimmedDetect
                        val resolved = Language.fromDisplayNameOrName(trimmedDetect)
                        if (resolved != null && resolved != Language.AUTO) {
                            finalSourceLang = resolved
                        }
                    }
                }

                val sourceName = detectedLangLabel ?: if (sourceLang == Language.AUTO) "Auto-Detect" else sourceLang.displayName

                val toneStr = when (_translationTone.value) {
                    TranslationTone.FORMAL -> " Use a formal tone: Use respectful, polite phrasing suitable for elders or official writing (e.g., 'आप' rather than 'तुम' in Hindi)."
                    TranslationTone.CASUAL -> " Use a casual tone: Use everyday, friendly phrasing suitable for texting a friend."
                }

                val prompt = if (isCompare) {
                    val targetsStr = compareLangs.joinToString(", ") { "${it.displayName} (use exact language_code: '${it.code}')" }
                    "You are an elite, professional multilingual translator and transliterator.\n" +
                            "Translate the text below from $sourceName into the following target languages: $targetsStr.\n\n" +
                            "For each target language, you must provide:\n" +
                            "1. The direct translation (in its native script).$toneStr\n" +
                            "2. A Roman-script (Latin letters) transliteration or pronunciation guide of that translation (for example, below 'नमस्ते' show 'namaste').\n\n" +
                            "Return your output as a raw JSON object containing a JSON array called 'translations' with one item per target language. Each item in the array MUST contain exactly these keys:\n" +
                            "- 'language_code': the code of the target language (e.g. 'hi' for Hindi, 'fr' for French, 'es' for Spanish, etc.)\n" +
                            "- 'translation': the direct translated text in native script.\n" +
                            "- 'transliteration': the Romanized transliteration of that translation.\n\n" +
                            "Do NOT wrap the JSON response inside a markdown code block (like ```json). Respond with the raw JSON string and absolutely no extra text or explanations.\n\n" +
                            "Text to translate:\n$textToTranslate"
                } else {
                    val targetName = targetLang.displayName
                    "You are a professional, high-fidelity translator and transliterator.\n" +
                            "1. Translate the text below from $sourceName into $targetName (native script).$toneStr\n" +
                            "2. Provide a Roman-script (Latin letters) transliteration or pronunciation guide of the translated text (for example, below 'नमस्ते' show 'namaste').\n\n" +
                            "Return your output as a raw JSON object with exactly two keys:\n" +
                            "- 'translation': the direct native-script translated text.\n" +
                            "- 'transliteration': the Romanized transliteration of that translation.\n" +
                            "Do NOT wrap the JSON response inside a markdown code block (like ```json). Respond with the raw JSON string.\n\n" +
                            "Text to translate:\n$textToTranslate"
                }

                val systemInst = if (isCompare) {
                    "You are a state-of-the-art multilingual translator. You return translations in raw JSON format with a 'translations' key containing a list of objects, each having 'language_code', 'translation', and 'transliteration' fields. Do not output anything except the valid raw JSON object. Avoid markdown wrappers or block delimiters."
                } else {
                    "You are a state-of-the-art translator that returns translations in JSON format containing 'translation' and 'transliteration' fields. Do not output anything except the valid raw JSON object. Avoid any markdown delimiters like ```json or trailing text."
                }

                val request = GenerateContentRequest(
                    contents = listOf(
                        Content(parts = listOf(Part(text = prompt)))
                    ),
                    generationConfig = GenerationConfig(
                        temperature = 0.2f // low temperature for highly deterministic translations
                    ),
                    systemInstruction = Content(
                        parts = listOf(Part(text = systemInst))
                    )
                )

                Log.d("TranslationViewModel", "Executing Network API Call to Gemini...")
                val startTime = System.currentTimeMillis()
                val response = RetrofitClient.service.generateContent(apiKey, request)
                val duration = System.currentTimeMillis() - startTime
                Log.d("TranslationViewModel", "Network API Call responded successfully in ${duration}ms.")

                val rawTranslation = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                Log.d("TranslationViewModel", "Raw translation from Gemini API: '$rawTranslation'")
                
                if (rawTranslation != null) {
                    val cleanedResponse = rawTranslation.trim().removeSurrounding("```json").removeSurrounding("```").trim()
                    
                    if (isCompare) {
                        val compareResultsList = mutableListOf<CompareResult>()
                        try {
                            val json = JSONObject(cleanedResponse)
                            val array = json.optJSONArray("translations")
                            if (array != null) {
                                for (i in 0 until array.length()) {
                                    val obj = array.getJSONObject(i)
                                    val code = obj.optString("language_code", "")
                                    val translation = obj.optString("translation", "").trim()
                                    val transliteration = obj.optString("transliteration", "").trim()
                                    
                                    val matchedLang = Language.fromCode(code)
                                    if (translation.isNotEmpty()) {
                                        compareResultsList.add(
                                            CompareResult(
                                                targetLang = matchedLang,
                                                translatedText = translation,
                                                transliteration = if (transliteration.isEmpty()) null else transliteration
                                            )
                                        )
                                    }
                                }
                            } else {
                                // Fallback: try parsing direct single translation structure just in case
                                val singleTranslation = json.optString("translation", "").trim()
                                val singleTransliteration = json.optString("transliteration", "").trim()
                                if (singleTranslation.isNotEmpty()) {
                                    compareResultsList.add(
                                        CompareResult(
                                            targetLang = compareLangs.first(),
                                            translatedText = singleTranslation,
                                            transliteration = if (singleTransliteration.isEmpty()) null else singleTransliteration
                                        )
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("TranslationViewModel", "Failed to parse JSON response for Compare Mode, falling back to basic single parse", e)
                            // fallback try single
                            try {
                                val json = JSONObject(cleanedResponse)
                                val translation = json.optString("translation", "").trim()
                                val transliteration = json.optString("transliteration", "").trim()
                                if (translation.isNotEmpty()) {
                                    compareResultsList.add(
                                        CompareResult(
                                            targetLang = compareLangs.first(),
                                            translatedText = translation,
                                            transliteration = if (transliteration.isEmpty()) null else transliteration
                                        )
                                    )
                                }
                            } catch (ex2: Exception) {
                                // direct text fallback
                                compareResultsList.add(
                                    CompareResult(
                                        targetLang = compareLangs.first(),
                                        translatedText = rawTranslation.trim().removeSurrounding("\"").removeSurrounding("'")
                                    )
                                )
                            }
                        }

                        // Ensure we have at least one valid comparison result
                        if (compareResultsList.isEmpty()) {
                            _uiState.value = TranslationUiState.Error(
                                message = "Compare translation failed: Received invalid JSON response.",
                                isNoInternet = false
                            )
                            return@launch
                        }

                        val firstResult = compareResultsList.first()
                        _uiState.value = TranslationUiState.Success(
                            originalText = textToTranslate,
                            translatedText = firstResult.translatedText,
                            transliteration = firstResult.transliteration,
                            sourceLang = sourceLang,
                            targetLang = firstResult.targetLang,
                            detectedLanguageName = detectedLangLabel,
                            isCompareMode = true,
                            compareResults = compareResultsList,
                            latencyMs = duration
                        )

                        // Save translations to history database
                        compareResultsList.forEach { valRes ->
                            repository.insert(
                                HistoryItem(
                                    originalText = textToTranslate,
                                    translatedText = valRes.translatedText,
                                    sourceLanguageCode = finalSourceLang.code,
                                    targetLanguageCode = valRes.targetLang.code
                                )
                            )
                        }
                        Log.d("TranslationViewModel", "All Compare Translations saved in Local DB history successfully.")

                    } else {
                        var cleanTranslation = ""
                        var transliterationText: String? = null

                        try {
                            val json = JSONObject(cleanedResponse)
                            cleanTranslation = json.optString("translation", "").trim()
                            transliterationText = json.optString("transliteration", "").trim()
                            if (transliterationText.isEmpty()) {
                                transliterationText = null
                            }
                        } catch (e: Exception) {
                            Log.e("TranslationViewModel", "Failed to parse JSON response, falling back", e)
                            cleanTranslation = rawTranslation.trim().removeSurrounding("\"").removeSurrounding("'")
                        }

                        _uiState.value = TranslationUiState.Success(
                            originalText = textToTranslate,
                            translatedText = cleanTranslation,
                            transliteration = transliterationText,
                            sourceLang = sourceLang,
                            targetLang = targetLang,
                            detectedLanguageName = detectedLangLabel,
                            latencyMs = duration
                        )

                        // Save translation to history database
                        repository.insert(
                            HistoryItem(
                                originalText = textToTranslate,
                                translatedText = cleanTranslation,
                                sourceLanguageCode = finalSourceLang.code,
                                targetLanguageCode = targetLang.code
                            )
                        )
                        Log.d("TranslationViewModel", "Translation saved in Local DB history successfully.")
                    }
                } else {
                    Log.e("TranslationViewModel", "No candidates/parts found in the response body.")
                    _uiState.value = TranslationUiState.Error(
                        message = "Failed to translate: Received an empty payload from Gemini API.",
                        isNoInternet = false
                    )
                }
            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                Log.e("TranslationViewModel", "Gemini API HTTP Translation Exception (Code: ${e.code()})", e)
                Log.e("TranslationViewModel", "HTTP Exception Error Body: $errorBody")
                val detailedMessage = parseGeminiError(errorBody)
                _uiState.value = TranslationUiState.Error(
                    message = "Translation failed (HTTP Class ${e.code()}): $detailedMessage",
                    isNoInternet = false
                )
            } catch (e: Exception) {
                Log.e("TranslationViewModel", "Unexpected exception during translation task execution", e)
                val errorMsg = e.localizedMessage ?: e.message ?: "An unexpected error occurred during translation."
                _uiState.value = TranslationUiState.Error(
                    message = "Translation failed: $errorMsg",
                    isNoInternet = false
                )
            }
        }
    }

    private fun parseGeminiError(errorJson: String?): String {
        if (errorJson.isNullOrBlank()) return "No error body reason sent by server."
        return try {
            val jsonObject = JSONObject(errorJson)
            val errorObj = jsonObject.optJSONObject("error")
            val message = errorObj?.optString("message")
            if (!message.isNullOrBlank()) {
                message
            } else {
                errorJson
            }
        } catch (_: Exception) {
            errorJson
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> true
                else -> false
            }
        } catch (e: Exception) {
            true // fallback to true to prevent crashes and let the network call attempt itself
        }
    }

    fun resetCameraState() {
        _cameraUiState.value = CameraUiState.Idle
    }

    fun saveCameraResultToHistory(original: String, translation: String, sourceLang: Language, targetLang: Language) {
        viewModelScope.launch {
            repository.insert(
                HistoryItem(
                    originalText = original,
                    translatedText = translation,
                    sourceLanguageCode = sourceLang.code,
                    targetLanguageCode = targetLang.code
                )
            )
            Log.d("TranslationViewModel", "Camera translation saved to history.")
        }
    }

    fun translateImage(context: Context, imageUri: Uri, sourceLang: Language, targetLang: Language) {
        if (!isNetworkAvailable(context)) {
            _cameraUiState.value = CameraUiState.Error("No internet connection. Please check your connectivity.")
            return
        }

        _cameraUiState.value = CameraUiState.Loading

        viewModelScope.launch {
            val rawApiKey = BuildConfig.GEMINI_API_KEY
            val apiKey = rawApiKey.trim().removeSurrounding("\"").removeSurrounding("'")

            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                _cameraUiState.value = CameraUiState.Error("Gemini API Key is missing. Please configure it in the Secrets panel in AI Studio.")
                return@launch
            }

            // Convert and resize Bitmap
            val base64Data = try {
                val inputStream = context.contentResolver.openInputStream(imageUri)
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (originalBitmap == null) {
                    _cameraUiState.value = CameraUiState.Error("Failed to decode captured image.")
                    return@launch
                }

                // Resize
                val maxDim = 1024
                val width = originalBitmap.width
                val height = originalBitmap.height
                val resizedBitmap = if (width > maxDim || height > maxDim) {
                    val ratio = width.toFloat() / height.toFloat()
                    val (newWidth, newHeight) = if (ratio > 1) {
                        maxDim to (maxDim / ratio).toInt()
                    } else {
                        (maxDim * ratio).toInt() to maxDim
                    }
                    Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
                } else {
                    originalBitmap
                }

                val bos = ByteArrayOutputStream()
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, bos)
                val bytes = bos.toByteArray()
                android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            } catch (e: Exception) {
                Log.e("TranslationViewModel", "Error processing captured image", e)
                _cameraUiState.value = CameraUiState.Error("Failed to process image: ${e.localizedMessage}")
                return@launch
            }

            if (base64Data == null) {
                _cameraUiState.value = CameraUiState.Error("Failed to encode image data.")
                return@launch
            }

            try {
                var detectedLangLabel: String? = null
                var finalSourceLang: Language = sourceLang

                if (sourceLang == Language.AUTO) {
                    val detectPromptText = "Identify the primary language of the text in this image. " +
                            "Return ONLY the English name of the language (for example: 'English', 'French', 'Spanish', 'Japanese', 'Hindi', 'Marathi'). " +
                            "If there is no text or the language cannot be identified, return 'English' as a fallback. " +
                            "Do NOT include any extra explanations, introductory comments, conversational replies, notes, or punctuation."

                    val inlineDataForDetect = InlineData(mimeType = "image/jpeg", data = base64Data)
                    val detectRequest = GenerateContentRequest(
                        contents = listOf(
                            Content(
                                parts = listOf(
                                    Part(text = detectPromptText),
                                    Part(inlineData = inlineDataForDetect)
                                )
                            )
                        ),
                        generationConfig = GenerationConfig(temperature = 0.1f)
                    )

                    Log.d("TranslationViewModel", "Sending preliminary image language detection request to Gemini Api...")
                    val detectResponse = RetrofitClient.service.generateContent(apiKey, detectRequest)
                    val rawDetect = detectResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    Log.d("TranslationViewModel", "Detected image language response from Gemini: '$rawDetect'")
                    if (!rawDetect.isNullOrBlank()) {
                        val trimmedDetect = rawDetect.trim().removeSurrounding("\"").removeSurrounding("'").trim()
                        detectedLangLabel = trimmedDetect
                        val resolved = Language.fromDisplayNameOrName(trimmedDetect)
                        if (resolved != null && resolved != Language.AUTO) {
                            finalSourceLang = resolved
                        }
                    }
                }

                val sourceName = detectedLangLabel ?: if (sourceLang == Language.AUTO) "Auto-Detect" else sourceLang.displayName
                val targetName = targetLang.displayName

                val toneStr = when (_translationTone.value) {
                    TranslationTone.FORMAL -> " Use a formal tone: Use respectful, polite phrasing suitable for elders or official writing (e.g., 'आप' rather than 'तुम' in Hindi)."
                    TranslationTone.CASUAL -> " Use a casual tone: Use everyday, friendly phrasing suitable for texting a friend."
                }

                val promptText = "You are a professional camera translation assistant. Analyze the text in the provided image.\n" +
                        "1. Detect and extract all text from the image, preserving lines and structure if appropriate. Keep this as 'original'.\n" +
                        "2. Translate that exact text into $targetName (the source language is $sourceName).$toneStr\n" +
                        "Return your output as a raw JSON object with exactly two keys: 'original' and 'translation'. Do not wrap the JSON in ```json markdown blocks."

                val inlineData = InlineData(mimeType = "image/jpeg", data = base64Data)
                val request = GenerateContentRequest(
                    contents = listOf(
                        Content(
                            parts = listOf(
                                Part(text = promptText),
                                Part(inlineData = inlineData)
                            )
                        )
                    ),
                    generationConfig = GenerationConfig(temperature = 0.2f)
                )

                Log.d("TranslationViewModel", "Sending image request to Gemini Api...")
                val response = RetrofitClient.service.generateContent(apiKey, request)
                val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text

                Log.d("TranslationViewModel", "Received image translation from Gemini: $responseText")

                if (!responseText.isNullOrBlank()) {
                    val cleanedResponse = responseText.trim().removeSurrounding("```json").removeSurrounding("```").trim()
                    val json = JSONObject(cleanedResponse)
                    val originalValue = json.optString("original", "").trim()
                    val translationValue = json.optString("translation", "").trim()

                    if (originalValue.isNotEmpty() || translationValue.isNotEmpty()) {
                        _cameraUiState.value = CameraUiState.Success(
                            imageUri = imageUri,
                            originalText = originalValue,
                            translatedText = translationValue,
                            sourceLang = sourceLang,
                            targetLang = targetLang,
                            detectedLanguageName = detectedLangLabel
                        )
                    } else {
                        _cameraUiState.value = CameraUiState.Error("No text found or analyzed in the image. Please try a clearer picture.")
                    }
                } else {
                    _cameraUiState.value = CameraUiState.Error("Gemini API replied with an empty response.")
                }
            } catch (e: HttpException) {
                val errBody = e.response()?.errorBody()?.string()
                val detailedMsg = parseGeminiError(errBody)
                _cameraUiState.value = CameraUiState.Error("Gemini analysis failed: $detailedMsg")
            } catch (e: Exception) {
                _cameraUiState.value = CameraUiState.Error("Unexpected error during analysis: ${e.localizedMessage}")
            }
        }
    }

    fun resetAudioState() {
        _audioUiState.value = AudioUiState.Idle
    }

    fun setAudioRecordingState() {
        _audioUiState.value = AudioUiState.Recording
    }

    fun translateAudio(context: Context, audioUri: Uri, sourceLang: Language, targetLang: Language) {
        if (!isNetworkAvailable(context)) {
            _audioUiState.value = AudioUiState.Error("No internet connection. Please check your connectivity.")
            return
        }

        _audioUiState.value = AudioUiState.Loading

        viewModelScope.launch {
            val rawApiKey = BuildConfig.GEMINI_API_KEY
            val apiKey = rawApiKey.trim().removeSurrounding("\"").removeSurrounding("'")

            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                _audioUiState.value = AudioUiState.Error("Gemini API Key is missing. Please configure it in the Secrets panel in AI Studio.")
                return@launch
            }

            // Read audio file
            val base64Data = try {
                val inputStream = context.contentResolver.openInputStream(audioUri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                if (bytes == null) {
                    _audioUiState.value = AudioUiState.Error("Failed to read audio file.")
                    return@launch
                }
                android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            } catch (e: Exception) {
                Log.e("TranslationViewModel", "Error reading audio file", e)
                _audioUiState.value = AudioUiState.Error("Failed to process audio: ${e.localizedMessage}")
                return@launch
            }

            try {
                var detectedLangLabel: String? = null
                val sourceName = if (sourceLang == Language.AUTO) {
                    detectedLangLabel = "Auto-Detect"
                    "its detected spoken language"
                } else {
                    sourceLang.displayName
                }
                val targetName = targetLang.displayName

                val toneStr = when (_translationTone.value) {
                    TranslationTone.FORMAL -> " Use a formal tone."
                    TranslationTone.CASUAL -> " Use a casual tone."
                }

                val promptText = "You are a professional audio transcription and translation assistant. Listen to the provided audio.\n" +
                        "1. Transcribe the speech accurately into its original language ($sourceName). Keep this as 'original'.\n" +
                        "2. Translate the transcription into $targetName.$toneStr\n" +
                        "Return your output as a raw JSON object with exactly two keys: 'original' and 'translation'. Do not wrap the JSON in ```json markdown blocks."

                val mimeType = context.contentResolver.getType(audioUri) ?: "audio/mp3" // Defaulting to mp3 if not available
                val inlineData = InlineData(mimeType = mimeType, data = base64Data)
                val parts = listOf(Part(text = promptText), Part(inlineData = inlineData))
                val content = Content(parts = parts)
                val request = GenerateContentRequest(contents = listOf(content))

                val response = RetrofitClient.service.generateContent(apiKey, request)
                val rawResponseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text

                if (rawResponseText != null) {
                    val cleanedResponse = rawResponseText.trim().removePrefix("```json").removeSuffix("```").trim()

                    val json = JSONObject(cleanedResponse)
                    val originalValue = json.optString("original", "").trim()
                    val translationValue = json.optString("translation", "").trim()

                    if (originalValue.isNotEmpty() || translationValue.isNotEmpty()) {
                        _audioUiState.value = AudioUiState.Success(
                            audioUri = audioUri,
                            originalText = originalValue,
                            translatedText = translationValue,
                            sourceLang = sourceLang,
                            targetLang = targetLang,
                            detectedLanguageName = detectedLangLabel
                        )
                    } else {
                        _audioUiState.value = AudioUiState.Error("No transcript found in the audio. Please try another recording.")
                    }
                } else {
                    _audioUiState.value = AudioUiState.Error("Gemini API replied with an empty response.")
                }
            } catch (e: HttpException) {
                val errBody = e.response()?.errorBody()?.string()
                val detailedMsg = parseGeminiError(errBody)
                _audioUiState.value = AudioUiState.Error("Gemini analysis failed: $detailedMsg")
            } catch (e: Exception) {
                _audioUiState.value = AudioUiState.Error("Unexpected error during analysis: ${e.localizedMessage}")
            }
        }
    }
}

