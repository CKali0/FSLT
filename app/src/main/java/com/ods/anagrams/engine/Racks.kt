package com.ods.anagrams.engine

import java.util.Collections
import java.util.Random

enum class PickMode { PROBABILITY, RANDOM }

class RackFactory(private val dict: Dictionary, private val rng: Random = Random()) {

    private val bag: CharArray = run {
        val list = ArrayList<Char>()
        for (i in 0 until 26) repeat(Letters.bagCounts[i]) { list.add('A' + i) }
        list.toCharArray()
    }

    /** Draws [size] tiles from the bag, in random order. */
    fun draw(size: Int): String {
        for (i in 0 until size) {
            val j = i + rng.nextInt(bag.size - i)
            val t = bag[i]; bag[i] = bag[j]; bag[j] = t
        }
        return String(bag, 0, size)
    }

    fun shuffle(s: String): String {
        if (s.length < 2) return s
        var result = s
        for (attempt in 0 until 8) {
            val c = s.toCharArray()
            for (i in c.size - 1 downTo 1) {
                val j = rng.nextInt(i + 1)
                val t = c[i]; c[i] = c[j]; c[j] = t
            }
            result = String(c)
            if (result != s) break
        }
        return result
    }

    /**
     * A random rack for Rack solve, with at least one word in the requested length range.
     * Prefers racks that have a reasonable number of words.
     */
    fun solveRack(size: Int, minLen: Int, maxLen: Int): String {
        val wanted = 6
        var best: String? = null
        var bestCount = 0
        var tries = 0
        while (tries < 200_000) {
            tries++
            val rack = draw(size)
            val n = dict.countSolutions(rack, minLen, maxLen)
            if (n >= wanted) return rack
            if (n > bestCount) { best = rack; bestCount = n }
            if (tries >= 300 && best != null) return best
        }
        return best ?: draw(size)
    }

    /**
     * Distinct racks of [size] tiles that make at least one word using all tiles,
     * drawn from the bag like a real game (so likelier racks come up more often).
     */
    fun bingoRacks(size: Int, count: Int): List<String> {
        val seen = HashSet<String>()
        val out = ArrayList<String>()
        var draws = 0
        while (out.size < count && draws < 5_000_000) {
            draws++
            val rack = draw(size)
            val key = Letters.alphagram(rack)
            if (!dict.alphagrams().contains(key)) continue
            if (seen.add(key)) out.add(rack)
        }
        return out
    }

    /**
     * Builds a list of racks (alphagrams) for a flashcard deck.
     *
     * PROBABILITY: for each length, racks are ranked by how many ways they can be drawn
     * from the bag (most likely first). [startRank] skips that many racks per length
     * (1 = start at the most likely). The deck is ordered most likely first.
     * RANDOM: uniformly random racks.
     *
     * [count] is split evenly across lengths [minLen]..[maxLen]; lengths that run out
     * hand their share to the others. Racks that can't be drawn from the bag, and racks
     * in [exclude], are never picked.
     */
    fun generate(
        minLen: Int, maxLen: Int, count: Int, mode: PickMode,
        startRank: Int = 1, exclude: Set<String> = emptySet()
    ): List<String> {
        val lo = minOf(minLen, maxLen)
        val hi = maxOf(minLen, maxLen)
        val pools = HashMap<Int, ArrayList<String>>()
        for (a in dict.alphagrams()) {
            val len = a.length
            if (len < lo || len > hi) continue
            if (exclude.contains(a)) continue
            if (Letters.combinations(a) <= 0.0) continue
            pools.getOrPut(len) { ArrayList() }.add(a)
        }
        val offset = if (mode == PickMode.PROBABILITY) maxOf(0, startRank - 1) else 0
        val ranked = HashMap<Int, List<String>>()
        for ((len, list) in pools) {
            if (mode == PickMode.PROBABILITY) {
                val combos = HashMap<String, Double>(list.size * 2)
                for (a in list) combos[a] = Letters.combinations(a)
                list.sortWith(Comparator { x, y ->
                    val c = combos.getValue(y).compareTo(combos.getValue(x))
                    if (c != 0) c else x.compareTo(y)
                })
                ranked[len] = if (offset >= list.size) emptyList() else list.subList(offset, list.size)
            } else {
                Collections.shuffle(list, rng)
                ranked[len] = list
            }
        }

        val lengths = (lo..hi).filter { (ranked[it]?.size ?: 0) > 0 }
        val quota = HashMap<Int, Int>()
        for (l in lengths) quota[l] = 0
        val active = lengths.toMutableList()
        var remaining = count
        while (remaining > 0 && active.isNotEmpty()) {
            val share = maxOf(1, remaining / active.size)
            for (l in active.toList()) {
                if (remaining <= 0) break
                val room = ranked.getValue(l).size - quota.getValue(l)
                val give = minOf(share, remaining, room)
                quota[l] = quota.getValue(l) + give
                remaining -= give
                if (quota.getValue(l) >= ranked.getValue(l).size) active.remove(l)
            }
        }

        val picked = ArrayList<Pair<Int, String>>() // rank within length, rack
        for (l in lengths) {
            val q = quota.getValue(l)
            val list = ranked.getValue(l)
            for (i in 0 until q) picked.add(Pair(i, list[i]))
        }
        return if (mode == PickMode.PROBABILITY) {
            picked.sortedWith(compareBy<Pair<Int, String>>({ it.first }, { it.second.length }, { it.second })).map { it.second }
        } else {
            val result = picked.map { it.second }.toMutableList()
            Collections.shuffle(result, rng)
            result
        }
    }
}

fun formatClock(totalSeconds: Int): String {
    val s = maxOf(0, totalSeconds)
    return "%d:%02d".format(s / 60, s % 60)
}
