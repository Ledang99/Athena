package com.projectathena.app.data

/**
 * High-level standard library collections for organizing books and notes
 * without AI. Matching uses metadata subjects, title, and folder names.
 */
enum class LibraryCollection(
    val id: String,
    val label: String,
    private val keywords: List<String>,
) {
    HABITS("habits", "Habits", listOf("habit", "habits", "routine", "discipline", "willpower")),
    INVESTING(
        "investing",
        "Investing",
        listOf(
            "invest",
            "investing",
            "investment",
            "stock",
            "stocks",
            "equity",
            "portfolio",
            "trading",
            "valuation",
            "dividend",
        ),
    ),
    PERSONAL_FINANCE(
        "personal-finance",
        "Personal finance",
        listOf("finance", "financial", "money", "wealth", "budget", "debt", "retirement", "saving"),
    ),
    PRODUCTIVITY(
        "productivity",
        "Productivity",
        listOf("productivity", "focus", "time management", "gtd", "deep work", "efficiency"),
    ),
    LEADERSHIP(
        "leadership",
        "Leadership",
        listOf("leadership", "management", "manager", "executive", "team building"),
    ),
    PSYCHOLOGY(
        "psychology",
        "Psychology",
        listOf("psychology", "cognitive", "behavior", "behaviour", "mindset", "mental"),
    ),
    BUSINESS(
        "business",
        "Business",
        listOf("business", "startup", "entrepreneur", "strategy", "marketing", "sales", "company"),
    ),
    TECHNOLOGY(
        "technology",
        "Technology",
        listOf("technology", "software", "programming", "coding", "computer", "ai", "machine learning", "data"),
    ),
    SCIENCE(
        "science",
        "Science",
        listOf("science", "physics", "biology", "chemistry", "astronomy", "math", "mathematics"),
    ),
    HEALTH(
        "health",
        "Health",
        listOf("health", "fitness", "nutrition", "medicine", "wellness", "exercise", "diet"),
    ),
    HISTORY("history", "History", listOf("history", "historical", "war", "civilization", "biography of")),
    BIOGRAPHY("biography", "Biography", listOf("biography", "memoir", "autobiography")),
    FICTION("fiction", "Fiction", listOf("fiction", "novel", "story", "fantasy", "thriller", "mystery")),
    PHILOSOPHY("philosophy", "Philosophy", listOf("philosophy", "ethics", "stoic", "stoicism", "existential")),
    EDUCATION("education", "Education", listOf("education", "learning", "teaching", "study", "pedagogy")),
    SELF_IMPROVEMENT(
        "self-improvement",
        "Self-improvement",
        listOf("self-help", "self help", "self-improvement", "personal development", "motivation"),
    ),
    ;

    fun matches(haystack: String): Boolean {
        val text = haystack.lowercase()
        return keywords.any { keyword -> text.contains(keyword) }
    }

    companion object {
        fun fromId(id: String): LibraryCollection? = entries.firstOrNull { it.id == id }

        fun matchAll(signals: Collection<String>): List<LibraryCollection> {
            val haystack = signals
                .map { normalizeForMatch(it) }
                .filter { it.isNotBlank() }
                .joinToString(" ")
            if (haystack.isBlank()) return emptyList()
            return entries.filter { it.matches(haystack) }
        }
    }
}

enum class ReadingStatus(val storage: String, val label: String) {
    UNREAD("unread", "Unread"),
    READING("reading", "Reading"),
    FINISHED("finished", "Finished"),
    ;

    companion object {
        fun fromStorage(value: String?): ReadingStatus =
            entries.firstOrNull { it.storage.equals(value, ignoreCase = true) } ?: UNREAD
    }
}

enum class LibrarySort(val label: String) {
    TITLE("Title"),
    RECENTLY_OPENED("Recently opened"),
    RECENTLY_ADDED("Recently added"),
    STATUS("Reading status"),
}

fun normalizeTags(raw: Collection<String>): List<String> =
    raw.asSequence()
        .flatMap { value ->
            value.split(',', ';', '/', '|')
                .map { it.trim() }
                .filter { it.isNotBlank() }
        }
        .map { tag ->
            tag.replace(Regex("\\s+"), " ")
                .trim()
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
        .distinctBy { it.lowercase() }
        .sortedBy { it.lowercase() }
        .toList()

fun encodeStringList(values: List<String>): String =
    values.joinToString("\u001f")

fun decodeStringList(raw: String?): List<String> =
    raw?.split('\u001f')
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        .orEmpty()

fun Book.collectionLabels(categories: List<BookCategory> = emptyList()): List<String> {
    val categoryMap = categories.associate { it.id to it.label }
    return collections.mapNotNull { id ->
        categoryMap[id] ?: LibraryCollection.fromId(id)?.label ?: id.takeIf { it.isNotBlank() }
    }
}

fun CapturedNote.collectionLabels(categories: List<BookCategory> = emptyList()): List<String> {
    val categoryMap = categories.associate { it.id to it.label }
    return collections.mapNotNull { id ->
        categoryMap[id] ?: LibraryCollection.fromId(id)?.label ?: id.takeIf { it.isNotBlank() }
    }
}
