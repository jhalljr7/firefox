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

/** A status tag shown before the competition name. */
enum class WrexhamTag(val label: String) {
    LIVE("LIVE"),
    DELAYED("Delayed"),
    SUSPENDED("Suspended"),
    POSTPONED("Postponed"),
}

/** Which side, if any, has received a red card. */
enum class WrexhamSide { HOME, AWAY }

/**
 * A single fixture shown on the Wrexham widget.
 *
 * @property competition Name of the competition, e.g. "EFL Championship".
 * @property home Home club.
 * @property away Away club.
 * @property homeScore Home score, or null before kickoff.
 * @property awayScore Away score, or null before kickoff.
 * @property dayLabel Day of kickoff shown above [detail] before kickoff, e.g. "Today".
 * @property detail Line under the score or day, e.g. "2:45 PM", "29'", "Halftime", "FT".
 * @property tag Optional status tag shown before the competition.
 * @property redCard Side that has received a red card, if any.
 * @property url Page to open when the match is tapped.
 */
data class WrexhamMatch(
    val competition: String,
    val home: WrexhamTeam,
    val away: WrexhamTeam,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val dayLabel: String? = null,
    val detail: String,
    val tag: WrexhamTag? = null,
    val redCard: WrexhamSide? = null,
    val url: String = WrexhamLinks.MATCHES,
) {
    val isLive: Boolean get() = tag == WrexhamTag.LIVE
}

/** What the card body shows. */
sealed interface WrexhamContent {
    /** One or more matches; more than one shows as a carousel ending in a "Full calendar" button. */
    data class Matches(val matches: List<WrexhamMatch>) : WrexhamContent

    /** The team has no upcoming games. */
    data object NoUpcoming : WrexhamContent

    /** Match data couldn't load. */
    data object Error : WrexhamContent

    /** The season is over. */
    data object SeasonOver : WrexhamContent
}

/**
 * State of the Wrexham widget on the homepage.
 *
 * @property title Card title.
 * @property crestRes Drawable resource for the crest next to the title.
 * @property content What the card body shows.
 */
data class WrexhamWidgetState(
    val title: String = "Wrexham AFC",
    @param:DrawableRes val crestRes: Int = R.drawable.wrexham_crest_wrexham,
    val content: WrexhamContent,
)

/** Pages on the club site. The site has no separate links for results or the table yet. */
object WrexhamLinks {
    const val MATCHES = "https://www.wrexhamafc.co.uk/matches/mens-team"
    const val RESULTS = MATCHES
    const val STANDINGS = MATCHES
}

/** Dummy scenarios used until the Merino club endpoints exist. Switch between them in Secret Settings. */
enum class WrexhamScenario(val label: String) {
    UPCOMING("Upcoming matches"),
    LIVE("Live"),
    HALFTIME("Halftime"),
    FULL_TIME_RED_CARD("Full time with red card"),
    CUP_EXTRA_TIME("FA Cup extra time"),
    CUP_PENALTIES("FA Cup penalties"),
    DELAYED("Delayed"),
    SUSPENDED("Suspended"),
    POSTPONED("Postponed"),
    NO_UPCOMING("No upcoming games"),
    ERROR("Error"),
    SEASON_OVER("Season over"),
}

/** Dummy data for each [WrexhamScenario]. */
object WrexhamFakeData {
    private const val EFL = "EFL Championship"
    private const val FA_CUP = "FA Cup"

    private val wrexham = WrexhamTeam("Wrexham AFC", R.drawable.wrexham_crest_wrexham)
    private val charlton = WrexhamTeam("Charlton", R.drawable.wrexham_crest_charlton)
    private val manUnited = WrexhamTeam("Man United", R.drawable.wrexham_crest_manunited)
    private val southampton = WrexhamTeam("Southampton", R.drawable.wrexham_crest_southampton)
    private val westHam = WrexhamTeam("West Ham", R.drawable.wrexham_crest_west_ham)

    fun forScenario(scenario: WrexhamScenario): WrexhamWidgetState {
        val content = when (scenario) {
            WrexhamScenario.UPCOMING -> WrexhamContent.Matches(
                listOf(
                    WrexhamMatch(EFL, charlton, wrexham, dayLabel = "Today", detail = "2:45 PM"),
                    WrexhamMatch(FA_CUP, wrexham, manUnited, dayLabel = "12/10/26", detail = "6:00 PM"),
                    WrexhamMatch(EFL, wrexham, southampton, dayLabel = "28/10/26", detail = "2:45 PM"),
                ),
            )
            WrexhamScenario.LIVE -> live(WrexhamMatch(EFL, westHam, wrexham, 1, 2, detail = "29'", tag = WrexhamTag.LIVE))
            WrexhamScenario.HALFTIME ->
                live(WrexhamMatch(EFL, westHam, wrexham, 1, 1, detail = "Halftime", tag = WrexhamTag.LIVE))
            WrexhamScenario.FULL_TIME_RED_CARD ->
                live(WrexhamMatch(EFL, westHam, wrexham, 1, 1, detail = "FT", redCard = WrexhamSide.HOME))
            WrexhamScenario.CUP_EXTRA_TIME ->
                live(WrexhamMatch(FA_CUP, manUnited, wrexham, 1, 1, detail = "ET 112'", tag = WrexhamTag.LIVE))
            WrexhamScenario.CUP_PENALTIES ->
                live(
                    WrexhamMatch(
                        FA_CUP, westHam, wrexham, 1, 1,
                        detail = "4 - 5\n(FT - Penalties)",
                        redCard = WrexhamSide.HOME,
                    ),
                )
            WrexhamScenario.DELAYED ->
                live(WrexhamMatch(EFL, westHam, wrexham, 0, 0, detail = "Starting\n3:00 PM", tag = WrexhamTag.DELAYED))
            WrexhamScenario.SUSPENDED ->
                live(WrexhamMatch(EFL, westHam, wrexham, 1, 1, detail = "", tag = WrexhamTag.SUSPENDED))
            WrexhamScenario.POSTPONED ->
                live(
                    WrexhamMatch(EFL, westHam, wrexham, 0, 0, detail = "Oct 21, 2026\n2:45 PM", tag = WrexhamTag.POSTPONED),
                )
            WrexhamScenario.NO_UPCOMING -> WrexhamContent.NoUpcoming
            WrexhamScenario.ERROR -> WrexhamContent.Error
            WrexhamScenario.SEASON_OVER -> WrexhamContent.SeasonOver
        }
        return WrexhamWidgetState(content = content)
    }

    private fun live(match: WrexhamMatch) = WrexhamContent.Matches(listOf(match))
}
