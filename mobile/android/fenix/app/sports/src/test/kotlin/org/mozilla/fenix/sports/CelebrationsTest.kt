/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.sports

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CelebrationsTest {
    private val followed = Team("WRE", "Wrexham AFC", null)
    private val opponent = Team("WHU", "West Ham", null)
    private val live =
        Match(1, "EFL", opponent, followed, LocalDateTime.of(2026, 10, 26, 14, 45), MatchStatus.Live("80"), 1, 1)

    @Test
    fun `nothing plays for the first version of a match`() {
        assertNull(celebrationFor(null, live, followed))
    }

    @Test
    fun `a goal by the followed team plays the goal celebration`() {
        assertEquals(Celebration.GOAL, celebrationFor(live, live.copy(awayScore = 2), followed))
    }

    @Test
    fun `a goal by the opponent plays nothing`() {
        assertNull(celebrationFor(live, live.copy(homeScore = 2), followed))
    }

    @Test
    fun `updates to a different match play nothing`() {
        assertNull(celebrationFor(live, live.copy(id = 2, awayScore = 2), followed))
    }

    @Test
    fun `a match that has just been won plays the win celebration`() {
        val won = live.copy(status = MatchStatus.FinalAfterPenalties(home = 4, away = 5))
        assertEquals(Celebration.WIN, celebrationFor(live, won, followed))
    }

    @Test
    fun `a win already shown does not play again`() {
        val won = live.copy(status = MatchStatus.Final, awayScore = 2)
        assertNull(celebrationFor(won, won, followed))
    }

    @Test
    fun `wonBy counts penalties for shootouts and goals otherwise`() {
        assertTrue(live.copy(status = MatchStatus.FinalAfterPenalties(4, 5)).wonBy(followed))
        assertFalse(live.copy(status = MatchStatus.FinalAfterPenalties(5, 4)).wonBy(followed))
        assertTrue(live.copy(status = MatchStatus.Final, awayScore = 2).wonBy(followed))
        assertFalse(live.copy(status = MatchStatus.Final).wonBy(followed))
        assertFalse(live.copy(awayScore = 3).wonBy(followed))
    }
}
