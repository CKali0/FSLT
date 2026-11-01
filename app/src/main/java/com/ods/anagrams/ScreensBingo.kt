package com.ods.anagrams

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun BingoSettingsScreen(vm: AppViewModel) {
    val s = vm.bingoSettings
    ScreenFrame("Bingo hunt", onBack = { vm.back() }) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            ChoiceRow(
                "Rack size",
                listOf("7 tiles" to 7, "8 tiles" to 8),
                s.size
            ) { n -> vm.updateBingoSettings(s.copy(size = n)) }
            SliderSetting(
                label = "Number of racks",
                valueText = s.racks.toString(),
                value = s.racks.toFloat(),
                range = 5f..100f,
                steps = 18,
                onChange = { vm.updateBingoSettings(s.copy(racks = (it / 5f).roundToInt() * 5)) }
            )
            TimeSlider(s.seconds) { vm.updateBingoSettings(s.copy(seconds = it)) }
        }
        AppButton(
            "Start",
            { vm.startBingoNew() },
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            primary = true
        )
    }
}

@Composable
fun BingoGameScreen(vm: AppViewModel) {
    val s = vm.bingoSession ?: return
    var confirmQuit by remember { mutableStateOf(false) }
    BackHandler { confirmQuit = true }
    val left = rememberSecondsLeft(s.endAtMs, s) { vm.bingoFinish() }
    val found = s.found
    val foundCount = found.sumOf { it.size }

    ScreenFrame(
        "Bingo hunt",
        trailing = {
            TextButton(onClick = { vm.bingoFinish() }) {
                Text("Give up", color = Pal.Accent, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TimerText(left)
            Text(
                "$foundCount / ${s.total} bingos found",
                color = Pal.Muted,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            )
        }
        LazyColumn(
            Modifier
                .weight(1f)
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(s.racks) { i, rack ->
                val total = s.answers[i].size
                val mine = found[i]
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (mine.size == total) Pal.Found else Pal.Surface)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RackRow(rack, Modifier.weight(1f), maxTile = 34.dp, center = false)
                        Text(
                            "${mine.size}/$total",
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                    if (mine.isNotEmpty()) {
                        Text(
                            mine.sorted().joinToString("   "),
                            fontSize = 14.sp,
                            color = Pal.Ok,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
        FeedbackLine(s.message)
        WordInput { vm.bingoSubmit(it) }
    }

    if (confirmQuit) {
        AlertDialog(
            onDismissRequest = { confirmQuit = false },
            title = { Text("Quit this game?") },
            text = { Text("Your progress will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmQuit = false
                    vm.go(Screen.BingoSettings)
                }) { Text("Quit", color = Pal.Bad) }
            },
            dismissButton = {
                TextButton(onClick = { confirmQuit = false }) { Text("Keep playing", color = Pal.Accent) }
            }
        )
    }
}

@Composable
fun BingoEndScreen(vm: AppViewModel) {
    val s = vm.bingoSession ?: return
    val count = s.foundCount
    ScreenFrame("Bingo hunt") {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                if (count >= s.total) "All bingos found!" else "Time to review",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text("Found $count of ${s.total} bingos on ${s.racks.size} racks", color = Pal.Muted)
            Spacer(Modifier.height(8.dp))
            AppButton("Show answers", { vm.go(Screen.BingoAnswers) }, Modifier.fillMaxWidth())
            AppButton("Restart with the same racks", { vm.startBingoSame() }, Modifier.fillMaxWidth())
            AppButton("New racks", { vm.startBingoNew() }, Modifier.fillMaxWidth(), primary = true)
            AppButton("Settings", { vm.go(Screen.BingoSettings) }, Modifier.fillMaxWidth())
            AppButton("Main menu", { vm.go(Screen.Menu) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun BingoAnswersScreen(vm: AppViewModel) {
    val s = vm.bingoSession ?: return
    ScreenFrame("Answers", onBack = { vm.back() }) {
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(s.racks) { i, rack ->
                Column(Modifier.fillMaxWidth()) {
                    RackRow(rack, maxTile = 30.dp, center = false, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                    for (w in s.answers[i]) WordRow(w, s.found[i].contains(w))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
