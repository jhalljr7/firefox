/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.sports

import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SportsLoaderTest {
    private val loaded = ClubWidgetState(content = SportsContent.NoUpcoming)
    private var now = 0L
    private var loads = 0
    private var online = true

    private fun TestScope.loader(
        repository: SportsRepository = SportsRepository {
            loads++
            Result.success(loaded)
        }
    ) =
        SportsLoader(
            repository = repository,
            scope = this,
            isOnline = { online },
            minIntervalMs = 60_000L,
            clock = { now },
        )

    @Test
    fun `refreshes inside the minimum gap are dropped unless forced`() =
        runTest(StandardTestDispatcher()) {
            val loader = loader()
            loader.refresh {}
            advanceUntilIdle()
            now = 30_000L
            loader.refresh {}
            advanceUntilIdle()
            assertEquals(1, loads)

            loader.refresh(force = true) {}
            advanceUntilIdle()
            assertEquals(2, loads)

            now = 100_000L
            loader.refresh {}
            advanceUntilIdle()
            assertEquals(3, loads)
        }

    @Test
    fun `only one request runs at a time`() =
        runTest(StandardTestDispatcher()) {
            val gate = CompletableDeferred<Unit>()
            val loader =
                loader(
                    SportsRepository {
                        loads++
                        gate.await()
                        Result.success(loaded)
                    }
                )
            loader.refresh {}
            advanceUntilIdle()
            loader.refresh(force = true) {}
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, loads)
        }

    @Test
    fun `offline reports a failure without making a request`() =
        runTest(StandardTestDispatcher()) {
            online = false
            var result: Result<ClubWidgetState>? = null
            loader().refresh { result = it }
            advanceUntilIdle()
            assertEquals(0, loads)
            assertTrue(result!!.isFailure)
        }

    @Test
    fun `refresh interval follows the match state`() {
        val today = LocalDate.of(2026, 10, 26)
        val match = Match(1, "EFL", Team("A", "A", null), Team("B", "B", null), today.atTime(14, 45))

        fun state(m: Match) = ClubWidgetState(SportsContent.Matches(listOf(m)))
        assertEquals(60_000L, state(match.copy(status = MatchStatus.Live("1"))).refreshIntervalMs(today))
        assertEquals(5 * 60_000L, state(match).refreshIntervalMs(today))
        assertEquals(30 * 60_000L, state(match).refreshIntervalMs(today.minusDays(1)))
        assertEquals(30 * 60_000L, null.refreshIntervalMs(today))
    }

    @Test
    fun `each demo scenario uses its own match ids`() {
        val ids =
            WrexhamScenario.entries.flatMap { scenario ->
                (scenario.state().content as? SportsContent.Matches)?.matches.orEmpty().map { it.id }
            }
        assertEquals(ids.size, ids.toSet().size)
    }
}
