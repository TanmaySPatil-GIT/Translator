package com.example

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.Content
import com.example.api.GenerateContentRequest
import com.example.api.GenerationConfig
import com.example.api.Part
import com.example.api.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TranslationUiState {
    object Idle : TranslationUiState
    object Loading : TranslationUiState
    data class Success(
        val originalText: String,
        val translatedText: String,
        val sourceLang: Language,
        val targetLang: Language
    ) : TranslationUiState
    data class Error(
        val message: String,
        val isNoInternet: Boolean = false
    ) : TranslationUiState
}

class TranslationViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<TranslationUiState>(TranslationUiState.Idle)
    val uiState: StateFlow<TranslationUiState> = _uiState.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _sourceLanguage = MutableStateFlow(Language.AUTO)
    val sourceLanguage: StateFlow<Language> = _sourceLanguage.asStateFlow()

    private val _targetLanguage = MutableStateFlow(Language.ENGLISH)
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
    }

    fun setTargetLanguage(lang: Language) {
        _targetLanguage.value = lang
    }

    fun swapLanguages() {
        val currentSource = _sourceLanguage.value
        val currentTarget = _targetLanguage.value
        
        // If current source is AUTO-DETECT, we cannot assign it to Target (which needs a specific language),
        // so we default Target to English and Source to the previous Target.
        if (currentSource == Language.AUTO) {
            _sourceLanguage.value = currentTarget
            _targetLanguage.value = Language.ENGLISH
        } else {
            _sourceLanguage.value = currentTarget
            _targetLanguage.value = currentSource
        }
    }

    fun translate(context: Context) {
        val textToTranslate = _inputText.value.trim()
        val sourceLang = _sourceLanguage.value
        val targetLang = _targetLanguage.value

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
            _uiState.value = TranslationUiState.Error(
                message = "No internet connection. Please connect your device and try again.",
                isNoInternet = true
            )
            return
        }

        // 3. Initiate Loading state
        _uiState.value = TranslationUiState.Loading

        viewModelScope.launch {
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                    _uiState.value = TranslationUiState.Error(
                        message = "Gemini API Key is missing or invalid. Please configure your GEMINI_API_KEY securely in the Secrets panel in AI Studio.",
                        isNoInternet = false
                    )
                    return@launch
                }

                // Build a very strict instruction prompt to translate perfectly without preamble
                val sourceName = if (sourceLang == Language.AUTO) "Auto-Detect" else sourceLang.displayName
                val targetName = targetLang.displayName

                val prompt = if (sourceLang == Language.AUTO) {
                    "You are a professional, high-fidelity translator. " +
                    "Analyze and translate the text below into $targetName. " +
                    "Do NOT include any extra explanations, introductory comments, conversational replies, or notes. " +
                    "Return ONLY the direct raw translated text.\n\n" +
                    "Text:\n$textToTranslate"
                } else {
                    "You are a professional, high-fidelity translator. " +
                    "Translate the text below from $sourceName to $targetName. " +
                    "Do NOT include any extra explanations, introductory comments, conversational replies, or notes. " +
                    "Return ONLY the direct raw translated text.\n\n" +
                    "Text:\n$textToTranslate"
                }

                val request = GenerateContentRequest(
                    contents = listOf(
                        Content(parts = listOf(Part(text = prompt)))
                    ),
                    generationConfig = GenerationConfig(
                        temperature = 0.2f // low temperature for highly deterministic translations
                    ),
                    systemInstruction = Content(
                        parts = listOf(Part(text = "You are a state-of-the-art exact translator. You only return the translated text without extra text, symbols, formatting wrappers, quotes or conversational fluff."))
                    )
                )

                val response = RetrofitClient.service.generateContent(apiKey, request)
                val rawTranslation = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                
                if (rawTranslation != null) {
                    val cleanTranslation = rawTranslation.trim().removeSurrounding("\"").removeSurrounding("'")
                    _uiState.value = TranslationUiState.Success(
                        originalText = textToTranslate,
                        translatedText = cleanTranslation,
                        sourceLang = sourceLang,
                        targetLang = targetLang
                    )
                } else {
                    _uiState.value = TranslationUiState.Error(
                        message = "Failed to translate: Received an empty payload from Gemini API.",
                        isNoInternet = false
                    )
                }
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: e.message ?: "An unexpected error occurred during translation."
                _uiState.value = TranslationUiState.Error(
                    message = "Translation failed: $errorMsg",
                    isNoInternet = false
                )
            }
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }
}
