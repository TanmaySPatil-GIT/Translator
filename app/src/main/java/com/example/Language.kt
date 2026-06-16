package com.example

enum class Language(val displayName: String, val code: String, val flag: String) {
    AUTO("Auto-Detect", "auto", "🔍"),
    ENGLISH("English", "en", "🇺🇸"),
    HINDI("Hindi (हिन्दी)", "hi", "🇮🇳"),
    SPANISH("Spanish", "es", "🇪🇸"),
    FRENCH("French", "fr", "🇫🇷"),
    GERMAN("German", "de", "🇩🇪"),
    JAPANESE("Japanese", "ja", "🇯🇵"),
    ARABIC("Arabic", "ar", "🇸🇦"),
    CHINESE("Chinese", "zh", "🇨🇳"),
    PORTUGUESE("Portuguese", "pt", "🇵🇹"),
    RUSSIAN("Russian", "ru", "🇷🇺"),
    
    // Indian regional languages
    BENGALI("Bengali (বাংলা)", "bn", "🇮🇳"),
    MARATHI("Marathi (मराठी)", "mr", "🇮🇳"),
    TELUGU("Telugu (తెలుగు)", "te", "🇮🇳"),
    TAMIL("Tamil (தமிழ்)", "ta", "🇮🇳"),
    GUJARATI("Gujarati (ગુજરાતી)", "gu", "🇮🇳"),
    URDU("Urdu (اردو)", "ur", "🇮🇳"),
    KANNADA("Kannada (ಕನ್ನಡ)", "kn", "🇮🇳"),
    ODIA("Odia (ଓଡ଼ିଆ)", "or", "🇮🇳"),
    PUNJABI("Punjabi (ਪੰਜਾਬੀ)", "pa", "🇮🇳"),
    MALAYALAM("Malayalam (മലയാളം)", "ml", "🇮🇳"),
    ASSAMESE("Assamese (অসমীয়া)", "as", "🇮🇳"),
    MAITHILI("Maithili (मैथिली)", "mai", "🇮🇳"),
    KONKANI("Konkani (कोंकणी)", "kok", "🇮🇳"),
    SANSKRIT("Sanskrit (संस्कृतम्)", "sa", "🇮🇳"),
    NEPALI("Nepali (नेपाली)", "ne", "🇳🇵"),
    SINDHI("Sindhi (سنڌي)", "sd", "🇮🇳"),
    KASHMIRI("Kashmiri (کأशुर)", "ks", "🇮🇳"),
    DOGRI("Dogri (डोगरी)", "doi", "🇮🇳"),
    MANIPURI("Manipuri (মৈতৈলোন)", "mni", "🇮🇳"),
    BODO("Bodo (बर')", "brx", "🇮🇳"),
    SANTALI("Santali (ᱥᱟᱱᱛᱟᱲᱤ)", "sat", "🇮🇳");

    val isIndian: Boolean
        get() = when (this) {
            HINDI, BENGALI, MARATHI, TELUGU, TAMIL, GUJARATI, URDU, KANNADA, ODIA, PUNJABI, MALAYALAM,
            ASSAMESE, MAITHILI, KONKANI, SANSKRIT, NEPALI, SINDHI, KASHMIRI, DOGRI, MANIPURI, BODO, SANTALI -> true
            else -> false
        }

    companion object {
        fun fromCode(code: String): Language {
            return values().find { it.code == code } ?: AUTO
        }

        fun fromDisplayNameOrName(name: String): Language? {
            val clean = name.lowercase().trim()
            return values().find { 
                it.name.lowercase() == clean || 
                it.displayName.lowercase() == clean ||
                it.displayName.lowercase().substringBefore(" (").trim() == clean
            } ?: values().find {
                val cleanName = it.displayName.lowercase().substringBefore(" (").trim()
                clean.contains(cleanName) || cleanName.contains(clean)
            }
        }
    }
}
