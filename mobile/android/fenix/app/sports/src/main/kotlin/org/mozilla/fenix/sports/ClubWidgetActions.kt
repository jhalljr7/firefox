/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * CLUB WIDGET / ACTIONS: the card's callbacks into the app.
 *
 * What: the card doesn't know about tabs, navigation or settings; it reports taps through these callbacks.
 * Built by: the app's WrexhamWidgetSection, which wires each one to real app behaviour.
 */

package org.mozilla.fenix.sports

/**
 * Callbacks from a club widget to the app.
 *
 * @property onOpenUrl Opens a club or match page in a new tab.
 * @property onWallpapers Opens the wallpaper settings.
 * @property onShare Shares a match, or the club when null.
 * @property onRemove Hides the widget.
 * @property onRefresh Retries loading match data.
 */
data class ClubWidgetActions(
    val onOpenUrl: (String) -> Unit = {},
    val onWallpapers: () -> Unit = {},
    val onShare: (Match?) -> Unit = {},
    val onRemove: () -> Unit = {},
    val onRefresh: () -> Unit = {},
)
