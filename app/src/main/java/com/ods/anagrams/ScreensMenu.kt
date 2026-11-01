package com.ods.anagrams

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun App(vm: AppViewModel) {
    BackHandler(enabled = vm.backEnabled) { vm.back() }
    when (val s = vm.screen) {
        Screen.Loading -> LoadingScreen(vm)
        Screen.Menu -> MenuScreen(vm)
        Screen.RackSettings -> RackSettingsScreen(vm)
        Screen.RackGame -> RackGameScreen(vm)
        Screen.RackEnd -> RackEndScreen(vm)
        Screen.RackAnswers -> RackAnswersScreen(vm)
        Screen.BingoSettings -> BingoSettingsScreen(vm)
        Screen.BingoGame -> BingoGameScreen(vm)
        Screen.BingoEnd -> BingoEndScreen(vm)
        Screen.BingoAnswers -> BingoAnswersScreen(vm)
        Screen.FlashDecks -> FlashDecksScreen(vm)
        is Screen.FlashDeck -> FlashDeckScreen(vm, s.deckId)
        is Screen.FlashAdd -> FlashAddScreen(vm, s.deckId)
        is Screen.FlashStudy -> FlashStudyScreen(vm, s.deckId)
    }
}

@Composable
fun LoadingScreen(vm: AppViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Pal.Bg)
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val err = vm.loadError
        if (err == null) {
            CircularProgressIndicator(color = Pal.Accent)
            Spacer(Modifier.height(16.dp))
            Text("Loading dictionary…", color = Pal.Muted)
        } else {
            Text("Could not load the dictionary", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Text(err, color = Pal.Bad, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun MenuScreen(vm: AppViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Pal.Bg)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("ODS Anagrams", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Pal.Accent)
        Text("Train your Scrabble anagrams", color = Pal.Muted, modifier = Modifier.padding(bottom = 32.dp))
        MenuButton("Rack solve") { vm.go(Screen.RackSettings) }
        Spacer(Modifier.height(12.dp))
        MenuButton("Bingo hunt") { vm.go(Screen.BingoSettings) }
        Spacer(Modifier.height(12.dp))
        MenuButton("Flashcards") { vm.go(Screen.FlashDecks) }
    }
}

@Composable
private fun MenuButton(text: String, onClick: () -> Unit) {
    AppButton(
        text = text,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
    )
}
