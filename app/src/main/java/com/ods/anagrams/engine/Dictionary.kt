package com.ods.anagrams.engine

import java.text.Normalizer
import java.util.TreeMap

object Letters {
    /** French Scrabble tile distribution, blanks excluded (100 tiles). Index 0 = A ... 25 = Z. */
    val bagCounts: IntArray = intArrayOf(
        9, 2, 2, 3, 15, 2, 2, 2, 8, 1, 1, 5, 3,
        6, 6, 2, 1, 6, 6, 6, 6, 2, 1, 1, 1, 1
    )

    /** Uppercases, strips accents, expands ligatures and keeps only A-Z. */
    fun normalize(s: String): String {
        val up = s.uppercase().replace("Œ", "OE").replace("Æ", "AE")
        val decomposed = Normalizer.normalize(up, Normalizer.Form.NFD)
        val sb = StringBuilder(decomposed.length)
        for (ch in decomposed) if (ch in 'A'..'Z') sb.append(ch)
        return sb.toString()
    }

    fun alphagram(s: String): String {
        val c = s.toCharArray()
        c.sort()
        return String(c)
    }

    /**
     * Number of ways to draw this rack from the bag (product of C(bag[l], k_l)).
     * This is the quantity Aerolith calls "probability" when ordering alphagrams.
     * Returns 0 when the rack needs more copies of a letter than the bag holds.
     */
    fun combinations(alphagram: String): Double {
        val counts = IntArray(26)
        for (ch in alphagram) counts[ch - 'A']++
        var result = 1.0
        for (i in 0 until 26) {
            val k = counts[i]
            if (k == 0) continue
            val n = bagCounts[i]
            if (k > n) return 0.0
            var c = 1.0
            for (j in 1..k) c = c * (n - k + j) / j
            result *= c
        }
        return result
    }
}

class Dictionary private constructor(private val map: HashMap<String, ArrayList<String>>, val wordCount: Int) {

    val alphagramCount: Int get() = map.size

    fun alphagrams(): Set<String> = map.keys

    /** All words that use exactly the letters of [alphagram] (which must already be sorted). */
    fun wordsFor(alphagram: String): List<String> = map[alphagram] ?: emptyList()

    fun isWord(word: String): Boolean = map[Letters.alphagram(word)]?.contains(word) == true

    /**
     * Every word that can be made from a subset of [rack], grouped by length
     * (ascending), restricted to [minLen]..[maxLen]. Words are sorted alphabetically.
     */
    fun solve(rack: String, minLen: Int, maxLen: Int): Map<Int, List<String>> {
        val sorted = Letters.alphagram(rack)
        val n = sorted.length
        val seen = HashSet<String>()
        val out = TreeMap<Int, MutableList<String>>()
        val buf = CharArray(n)
        for (mask in 1 until (1 shl n)) {
            val len = Integer.bitCount(mask)
            if (len < minLen || len > maxLen) continue
            var k = 0
            for (i in 0 until n) if ((mask and (1 shl i)) != 0) buf[k++] = sorted[i]
            val key = String(buf, 0, k)
            if (!seen.add(key)) continue
            val ws = map[key] ?: continue
            out.getOrPut(len) { ArrayList() }.addAll(ws)
        }
        for (list in out.values) list.sort()
        return out
    }

    fun countSolutions(rack: String, minLen: Int, maxLen: Int): Int =
        solve(rack, minLen, maxLen).values.sumOf { it.size }

    companion object {
        fun fromLines(lines: Sequence<String>): Dictionary {
            val map = HashMap<String, ArrayList<String>>(500_000)
            var count = 0
            for (raw in lines) {
                val w = raw.trim()
                if (w.isEmpty()) continue
                val key = Letters.alphagram(w)
                map.getOrPut(key) { ArrayList(1) }.add(w)
                count++
            }
            return Dictionary(map, count)
        }
    }
}
