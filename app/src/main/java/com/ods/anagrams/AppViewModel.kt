package com.ods.anagrams

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ods.anagrams.engine.Card
import com.ods.anagrams.engine.CardState
import com.ods.anagrams.engine.Deck
import com.ods.anagrams.engine.DeckStore
import com.ods.anagrams.engine.Dictionary
import com.ods.anagrams.engine.Grade
import com.ods.anagrams.engine.Letters
import com.ods.anagrams.engine.PickMode
import com.ods.anagrams.engine.RackFactory
import com.ods.anagrams.engine.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

sealed interface Screen {
    data object Loading : Screen
    data object Menu : Screen
    data object RackSettings : Screen
    data object RackGame : Screen
    data object RackEnd : Screen
    data object RackAnswers : Screen
    data object BingoSettings : Screen
    data object BingoGame : Screen
    data object BingoEnd : Screen
    data object BingoAnswers : Screen
    data object FlashDecks : Screen
    data class FlashDeck(val deckId: Long) : Screen
    data class FlashAdd(val deckId: Long?) : Screen
    data class FlashStudy(val deckId: Long) : Screen
}

data class RackSettings(
    val size: Int = 7,
    val minLen: Int = 3,
    val maxLen: Int = 7,
    val seconds: Int = 300
)

data class BingoSettings(
    val size: Int = 7,
    val racks: Int = 20,
    val seconds: Int = 300
)

data class GenSettings(
    val minLen: Int = 7,
    val maxLen: Int = 8,
    val count: Int = 100,
    val byProbability: Boolean = true,
    val startRank: Int = 1
)

data class DeckInfo(
    val id: Long,
    val name: String,
    val total: Int,
    val fresh: Int,
    val learning: Int,
    val review: Int,
    val free: Int,
    val due: Int
)

class RackSession(
    val rack: String,
    val settings: RackSettings,
    val solutions: Map<Int, List<String>>,
    val endAtMs: Long
) {
    val allWords: Set<String> = solutions.values.flatten().toHashSet()
    val total: Int = allWords.size
    var found by mutableStateOf<Set<String>>(emptySet())
    var shown by mutableStateOf(rack)
    var message by mutableStateOf("")
}

class BingoSession(
    val racks: List<String>,
    val settings: BingoSettings,
    val answers: List<List<String>>,
    val endAtMs: Long
) {
    val indexByAlphagram: Map<String, Int> =
        racks.withIndex().associate { (i, r) -> Letters.alphagram(r) to i }
    val total: Int = answers.sumOf { it.size }
    var found by mutableStateOf<List<Set<String>>>(racks.map { emptySet() })
    var message by mutableStateOf("")
    val foundCount: Int get() = found.sumOf { it.size }
}

class StudySession(val deckId: Long, val card: Card?, val solutions: List<String>) {
    var found by mutableStateOf<Set<String>>(emptySet())
    var revealed by mutableStateOf(false)
    var shown by mutableStateOf(card?.rack ?: "")
    var message by mutableStateOf("")
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val io = Executors.newSingleThreadExecutor()
    private val decksFile = File(app.filesDir, "decks.txt")

    var screen: Screen by mutableStateOf(Screen.Loading)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set

    private var dict: Dictionary? = null
    private var factory: RackFactory? = null
    private val d: Dictionary get() = dict!!
    private val f: RackFactory get() = factory!!

    // ---- settings -------------------------------------------------------------------------

    var rackSettings by mutableStateOf(
        RackSettings(
            size = prefs.getInt("rack_size", 7),
            minLen = prefs.getInt("rack_min", 3),
            maxLen = prefs.getInt("rack_max", 7),
            seconds = prefs.getInt("rack_secs", 300)
        )
    )
        private set

    var bingoSettings by mutableStateOf(
        BingoSettings(
            size = prefs.getInt("bingo_size", 7),
            racks = prefs.getInt("bingo_racks", 20),
            seconds = prefs.getInt("bingo_secs", 300)
        )
    )
        private set

    var genSettings by mutableStateOf(
        GenSettings(
            minLen = prefs.getInt("gen_min", 7),
            maxLen = prefs.getInt("gen_max", 8),
            count = prefs.getInt("gen_count", 100),
            byProbability = prefs.getBoolean("gen_prob", true),
            startRank = prefs.getInt("gen_start", 1)
        )
    )
        private set

    fun updateRackSettings(s: RackSettings) {
        val size = if (s.size == 8) 8 else 7
        val max = s.maxLen.coerceIn(2, size)
        val min = s.minLen.coerceIn(2, max)
        val secs = s.seconds.coerceIn(30, 3600)
        rackSettings = RackSettings(size, min, max, secs)
        prefs.edit()
            .putInt("rack_size", size).putInt("rack_min", min)
            .putInt("rack_max", max).putInt("rack_secs", secs).apply()
    }

    fun updateBingoSettings(s: BingoSettings) {
        val size = if (s.size == 8) 8 else 7
        val racks = s.racks.coerceIn(1, 200)
        val secs = s.seconds.coerceIn(30, 3600)
        bingoSettings = BingoSettings(size, racks, secs)
        prefs.edit()
            .putInt("bingo_size", size).putInt("bingo_racks", racks)
            .putInt("bingo_secs", secs).apply()
    }

    fun updateGenSettings(s: GenSettings) {
        val min = s.minLen.coerceIn(2, 15)
        val max = s.maxLen.coerceIn(min, 15)
        genSettings = s.copy(minLen = min, maxLen = max)
        prefs.edit()
            .putInt("gen_min", min).putInt("gen_max", max).putInt("gen_count", s.count)
            .putBoolean("gen_prob", s.byProbability).putInt("gen_start", s.startRank).apply()
    }

    // ---- navigation -----------------------------------------------------------------------

    fun go(s: Screen) {
        screen = s
    }

    val backEnabled: Boolean
        get() = when (screen) {
            Screen.Loading, Screen.Menu, Screen.RackGame, Screen.BingoGame -> false
            else -> true
        }

    fun back() {
        val s = screen
        screen = when (s) {
            Screen.RackSettings, Screen.BingoSettings, Screen.FlashDecks -> Screen.Menu
            Screen.RackEnd -> Screen.RackSettings
            Screen.RackAnswers -> Screen.RackEnd
            Screen.BingoEnd -> Screen.BingoSettings
            Screen.BingoAnswers -> Screen.BingoEnd
            is Screen.FlashDeck -> Screen.FlashDecks
            is Screen.FlashAdd -> if (s.deckId == null) Screen.FlashDecks else Screen.FlashDeck(s.deckId)
            is Screen.FlashStudy -> Screen.FlashDeck(s.deckId)
            else -> s
        }
    }

    // ---- rack solve -----------------------------------------------------------------------

    var rackSession by mutableStateOf<RackSession?>(null)
        private set

    private fun beginRack(rack: String, st: RackSettings) {
        val solutions = d.solve(rack, st.minLen, st.maxLen)
        rackSession = RackSession(rack, st, solutions, SystemClock.elapsedRealtime() + st.seconds * 1000L)
        screen = Screen.RackGame
    }

    fun startRackNew() {
        val st = rackSettings
        beginRack(f.solveRack(st.size, st.minLen, st.maxLen), st)
    }

    fun startRackSame() {
        val s = rackSession ?: return
        beginRack(s.rack, s.settings)
    }

    fun rackShuffle() {
        val s = rackSession ?: return
        s.shown = f.shuffle(s.shown)
    }

    fun rackSubmit(raw: String) {
        val s = rackSession ?: return
        val w = Letters.normalize(raw)
        if (w.isEmpty()) return
        when {
            s.found.contains(w) -> s.message = "$w: already found"
            s.allWords.contains(w) -> {
                s.found = s.found + w
                s.message = "✓ $w"
                if (s.found.size >= s.total) rackFinish()
            }
            else -> s.message = "$w: not valid"
        }
    }

    fun rackFinish() {
        if (screen == Screen.RackGame) screen = Screen.RackEnd
    }

    // ---- bingo hunt -----------------------------------------------------------------------

    var bingoSession by mutableStateOf<BingoSession?>(null)
        private set

    private fun beginBingo(racks: List<String>, st: BingoSettings) {
        val answers = racks.map { d.wordsFor(Letters.alphagram(it)) }
        bingoSession = BingoSession(racks, st, answers, SystemClock.elapsedRealtime() + st.seconds * 1000L)
        screen = Screen.BingoGame
    }

    fun startBingoNew() {
        val st = bingoSettings
        beginBingo(f.bingoRacks(st.size, st.racks), st)
    }

    fun startBingoSame() {
        val s = bingoSession ?: return
        beginBingo(s.racks, s.settings)
    }

    fun bingoSubmit(raw: String) {
        val s = bingoSession ?: return
        val w = Letters.normalize(raw)
        if (w.isEmpty()) return
        val idx = s.indexByAlphagram[Letters.alphagram(w)]
        if (idx == null) {
            s.message = "$w: not a bingo on the board"
            return
        }
        if (!s.answers[idx].contains(w)) {
            s.message = "$w: not a valid word"
            return
        }
        if (s.found[idx].contains(w)) {
            s.message = "$w: already found"
            return
        }
        val updated = s.found.toMutableList()
        updated[idx] = updated[idx] + w
        s.found = updated
        s.message = "✓ $w"
        if (s.foundCount >= s.total) bingoFinish()
    }

    fun bingoFinish() {
        if (screen == Screen.BingoGame) screen = Screen.BingoEnd
    }

    // ---- flashcards -----------------------------------------------------------------------

    private val decks = ArrayList<Deck>()

    var deckInfos by mutableStateOf<List<DeckInfo>>(emptyList())
        private set
    var deckNotice by mutableStateOf("")
    var busy by mutableStateOf(false)
        private set
    var addMessage by mutableStateOf("")
    var study by mutableStateOf<StudySession?>(null)
        private set

    private fun deck(id: Long): Deck? = decks.firstOrNull { it.id == id }

    private fun refreshDecks() {
        deckInfos = decks.map {
            DeckInfo(
                it.id, it.name, it.cards.size,
                it.count(CardState.NEW), it.count(CardState.LEARNING),
                it.count(CardState.REVIEW), it.count(CardState.FREE), it.dueCount()
            )
        }
    }

    private fun saveDecks() {
        val text = DeckStore.serialize(decks)
        io.execute {
            try {
                val tmp = File(decksFile.path + ".tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(decksFile)) {
                    decksFile.writeText(text)
                    tmp.delete()
                }
            } catch (e: Exception) {
                // ignore: next save will retry
            }
        }
    }

    fun addGenerated(deckId: Long?, name: String) {
        if (busy) return
        val g = genSettings
        busy = true
        addMessage = ""
        val exclude = deckId?.let { deck(it)?.racks() } ?: emptySet()
        viewModelScope.launch {
            val racks = withContext(Dispatchers.Default) {
                f.generate(
                    g.minLen, g.maxLen, g.count.coerceIn(1, 20000),
                    if (g.byProbability) PickMode.PROBABILITY else PickMode.RANDOM,
                    g.startRank.coerceAtLeast(1), exclude
                )
            }
            finishAdd(deckId, name, racks, "")
        }
    }

    fun addCustom(deckId: Long?, name: String, text: String) {
        if (busy) return
        val exclude = deckId?.let { deck(it)?.racks() } ?: emptySet()
        val good = LinkedHashSet<String>()
        val bad = ArrayList<String>()
        var dupes = 0
        for (token in text.split(Regex("[\\s,;]+"))) {
            if (token.isBlank()) continue
            val a = Letters.alphagram(Letters.normalize(token))
            if (a.length < 2 || d.wordsFor(a).isEmpty()) bad.add(token)
            else if (exclude.contains(a) || !good.add(a)) dupes++
        }
        val notes = ArrayList<String>()
        if (bad.isNotEmpty()) notes.add("No word uses all letters of: ${bad.joinToString(", ")}")
        if (dupes > 0) notes.add("$dupes duplicate rack(s) skipped")
        finishAdd(deckId, name, good.toList(), notes.joinToString("\n"))
    }

    private fun finishAdd(deckId: Long?, name: String, racks: List<String>, note: String) {
        busy = false
        if (racks.isEmpty()) {
            addMessage = if (note.isEmpty()) "No racks to add." else note
            return
        }
        val target = deckId?.let { deck(it) } ?: Deck(
            System.currentTimeMillis(),
            name.trim().ifEmpty { "Deck ${decks.size + 1}" }
        ).also { decks.add(it) }
        for (r in racks) target.cards.add(Card(r))
        saveDecks()
        refreshDecks()
        addMessage = ""
        deckNotice = "Added ${racks.size} racks." + if (note.isEmpty()) "" else "\n$note"
        screen = Screen.FlashDeck(target.id)
    }

    fun deleteDeck(id: Long) {
        decks.removeAll { it.id == id }
        saveDecks()
        refreshDecks()
        screen = Screen.FlashDecks
    }

    fun openStudy(deckId: Long) {
        val dk = deck(deckId) ?: return
        study = makeStudy(dk)
        screen = Screen.FlashStudy(deckId)
    }

    private fun makeStudy(dk: Deck): StudySession {
        val card = dk.nextCard()
        return StudySession(dk.id, card, if (card == null) emptyList() else d.wordsFor(card.rack))
    }

    fun studyShuffle() {
        val s = study ?: return
        s.shown = f.shuffle(s.shown)
    }

    fun studyReveal() {
        study?.revealed = true
    }

    fun studySubmit(raw: String) {
        val s = study ?: return
        if (s.revealed) return
        val w = Letters.normalize(raw)
        if (w.isEmpty()) return
        when {
            s.found.contains(w) -> s.message = "$w: already found"
            s.solutions.contains(w) -> {
                s.found = s.found + w
                s.message = "✓ $w"
                if (s.found.size >= s.solutions.size) s.revealed = true
            }
            else -> s.message = "$w: not valid (must use all letters)"
        }
    }

    fun gradeLabel(grade: Grade): String {
        val s = study ?: return ""
        val card = s.card ?: return ""
        val dk = deck(s.deckId) ?: return ""
        return Scheduler.preview(card, grade, dk.position)
    }

    fun studyAnswer(grade: Grade) {
        val s = study ?: return
        val card = s.card ?: return
        val dk = deck(s.deckId) ?: return
        dk.answer(card, grade)
        saveDecks()
        refreshDecks()
        study = makeStudy(dk)
    }

    // ---- loading --------------------------------------------------------------------------

    init {
        viewModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.Default) {
                    val dictionary = app.assets.open("ods.txt").bufferedReader().useLines { lines ->
                        Dictionary.fromLines(lines)
                    }
                    val stored = if (decksFile.exists()) DeckStore.parse(decksFile.readText()) else mutableListOf<Deck>()
                    Pair(dictionary, stored)
                }
                dict = loaded.first
                factory = RackFactory(loaded.first)
                decks.addAll(loaded.second)
                refreshDecks()
                screen = Screen.Menu
            } catch (e: Exception) {
                loadError = e.message ?: e.toString()
            }
        }
    }
}
