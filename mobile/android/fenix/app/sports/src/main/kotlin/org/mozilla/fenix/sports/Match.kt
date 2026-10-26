/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * SPORTS CORE / MODEL: teams, matches, and where a match is in its life cycle.
 *
 * What: plain data with no Android or UI code. It holds facts (scores, kickoff time, a structured
 * [MatchStatus]); the UI turns them into display text like "29'" or "Today".
 * Built by: MerinoSportsApi.kt (real data, once it exists) and WrexhamDemo.kt (dummy data).
 * Used by: every other file in the module, and the app's AppStore slice ([ClubWidgetState]).
 * Why: with no display strings in the model, mapping an API response is a field-to-field copy, and
 * a club, league or global football widget can all reuse it.
 */

package org.mozilla.fenix.sports

import androidx.annotation.DrawableRes
import java.time.LocalDateTime

/**
 * A team.
 *
 * @property key Stable team identifier from the data feed. Teams are compared by key.
 * @property name Display name.
 * @property crestRes Bundled crest, or null when we don't ship one for this team.
 */
data class Team(
    val key: String,
    val name: String,
    @param:DrawableRes val crestRes: Int?,
)

/**
 * Where a match is in its life cycle. Mirrors the World Cup widget's `MatchStatus` (mapped from the same
 * Merino/SportsData.io feed), plus the club-football states that widget collapsed: halftime, extra time, delayed,
 * suspended and postponed.
 */
sealed interface MatchStatus {
    /** Not started yet; the card shows the kickoff day and time. */
    data object Scheduled : MatchStatus

    /**
     * In normal time.
     *
     * @property clock Match minute as the feed sends it, e.g. "29" or "90+3".
     */
    data class Live(val clock: String) : MatchStatus

    /** Halftime break. */
    data object Halftime : MatchStatus

    /**
     * In extra time (cup ties only).
     *
     * @property clock Match minute, e.g. "112".
     */
    data class ExtraTime(val clock: String) : MatchStatus

    /**
     * Penalty shootout in progress. The match score stays as it was at the end of extra time.
     *
     * @property home Home side's shootout goals so far.
     * @property away Away side's shootout goals so far.
     */
    data class Penalties(val home: Int, val away: Int) : MatchStatus

    /** Finished in normal or extra time. */
    data object Final : MatchStatus

    /**
     * Finished after a penalty shootout.
     *
     * @property home Home side's shootout goals.
     * @property away Away side's shootout goals.
     */
    data class FinalAfterPenalties(val home: Int, val away: Int) : MatchStatus

    /**
     * Kickoff pushed back on the day.
     *
     * @property newKickoff The new kickoff time.
     */
    data class Delayed(val newKickoff: LocalDateTime) : MatchStatus

    /** Stopped part-way through; may or may not resume. */
    data object Suspended : MatchStatus

    /**
     * Moved to another day.
     *
     * @property newKickoff The rescheduled kickoff.
     */
    data class Postponed(val newKickoff: LocalDateTime) : MatchStatus
}

/** Whether the match is being played right now: the card shows LIVE and a red score pill. */
val MatchStatus.isLive: Boolean
    get() =
        this is MatchStatus.Live ||
            this is MatchStatus.Halftime ||
            this is MatchStatus.ExtraTime ||
            this is MatchStatus.Penalties

/** Whether the match is over. */
val MatchStatus.isFinished: Boolean
    get() = this is MatchStatus.Final || this is MatchStatus.FinalAfterPenalties

/** One side of a match. */
enum class Side {
    HOME,
    AWAY,
}

/**
 * A single match.
 *
 * @property id Stable match identifier from the feed, so updates to the same match can be compared.
 * @property competition Name of the competition, e.g. "EFL Championship".
 * @property home Home team.
 * @property away Away team.
 * @property kickoff Scheduled kickoff in the device's time zone.
 * @property status Where the match is in its life cycle.
 * @property homeScore Home goals including extra time, or null before kickoff.
 * @property awayScore Away goals including extra time, or null before kickoff.
 * @property redCard Side that has had a player sent off, if any.
 * @property url Page to open when the match is tapped.
 */
data class Match(
    val id: Long,
    val competition: String,
    val home: Team,
    val away: Team,
    val kickoff: LocalDateTime,
    val status: MatchStatus = MatchStatus.Scheduled,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val redCard: Side? = null,
    val url: String = "",
) {
    /** Which side [team] is playing on, or null if it isn't in this match. */
    fun sideOf(team: Team): Side? =
        when (team.key) {
            home.key -> Side.HOME
            away.key -> Side.AWAY
            else -> null
        }

    /** Goals [team] has scored, or null before kickoff or if it isn't playing. */
    fun goalsFor(team: Team): Int? =
        when (sideOf(team)) {
            Side.HOME -> homeScore
            Side.AWAY -> awayScore
            null -> null
        }

    /** True once the match is over and [team] won it, on goals or on penalties. */
    fun wonBy(team: Team): Boolean {
        val side = sideOf(team) ?: return false
        val (home, away) =
            when (val s = status) {
                is MatchStatus.FinalAfterPenalties -> s.home to s.away
                MatchStatus.Final -> (homeScore ?: 0) to (awayScore ?: 0)
                else -> return false
            }
        return if (side == Side.HOME) home > away else away > home
    }
}

/** What a widget's body shows. */
sealed interface SportsContent {
    /** One or more matches; more than one shows as a carousel ending in a "Full Calendar" button. */
    data class Matches(val matches: List<Match>) : SportsContent

    /** No upcoming games. */
    data object NoUpcoming : SportsContent

    /** Match data couldn't load. The card offers a Refresh button. */
    data object Error : SportsContent

    /** The season is over. */
    data object SeasonOver : SportsContent
}

/**
 * State of a club widget.
 *
 * @property content What the card body shows.
 * @property script Optional demo sequence that fakes live updates once the card is on screen (see DemoScript.kt).
 *   Always null for real data.
 */
data class ClubWidgetState(
    val content: SportsContent,
    val script: DemoScript? = null,
) {
    /** Which celebration plays when the followed team scores. Real data always gets the designed default. */
    val goalStyle: GoalStyle
        get() = script?.goalStyle ?: GoalStyle.KIT

    /** The match when the card shows exactly one (live, finished or a single fixture), else null. */
    val singleMatch: Match?
        get() = (content as? SportsContent.Matches)?.matches?.singleOrNull()
}

/** The two goal celebrations in the design. */
enum class GoalStyle {
    /** The Kit animation (fox dribbling a ball) plus confetti, over the card. The designed default. */
    KIT,

    /** The design prototype's CSS effect: Firefox fox, ball and "GOAL!" banner across the screen. */
    FOX,
}
