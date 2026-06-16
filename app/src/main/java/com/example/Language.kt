package com.example

enum class Language(val displayName: String, val code: String, val flag: String) {
    AUTO("Auto-Detect", "auto", "🔍"),
    ENGLISH("English", "en", "🇺🇸"),
    HINDI("Hindi", "hi", "🇮🇳"),
    SPANISH("Spanish", "es", "🇪🇸"),
    FRENCH("French", "fr", "🇫🇷"),
    GERMAN("German", "de", "🇩🇪"),
    JAPANESE("Japanese", "ja", "🇯🇵"),
    ARABIC("Arabic", "ar", "🇸🇦"),
    CHINESE("Chinese", "zh", "🇨🇳"),
    PORTUGUESE("Portuguese", "pt", "🇵🇹"),
    RUSSIAN("Russian", "ru", "🇷🇺")
}
