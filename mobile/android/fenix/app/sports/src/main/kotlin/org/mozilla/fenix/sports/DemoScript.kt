/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * DEMO ONLY / SCRIPTS: fake live feeds for the animated demo scenarios.
 *
 * What: each [DemoScript] emits newer versions of a match over time (clock ticks, goals, shootout kicks).
 * Called by: rememberSportsFx in Celebrations.kt, once the card is on screen.
 * Why: the card treats these updates exactly like real ones, so the demo runs the same celebration
 * code real data will.
 * Remove with WrexhamDemo.kt once real data exists.
 */

package org.mozilla.fenix.sports

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

/**
 * Demo sequences that fake a live feed: each emits a few updated versions of a match over time, the way polling a real
 * API would. The card reacts to those updates exactly as it would to real ones, so the celebrations are triggered by
 * the data, not by the script. Timings follow the design prototype.
 *
 * @property goalStyle Which goal celebration this demo shows.
 */
enum class DemoScript(val goalStyle: GoalStyle = GoalStyle.KIT) {
    /** The clock runs a minute per second from 29'; the followed team scores at 33'; halftime at 45'. */
    LIVE_CLOCK(GoalStyle.FOX),

    /** The followed team scores in the 88th minute. */
    GOAL,

    /** A shootout tied 4 - 4 ends 4 - 5 to the away side. */
    WINNER,

    /** A whole shootout, kick by kick, ending 4 - 5 to the away side. */
    SHOOTOUT,
}

private const val TICK_MS = 1000L
private const val SETTLE_MS = 600L
private const val KICK_MS = 1400L
private const val START_MINUTE = 30
private const val GOAL_MINUTE = 33
private const val HALFTIME_MINUTE = 45
private const val SHOOTOUT_WINNING_SCORE = 5
private const val SHOOTOUT_LOSING_SCORE = 4

/**
 * The updates [script] would receive from a live feed, starting from [start].
 *
 * @param script Which demo to play.
 * @param start The match as the card first shows it.
 * @param followed The team whose goals the script adds.
 */
fun demoUpdates(script: DemoScript, start: Match, followed: Team): Flow<Match> = flow {
    when (script) {
        DemoScript.LIVE_CLOCK -> liveClock(start, followed)
        DemoScript.GOAL -> {
            delay(SETTLE_MS)
            emit(start.plusGoal(followed).copy(status = MatchStatus.Live("88")))
        }
        DemoScript.WINNER -> {
            delay(SETTLE_MS)
            emit(start.copy(status = MatchStatus.FinalAfterPenalties(SHOOTOUT_LOSING_SCORE, SHOOTOUT_WINNING_SCORE)))
        }
        DemoScript.SHOOTOUT -> shootout(start)
    }
}

private suspend fun FlowCollector<Match>.liveClock(start: Match, followed: Team) {
    var match = start
    for (minute in START_MINUTE until HALFTIME_MINUTE) {
        delay(TICK_MS)
        match = match.copy(status = MatchStatus.Live("$minute"))
        if (minute == GOAL_MINUTE) match = match.plusGoal(followed)
        emit(match)
    }
    delay(TICK_MS)
    emit(match.copy(status = MatchStatus.Halftime))
}

/**
 * Scripted rather than random (the prototype scores each kick 75% of the time) so the demo always ends in the
 * celebration. The home side kicks first and misses its third.
 */
private suspend fun FlowCollector<Match>.shootout(start: Match) {
    val homeMisses = setOf(2)
    var home = 0
    var away = 0
    repeat(SHOOTOUT_WINNING_SCORE) { kick ->
        delay(KICK_MS)
        if (kick !in homeMisses) home++
        emit(start.copy(status = MatchStatus.Penalties(home, away)))
        delay(KICK_MS)
        away++
        emit(start.copy(status = MatchStatus.Penalties(home, away)))
    }
    emit(start.copy(status = MatchStatus.FinalAfterPenalties(home, away)))
}

private fun Match.plusGoal(team: Team): Match =
    when (sideOf(team)) {
        Side.HOME -> copy(homeScore = (homeScore ?: 0) + 1)
        Side.AWAY -> copy(awayScore = (awayScore ?: 0) + 1)
        null -> this
    }
