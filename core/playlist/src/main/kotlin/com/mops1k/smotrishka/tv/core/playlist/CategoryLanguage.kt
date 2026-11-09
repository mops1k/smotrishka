package com.mops1k.smotrishka.tv.core.playlist

import java.util.Locale

/**
 * Классификатор названий категорий: язык это или тема.
 *
 * Названия языковых категорий у провайдеров встречаются в трёх видах: по-русски
 * («Казахские»), по-английски («Kazakh») и в родной письменности («Қазақша», «العربية»),
 * поэтому у каждого языка в словаре есть все три набора написаний (решение Александра
 * 09.11.2026).
 *
 * Порядок разбора: точное совпадение с языком → тематическое слово («Кино», «Спорт»,
 * «Новости») → письменность названия (арабица, иероглифы, хангыль, армянский, грузинский,
 * иврит) → вхождение названия языка в строку. Если ничего не сработало — тема, то есть
 * язык канала не определяется.
 */
object CategoryLanguage {

    /** Язык: код, название для настроек и варианты написания. */
    private class Language(val title: String, vararg aliases: String) {
        val names: Set<String> = aliases.map { normalize(it) }.toSet()
    }

    private val LANGUAGES = listOf(
        Language("Русский", "русский", "русские", "русская", "russian", "russia", "ru", "рус"),
        Language(
            "Казахский",
            "казахский", "казахские", "казахстан", "kazakh", "kazakhstan", "kz",
            "қазақша", "казакша", "қазақстан", "қазақ",
        ),
        Language(
            "Украинский",
            "украинский", "украинские", "украина", "ukrainian", "ukraine", "ua", "ukr",
            "українські", "українська", "україна",
        ),
        Language(
            "Белорусский",
            "белорусский", "белорусские", "беларусь", "belarusian", "belarus", "by", "blr",
            "беларускія", "беларуская", "беларусь",
        ),
        Language(
            "Английский",
            "английский", "английские", "english", "usa", "us", "uk", "great britain",
            "англ", "british",
        ),
        Language("Арабский", "арабский", "арабские", "arabic", "arab", "ar", "العربية", "عربي"),
        Language("Турецкий", "турецкий", "турецкие", "turkish", "turkey", "tr", "turk", "türk", "türkçe", "turkce"),
        Language(
            "Таджикский",
            "таджикский", "tajik", "tj", "точик", "тоҷик", "тоҷикӣ", "tоҷik",
        ),
        Language(
            "Узбекский",
            "узбекский", "uzbek", "uzbekistan", "uz", "ozbek", "oʻzbek", "o'zbek",
        ),
        Language(
            "Молдавский",
            "молдавский", "молдовский", "moldavian", "moldovan", "md",
            "moldoveneasca", "moldovenesc", "moldova", "română",
        ),
        Language("Немецкий", "немецкий", "немецкие", "german", "germany", "de", "deutsch"),
        Language("Французский", "французский", "французские", "french", "france", "fr", "français"),
        Language("Испанский", "испанский", "испанские", "spanish", "spain", "es", "español"),
        Language("Итальянский", "итальянский", "итальянские", "italian", "italy", "it", "italiano"),
        Language("Польский", "польский", "польские", "polish", "poland", "pl", "polski"),
        Language("Португальский", "португальский", "portuguese", "portugal", "pt", "português"),
        Language("Армянский", "армянский", "армянские", "armenian", "armenia", "am", "հայերեն"),
        Language("Грузинский", "грузинский", "грузинские", "georgian", "georgia", "ka", "ქართული"),
        Language("Азербайджанский", "азербайджанский", "azerbaijani", "azerbaijan", "az", "azərbaycan"),
        Language("Узбекский", "узбекский", "uzbek", "uzbekistan", "uz", "oʻzbekcha"),
        Language("Киргизский", "киргизский", "кыргызский", "kyrgyz", "kg", "кыргызча"),
        Language("Таджикский", "таджикский", "tajik", "tj", "тоҷикӣ"),
        Language("Молдавский", "молдавский", "молдовский", "moldavian", "moldovan", "md", "română"),
        Language("Румынский", "румынский", "romanian", "ro"),
        Language("Болгарский", "болгарский", "bulgarian", "bg", "български"),
        Language("Сербский", "сербский", "serbian", "sr", "српски"),
        Language("Хорватский", "хорватский", "croatian", "hr", "hrvatski"),
        Language("Греческий", "греческий", "greek", "greece", "el", "ελληνικά"),
        Language("Иврит", "иврит", "hebrew", "israel", "il", "he", "עברית"),
        Language("Персидский", "персидский", "фарси", "persian", "farsi", "iran", "ir", "فارسی"),
        Language("Курдский", "курдский", "kurdish", "ku", "کوردی"),
        Language("Хинди", "хинди", "hindi", "india", "in", "हिन्दी"),
        Language("Китайский", "китайский", "chinese", "china", "cn", "zh", "中文", "汉语"),
        Language("Японский", "японский", "japanese", "japan", "jp", "ja", "日本語"),
        Language("Корейский", "корейский", "korean", "korea", "kr", "ko", "한국어"),
        Language("Вьетнамский", "вьетнамский", "vietnamese", "vietnam", "vn", "vi", "tiếng việt"),
        Language("Тайский", "тайский", "thai", "thailand", "th", "ไทย"),
        Language("Индонезийский", "индонезийский", "indonesian", "indonesia", "id", "bahasa"),
        Language("Нидерландский", "нидерландский", "голландский", "dutch", "netherlands", "nl"),
        Language("Шведский", "шведский", "swedish", "sweden", "se", "sv"),
        Language("Норвежский", "норвежский", "norwegian", "norway", "no", "nb"),
        Language("Датский", "датский", "danish", "denmark", "dk", "da"),
        Language("Финский", "финский", "finnish", "finland", "fi"),
        Language("Чешский", "чешский", "czech", "czechia", "cz", "cs"),
        Language("Венгерский", "венгерский", "hungarian", "hungary", "hu"),
        Language("Литовский", "литовский", "lithuanian", "lt"),
        Language("Латвийский", "латышский", "latvian", "lv"),
        Language("Эстонский", "эстонский", "estonian", "ee", "et"),
    )

    /** Слова тематических категорий: они языком не являются. */
    private val THEMES = listOf(
        "кино", "фильм", "сериал", "мульт", "спорт", "новост", "музык", "детск", "детям",
        "познават", "развлекат", "документ", "взросл", "эрот", "шоу", "юмор", "кулинар",
        "путешеств", "природ", "наук", "истор", "религ", "авто", "мода", "одежд", "магазин",
        "телеканал", "канал", "все каналы", "другие", "прочее", "разное", "тест", "промо",
        "hd", "fhd", "uhd", "4k", "8k", "sd", "vip", "premium", "archive", "архив",
        "movie", "film", "series", "sport", "news", "music", "kids", "documentary", "adult",
        "xxx", "show", "comedy", "cinema", "entertainment", "lifestyle", "education",
    )

    /**
     * Возвращает язык категории.
     *
     * @param name название категории из плейлиста;
     * @return язык категории или `null`, если категория тематическая.
     */
    fun resolve(name: String?): String? {
        val raw = name?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val normalized = normalize(raw)

        // 1. Точное название языка — самый надёжный признак.
        LANGUAGES.firstOrNull { normalized in it.names }?.let { return it.title }

        // 2. Тематическое слово важнее вхождения языка: «Новости на казахском» — тема.
        if (THEMES.any { normalized.contains(it) }) return null

        // 3. Письменность названия: родная запись языка определяется по алфавиту.
        scriptLanguage(raw)?.let { return it }

        // 4. Вхождение названия языка в строку (для составных названий).
        return LANGUAGES.firstOrNull { language ->
            language.names.any { alias -> alias.length >= 4 && normalized.contains(alias) }
        }?.title
    }

    /** Определяет язык по алфавиту названия категории. */
    private fun scriptLanguage(name: String): String? = when {
        name.any { it in '\u0600'..'\u06FF' } -> "Арабский"
        name.any { it in '\u0590'..'\u05FF' } -> "Иврит"
        name.any { it in '\uAC00'..'\uD7AF' } -> "Корейский"
        name.any { it in '\u3040'..'\u30FF' } -> "Японский"
        name.any { it in '\u4E00'..'\u9FFF' } -> "Китайский"
        name.any { it in '\u0E00'..'\u0E7F' } -> "Тайский"
        name.any { it in '\u0530'..'\u058F' } -> "Армянский"
        name.any { it in '\u10A0'..'\u10FF' } -> "Грузинский"
        name.any { it in '\u0900'..'\u097F' } -> "Хинди"
        else -> null
    }

    /** Приводит название к сравнимому виду: без диакритики, апострофов и лишних знаков. */
    private fun normalize(value: String): String {
        // Диакритика снимается заранее: «türk», «Moldovenească» и «Oʻzbek» сравниваются
        // с простыми написаниями, которые провайдеры пишут по-разному.
        val withoutDiacritics = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return withoutDiacritics
            .lowercase(Locale.ROOT)
            .replace('ё', 'е')
            .replace(Regex("[«»\"'`’ʻ.,!?:;()\\[\\]/\\\\|_*&+—-]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
