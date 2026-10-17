package com.zenithai.countai.util

/**
 * Parses natural-language counting commands in English, Urdu and Roman Urdu.
 *
 * Examples:
 *   "count only bottles" / "sirf botalen gino" / "صرف بوتلیں گنو"
 *   "how many chairs?" / "kitni kursiyan hain" / "کتنی کرسیاں ہیں"
 *   "count the red bottles" / "lal botalen gino"
 *
 * @property labels canonical COCO labels to count (empty = all).
 *   "box" is special: COCO has no box class, so it expands to the closest
 *   shapes (suitcase, book, refrigerator) — see [expandBoxLabel].
 * @property color one of red/blue/green/yellow/black/white, or null.
 * @property isQuestion true for "how many …?" style queries.
 * @property unsupportedAttribute non-null when the user asked for an
 *   attribute we can't do (e.g. "tall", "broken") so the UI can say so.
 */
data class ParsedCommand(
    val labels: Set<String>,
    val color: String?,
    val isQuestion: Boolean,
    val unsupportedAttribute: String?,
)

object CommandParser {

    /** Canonical label -> words that mean it (English, Roman Urdu, Urdu). */
    private val LABEL_WORDS: Map<String, List<String>> = mapOf(
        "person" to listOf("person", "people", "man", "woman", "men", "women", "admi", "aadmi", "aadmiyon", "log", "banday", "آدمی", "لوگ"),
        "bicycle" to listOf("bicycle", "cycle", "saikal", "سائیکل"),
        "car" to listOf("car", "cars", "gari", "gaari", "gaadi", "گاڑی"),
        "motorcycle" to listOf("motorcycle", "motorbike", "bike", "bikes", "موٹر سائیکل"),
        "bus" to listOf("bus", "buses", "بس"),
        "truck" to listOf("truck", "ٹرک"),
        "bottle" to listOf("bottle", "bottles", "botal", "botalen", "botalon", "بوتل", "بوتلیں"),
        "cup" to listOf("cup", "cups", "piyala", "piyali", "پیالہ", "کپ"),
        "chair" to listOf("chair", "chairs", "kursi", "kursiyan", "کرسی", "کرسیاں"),
        "couch" to listOf("couch", "sofa", "sofay", "صوفہ"),
        "bed" to listOf("bed", "bistar", "بستر"),
        "dining table" to listOf("table", "tables", "mez", "میز", "dining table"),
        "tv" to listOf("tv", "television", "ٹی وی"),
        "laptop" to listOf("laptop", "لیپ ٹاپ"),
        "cell phone" to listOf("phone", "phones", "mobile", "cell phone", "cellphone", "فون", "موبائل"),
        "book" to listOf("book", "books", "kitab", "kitaben", "کتاب", "کتابیں"),
        "dog" to listOf("dog", "dogs", "kutta", "کتا"),
        "cat" to listOf("cat", "cats", "billi", "بلی"),
        "backpack" to listOf("backpack", "bag", "bags", "بیگ"),
        "handbag" to listOf("handbag", "purse", "پرس"),
        "suitcase" to listOf("suitcase", "briefcase"),
        // Special: not a COCO class — expanded at filter time (see below).
        "box" to listOf("box", "boxes", "dabba", "dabbay", "ڈبہ", "ڈبے", "carton"),
    )

    /** COCO labels that "box" maps to (honest approximation, documented). */
    fun expandBoxLabel(): Set<String> = setOf("suitcase", "book", "refrigerator", "handbag")

    private val COLOR_WORDS: Map<String, List<String>> = mapOf(
        "red" to listOf("red", "lal", "surkh", "سرخ", "لال"),
        "blue" to listOf("blue", "neela", "neelay", "نیلا", "نیلے"),
        "green" to listOf("green", "hara", "hare", "sabz", "sabaz", "سبز", "ہرا"),
        "yellow" to listOf("yellow", "peela", "peelay", "پیلا", "پیلے"),
        "black" to listOf("black", "kala", "kalay", "siyah", "کالا", "سیاہ"),
        "white" to listOf("white", "safed", "safaid", "سفید"),
    )

    private val QUESTION_WORDS = listOf(
        "how many", "kitne", "kitni", "کتنے", "کتنی", "how much", "?", "gin kar batao", "batao",
    )

    /** Attribute words we understand vs. ones we must decline honestly. */
    private val KNOWN_ATTRIBUTES = setOf("red", "blue", "green", "yellow", "black", "white")
    private val ATTRIBUTE_HINTS = listOf(
        "tall", "short", "big", "small", "bara", "chota", "choti", "broken", "toota",
        "left", "right", "side", "bayan", "daen", "corner", "upar", "neeche",
    )

    fun parse(rawQuery: String): ParsedCommand {
        val q = " $rawQuery ".lowercase()
        val labels = mutableSetOf<String>()
        // Longest words first so "dining table" beats "table".
        val sorted = LABEL_WORDS.entries.sortedByDescending { (_, words) ->
            words.maxOf { it.length }
        }
        for ((label, words) in sorted) {
            if (words.any { " $it " in q || q.contains(it) }) labels.add(label)
        }
        var color: String? = null
        for ((c, words) in COLOR_WORDS) {
            if (words.any { " $it " in q }) { color = c; break }
        }
        val isQuestion = QUESTION_WORDS.any { it in q }
        val unsupported = if (color == null) {
            ATTRIBUTE_HINTS.firstOrNull { " $it " in q }
        } else null
        return ParsedCommand(labels, color, isQuestion, unsupported)
    }

    /** All known object words (for hint chips etc.). */
    fun knownLabels(): List<String> = LABEL_WORDS.keys.sorted()

    fun colorDisplayName(color: String): String = color
}
