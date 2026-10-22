/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.home.wrexham

import androidx.annotation.DrawableRes
import org.mozilla.fenix.R

/**
 * A club shown on the Wrexham widget.
 *
 * @property name Display name of the club.
 * @property crestRes Drawable resource for the club crest.
 */
data class WrexhamTeam(
    val name: String,
    @param:DrawableRes val crestRes: Int,
)

/**
 * A single fixture shown on the Wrexham widget.
 *
 * @property competition Name of the competition, e.g. "EFL Championship".
 * @property home Home club.
 * @property away Away club.
 * @property dayLabel Relative or absolute day of kickoff, e.g. "Today" or "28/10/26".
 * @property timeLabel Kickoff time, already formatted for display.
 */
data class WrexhamMatch(
    val competition: String,
    val home: WrexhamTeam,
    val away: WrexhamTeam,
    val dayLabel: String,
    val timeLabel: String,
)

/**
 * State of the Wrexham widget on the homepage.
 *
 * @property title Card title.
 * @property crestRes Drawable resource for the crest next to the title.
 * @property matches Upcoming matches, soonest first.
 */
data class WrexhamWidgetState(
    val title: String = "Wrexham AFC",
    @param:DrawableRes val crestRes: Int = R.drawable.wrexham_crest_wrexham,
    val matches: List<WrexhamMatch>,
)

/** Dummy data used until the Merino club endpoints exist. */
object WrexhamFakeData {
    private val wrexham = WrexhamTeam("Wrexham AFC", R.drawable.wrexham_crest_wrexham)
    private val charlton = WrexhamTeam("Charlton", R.drawable.wrexham_crest_charlton)
    private val manUnited = WrexhamTeam("Man United", R.drawable.wrexham_crest_manunited)
    private val southampton = WrexhamTeam("Southampton", R.drawable.wrexham_crest_southampton)

    val upcoming = WrexhamWidgetState(
        matches = listOf(
            WrexhamMatch("EFL Championship", charlton, wrexham, "Today", "2:45 PM"),
            WrexhamMatch("FA Cup", wrexham, manUnited, "12/10/26", "6:00 PM"),
            WrexhamMatch("EFL Championship", wrexham, southampton, "28/10/26", "2:45 PM"),
        ),
    )
}
