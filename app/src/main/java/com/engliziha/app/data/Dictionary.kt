package com.engliziha.app.data

import android.content.Context

data class Example(val arabic: String, val sentence: String, val sentenceArabic: String)

sealed interface WordCheck {
    val typed: String

    /** Spelled correctly; [example] is present for common words. */
    data class Correct(override val typed: String, val example: Example?) : WordCheck

    /** Misspelled; [correct] is the closest real word (null if nothing is close) and [reason] says what went wrong. */
    data class Wrong(override val typed: String, val correct: String?, val reason: String, val example: Example?) : WordCheck
}

/** Offline English dictionary: a spelling list, plus meanings and example sentences for common words. */
class Dictionary(private val words: Map<String, Int>, private val examples: Map<String, Example>) {

    fun example(word: String): Example? = examples[word.lowercase()]

    /** Splits free text into words and checks each one once, in the order written. */
    fun check(text: String): List<WordCheck> =
        TOKEN.findAll(text).map { it.value.lowercase() }.distinct().take(MAX_WORDS).map { w ->
            if (w in words) WordCheck.Correct(w, examples[w])
            else {
                val fix = suggest(w)
                WordCheck.Wrong(w, fix, if (fix == null) "لم أجد كلمة قريبة منها في القاموس" else explain(w, fix), fix?.let(examples::get))
            }
        }.toList()

    /** The closest real word: fewest edits first, then the more common word. */
    fun suggest(typed: String): String? {
        val limit = if (typed.length <= 3) 1 else 2
        var best: String? = null
        var bestScore = Int.MAX_VALUE
        for ((word, tier) in words) {
            if (kotlin.math.abs(word.length - typed.length) > limit) continue
            val d = editDistance(typed, word, limit)
            if (d > limit) continue
            // A different first letter is an unusual mistake, so prefer candidates that keep it.
            var score = d * 100 + tier + (if (word[0] != typed[0]) 40 else 0)
            // Learners mostly drop a doubled letter or swap two letters; replacing a letter is rarer.
            if (word.length == typed.length + 1 && droppedDouble(typed, word)) score -= 25
            else if (word.length == typed.length) score += if (sameLetters(typed, word)) -15 else 15
            if (score < bestScore) { bestScore = score; best = word }
        }
        return best
    }

    companion object {
        const val MAX_WORDS = 60
        private val TOKEN = Regex("[A-Za-z]+(?:'[A-Za-z]+)?")

        fun load(context: Context): Dictionary {
            val words = HashMap<String, Int>(120_000)
            context.assets.open("dict/words.tsv").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val tab = line.indexOf('\t')
                    if (tab > 0) words[line.substring(0, tab)] = line.substring(tab + 1).trim().toIntOrNull() ?: 60
                }
            }
            val examples = HashMap<String, Example>(6_000)
            context.assets.open("dict/examples.tsv").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val f = line.split('\t')
                    if (f.size == 4) examples[f[0]] = Example(f[1], f[2], f[3])
                }
            }
            return Dictionary(words, examples)
        }

        private fun sameLetters(a: String, b: String) = a.toCharArray().sorted() == b.toCharArray().sorted()

        /** True if [word] is [typed] with one of its letters doubled (aple -> apple). */
        private fun droppedDouble(typed: String, word: String): Boolean {
            for (i in 1 until word.length) {
                if (word[i] == word[i - 1] && word.removeRange(i, i + 1) == typed) return true
            }
            return false
        }

        /** Edit distance where swapping two neighbouring letters counts as one edit. Returns limit+1 when above the limit. */
        fun editDistance(a: String, b: String, limit: Int): Int {
            var prev2 = IntArray(b.length + 1)
            var prev = IntArray(b.length + 1) { it }
            var cur = IntArray(b.length + 1)
            for (i in 1..a.length) {
                cur[0] = i
                var rowMin = cur[0]
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                    if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, prev2[j - 2] + 1)
                    cur[j] = v
                    if (v < rowMin) rowMin = v
                }
                if (rowMin > limit) return limit + 1
                val t = prev2; prev2 = prev; prev = cur; cur = t
            }
            return prev[b.length]
        }

        /** Says in Arabic what kind of spelling mistake turned [correct] into [typed]. */
        fun explain(typed: String, correct: String): String {
            if (editDistance(typed, correct, 1) == 1) {
                if (typed.length == correct.length) {
                    val diff = typed.indices.filter { typed[it] != correct[it] }
                    if (diff.size == 1) return "حرف خاطئ: كتبت «${typed[diff[0]]}» والصحيح «${correct[diff[0]]}»"
                    if (diff.size == 2) return "حرفان متبادلان: كتبت «${typed.substring(diff[0], diff[1] + 1)}» والصحيح «${correct.substring(diff[0], diff[1] + 1)}»"
                } else if (typed.length < correct.length) {
                    val i = correct.indices.firstOrNull { it >= typed.length || typed[it] != correct[it] } ?: 0
                    val doubled = i > 0 && correct[i] == correct[i - 1]
                    return if (doubled) "حرف ناقص: الحرف «${correct[i]}» يُكتب مرتين" else "حرف ناقص: «${correct[i]}»"
                } else {
                    val i = typed.indices.firstOrNull { it >= correct.length || typed[it] != correct[it] } ?: 0
                    val doubled = i > 0 && typed[i] == typed[i - 1]
                    return if (doubled) "حرف زائد: الحرف «${typed[i]}» يُكتب مرة واحدة" else "حرف زائد: «${typed[i]}»"
                }
            }
            return "أكثر من خطأ إملائي — قارن بين الكلمتين حرفاً حرفاً"
        }

        /** For each letter of [correct], true if the learner did not write it in that place (to highlight it). */
        fun fixedLetters(typed: String, correct: String): BooleanArray {
            // Longest common subsequence: letters outside it are the ones that were wrong or missing.
            val n = typed.length; val m = correct.length
            val lcs = Array(n + 1) { IntArray(m + 1) }
            for (i in n - 1 downTo 0) for (j in m - 1 downTo 0)
                lcs[i][j] = if (typed[i] == correct[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
            val changed = BooleanArray(m) { true }
            var i = 0; var j = 0
            while (i < n && j < m) {
                when {
                    typed[i] == correct[j] -> { changed[j] = false; i++; j++ }
                    lcs[i + 1][j] >= lcs[i][j + 1] -> i++
                    else -> j++
                }
            }
            return changed
        }

        /** The sentence with the word replaced by a blank, or null if the word is not in it. */
        fun blank(sentence: String, word: String): String? {
            val match = Regex("(?i)(?<![A-Za-z'])" + Regex.escape(word) + "(?![A-Za-z'])").find(sentence) ?: return null
            return sentence.replaceRange(match.range, "_____")
        }
    }
}
