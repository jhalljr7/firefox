/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * APP GLUE / DATA LOADING: turns Refresh actions into loads, and loads into Loaded or Failed actions.
 *
 * Registered in: Components.kt (the AppStore's middleware list), with the repository to load from.
 * Calls: the sports module's SportsLoader. The reducer (AppStoreReducer.kt) stores the result in AppState.
 */

package org.mozilla.fenix.home.sports

import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store
import org.mozilla.fenix.components.appstate.AppAction
import org.mozilla.fenix.components.appstate.AppAction.WrexhamWidgetAction
import org.mozilla.fenix.components.appstate.AppState
import org.mozilla.fenix.sports.SportsLoader

/**
 * Loads the Wrexham widget's data into the AppStore, like the World Cup widget's `SportsWidgetMiddleware`. On
 * [WrexhamWidgetAction.Refresh] it asks [loader] for fresh data (the loader throttles, skips while offline and drops
 * duplicate requests) and dispatches [WrexhamWidgetAction.Loaded] or [WrexhamWidgetAction.Failed]. Refreshes come from
 * [refreshWrexhamWidgetWhileResumed] and the widget's Refresh button.
 *
 * @param loader Loads from the widget's repository with the request guards.
 */
class WrexhamWidgetMiddleware(private val loader: SportsLoader) : Middleware<AppState, AppAction> {
    override fun invoke(
        store: Store<AppState, AppAction>,
        next: (AppAction) -> Unit,
        action: AppAction,
    ) {
        next(action)
        if (action is WrexhamWidgetAction.Refresh) {
            loader.refresh(force = action.force) { result ->
                store.dispatch(result.fold({ WrexhamWidgetAction.Loaded(it) }, { WrexhamWidgetAction.Failed }))
            }
        }
    }
}
