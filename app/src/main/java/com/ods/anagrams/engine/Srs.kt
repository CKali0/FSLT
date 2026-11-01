package com.ods.anagrams.engine

import kotlin.math.max
import kotlin.math.roundToInt

enum class CardState { NEW, LEARNING, REVIEW, FREE }

enum class Grade { MISSED, HARD, EASY, FREE }

/**
 * One rack in a deck. All "time" is measured in cards answered in the deck
 * (Deck.position), never in minutes or days.
 */
class Card(
    val rack: String,
    var state: CardState = CardState.NEW,
    var step: Int = 0,
    var interval: Int = 0,
    var ease: Double = 2.5,
    var due: Int = 0
) {
    fun copy() = Card(rack, state, step, interval, ease, due)
}

object Scheduler {
    // Everything below is "number of cards answered before this one shows again".
    const val RELEARN_DELAY = 3          // after a miss
    const val SECOND_STEP_DELAY = 10     // first Hard on a new card
    const val GRADUATE_HARD = 20         // new card answered Hard twice
    const val GRADUATE_EASY = 60         // new card answered Easy
    const val LAPSE_MIN_INTERVAL = 15
    const val MIN_EASE = 1.3
    const val HARD_FACTOR = 1.5
    const val EASY_BONUS = 1.3
    const val MAX_INTERVAL = 1_000_000

    /**
     * Updates [card] in place. [position] is the deck position AFTER counting this answer,
     * so "due = position + N" means N other cards are answered first.
     */
    fun apply(card: Card, grade: Grade, position: Int) {
        if (grade == Grade.FREE) {
            card.state = CardState.FREE
            return
        }
        when (card.state) {
            CardState.NEW, CardState.LEARNING -> learn(card, grade, position)
            CardState.REVIEW -> review(card, grade, position)
            CardState.FREE -> {}
        }
    }

    private fun learn(card: Card, grade: Grade, position: Int) {
        val relearning = card.interval > 0 // a lapsed review card keeps its interval
        when (grade) {
            Grade.MISSED -> {
                card.state = CardState.LEARNING
                card.step = 0
                card.due = position + RELEARN_DELAY
            }
            Grade.HARD -> {
                if (relearning) {
                    graduate(card, card.interval, position)
                } else if (card.state == CardState.NEW || card.step == 0) {
                    card.state = CardState.LEARNING
                    card.step = 1
                    card.due = position + SECOND_STEP_DELAY
                } else {
                    graduate(card, GRADUATE_HARD, position)
                }
            }
            Grade.EASY -> {
                val interval = if (relearning) (card.interval * 1.5).roundToInt() else GRADUATE_EASY
                graduate(card, interval, position)
            }
            Grade.FREE -> {}
        }
    }

    private fun graduate(card: Card, interval: Int, position: Int) {
        card.state = CardState.REVIEW
        card.step = 0
        card.interval = interval.coerceIn(1, MAX_INTERVAL)
        card.due = position + card.interval
    }

    private fun review(card: Card, grade: Grade, position: Int) {
        when (grade) {
            Grade.MISSED -> {
                card.ease = max(MIN_EASE, card.ease - 0.2)
                card.interval = max(LAPSE_MIN_INTERVAL, (card.interval * 0.3).roundToInt())
                card.state = CardState.LEARNING
                card.step = 0
                card.due = position + RELEARN_DELAY
            }
            Grade.HARD -> {
                card.ease = max(MIN_EASE, card.ease - 0.1)
                card.interval = max(card.interval + 1, (card.interval * HARD_FACTOR).roundToInt())
                    .coerceAtMost(MAX_INTERVAL)
                card.due = position + card.interval
            }
            Grade.EASY -> {
                card.interval = max(card.interval + 1, (card.interval * card.ease * EASY_BONUS).roundToInt())
                    .coerceAtMost(MAX_INTERVAL)
                card.ease += 0.1
                card.due = position + card.interval
            }
            Grade.FREE -> {}
        }
    }

    /** Label for a grade button: how many cards until this rack comes back. */
    fun preview(card: Card, grade: Grade, position: Int): String {
        if (grade == Grade.FREE) return "never"
        val c = card.copy()
        apply(c, grade, position + 1)
        val n = c.due - (position + 1)
        return if (n <= 0) "next" else "$n cards"
    }
}

class Deck(
    val id: Long,
    var name: String,
    val cards: MutableList<Card> = ArrayList(),
    var position: Int = 0
) {
    /** The card to study now, or null when every card is Free. */
    fun nextCard(): Card? {
        var due: Card? = null
        var firstNew: Card? = null
        var soonest: Card? = null
        for (c in cards) {
            when (c.state) {
                CardState.NEW -> if (firstNew == null) firstNew = c
                CardState.LEARNING, CardState.REVIEW -> {
                    if (c.due <= position && (due == null || c.due < due.due)) due = c
                    if (soonest == null || c.due < soonest.due) soonest = c
                }
                CardState.FREE -> {}
            }
        }
        return due ?: firstNew ?: soonest
    }

    fun answer(card: Card, grade: Grade) {
        position += 1
        Scheduler.apply(card, grade, position)
    }

    fun count(state: CardState): Int = cards.count { it.state == state }

    fun dueCount(): Int = cards.count {
        (it.state == CardState.LEARNING || it.state == CardState.REVIEW) && it.due <= position
    }

    fun racks(): Set<String> = cards.mapTo(HashSet()) { it.rack }
}

object DeckStore {
    private const val HEADER = "ODSDECKS1"

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    fun serialize(decks: List<Deck>): String {
        val sb = StringBuilder()
        sb.append(HEADER).append('\n')
        for (d in decks) {
            sb.append("D\t").append(d.id).append('\t').append(clean(d.name)).append('\t').append(d.position).append('\n')
            for (c in d.cards) {
                sb.append("C\t").append(c.rack).append('\t').append(c.state.ordinal).append('\t')
                    .append(c.step).append('\t').append(c.interval).append('\t')
                    .append(c.ease).append('\t').append(c.due).append('\n')
            }
        }
        return sb.toString()
    }

    fun parse(text: String): MutableList<Deck> {
        val decks = ArrayList<Deck>()
        var current: Deck? = null
        for (line in text.lineSequence()) {
            val p = line.split('\t')
            try {
                when (p[0]) {
                    "D" -> {
                        current = Deck(p[1].toLong(), p[2], ArrayList(), p[3].toInt())
                        decks.add(current)
                    }
                    "C" -> current?.cards?.add(
                        Card(
                            rack = p[1],
                            state = CardState.values()[p[2].toInt()],
                            step = p[3].toInt(),
                            interval = p[4].toInt(),
                            ease = p[5].toDouble(),
                            due = p[6].toInt()
                        )
                    )
                }
            } catch (e: Exception) {
                // skip a damaged line rather than lose the whole file
            }
        }
        return decks
    }
}
