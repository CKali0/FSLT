package com.ods.anagrams

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RackSettingsScreen(vm: AppViewModel) {
    val s = vm.rackSettings
    ScreenFrame("Rack solve", onBack = { vm.back() }) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            ChoiceRow(
                "Rack size",
                listOf("7 tiles" to 7, "8 tiles" to 8),
                s.size
            ) { n ->
                vm.updateRackSettings(s.copy(size = n, maxLen = if (s.maxLen == s.size) n else s.maxLen))
            }
            Stepper("Shortest word to find", s.minLen, 2, s.maxLen) { vm.updateRackSettings(s.copy(minLen = it)) }
            Stepper("Longest word to find", s.maxLen, s.minLen, s.size) { vm.updateRackSettings(s.copy(maxLen = it)) }
            TimeSlider(s.seconds) { vm.updateRackSettings(s.copy(seconds = it)) }
        }
        AppButton(
            "Start",
            { vm.startRackNew() },
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            primary = true
        )
    }
}

@Composable
fun RackGameScreen(vm: AppViewModel) {
    val s = vm.rackSession ?: return
    var confirmQuit by remember { mutableStateOf(false) }
    BackHandler { confirmQuit = true }
    val left = rememberSecondsLeft(s.endAtMs, s) { vm.rackFinish() }
    val found = s.found

    ScreenFrame(
        "Rack solve",
        trailing = {
            TextButton(onClick = { vm.rackFinish() }) {
                Text("Give up", color = Pal.Accent, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TimerText(left)
            Text(
                "${found.size} / ${s.total} found",
                color = Pal.Muted,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        RackRow(s.shown, onClick = { vm.rackShuffle() })
        Text(
            "tap the rack to shuffle",
            color = Pal.Muted,
            fontSize = 12.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        )
        LazyColumn(Modifier.weight(1f)) {
            for ((len, words) in s.solutions) {
                val mine = words.filter { found.contains(it) }
                item(key = "h$len") {
                    SectionHeader("$len letters", "${mine.size} / ${words.size}", mine.size == words.size)
                }
                if (mine.isNotEmpty()) {
                    item(key = "w$len") {
                        Text(mine.joinToString("    "), fontSize = 17.sp, color = Pal.Text)
                    }
                }
            }
        }
        FeedbackLine(s.message)
        WordInput { vm.rackSubmit(it) }
    }

    if (confirmQuit) {
        AlertDialog(
            onDismissRequest = { confirmQuit = false },
            title = { Text("Quit this game?") },
            text = { Text("Your progress will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmQuit = false
                    vm.go(Screen.RackSettings)
                }) { Text("Quit", color = Pal.Bad) }
            },
            dismissButton = {
                TextButton(onClick = { confirmQuit = false }) { Text("Keep playing", color = Pal.Accent) }
            }
        )
    }
}

@Composable
fun RackEndScreen(vm: AppViewModel) {
    val s = vm.rackSession ?: return
    ScreenFrame("Rack solve") {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            RackRow(s.rack)
            Text(
                if (s.found.size >= s.total) "All words found!" else "Time to review",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text("Found ${s.found.size} of ${s.total} words", color = Pal.Muted)
            Spacer(Modifier.height(8.dp))
            AppButton("Show answers", { vm.go(Screen.RackAnswers) }, Modifier.fillMaxWidth())
            AppButton("Restart with the same rack", { vm.startRackSame() }, Modifier.fillMaxWidth())
            AppButton("New rack", { vm.startRackNew() }, Modifier.fillMaxWidth(), primary = true)
            AppButton("Settings", { vm.go(Screen.RackSettings) }, Modifier.fillMaxWidth())
            AppButton("Main menu", { vm.go(Screen.Menu) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun RackAnswersScreen(vm: AppViewModel) {
    val s = vm.rackSession ?: return
    ScreenFrame("Answers", onBack = { vm.back() }) {
        RackRow(s.rack, maxTile = 36.dp)
        LazyColumn(Modifier.weight(1f)) {
            for ((len, words) in s.solutions) {
                val n = words.count { s.found.contains(it) }
                item(key = "h$len") { SectionHeader("$len letters", "$n / ${words.size}", n == words.size) }
                items(words) { w -> WordRow(w, s.found.contains(w)) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
