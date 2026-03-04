package com.example.ocr2

class KeywordSearchHelper {

    fun answer(extractedText: String, question: String): String {
        if (extractedText.isBlank()) return "No text has been extracted yet."
        if (question.isBlank()) return "Please enter a question."

        val questionLower = question.lowercase().trim()

        // Split text into sentences
        val sentences = extractedText
            .split(Regex("[.!?\n]+"))
            .map { it.trim() }
            .filter { it.length > 5 }

        if (sentences.isEmpty()) return "Could not parse the extracted text."

        // Find most relevant sentence first
        val bestSentence = findBestSentence(sentences, questionLower)
            ?: return "No relevant information found in the scanned text."

        // Try to extract specific answer from best sentence
        return extractAnswer(questionLower, bestSentence)
    }

    private fun findBestSentence(sentences: List<String>, question: String): String? {
        // Remove common question words to get keywords
        val keywords = question
            .replace(Regex("[?.,!]"), "")
            .split(" ")
            .filter { it.length > 2 }
            .filter { it !in STOP_WORDS }

        if (keywords.isEmpty()) return sentences.firstOrNull()

        val scored = sentences.map { sentence ->
            val lower = sentence.lowercase()
            var score = 0
            for (kw in keywords) {
                if (lower.contains(kw)) score += 3
                else if (kw.length > 4 && lower.contains(kw.take(4))) score += 1
            }
            Pair(sentence, score)
        }.filter { it.second > 0 }
            .sortedByDescending { it.second }

        return scored.firstOrNull()?.first
    }

    private fun extractAnswer(question: String, sentence: String): String {

        val isNumberQuestion = NUMBER_TRIGGERS.any { question.contains(it) }

        if (isNumberQuestion) {
            // Extract ALL numbers with surrounding context from sentence
            val numberRegex = Regex("""(\d[\d,.]*)(\s*[a-zA-Z]+)?""")
            val allNumbers = numberRegex.findAll(sentence).toList()

            if (allNumbers.isNotEmpty()) {
                // Find the number that appears closest to a keyword from question
                val questionKeywords = question.split(" ")
                    .filter { it.length > 2 }
                    .filter { it !in STOP_WORDS }

                // Try each number — pick one whose surrounding text matches question
                for (match in allNumbers) {
                    val start = (match.range.first - 40).coerceAtLeast(0)
                    val end   = (match.range.last  + 40).coerceAtMost(sentence.length)
                    val context = sentence.substring(start, end).lowercase()

                    val matched = questionKeywords.any { kw -> context.contains(kw) }
                    if (matched) {
                        val number = match.groupValues[1].trim()
                        val unit   = match.groupValues[2].trim()
                        val answer = if (unit.isNotBlank()) "$number $unit" else number
                        return "📊 Answer: $answer\n\n📄 From: \"$sentence\""
                    }
                }

                // Fallback: just return the first number in the sentence
                val first  = allNumbers.first()
                val number = first.groupValues[1].trim()
                val unit   = first.groupValues[2].trim()
                val answer = if (unit.isNotBlank()) "$number $unit" else number
                return "📊 Answer: $answer\n\n📄 From: \"$sentence\""
            }
        }

        // DATE question
        if (DATE_TRIGGERS.any { question.contains(it) }) {
            val dateRegex = Regex(
                "(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{4}|" +
                        "january|february|march|april|may|june|july|august|" +
                        "september|october|november|december)",
                RegexOption.IGNORE_CASE
            )
            val match = dateRegex.find(sentence)
            if (match != null) {
                return "📅 Answer: ${match.value}\n\n📄 From: \"$sentence\""
            }
        }

        // WHO question
        if (WHO_TRIGGERS.any { question.contains(it) }) {
            val nameRegex = Regex("[A-Z][a-z]+(\\s[A-Z][a-z]+)*")
            val match = nameRegex.find(sentence)
            if (match != null) {
                return "👤 Answer: ${match.value}\n\n📄 From: \"$sentence\""
            }
        }

        // WHERE question
        if (WHERE_TRIGGERS.any { question.contains(it) }) {
            val nameRegex = Regex("[A-Z][a-z]+(\\s[A-Z][a-z]+)*")
            val match = nameRegex.find(sentence)
            if (match != null) {
                return "📍 Answer: ${match.value}\n\n📄 From: \"$sentence\""
            }
        }

        // Default: return the full relevant sentence
        return "💬 Answer: $sentence"
    }

    private val NUMBER_TRIGGERS = listOf(
        "how many", "how much", "how long", "how far", "how tall",
        "how big", "how heavy", "how wide", "how deep", "how fast",
        "what amount", "what number", "what quantity", "what weight",
        "what size", "what distance", "what speed", "what age",
        "pounds", "lbs", "kilograms", "kg", "miles", "kilometers",
        "meters", "feet", "inches", "tons", "gallons", "liters",
        "years", "days", "hours", "minutes", "seconds",
        "dollars", "rupees", "euros", "percent", "%",
        "calories", "times", "count", "total", "number",
        "many", "much", "cost", "price", "weight", "height",
        "distance", "speed", "rate", "age", "size"
    )

    private val DATE_TRIGGERS = listOf("when", "what date", "what year", "what month", "what day", "what time")
    private val WHO_TRIGGERS  = listOf("who", "whose", "whom")
    private val WHERE_TRIGGERS = listOf("where", "which place", "which country", "which city")

    private val STOP_WORDS = setOf(
        "the", "and", "for", "are", "but", "not", "you", "all",
        "can", "has", "had", "was", "one", "our", "out", "day",
        "get", "him", "his", "how", "its", "may", "now", "did",
        "from", "have", "this", "that", "with", "they", "will",
        "been", "when", "what", "your", "also", "into", "than",
        "then", "some", "time", "very", "just", "come", "over",
        "said", "each", "which", "their", "there", "were", "food",
        "about", "eat", "will"
    )
}