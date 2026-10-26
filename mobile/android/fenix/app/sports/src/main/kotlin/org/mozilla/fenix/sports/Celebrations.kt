/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * SPORTS CORE / CELEBRATIONS: decides when to celebrate, and tracks which effects are playing.
 *
 * How it works:
 * 1. A card shows a match. Newer versions of it arrive from the data source (each reload) or, in
 *    demo scenarios, from a script that fakes a live feed (DemoScript.kt).
 * 2. [rememberSportsFx] compares each new version with the last one ([celebrationFor]): the followed
 *    team scored, or just won. If so, it starts the matching effects on a [SportsFx].
 * 3. The card reads [SportsFx] and draws the effects (KitEffects.kt).
 * Called by: ClubWidget.kt. Timings come from the designer's HTML prototype; where a number looks
 * arbitrary, it's the prototype's.
 */

package org.mozilla.fenix.sports

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The moments worth celebrating. */
enum class Celebration {
    GOAL,
    WIN,
}

/**
 * Decides whether going from [before] to [after] deserves a celebration for [followed]. Works the same for real polled
 * data and for demo scripts, since both just produce a newer version of the match.
 *
 * Nothing plays for the first version a card sees ([before] is null) or across different matches, so opening the
 * homepage after a goal shows the new score without replaying the goal.
 *
 * @return [Celebration.WIN] when the match has just finished with [followed] winning, [Celebration.GOAL] when
 *   [followed]'s score went up, otherwise null.
 */
fun celebrationFor(before: Match?, after: Match, followed: Team): Celebration? =
    when {
        before == null || before.id != after.id -> null
        !before.status.isFinished && after.wonBy(followed) -> Celebration.WIN
        (after.goalsFor(followed) ?: 0) > (before.goalsFor(followed) ?: 0) -> Celebration.GOAL
        else -> null
    }

/** Length of sports_kit_goal.gif. */
internal const val GOAL_GIF_MS = 2000L

/** Card confetti duration, from the design's `startConfetti()`. */
internal const val CONFETTI_MS = 3600L

/** Length of sports_kit_win.gif. */
private const val KIT_WIN_MS = 4040L

/** How long the full-screen confetti shows. The GIF itself is 6.76s; the design cuts it at 4s. */
internal const val WINNER_SCREEN_MS = 4000L

/** When the Kit fox joins the winner confetti. */
private const val KIT_WIN_DELAY_MS = 3000L

/** How long the fox runner is on screen; its run takes 1.9s. */
private const val FOX_MS = 2000L

/**
 * Observable state for one card's animations. Every property except [lastSeen] is Compose state, so changing it
 * recomposes the card. The composable that draws an effect starts its animation when it enters composition, so
 * switching an effect on is the same as pressing play.
 */
class SportsFx {
    /**
     * The match as a demo script has updated it (new score, ticking clock), or null to show the widget state's own
     * match. Real data never sets this; it arrives as a new widget state instead.
     */
    var match by mutableStateOf<Match?>(null)

    /**
     * True once the card has been drawn on screen. Demo scripts wait for this, because on a cold start the homepage
     * composes behind the splash screen and a script started at composition would be over before anyone could see it.
     */
    var drawn by mutableStateOf(false)

    // Each effect is a play number: 0 while it isn't showing, otherwise which play it is. The card keys the effect's
    // composable on that number, so a celebration that starts while the last one is still running restarts it.

    /** The Kit goal GIF (fox dribbling a ball) over the card. */
    var goalGif by mutableIntStateOf(0)
        private set

    /** Confetti falling inside the card. */
    var confetti by mutableIntStateOf(0)
        private set

    /** The Kit "win" GIF (fox popping up) on the card's bottom edge. */
    var kitWin by mutableIntStateOf(0)
        private set

    /** The Kit full-screen confetti GIF over the whole screen. */
    var winnerScreen by mutableIntStateOf(0)
        private set

    /** The fox, ball and "GOAL!" banner running across the screen. */
    var fox by mutableIntStateOf(0)
        private set

    /** The last match the card showed, to compare the next update against. Not drawn, so plain. */
    internal var lastSeen: Match? = null

    private var plays = 0

    /**
     * Shows an effect for [durationMs] without blocking the caller. If the effect is replayed in the meantime, the
     * newer play is left running.
     */
    private fun CoroutineScope.flash(durationMs: Long, get: () -> Int, set: (Int) -> Unit) = launch {
        val play = ++plays
        set(play)
        delay(durationMs)
        if (get() == play) set(0)
    }

    /**
     * Plays [celebration] in [scope], following the design prototype:
     * - Goal, Kit style: the goal GIF and card confetti start together (`playGoal()`).
     * - Goal, fox style: the fox, ball and banner run across the screen (`runFox()`).
     * - Win: full-screen confetti for 4s; 3s in, while it's still falling, the Kit fox pops up on the card's bottom
     *   edge and plays once (`playWinner()`).
     */
    fun play(scope: CoroutineScope, celebration: Celebration, goalStyle: GoalStyle) =
        with(scope) {
            when (celebration) {
                Celebration.GOAL ->
                    when (goalStyle) {
                        GoalStyle.KIT -> {
                            flash(GOAL_GIF_MS, { goalGif }) { goalGif = it }
                            flash(CONFETTI_MS, { confetti }) { confetti = it }
                        }
                        GoalStyle.FOX -> flash(FOX_MS, { fox }) { fox = it }
                    }
                Celebration.WIN -> {
                    flash(WINNER_SCREEN_MS, { winnerScreen }) { winnerScreen = it }
                    launch {
                        delay(KIT_WIN_DELAY_MS)
                        flash(KIT_WIN_MS, { kitWin }) { kitWin = it }
                    }
                }
            }
        }
}

/**
 * Creates a card's [SportsFx] and keeps it in step with [state]:
 * 1. Runs the state's demo script, if any, once the card is on screen. The script only produces updated matches; it
 *    doesn't trigger any effect itself.
 * 2. Watches the match the card shows and plays a celebration when [celebrationFor] says so. Celebrations run in the
 *    composition's scope, so a newer update arriving mid-celebration doesn't cut one short.
 *
 * Demo scripts restart whenever the card re-enters composition, so they replay on every visit.
 *
 * @param state The widget state on display.
 * @param followed The team whose goals and wins are celebrated.
 */
@Composable
fun rememberSportsFx(state: ClubWidgetState, followed: Team): SportsFx {
    val fx = remember { SportsFx() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state, fx.drawn) {
        fx.match = null
        val script = state.script ?: return@LaunchedEffect
        val start = state.singleMatch ?: return@LaunchedEffect
        if (!fx.drawn) return@LaunchedEffect
        demoUpdates(script, start, followed).collect { fx.match = it }
    }

    val shown = fx.match ?: state.singleMatch
    LaunchedEffect(shown) {
        if (shown == null) return@LaunchedEffect
        celebrationFor(fx.lastSeen, shown, followed)?.let { fx.play(scope, it, state.goalStyle) }
        fx.lastSeen = shown
    }
    return fx
}
