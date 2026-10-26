/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.home.wrexham

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.mozilla.fenix.R

/**
 * Dummy scenarios used until the Merino club endpoint exists. Switch between them in Secret Settings > "Wrexham widget
 * scenario"; that list is built from this enum, so a new entry shows up automatically. Each one is a design state from
 * the Android PDF.
 *
 * @property label Name shown in the Secret Settings list.
 * @property script Demo sequence that plays once the card is on screen, or null for a still state.
 */
enum class WrexhamScenario(val label: String, val script: WrexhamScript? = null) {
    UPCOMING("Upcoming matches"),
    LIVE("Live"),
    HALFTIME("Halftime"),
    FULL_TIME_RED_CARD("Full time with red card"),
    CUP_EXTRA_TIME("FA Cup extra time"),
    CUP_PENALTIES("FA Cup penalties"),
    LIVE_CLOCK("Live, Wrexham scores (plays out)", WrexhamScript.LIVE_CLOCK),
    GOAL("Goal celebration", WrexhamScript.GOAL),
    WINNER("Winner celebration", WrexhamScript.WINNER),
    SHOOTOUT("FA Cup shootout (plays out)", WrexhamScript.SHOOTOUT),
    DELAYED("Delayed"),
    SUSPENDED("Suspended"),
    POSTPONED("Postponed"),
    NO_UPCOMING("No upcoming games"),
    ERROR("Error"),
    SEASON_OVER("Season over"),
}

/**
 * Demo sequences that fake a live feed: each emits a few updated versions of the scenario's match over time, the way
 * polling a real API would. The card reacts to those updates exactly as it would to real ones, so the celebrations are
 * triggered by the data, not by the script.
 *
 * @property goalStyle Which goal celebration this demo shows.
 */
enum class WrexhamScript(val goalStyle: WrexhamGoalStyle = WrexhamGoalStyle.KIT) {
    /** The clock runs a minute per second from 29'; Wrexham scores at 33'; halftime at 45'. */
    LIVE_CLOCK(WrexhamGoalStyle.FOX),

    /** Wrexham scores to make it 3 - 2 in the 88th minute. */
    GOAL,

    /** A shootout tied 4 - 4 ends 4 - 5 to Wrexham. */
    WINNER,

    /** A whole shootout, kick by kick, ending 4 - 5 to Wrexham. */
    SHOOTOUT,
}

/** Builds the dummy data for each [WrexhamScenario]. */
object WrexhamFakeData {
    private const val EFL = "EFL Championship"
    private const val FA_CUP = "FA Cup"

    private val charlton = WrexhamTeam("CHA", "Charlton", R.drawable.wrexham_crest_charlton)
    private val manUnited = WrexhamTeam("MUN", "Man United", R.drawable.wrexham_crest_manunited)
    private val southampton = WrexhamTeam("SOU", "Southampton", R.drawable.wrexham_crest_southampton)
    private val westHam = WrexhamTeam("WHU", "West Ham", R.drawable.wrexham_crest_west_ham)

    /**
     * Builds the widget state for [scenario]. Kickoffs are relative to today, so "Today" stays true. The details don't
     * have to agree across scenarios; the real API replaces all of this.
     */
    fun forScenario(scenario: WrexhamScenario): WrexhamWidgetState {
        val today = LocalDate.now()
        fun at(daysFromToday: Long, hour: Int, minute: Int = 0): LocalDateTime =
            today.plusDays(daysFromToday).atTime(LocalTime.of(hour, minute))

        val kickoff = at(0, 14, 45)
        fun westHamGame(home: Int, away: Int, status: WrexhamMatchStatus, competition: String = EFL) =
            WrexhamMatch(1, competition, westHam, Wrexham, kickoff, status, home, away)
        fun manUnitedCupTie(status: WrexhamMatchStatus) =
            WrexhamMatch(2, FA_CUP, manUnited, Wrexham, kickoff, status, 1, 1)

        val content =
            when (scenario) {
                WrexhamScenario.UPCOMING ->
                    WrexhamContent.Matches(
                        listOf(
                            WrexhamMatch(3, EFL, charlton, Wrexham, kickoff),
                            WrexhamMatch(4, FA_CUP, Wrexham, manUnited, at(5, 18)),
                            WrexhamMatch(5, EFL, Wrexham, southampton, at(12, 14, 45)),
                        )
                    )
                WrexhamScenario.LIVE -> single(westHamGame(1, 2, WrexhamMatchStatus.Live("29")))
                WrexhamScenario.HALFTIME -> single(westHamGame(1, 1, WrexhamMatchStatus.Halftime))
                WrexhamScenario.FULL_TIME_RED_CARD ->
                    single(westHamGame(1, 1, WrexhamMatchStatus.Final).copy(redCard = WrexhamSide.HOME))
                WrexhamScenario.CUP_EXTRA_TIME -> single(manUnitedCupTie(WrexhamMatchStatus.ExtraTime("112")))
                WrexhamScenario.CUP_PENALTIES ->
                    single(
                        westHamGame(1, 1, WrexhamMatchStatus.FinalAfterPenalties(4, 5), FA_CUP)
                            .copy(redCard = WrexhamSide.HOME)
                    )
                WrexhamScenario.LIVE_CLOCK -> single(westHamGame(1, 1, WrexhamMatchStatus.Live("29")))
                WrexhamScenario.GOAL -> single(westHamGame(3, 1, WrexhamMatchStatus.Live("87")))
                WrexhamScenario.WINNER -> single(westHamGame(2, 2, WrexhamMatchStatus.Penalties(4, 4), FA_CUP))
                WrexhamScenario.SHOOTOUT -> single(manUnitedCupTie(WrexhamMatchStatus.Penalties(0, 0)))
                WrexhamScenario.DELAYED -> single(westHamGame(0, 0, WrexhamMatchStatus.Delayed(at(0, 15))))
                WrexhamScenario.SUSPENDED -> single(westHamGame(1, 1, WrexhamMatchStatus.Suspended))
                WrexhamScenario.POSTPONED -> single(westHamGame(0, 0, WrexhamMatchStatus.Postponed(at(7, 14, 45))))
                WrexhamScenario.NO_UPCOMING -> WrexhamContent.NoUpcoming
                WrexhamScenario.ERROR -> WrexhamContent.Error
                WrexhamScenario.SEASON_OVER -> WrexhamContent.SeasonOver
            }
        return WrexhamWidgetState(content = content, script = scenario.script)
    }

    /**
     * The updates [script] would receive from a live feed, starting from [start]. Collected by the card once it's on
     * screen. Timings follow the design prototype.
     */
    fun updates(script: WrexhamScript, start: WrexhamMatch): Flow<WrexhamMatch> = flow {
        when (script) {
            WrexhamScript.LIVE_CLOCK -> {
                var match = start
                for (minute in 30..44) {
                    delay(1000)
                    match = match.copy(status = WrexhamMatchStatus.Live("$minute"))
                    if (minute == 33) match = match.plusWrexhamGoal()
                    emit(match)
                }
                delay(1000)
                emit(match.copy(status = WrexhamMatchStatus.Halftime))
            }
            WrexhamScript.GOAL -> {
                delay(600)
                emit(start.plusWrexhamGoal().copy(status = WrexhamMatchStatus.Live("88")))
            }
            WrexhamScript.WINNER -> {
                delay(600)
                emit(start.copy(status = WrexhamMatchStatus.FinalAfterPenalties(4, 5)))
            }
            WrexhamScript.SHOOTOUT -> {
                // Scripted rather than random (the prototype scores each kick 75% of the time) so the
                // demo always ends in the celebration. The home side kicks first, one kick every 1.4s.
                val homeKicks = listOf(true, true, false, true, true)
                val awayKicks = listOf(true, true, true, true, true)
                var home = 0
                var away = 0
                for (i in homeKicks.indices) {
                    delay(1400)
                    if (homeKicks[i]) home++
                    emit(start.copy(status = WrexhamMatchStatus.Penalties(home, away)))
                    delay(1400)
                    if (awayKicks[i]) away++
                    emit(start.copy(status = WrexhamMatchStatus.Penalties(home, away)))
                }
                emit(start.copy(status = WrexhamMatchStatus.FinalAfterPenalties(home, away)))
            }
        }
    }

    private fun single(match: WrexhamMatch) = WrexhamContent.Matches(listOf(match))

    private fun WrexhamMatch.plusWrexhamGoal() =
        if (home.key == Wrexham.key) copy(homeScore = (homeScore ?: 0) + 1) else copy(awayScore = (awayScore ?: 0) + 1)
}
