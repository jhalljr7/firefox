/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.home.wrexham

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.mozilla.fenix.R

/*
 * MERINO CLUB API: PLACEHOLDER CONTRACT
 *
 * There is no club endpoint or spec yet. Everything here is ASSUMED to match the World Cup
 * endpoint (`/api/v1/wcs/matches`, removed 2026-08-20), because a club endpoint would most likely
 * come from the same partner (SportsData.io) through the same Merino service. When the spec lands,
 * check each field and every "ASSUMED" note against it; the rest of the widget only sees the
 * mapped [WrexhamMatch], so changes stay inside this file.
 *
 * Sources, most trusted first:
 * 1. The shipped World Cup client code (removed in commit a91ad60e864c; `git show a91ad60e864c^:<path>`
 *    under home/sports/). Where it and the docs below disagree, the code wins: it ran against the
 *    real endpoint, and the doc is the pre-build proposal.
 * 2. Merino's "API Contract Proposal for SportsData Suggestions" doc. Its World Cup tab is the
 *    original contract; its Suggest API tab is the regular-season (NFL/NHL/MLB) shape, which nests
 *    scores inside each team and uses long statuses like "Final - Shoot Out":
 *    https://docs.google.com/document/d/1KsTTPpmsx2v-HFlRni1Q74C9UO6KRqcYO-mEnQ-tFoQ/edit?tab=t.wb5kt1vxfgdc
 * 3. SportsData.io's soccer workflow guide, for the upstream feed Merino wraps:
 *    https://sportsdata.io/developers/workflow-guide/soccer
 *
 * What they tell us beyond the fields below:
 * - Statuses (Merino): Scheduled, Delayed, Postponed, In Progress, Suspended, Canceled, Final,
 *   Not Necessary. "Break" (halftime) appears only in the World Cup code. Canceled and Not Necessary
 *   have no design state yet and fall through to Scheduled. Product question.
 * - Period: "1", "2", "Extra" (Merino doc), "Regular"/"ExtraTime" (SportsData.io), "PEN" or
 *   "PenaltyShootout" during a shootout (World Cup code).
 * - Scores: `home_score` is normal time only; extra-time goals arrive in `home_extra` and shootout
 *   goals in `home_penalty`. The World Cup code added them up to decide a winner.
 * - Clock: Merino pre-formats stoppage time as "90+3"; upstream sends it as a separate ClockExtra.
 * - Postponed games keep their ID and go back to Scheduled on the new date (SportsData.io).
 * - Merino refreshes match data every 3 minutes, and also has a lighter live-only endpoint
 *   (`/api/v1/wcs/live`) that would suit polling during a match.
 * - Teams come with Merino-hosted `icon_url` logos and hex `colors`, which could replace the crests
 *   bundled with the prototype.
 * - Competitions are numeric CompetitionIDs upstream: EFL Championship is 34, FA Cup is 27.
 */

/**
 * Fetches Wrexham's matches from Merino. Wrapped in an interface so the repository can be tested without the
 * app-services binding.
 */
fun interface WrexhamMatchesClient {
    /**
     * @param team The club's key in the feed.
     * @return The raw JSON body, or null on a network or API error.
     */
    fun fetchMatches(team: String): String?
}

/**
 * The response for one team: its recent, in-progress and upcoming matches. ASSUMED: the World Cup endpoint's
 * team-filtered shape.
 */
@Serializable
data class WrexhamMatchesResponseDto(
    val previous: List<WrexhamEventDto> = emptyList(),
    val current: List<WrexhamEventDto> = emptyList(),
    val next: List<WrexhamEventDto> = emptyList(),
)

/** One match. ASSUMED: field for field the World Cup endpoint's `EventInfoDto`. */
@Serializable
data class WrexhamEventDto(
    @SerialName("global_event_id") val globalEventId: Long = 0L,
    /** Kickoff, ISO-8601 in UTC. */
    val date: String = "",
    @SerialName("home_team") val homeTeam: WrexhamTeamDto? = null,
    @SerialName("away_team") val awayTeam: WrexhamTeamDto? = null,
    /** Normal-time goals; null before kickoff. */
    @SerialName("home_score") val homeScore: Int? = null,
    @SerialName("away_score") val awayScore: Int? = null,
    /** Extra-time goals, counted separately from [homeScore]. */
    @SerialName("home_extra") val homeExtra: Int? = null,
    @SerialName("away_extra") val awayExtra: Int? = null,
    /** Shootout goals. */
    @SerialName("home_penalty") val homePenalty: Int? = null,
    @SerialName("away_penalty") val awayPenalty: Int? = null,
    /** "1", "2", "Extra", or "PEN" during a shootout. See the header for other spellings. */
    val period: String? = null,
    /** Match minute, e.g. "42" or "90+3". */
    val clock: String? = null,
    /** Detail status, e.g. "In Progress", "Break", "Suspended", "Postponed". */
    val status: String = "",
    /** Coarse bucket: "scheduled", "live" or "past". */
    @SerialName("status_type") val statusType: String = "",
    /** ASSUMED: new field a club endpoint would need; the World Cup feed had no competitions. */
    val competition: String = "",
)

/** A team inside a [WrexhamEventDto]. */
@Serializable
data class WrexhamTeamDto(
    val key: String = "",
    val name: String = "",
)

/**
 * Turns the response into what the card shows, in priority order: a match being played; else one that finished today
 * (its result stays up for the day); else up to three upcoming matches; else the "no upcoming games" message.
 *
 * @param today Today's date in the device's time zone.
 */
fun WrexhamMatchesResponseDto.toContent(today: LocalDate): WrexhamContent {
    val live = current.map { it.toMatch() }.firstOrNull()
    val finishedToday = previous.map { it.toMatch() }.lastOrNull { it.kickoff.toLocalDate() == today }
    val upcoming = next.map { it.toMatch() }.take(3)
    return when {
        live != null -> WrexhamContent.Matches(listOf(live))
        finishedToday != null -> WrexhamContent.Matches(listOf(finishedToday))
        upcoming.isNotEmpty() -> WrexhamContent.Matches(upcoming)
        else -> WrexhamContent.NoUpcoming
    }
}

/**
 * Maps one event to the widget's model. Kickoff times move from UTC to the device's time zone, and the score shown is
 * all goals so far: normal time plus extra time.
 */
fun WrexhamEventDto.toMatch(zone: ZoneId = ZoneId.systemDefault()): WrexhamMatch {
    val kickoff =
        try {
            ZonedDateTime.parse(date).withZoneSameInstant(zone).toLocalDateTime()
        } catch (e: DateTimeParseException) {
            LocalDateTime.now(zone)
        }
    return WrexhamMatch(
        id = globalEventId,
        competition = competition,
        home = homeTeam.toTeam(),
        away = awayTeam.toTeam(),
        kickoff = kickoff,
        status = toStatus(kickoff),
        homeScore = homeScore?.plus(homeExtra ?: 0),
        awayScore = awayScore?.plus(awayExtra ?: 0),
    )
}

/**
 * Status rules, extending the World Cup mapper: `status_type` gives the bucket and the detail `status`/`period` refine
 * it. A shootout shows up as period "PEN" or "PenaltyShootout" while live, but once the match is final the period flips
 * back, so penalty scores are checked too (as the World Cup code did). Halftime is the World Cup code's "Break" status.
 * Extra time matches "Extra" (Merino doc) and "ExtraTime" (SportsData.io).
 */
private fun WrexhamEventDto.toStatus(kickoff: LocalDateTime): WrexhamMatchStatus {
    val shootout =
        period.equals("PEN", ignoreCase = true) ||
            period.equals("PenaltyShootout", ignoreCase = true) ||
            homePenalty != null ||
            awayPenalty != null
    return when {
        status.equals("Postponed", ignoreCase = true) -> WrexhamMatchStatus.Postponed(kickoff)
        status.equals("Delayed", ignoreCase = true) -> WrexhamMatchStatus.Delayed(kickoff)
        status.equals("Suspended", ignoreCase = true) -> WrexhamMatchStatus.Suspended
        statusType == "live" ->
            when {
                shootout -> WrexhamMatchStatus.Penalties(homePenalty ?: 0, awayPenalty ?: 0)
                status.equals("Break", ignoreCase = true) -> WrexhamMatchStatus.Halftime
                period?.startsWith("Extra", ignoreCase = true) == true -> WrexhamMatchStatus.ExtraTime(clock.orEmpty())
                else -> WrexhamMatchStatus.Live(clock.orEmpty())
            }
        statusType == "past" && shootout -> WrexhamMatchStatus.FinalAfterPenalties(homePenalty ?: 0, awayPenalty ?: 0)
        statusType == "past" -> WrexhamMatchStatus.Final
        else -> WrexhamMatchStatus.Scheduled
    }
}

private fun WrexhamTeamDto?.toTeam(): WrexhamTeam {
    val key = this?.key.orEmpty()
    return if (key == Wrexham.key) Wrexham else WrexhamTeam(key, this?.name.orEmpty(), bundledCrests[key])
}

/**
 * Crests shipped with the prototype, by team key, like the World Cup widget's bundled flags. ASSUMED keys; teams
 * without a crest show an empty slot.
 */
private val bundledCrests =
    mapOf(
        "CHA" to R.drawable.wrexham_crest_charlton,
        "MUN" to R.drawable.wrexham_crest_manunited,
        "SOU" to R.drawable.wrexham_crest_southampton,
        "WHU" to R.drawable.wrexham_crest_west_ham,
    )
