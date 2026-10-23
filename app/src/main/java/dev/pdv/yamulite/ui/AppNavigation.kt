package dev.pdv.yamulite.ui

import androidx.compose.runtime.Composable
import dev.pdv.yamulite.ui.main.MainScreen

/** No sign-in step any more: music comes from YouTube Music, so the app opens straight to the main UI. */
@Composable
fun AppRoot() {
    MainScreen()
}
