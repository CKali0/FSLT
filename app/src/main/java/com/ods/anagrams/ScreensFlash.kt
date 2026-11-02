package com.ods.anagrams

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ods.anagrams.engine.Grade

@Composable
fun FlashDecksScreen(vm: AppViewModel) {
    val infos = vm.deckInfos
    ScreenFrame("Flashcards", onBack = { vm.back() }) {
        if (infos.isEmpty()) {
            Text(
                "No decks yet. Create one to start learning racks.",
                color = Pal.Muted,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 24.dp)
            )
        } else {
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(infos, key = { it.id }) { info ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Pal.Surface)
                            .clickable { vm.go(Screen.FlashDeck(info.id)) }
                            .padding(16.dp)
                    ) {
                        Text(info.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${info.total} racks · ${info.fresh} new · ${info.due} due",
                            color = Pal.Muted,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
        AppButton(
            "New deck",
            { vm.addMessage = ""; vm.go(Screen.FlashAdd(null)) },
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            primary = true
        )
    }
}

@Composable
fun FlashDeckScreen(vm: AppViewModel, deckId: Long) {
    val info = vm.deckInfos.firstOrNull { it.id == deckId }
    var confirmDelete by remember { mutableStateOf(false) }
    if (info == null) {
        ScreenFrame("Deck", onBack = { vm.go(Screen.FlashDecks) }) {
            Text("This deck no longer exists.", color = Pal.Muted)
        }
        return
    }
    ScreenFrame(info.name, onBack = { vm.deckNotice = ""; vm.back() }) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            if (vm.deckNotice.isNotEmpty()) {
                Text(
                    vm.deckNotice,
                    color = Pal.Ok,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            StatLine("Racks", info.total)
            StatLine("New", info.fresh)
            StatLine("Learning", info.learning)
            StatLine("Review", info.review)
            StatLine("Free (retired)", info.free)
            StatLine("Due now", info.due)
            Spacer(Modifier.height(24.dp))
            AppButton(
                "Study",
                { vm.deckNotice = ""; vm.openStudy(deckId) },
                Modifier.fillMaxWidth(),
                primary = true,
                enabled = info.free < info.total
            )
            Spacer(Modifier.height(12.dp))
            AppButton(
                "Add racks",
                { vm.deckNotice = ""; vm.addMessage = ""; vm.go(Screen.FlashAdd(deckId)) },
                Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            AppButton("Delete deck", { confirmDelete = true }, Modifier.fillMaxWidth())
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${info.name}\"?") },
            text = { Text("All progress in this deck will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteDeck(deckId)
                }) { Text("Delete", color = Pal.Bad) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = Pal.Accent) }
            }
        )
    }
}

@Composable
private fun StatLine(label: String, value: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(label, color = Pal.Muted, modifier = Modifier.weight(1f))
        Text(value.toString(), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun FlashAddScreen(vm: AppViewModel, deckId: Long?) {
    val g = vm.genSettings
    var source by remember { mutableIntStateOf(0) } // 0 = generate, 1 = custom
    var name by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf("") }

    ScreenFrame(if (deckId == null) "New deck" else "Add racks", onBack = { vm.back() }) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            if (deckId == null) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Deck name") },
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
            ChoiceRow(
                "Racks come from",
                listOf("Generated list" to 0, "My own racks" to 1),
                source
            ) { source = it }

            if (source == 0) {
                Stepper("Shortest rack", g.minLen, 2, g.maxLen) { vm.updateGenSettings(g.copy(minLen = it)) }
                Stepper("Longest rack", g.maxLen, g.minLen, 15) { vm.updateGenSettings(g.copy(maxLen = it)) }
                NumberField("Number of racks", g.count) { vm.updateGenSettings(g.copy(count = it)) }
                ChoiceRow(
                    "Pick racks",
                    listOf("By probability" to true, "Random" to false),
                    g.byProbability
                ) { vm.updateGenSettings(g.copy(byProbability = it)) }
                if (g.byProbability) {
                    Text(
                        "Racks are ranked, for each length, by how many ways they can be drawn from the bag. " +
                            "The most likely racks come first, so you learn the ones you will actually meet.",
                        color = Pal.Muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    NumberField("Start at probability rank", g.startRank) { vm.updateGenSettings(g.copy(startRank = it)) }
                } else {
                    Text(
                        "Racks are picked at random, whatever their probability.",
                        color = Pal.Muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
                Text(
                    "The count is split evenly across the lengths. Only racks with at least one word using all letters are included.",
                    color = Pal.Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 10.dp)
                )
            } else {
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it.uppercase() },
                    label = { Text("Racks (separated by spaces or new lines)") },
                    minLines = 5,
                    colors = fieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                )
            }
            if (vm.addMessage.isNotEmpty()) {
                Text(vm.addMessage, color = Pal.Bad, modifier = Modifier.padding(top = 12.dp))
            }
            Spacer(Modifier.height(12.dp))
        }
        AppButton(
            if (vm.busy) "Working…" else if (deckId == null) "Create deck" else "Add racks",
            {
                if (source == 0) vm.addGenerated(deckId, name) else vm.addCustom(deckId, name, custom)
            },
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            primary = true,
            enabled = !vm.busy
        )
    }
}

@Composable
fun FlashStudyScreen(vm: AppViewModel, deckId: Long) {
    val s = vm.study
    val info = vm.deckInfos.firstOrNull { it.id == deckId }
    val title = info?.name ?: "Flashcards"
    if (s == null || s.card == null) {
        ScreenFrame(title, onBack = { vm.back() }) {
            Text(
                "Every rack in this deck is marked Free. Nothing left to study.",
                color = Pal.Muted,
                modifier = Modifier.padding(top = 24.dp)
            )
        }
        return
    }
    val revealed = s.revealed
    val found = s.found
    val total = s.solutions.size

    ScreenFrame(
        title,
        onBack = { vm.back() },
        trailing = {
            if (!revealed) {
                TextButton(onClick = { vm.studyReveal() }) {
                    Text("Show answers", color = Pal.Accent, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) {
        Text(
            (if (total == 1) "1 word" else "$total words") + " use all the letters" +
                (info?.let { "   ·   ${it.fresh} new, ${it.due} due" } ?: ""),
            color = Pal.Muted,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(12.dp))
        RackRow(s.shown)
        RackControls(onShuffle = { vm.studyShuffle() }, onSort = { vm.studySort() })

        if (!revealed) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("${found.size} / $total found", color = Pal.Muted, modifier = Modifier.padding(vertical = 8.dp))
                Text(found.sorted().joinToString("    "), fontSize = 18.sp)
            }
            FeedbackLine(s.message)
            WordInput { vm.studySubmit(it) }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(s.solutions) { w -> WordRow(w, found.contains(w)) }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GradeButton("Missed", vm.gradeLabel(Grade.MISSED), Color(0xFF8E3B3B), Modifier.weight(1f)) {
                    vm.studyAnswer(Grade.MISSED)
                }
                GradeButton("Hard", vm.gradeLabel(Grade.HARD), Color(0xFF8A6A2C), Modifier.weight(1f)) {
                    vm.studyAnswer(Grade.HARD)
                }
                GradeButton("Easy", vm.gradeLabel(Grade.EASY), Color(0xFF2F7A4B), Modifier.weight(1f)) {
                    vm.studyAnswer(Grade.EASY)
                }
                GradeButton("Free", vm.gradeLabel(Grade.FREE), Color(0xFF4A5057), Modifier.weight(1f)) {
                    vm.studyAnswer(Grade.FREE)
                }
            }
        }
    }
}

@Composable
private fun GradeButton(
    title: String,
    sub: String,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        contentPadding = tightButtonPadding,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, textAlign = TextAlign.Center)
            Text(sub, fontSize = 11.sp, textAlign = TextAlign.Center, color = Color(0xFFDDDDDD))
        }
    }
}
