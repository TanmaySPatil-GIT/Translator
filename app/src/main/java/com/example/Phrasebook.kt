package com.example

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

object PhrasebookData {
    val categories = listOf(
        "Greetings" to listOf("Hello", "Good morning", "Good evening", "How are you?", "Nice to meet you", "Goodbye"),
        "Directions" to listOf("Where is the bathroom?", "How do I get to...", "I am lost", "Turn left", "Turn right", "Go straight"),
        "Ordering Food" to listOf("A table for two, please", "Can I have the menu?", "Check, please", "Water, please", "Do you have vegetarian food?", "It is delicious"),
        "Numbers & More" to listOf("How much does this cost?", "One", "Two", "Three", "Please", "Thank you"),
        "Emergencies" to listOf("Help!", "I need a doctor", "Call the police", "I lost my passport", "Where is the hospital?", "I do not understand")
    )
    
    val allPhrases = categories.flatMap { it.second }

    // Hardcoded translations for the 30 phrases
    fun getTranslation(phraseIndex: Int, langCode: String): String {
        val mapped = phraseTranslationMap[langCode]
        if (mapped != null && phraseIndex < mapped.size && mapped[phraseIndex].isNotBlank()) {
            return mapped[phraseIndex]
        }
        return allPhrases[phraseIndex]
    }

    private val phraseTranslationMap = mapOf(
        "es" to arrayOf("Hola", "Buenos días", "Buenas tardes", "¿Cómo estás?", "Mucho gusto", "Adiós", "¿Dónde está el baño?", "¿Cómo llego a...", "Estoy perdido", "Gire a la izquierda", "Gire a la derecha", "Vaya derecho", "Una mesa para dos, por favor", "¿Me puede dar el menú?", "La cuenta, por favor", "Agua, por favor", "¿Tiene comida vegetariana?", "Está delicioso", "¿Cuánto cuesta esto?", "Uno", "Dos", "Tres", "Por favor", "Gracias", "¡Ayuda!", "Necesito un médico", "Llame a la policía", "Perdí mi pasaporte", "¿Dónde está el hospital?", "No entiendo"),
        "fr" to arrayOf("Bonjour", "Bonjour", "Bonsoir", "Comment ça va ?", "Enchanté", "Au revoir", "Où sont les toilettes ?", "Comment aller à...", "Je suis perdu", "Tournez à gauche", "Tournez à droite", "Allez tout droit", "Une table pour deux, s'il vous plaît", "Puis-je avoir le menu ?", "L'addition, s'il vous plaît", "De l'eau, s'il vous plaît", "Avez-vous des plats végétariens ?", "C'est délicieux", "Combien ça coûte ?", "Un", "Deux", "Trois", "S'il vous plaît", "Merci", "À l'aide !", "J'ai besoin d'un médecin", "Appelez la police", "J'ai perdu mon passeport", "Où est l'hôpital ?", "Je ne comprends pas"),
        "de" to arrayOf("Hallo", "Guten Morgen", "Guten Abend", "Wie geht es dir?", "Freut mich, dich kennenzulernen", "Auf Wiedersehen", "Wo ist die Toilette?", "Wie komme ich nach...", "Ich habe mich verirrt", "Biegen Sie links ab", "Biegen Sie rechts ab", "Gehen Sie geradeaus", "Einen Tisch für zwei, bitte", "Kann ich die Speisekarte haben?", "Die Rechnung, bitte", "Wasser, bitte", "Haben Sie vegetarisches Essen?", "Es ist lecker", "Wie viel kostet das?", "Eins", "Zwei", "Drei", "Bitte", "Danke", "Hilfe!", "Ich brauche einen Arzt", "Rufen Sie die Polizei", "Ich habe meinen Reisepass verloren", "Wo ist das Krankenhaus?", "Ich verstehe nicht"),
        "hi" to arrayOf("नमस्ते", "सुप्रभात", "शुभ संध्या", "आप कैसे हैं?", "आपसे मिलकर अच्छा लगा", "अलविदा", "बाथरूम कहाँ है?", "मैं ... तक कैसे पहुंचूं", "मैं खो गया हूँ", "बाएं मुड़ें", "दाएं मुड़ें", "सीधे जाएं", "कृपया दो लोगों के लिए एक मेज", "क्या मुझे मेनू मिल सकता है?", "बिल लाना, कृपया", "पानी, कृपया", "क्या आपके पास शाकाहारी भोजन है?", "यह स्वादिष्ट है", "इसकी कीमत क्या है?", "एक", "दो", "तीन", "कृपया", "धन्यवाद", "मदद!", "मुझे एक डॉक्टर चाहिए", "पुलिस को बुलाओ", "मैंने अपना पासपोर्ट खो दिया है", "अस्पताल कहाँ है?", "मुझे समझ नहीं आ रहा है"),
        "ja" to arrayOf("こんにちは", "おはようございます", "こんばんは", "お元気ですか？", "はじめまして", "さようなら", "トイレはどこですか？", "...へはどう行けばいいですか？", "道に迷いました", "左に曲がってください", "右に曲がってください", "まっすぐ進んでください", "2人用の席をお願いします", "メニューをもらえますか？", "お会計をお願いします", "お水をお願いします", "ベジタリアン料理はありますか？", "とても美味しいです", "これはいくらですか？", "一", "二", "三", "お願いします", "ありがとうございます", "助けて！", "医者を呼んでください", "警察を呼んでください", "パスポートをなくしました", "病院はどこですか？", "わかりません"),
        "zh" to arrayOf("你好", "早上好", "晚上好", "你好吗？", "很高兴认识你", "再见", "洗手间在哪里？", "我该怎么去...", "我迷路了", "向左转", "向右转", "直走", "请准备两个人的桌子", "可以给我看菜单吗？", "买单，谢谢", "请给我水", "你们有素食吗？", "很好吃", "这个多少钱？", "一", "二", "三", "请", "谢谢", "救命！", "我需要医生", "叫警察", "我丢了护照", "医院在哪里？", "我不明白"),
        "ar" to arrayOf("مرحباً", "صباح الخير", "مساء الخير", "كيف حالك؟", "سعدت بلقائك", "وداعاً", "أين الحمام؟", "كيف أصل إلى...", "أنا ضائع", "انعطف يساراً", "انعطف يميناً", "اذهب بشكل مستقيم", "طاولة لشخصين، من فضلك", "هل يمكنني الحصول على القائمة؟", "الحساب، من فضلك", "ماء، من فضلك", "هل لديكم طعام نباتي؟", "إنه لذيذ", "كم سعر هذا؟", "واحد", "اثنان", "ثلاثة", "من فضلك", "شكراً لك", "النجدة!", "أحتاج إلى طبيب", "اتصل بالشرطة", "لقد فقدت جواز سفري", "أين المستشفى؟", "أنا لا أفهم"),
        "ru" to arrayOf("Здравствуйте", "Доброе утро", "Добрый вечер", "Как дела?", "Приятно познакомиться", "До свидания", "Где здесь туалет?", "Как мне добраться до...", "Я заблудился", "Поверните налево", "Поверните направо", "Идите прямо", "Столик на двоих, пожалуйста", "Можно мне меню?", "Счет, пожалуйста", "Воды, пожалуйста", "У вас есть вегетарианская еда?", "Это вкусно", "Сколько это стоит?", "Один", "Два", "Три", "Пожалуйста", "Спасибо", "Помогите!", "Мне нужен врач", "Вызовите полицию", "Я потерял свой паспорт", "Где находится больница?", "Я не понимаю"),
        "pt" to arrayOf("Olá", "Bom dia", "Boa noite", "Como está?", "Prazer em conhecer", "Adeus", "Onde é a casa de banho?", "Como chego a...", "Estou perdido", "Vire à esquerda", "Vire à direita", "Siga em frente", "Uma mesa para dois, por favor", "Pode-me dar a ementa?", "A conta, por favor", "Água, por favor", "Têm comida vegetariana?", "Está delicioso", "Quanto custa isto?", "Um", "Dois", "Três", "Por favor", "Obrigado", "Socorro!", "Preciso de um médico", "Chame a polícia", "Perdi o meu passaporte", "Onde fica o hospital?", "Não entendo"),
        // Including a fallback default mapping for all other languages, which is just english
        "auto" to allPhrases.toTypedArray(),
        "en" to allPhrases.toTypedArray()
    )
}

@Composable
fun PhrasebookLayout(
    viewModel: TranslationViewModel,
    fontSizeScale: Float,
    speakOut: (String, String) -> Unit,
    targetLanguage: Language
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val context = LocalContext.current
    var expandedCategory by remember { mutableStateOf<String?>(PhrasebookData.categories.first().first) }
    val favorites by viewModel.favoriteHistoryState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.List,
                contentDescription = "Phrasebook",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Offline Phrasebook",
                fontSize = (20 * fontSizeScale).sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Text(
            text = "Tap the play buttons to hear the phrase aloud. Translation shown in ${targetLanguage.displayName}.",
            fontSize = (13 * fontSizeScale).sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 4.dp, bottom = 16.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            var globalPhraseIndex = 0
            PhrasebookData.categories.forEach { category ->
                val categoryIndex = globalPhraseIndex
                item {
                    val isExpanded = expandedCategory == category.first
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                expandedCategory = if (isExpanded) null else category.first
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isExpanded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = category.first,
                                fontSize = (16 * fontSizeScale).sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isExpanded) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                tint = if (isExpanded) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                if (expandedCategory == category.first) {
                    items(category.second.size) { localIndex ->
                        val currentGlobalIndex = categoryIndex + localIndex
                        val originalText = category.second[localIndex]
                        val translatedText = PhrasebookData.getTranslation(currentGlobalIndex, targetLanguage.code)
                        val targetLangCodeToUse = if (targetLanguage.code == "auto") "en" else targetLanguage.code
                        val isFavorite = favorites.any { it.originalText == originalText && it.translatedText == translatedText }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        if (!isFavorite) {
                                            viewModel.saveAsFavorite(
                                                originalText = originalText,
                                                translatedText = translatedText,
                                                sourceLangCode = "en",
                                                targetLangCode = targetLangCodeToUse
                                            )
                                            Toast.makeText(context, "Saved to Favorites", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val favItem = favorites.firstOrNull { it.originalText == originalText && it.translatedText == translatedText }
                                            if (favItem != null) {
                                                viewModel.toggleFavorite(favItem.id, false)
                                                Toast.makeText(context, "Removed from Favorites", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.padding(end = 8.dp).size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = if (isFavorite) "Remove from Favorites" else "Save to Favorites",
                                        tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = originalText,
                                        fontSize = (13 * fontSizeScale).sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                    Text(
                                        text = translatedText,
                                        fontSize = (16 * fontSizeScale).sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            .clickable {
                                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                                speakOut(originalText, "en")
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VolumeUp,
                                            contentDescription = "Listen to original",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f))
                                            .clickable {
                                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                                val code = if (targetLanguage.code == "auto") "en" else targetLanguage.code
                                                speakOut(translatedText, code)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VolumeUp,
                                            contentDescription = "Listen to translation",
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Advance global index by the size of this category
                globalPhraseIndex += category.second.size
            }
        }
    }
}

