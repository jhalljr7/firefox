/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * SPORTS CORE / CLUB CONFIG: what makes a widget about one particular club.
 *
 * What: a [Club] is a team plus its links, copy and colours. [Wrexham] is the only one so far.
 * Used by: ClubWidget.kt (header, links, messages, confetti), the celebrations (the followed team),
 * and the app's WrexhamWidgetSection.
 * Placeholder: Wrexham's team key ("WRE") until the API spec gives the real one.
 */

package org.mozilla.fenix.sports

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color

/**
 * Everything that makes a club widget about one particular club. The card itself ([ClubWidget]) is generic; adding
 * another club means adding another [Club], not another widget. A league or global widget would follow several teams
 * instead of one, reusing the same core.
 *
 * @property team The club followed by the widget. Its goals and wins trigger the celebrations.
 * @property matchesUrl Club page with fixtures.
 * @property resultsUrl Club page with results.
 * @property standingsUrl Club page with the league table.
 * @property noUpcomingTitle Message when the club has no upcoming games.
 * @property seasonOverTitle Message once the club's season is over.
 * @property celebrationColors Confetti colours, club colour first; the "GOAL!" banner uses the first.
 */
data class Club(
    val team: Team,
    val matchesUrl: String,
    val resultsUrl: String,
    val standingsUrl: String,
    @param:StringRes val noUpcomingTitle: Int,
    @param:StringRes val seasonOverTitle: Int,
    val celebrationColors: List<Color>,
)

/**
 * Wrexham AFC. The team key is a placeholder until the API spec gives the real one. The club site has one page per team
 * with Fixtures, Results and Table tabs, so the three links share a URL.
 */
val Wrexham =
    Club(
        team = Team(key = "WRE", name = "Wrexham AFC", crestRes = R.drawable.sports_crest_wrexham),
        matchesUrl = "https://www.wrexhamafc.co.uk/matches/mens-team",
        resultsUrl = "https://www.wrexhamafc.co.uk/matches/mens-team",
        standingsUrl = "https://www.wrexhamafc.co.uk/matches/mens-team",
        noUpcomingTitle = R.string.sports_wrexham_no_upcoming,
        seasonOverTitle = R.string.sports_wrexham_season_over,
        // From the design: Wrexham red, white, gold, purple, pink, green.
        celebrationColors =
            listOf(
                Color(0xFFC8102E),
                Color.White,
                Color(0xFFF6C343),
                Color(0xFF764EDD),
                Color(0xFFFF8A9C),
                Color(0xFF4CC38A),
            ),
    )
