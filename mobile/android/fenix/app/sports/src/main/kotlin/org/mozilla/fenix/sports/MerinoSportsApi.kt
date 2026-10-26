/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * SPORTS CORE / API MAPPING (PLACEHOLDER): the Merino response shape and how it maps to the model.
 *
 * What: [SportsMatchesClient] (fetches raw JSON), the DTOs, and [toContent] / [toMatch].
 * Called by: MerinoSportsRepository. Nothing calls it in the demo build.
 * Placeholder: there is no club endpoint or spec yet. Everything marked ASSUMED copies the World Cup
 * endpoint (`/api/v1/wcs/matches`, removed 2026-08-20), since a club endpoint would most likely come
 * from the same partner (SportsData.io) through the same Merino service. Check each ASSUMED item
 * against the spec; the rest of the module only sees the mapped [Match], so changes stay in this file.
 * Sources, most trusted first: the shipped World Cup client code (it ran against the real endpoint),
 * Merino's "API Contract Proposal for SportsData Suggestions" doc, and SportsData.io's soccer workflow
 * guide. The module README sums up what each says.
 */

package org.mozilla.fenix.sports

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Fetches matches from Merino. Wrapped in an interface so the repository can be tested without the app-services
 * binding.
 */
fun interface SportsMatchesClient {
    /**
     * @param teams Keys of the teams to fetch matches for.
     * @return The raw JSON body, or null on a network or API error.
     */
    fun fetchMatches(teams: Set<String>): String?
}

/** Recent, in-progress and upcoming matches for the requested teams. ASSUMED: the World Cup shape. */
@Serializable
data class MatchesResponseDto(
    val previous: List<EventDto> = emptyList(),
    val current: List<EventDto> = emptyList(),
    val next: List<EventDto> = emptyList(),
)

/** One match. ASSUMED: field for field the World Cup endpoint's `EventInfoDto`. */
@Serializable
data class EventDto(
    @SerialName("global_event_id") val globalEventId: Long = 0L,
    /** Kickoff, ISO-8601 in UTC. */
    val date: String = "",
    @SerialName("home_team") val homeTeam: TeamDto? = null,
    @SerialName("away_team") val awayTeam: TeamDto? = null,
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

/** A team inside an [EventDto]. */
@Serializable
data class TeamDto(
    val key: String = "",
    val name: String = "",
)

private const val MAX_UPCOMING = 3

/**
 * Turns the response into what the card shows, in priority order: a match being played; else one that finished today
 * (its result stays up for the day); else up to three upcoming matches; else the "no upcoming games" message.
 *
 * @param today Today's date in the device's time zone.
 */
fun MatchesResponseDto.toContent(today: LocalDate): SportsContent {
    val live = current.firstNotNullOfOrNull { it.toMatch() }
    val finishedToday = previous.mapNotNull { it.toMatch() }.lastOrNull { it.kickoff.toLocalDate() == today }
    val upcoming = next.mapNotNull { it.toMatch() }.take(MAX_UPCOMING)
    return when {
        live != null -> SportsContent.Matches(listOf(live))
        finishedToday != null -> SportsContent.Matches(listOf(finishedToday))
        upcoming.isNotEmpty() -> SportsContent.Matches(upcoming)
        else -> SportsContent.NoUpcoming
    }
}

/**
 * Maps one event to the model, or null if its kickoff can't be read (better to drop it than to show it as "Today").
 * Kickoff times move from UTC to the device's time zone, and the score is all goals so far: normal time plus extra
 * time.
 */
fun EventDto.toMatch(zone: ZoneId = ZoneId.systemDefault()): Match? {
    val kickoff =
        try {
            ZonedDateTime.parse(date).withZoneSameInstant(zone).toLocalDateTime()
        } catch (e: DateTimeParseException) {
            return null
        }
    return Match(
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
 * it. Detail statuses that can happen at any point win first.
 */
private fun EventDto.toStatus(kickoff: LocalDateTime): MatchStatus =
    when {
        status.equals("Postponed", ignoreCase = true) -> MatchStatus.Postponed(kickoff)
        status.equals("Delayed", ignoreCase = true) -> MatchStatus.Delayed(kickoff)
        status.equals("Suspended", ignoreCase = true) -> MatchStatus.Suspended
        statusType == "live" -> liveStatus()
        statusType == "past" -> pastStatus()
        else -> MatchStatus.Scheduled
    }

/** Halftime is the World Cup code's "Break" status; extra time matches "Extra" and "ExtraTime". */
private fun EventDto.liveStatus(): MatchStatus =
    when {
        isShootout() -> MatchStatus.Penalties(homePenalty ?: 0, awayPenalty ?: 0)
        status.equals("Break", ignoreCase = true) -> MatchStatus.Halftime
        period?.startsWith("Extra", ignoreCase = true) == true -> MatchStatus.ExtraTime(clock.orEmpty())
        else -> MatchStatus.Live(clock.orEmpty())
    }

private fun EventDto.pastStatus(): MatchStatus =
    if (isShootout()) {
        MatchStatus.FinalAfterPenalties(homePenalty ?: 0, awayPenalty ?: 0)
    } else {
        MatchStatus.Final
    }

/**
 * A shootout shows up as period "PEN" or "PenaltyShootout" while live, but once the match is final the period flips
 * back, so penalty scores are checked too (as the World Cup code did).
 */
private fun EventDto.isShootout(): Boolean =
    period?.lowercase() in setOf("pen", "penaltyshootout") || homePenalty != null || awayPenalty != null

private fun TeamDto?.toTeam(): Team {
    val key = this?.key.orEmpty()
    return Team(key, this?.name.orEmpty(), bundledCrests[key])
}

/**
 * Crests shipped with the prototype, by team key, like the World Cup widget's bundled flags. ASSUMED keys; teams
 * without a crest show an empty slot.
 */
private val bundledCrests =
    mapOf(
        "WRE" to R.drawable.sports_crest_wrexham,
        "CHA" to R.drawable.sports_crest_charlton,
        "MUN" to R.drawable.sports_crest_manunited,
        "SOU" to R.drawable.sports_crest_southampton,
        "WHU" to R.drawable.sports_crest_west_ham,
    )
