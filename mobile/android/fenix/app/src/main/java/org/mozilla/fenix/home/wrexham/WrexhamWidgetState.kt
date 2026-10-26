/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * WREXHAM AFC HOMEPAGE WIDGET: DATA MODEL (prototype only, never meant to land)
 *
 * Everything the card displays is described by a [WrexhamWidgetState]. The model holds facts
 * (scores, kickoff times, a structured [WrexhamMatchStatus]), never display strings; the UI turns
 * them into text like "29'" or "Today". That keeps the future API mapping a straight field-to-field
 * copy. Where the data comes from: WrexhamRepository.kt. Dummy data: WrexhamFakeData.kt.
 *
 * Team crests are design assets from the drop (res/drawable-nodpi/wrexham_crest_*). They're club
 * trademarks, so builds with them stay internal.
 */

package org.mozilla.fenix.home.wrexham

import androidx.annotation.DrawableRes
import java.time.LocalDateTime
import org.mozilla.fenix.R

/**
 * A club shown on the Wrexham widget.
 *
 * @property key Stable team identifier from the data feed. Used to tell which side is Wrexham.
 * @property name Display name of the club.
 * @property crestRes Bundled crest, or null when we don't ship one for this club.
 */
data class WrexhamTeam(
    val key: String,
    val name: String,
    @param:DrawableRes val crestRes: Int?,
)

/** Wrexham AFC itself. The key is a placeholder until the API spec gives us the real one. */
val Wrexham = WrexhamTeam(key = "WRE", name = "Wrexham AFC", crestRes = R.drawable.wrexham_crest_wrexham)

/**
 * Where a match is in its life cycle. Mirrors the World Cup widget's `MatchStatus`, which was mapped from the same kind
 * of feed (SportsData.io via Merino), plus the club-football states the World Cup feed collapsed: halftime, extra time,
 * delayed, suspended and postponed.
 */
sealed interface WrexhamMatchStatus {
    /** Not started yet; the card shows the kickoff day and time. */
    data object Scheduled : WrexhamMatchStatus

    /**
     * In normal time.
     *
     * @property clock Match minute as the feed sends it, e.g. "29" or "90+3".
     */
    data class Live(val clock: String) : WrexhamMatchStatus

    /** Halftime break. */
    data object Halftime : WrexhamMatchStatus

    /**
     * In extra time (cup ties only).
     *
     * @property clock Match minute, e.g. "112".
     */
    data class ExtraTime(val clock: String) : WrexhamMatchStatus

    /**
     * Penalty shootout in progress. The match score stays as it was at the end of extra time.
     *
     * @property home Home team's shootout goals so far.
     * @property away Away team's shootout goals so far.
     */
    data class Penalties(val home: Int, val away: Int) : WrexhamMatchStatus

    /** Finished in normal or extra time. */
    data object Final : WrexhamMatchStatus

    /**
     * Finished after a penalty shootout.
     *
     * @property home Home team's shootout goals.
     * @property away Away team's shootout goals.
     */
    data class FinalAfterPenalties(val home: Int, val away: Int) : WrexhamMatchStatus

    /**
     * Kickoff pushed back on the day.
     *
     * @property newKickoff The new kickoff time.
     */
    data class Delayed(val newKickoff: LocalDateTime) : WrexhamMatchStatus

    /** Stopped part-way through; may or may not resume. */
    data object Suspended : WrexhamMatchStatus

    /**
     * Moved to another day.
     *
     * @property newKickoff The rescheduled kickoff.
     */
    data class Postponed(val newKickoff: LocalDateTime) : WrexhamMatchStatus
}

/** Whether the match is being played right now: the card shows LIVE and a red score pill. */
val WrexhamMatchStatus.isLive: Boolean
    get() =
        this is WrexhamMatchStatus.Live ||
            this is WrexhamMatchStatus.Halftime ||
            this is WrexhamMatchStatus.ExtraTime ||
            this is WrexhamMatchStatus.Penalties

/** Whether the match is over. */
val WrexhamMatchStatus.isFinished: Boolean
    get() = this is WrexhamMatchStatus.Final || this is WrexhamMatchStatus.FinalAfterPenalties

/** Which side, if any, has had a player sent off. */
enum class WrexhamSide {
    HOME,
    AWAY,
}

/**
 * A single fixture shown on the Wrexham widget.
 *
 * @property id Stable match identifier from the feed, so updates to the same match can be compared.
 * @property competition Name of the competition, e.g. "EFL Championship".
 * @property home Home club.
 * @property away Away club.
 * @property kickoff Scheduled kickoff in the device's time zone.
 * @property status Where the match is in its life cycle.
 * @property homeScore Home score, or null before kickoff.
 * @property awayScore Away score, or null before kickoff.
 * @property redCard Side that has had a player sent off, if any.
 * @property url Page to open when the match is tapped.
 */
data class WrexhamMatch(
    val id: Long,
    val competition: String,
    val home: WrexhamTeam,
    val away: WrexhamTeam,
    val kickoff: LocalDateTime,
    val status: WrexhamMatchStatus = WrexhamMatchStatus.Scheduled,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val redCard: WrexhamSide? = null,
    val url: String = WrexhamLinks.MATCHES,
) {
    private val wrexhamIsHome: Boolean
        get() = home.key == Wrexham.key

    /** Goals Wrexham has scored, or null before kickoff. */
    val wrexhamGoals: Int?
        get() = if (wrexhamIsHome) homeScore else awayScore

    /** True once the match is over and Wrexham won it, on goals or on penalties. */
    val wrexhamWon: Boolean
        get() {
            val (home, away) =
                when (val s = status) {
                    is WrexhamMatchStatus.FinalAfterPenalties -> s.home to s.away
                    WrexhamMatchStatus.Final -> (homeScore ?: 0) to (awayScore ?: 0)
                    else -> return false
                }
            return if (wrexhamIsHome) home > away else away > home
        }
}

/** What the card body shows. */
sealed interface WrexhamContent {
    /** One or more matches; more than one shows as a carousel ending in a "Full Calendar" button. */
    data class Matches(val matches: List<WrexhamMatch>) : WrexhamContent

    /** The team has no upcoming games. */
    data object NoUpcoming : WrexhamContent

    /** Match data couldn't load. The card offers a Refresh button. */
    data object Error : WrexhamContent

    /** The season is over. */
    data object SeasonOver : WrexhamContent
}

/**
 * State of the Wrexham widget on the homepage.
 *
 * @property content What the card body shows.
 * @property script Optional demo sequence that fakes live updates once the card is on screen (see
 *   [WrexhamFakeData.updates]). Always null for real data.
 */
data class WrexhamWidgetState(
    val content: WrexhamContent,
    val script: WrexhamScript? = null,
) {
    /** Which celebration plays when Wrexham scores. Real data always gets the designed default. */
    val goalStyle: WrexhamGoalStyle
        get() = script?.goalStyle ?: WrexhamGoalStyle.KIT
}

/** The two goal celebrations in the design. */
enum class WrexhamGoalStyle {
    /** The Kit animation (fox dribbling a ball) plus confetti, over the card. The designed default. */
    KIT,

    /** The design prototype's CSS effect: Firefox fox, ball and "GOAL!" banner across the screen. */
    FOX,
}

/** Pages on the club site. The site has no separate links for results or the table yet. */
object WrexhamLinks {
    const val MATCHES = "https://www.wrexhamafc.co.uk/matches/mens-team"
    const val RESULTS = MATCHES
    const val STANDINGS = MATCHES
}
