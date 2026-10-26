/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.sports

import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class MerinoSportsApiTest {
    private val utc = ZoneOffset.UTC
    private val json = Json { ignoreUnknownKeys = true }
    private val wrexham = TeamDto(key = "WRE", name = "Wrexham AFC")
    private val westHam = TeamDto(key = "WHU", name = "West Ham")

    private fun event(
        statusType: String,
        status: String = "",
        period: String? = null,
        date: String = "2026-10-26T14:45:00+00:00",
        homeScore: Int? = null,
        homeExtra: Int? = null,
        homePenalty: Int? = null,
        awayPenalty: Int? = null,
    ) =
        EventDto(
            globalEventId = 1,
            date = date,
            homeTeam = westHam,
            awayTeam = wrexham,
            homeScore = homeScore,
            awayScore = homeScore,
            homeExtra = homeExtra,
            homePenalty = homePenalty,
            awayPenalty = awayPenalty,
            period = period,
            clock = "29",
            status = status,
            statusType = statusType,
        )

    @Test
    fun `maps each status to the model`() {
        assertEquals(MatchStatus.Scheduled, event("scheduled").toMatch(utc)!!.status)
        assertEquals(MatchStatus.Live("29"), event("live", "In Progress", "1").toMatch(utc)!!.status)
        assertEquals(MatchStatus.Halftime, event("live", "Break").toMatch(utc)!!.status)
        assertEquals(MatchStatus.ExtraTime("29"), event("live", period = "Extra").toMatch(utc)!!.status)
        assertEquals(MatchStatus.ExtraTime("29"), event("live", period = "ExtraTime").toMatch(utc)!!.status)
        assertEquals(
            MatchStatus.Penalties(3, 2),
            event("live", period = "PEN", homePenalty = 3, awayPenalty = 2).toMatch(utc)!!.status,
        )
        assertEquals(MatchStatus.Final, event("past", "Final").toMatch(utc)!!.status)
        assertEquals(
            MatchStatus.FinalAfterPenalties(4, 5),
            event("past", homePenalty = 4, awayPenalty = 5).toMatch(utc)!!.status,
        )
        assertEquals(MatchStatus.Suspended, event("live", "Suspended").toMatch(utc)!!.status)
    }

    @Test
    fun `postponed and delayed carry the kickoff as the new time`() {
        val match = event("scheduled", "Postponed").toMatch(utc)!!
        assertEquals(MatchStatus.Postponed(match.kickoff), match.status)
        assertEquals(MatchStatus.Delayed(match.kickoff), event("scheduled", "Delayed").toMatch(utc)!!.status)
    }

    @Test
    fun `the score includes extra-time goals`() {
        assertEquals(3, event("live", period = "Extra", homeScore = 1, homeExtra = 2).toMatch(utc)!!.homeScore)
    }

    @Test
    fun `an event with an unreadable kickoff is dropped`() {
        assertEquals(null, event("scheduled", date = "not a date").toMatch(utc))
        assertEquals(
            SportsContent.NoUpcoming,
            MatchesResponseDto(next = listOf(event("scheduled", date = "?"))).toContent(LocalDate.MIN),
        )
    }

    @Test
    fun `content prefers a live match, then today's result, then upcoming matches`() {
        val today = LocalDate.of(2026, 10, 26)
        val live = event("live", "In Progress")
        val finishedToday = event("past", "Final")
        val upcoming = List(4) { event("scheduled", date = "2026-11-0${it + 1}T14:45:00+00:00") }

        val all = MatchesResponseDto(previous = listOf(finishedToday), current = listOf(live), next = upcoming)
        assertEquals(1, (all.toContent(today) as SportsContent.Matches).matches.size)
        assertEquals(
            MatchStatus.Final,
            (all.copy(current = emptyList()).toContent(today) as SportsContent.Matches).matches.single().status,
        )
        assertEquals(3, (MatchesResponseDto(next = upcoming).toContent(today) as SportsContent.Matches).matches.size)
        assertEquals(SportsContent.NoUpcoming, MatchesResponseDto().toContent(today))
    }

    @Test
    fun `decodes a World Cup shaped response and ignores unknown fields`() {
        val body =
            """
            {"previous": [], "next": [], "current": [{
              "global_event_id": 7, "date": "2026-10-26T14:45:00+00:00", "status": "In Progress",
              "status_type": "live", "period": "2", "clock": "90+3", "home_score": 1, "away_score": 2,
              "venue": {"name": "Racecourse Ground"}, "sport": "FIFA",
              "home_team": {"key": "WHU", "name": "West Ham", "colors": ["#7A263A"]},
              "away_team": {"key": "WRE", "name": "Wrexham AFC"}
            }]}
            """
                .trimIndent()
        val match = json.decodeFromString<MatchesResponseDto>(body).current.single().toMatch(utc)!!
        assertEquals(MatchStatus.Live("90+3"), match.status)
        assertEquals(2, match.goalsFor(Wrexham.team))
    }
}
