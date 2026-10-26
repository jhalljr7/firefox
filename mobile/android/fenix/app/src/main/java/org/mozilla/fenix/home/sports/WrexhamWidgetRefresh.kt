/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * APP GLUE / WHEN TO LOAD: dispatches Refresh while the homepage is on screen.
 *
 * Called by: HomeFragment.onViewCreated.
 */

package org.mozilla.fenix.home.sports

import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mozilla.fenix.components.AppStore
import org.mozilla.fenix.components.appstate.AppAction.WrexhamWidgetAction
import org.mozilla.fenix.sports.refreshIntervalMs

/** How often the loop checks whether a refresh is due. Matches the shortest refresh interval. */
private const val TICK_MS = 60_000L

/**
 * Keeps the Wrexham widget's data fresh while the homepage is on screen: a refresh on every resume (when the World Cup
 * widget fetched), then once a minute it checks whether the latest data's `refreshIntervalMs` has passed (every minute
 * during a match, every 5 on match day, every 30 otherwise). Stops while the homepage is in the background. The
 * middleware throttles and de-duplicates, so a stray extra refresh is cheap.
 *
 * @param appStore Store to dispatch refreshes to and read the latest widget state from.
 * @param isShown Whether the widget is enabled and visible; nothing is fetched otherwise.
 */
fun LifecycleOwner.refreshWrexhamWidgetWhileResumed(
    appStore: AppStore,
    isShown: () -> Boolean,
) {
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var lastRefresh: Long? = null
            while (true) {
                val now = SystemClock.elapsedRealtime()
                val due = lastRefresh?.let { now - it >= appStore.state.wrexhamWidgetState.refreshIntervalMs() } ?: true
                if (isShown() && due) {
                    appStore.dispatch(WrexhamWidgetAction.Refresh())
                    lastRefresh = now
                }
                delay(TICK_MS)
            }
        }
    }
}
