/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * APP GLUE / HOMEPAGE SECTION: puts the sports module's Wrexham card on the homepage.
 *
 * Called by: Homepage.kt, right after the shortcuts, with AppState.wrexhamWidgetState (via HomepageState).
 * Calls: the sports module's ClubWidget, plus app services for taps (tabs, navigation, share, settings).
 */

package org.mozilla.fenix.home.sports

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.Navigation
import mozilla.components.support.ktx.android.content.share
import org.mozilla.fenix.Config
import org.mozilla.fenix.NavGraphDirections
import org.mozilla.fenix.R
import org.mozilla.fenix.components.appstate.AppAction.WrexhamWidgetAction
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.openToBrowser
import org.mozilla.fenix.sports.ClubWidget
import org.mozilla.fenix.sports.ClubWidgetActions
import org.mozilla.fenix.sports.ClubWidgetState
import org.mozilla.fenix.sports.R as sportsR
import org.mozilla.fenix.sports.RemovedClubWidgetRow
import org.mozilla.fenix.sports.Wrexham
import org.mozilla.fenix.sports.WrexhamScenario
import org.mozilla.fenix.sports.WrexhamScenarioPicker

/**
 * The homepage's Wrexham AFC widget: the app-side glue around the sports module's [ClubWidget]. It wires the card's
 * taps to real app behaviour (new tab, wallpaper settings, system share, hiding the widget) and handles
 * remove-with-undo. The data comes from the AppStore; see [WrexhamWidgetMiddleware].
 *
 * Removing the widget swaps it for an "Undo" row. The hidden setting is only saved when the section leaves the screen
 * (the homepage is left), because the homepage drops the whole section as soon as that setting is off, which would take
 * the Undo row with it. After that the widget stays gone until turned back on in Settings > Homepage.
 *
 * @param state The widget state from the AppStore.
 * @param modifier [Modifier] for the section.
 */
@Composable
fun WrexhamWidgetSection(
    state: ClubWidgetState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val components = context.components
    var removed by remember { mutableStateOf(false) }
    val removedOnLeave by rememberUpdatedState(removed)
    DisposableEffect(Unit) {
        onDispose { if (removedOnLeave) components.settings.showHomepageWrexhamWidget = false }
    }

    if (removed) {
        RemovedClubWidgetRow(club = Wrexham, onUndo = { removed = false }, modifier = modifier)
        return
    }

    // Debug builds only: the menu's "Demo: switch scenario" item opens this picker. A pick saves the same setting
    // Secret Settings uses and forces a reload, so the card switches straight away.
    var pickingScenario by remember { mutableStateOf(false) }
    if (pickingScenario) {
        WrexhamScenarioPicker(
            current = WrexhamScenario.from(components.settings.wrexhamWidgetScenario),
            onPick = {
                components.settings.wrexhamWidgetScenario = it.name
                components.appStore.dispatch(WrexhamWidgetAction.Refresh(force = true))
                pickingScenario = false
            },
            onDismiss = { pickingScenario = false },
        )
    }

    ClubWidget(
        club = Wrexham,
        state = state,
        modifier = modifier,
        actions =
            ClubWidgetActions(
                onOpenUrl = { url ->
                    components.useCases.fenixBrowserUseCases.loadUrlOrSearch(searchTermOrURL = url, newTab = true)
                    (context as? Activity)?.let { Navigation.findNavController(it, R.id.container).openToBrowser() }
                },
                onWallpapers = {
                    (context as? Activity)?.let {
                        Navigation.findNavController(it, R.id.container)
                            .navigate(NavGraphDirections.actionGlobalWallpaperSettingsFragment())
                    }
                },
                onShare = { match ->
                    val text =
                        match?.let {
                            context.getString(
                                sportsR.string.sports_share_match,
                                it.home.name,
                                it.away.name,
                                it.competition,
                                it.url,
                            )
                        } ?: context.getString(sportsR.string.sports_share_club, Wrexham.team.name, Wrexham.matchesUrl)
                    context.share(text, subject = Wrexham.team.name)
                },
                onRemove = { removed = true },
                onRefresh = { components.appStore.dispatch(WrexhamWidgetAction.Refresh(force = true)) },
                onDemoScenarios = { pickingScenario = true }.takeIf { Config.channel.isDebug },
            ),
    )
}
