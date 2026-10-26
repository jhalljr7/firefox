/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * SPORTS CORE / DATA: where a widget's state comes from, and when to reload it.
 *
 * What: [SportsRepository] (the interface), [MerinoSportsRepository] (real data, not wired up yet),
 * [refreshIntervalMs] (the reload schedule) and [SportsLoader] (one request at a time, a minimum
 * gap between requests, no request while offline).
 * Called by: the app's WrexhamWidgetMiddleware (SportsLoader) and refreshWrexhamWidgetWhileResumed
 * (refreshIntervalMs). The middleware turns each result into a Loaded or Failed AppStore action.
 * Calls: the client and mapping in MerinoSportsApi.kt; today the app passes FakeWrexhamRepository instead.
 *
 * Plugging in real data:
 * 1. Implement [SportsMatchesClient] on the app-services Merino binding, the way the World Cup widget's
 *    `AppServicesWorldCupMatchesClient` wrapped `WorldCupClient` (removed in commit a91ad60e864c; read
 *    it with `git show a91ad60e864c^:<path>` under home/sports/client/).
 * 2. Check the DTOs and status rules in MerinoSportsApi.kt against the spec.
 * 3. In the app's Components.kt, pass a [MerinoSportsRepository] instead of the fake and drop
 *    `minIntervalMs = 0L`. Nothing in the UI changes.
 */

package org.mozilla.fenix.sports

import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import mozilla.components.support.base.log.logger.Logger

/** Supplies a club widget's state. */
fun interface SportsRepository {
    /**
     * Loads the latest state. A failed load (network, server, bad response) comes back as a failed [Result], so the
     * caller can keep showing what it already has; [SportsContent.Error] is only for a cold start.
     */
    suspend fun load(): Result<ClubWidgetState>
}

/**
 * Real data from Merino, shaped after the World Cup widget's `WorldCupMatchesRepository`. Not used yet; see the steps
 * at the top of this file.
 *
 * @param client Fetches the raw JSON.
 * @param teams Keys of the teams to fetch, e.g. the followed club's.
 * @param json Decoder that ignores fields we don't use, so new API fields don't break us.
 * @param today Today's date, for keeping a finished match on the card for the rest of its day.
 */
class MerinoSportsRepository(
    private val client: SportsMatchesClient,
    private val teams: Set<String>,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val today: () -> LocalDate = LocalDate::now,
) : SportsRepository {
    private val logger = Logger("MerinoSportsRepository")

    override suspend fun load(): Result<ClubWidgetState> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = client.fetchMatches(teams) ?: error("Sports matches request returned no body")
                ClubWidgetState(json.decodeFromString<MatchesResponseDto>(body).toContent(today()))
            }
                .onFailure { logger.error("Failed to fetch sports matches", it) }
        }
}

private const val LIVE_REFRESH_MS = 60_000L
private const val MATCH_DAY_REFRESH_MS = 5 * 60_000L
private const val IDLE_REFRESH_MS = 30 * 60_000L

/**
 * How long to wait before loading again while the homepage stays on screen. Merino refreshes match data every 3
 * minutes, so polling every 60s during a match keeps the card at most a minute behind it; 60s was also the World Cup
 * widget's fetch throttle.
 *
 * @param today Today's date in the device's time zone.
 */
fun ClubWidgetState?.refreshIntervalMs(today: LocalDate = LocalDate.now()): Long {
    val matches = (this?.content as? SportsContent.Matches)?.matches.orEmpty()
    return when {
        matches.any { it.status.isLive } -> LIVE_REFRESH_MS
        matches.any { it.kickoff.toLocalDate() == today } -> MATCH_DAY_REFRESH_MS
        else -> IDLE_REFRESH_MS
    }
}

/**
 * Loads from a [SportsRepository] with the guards the World Cup widget's `SportsWidgetMiddleware` had: one request at a
 * time, a minimum gap between requests, and no request while offline (reported as a failure straight away instead of
 * waiting for a timeout).
 *
 * @param repository Where the data comes from.
 * @param scope Scope the loads run in.
 * @param isOnline Whether the device has a connection.
 * @param minIntervalMs Minimum gap between requests, counted from the last attempt so a failing endpoint isn't hammered
 *   by retries. 0 for dummy data, so a Secret Settings scenario switch shows up straight away.
 * @param clock Current time in milliseconds.
 */
class SportsLoader(
    private val repository: SportsRepository,
    private val scope: CoroutineScope,
    private val isOnline: () -> Boolean,
    private val minIntervalMs: Long = LIVE_REFRESH_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Volatile private var inFlight: Job? = null

    @Volatile private var lastAttemptMs: Long? = null

    /**
     * Starts a load unless one is running or the last one was too recent.
     *
     * @param force Skips the minimum gap, for an explicit user retry. Still one request at a time.
     * @param onResult Receives the load's result, on [scope].
     */
    fun refresh(
        force: Boolean = false,
        onResult: (Result<ClubWidgetState>) -> Unit,
    ) {
        if (inFlight?.isActive == true) return
        val now = clock()
        val last = lastAttemptMs
        if (!force && last != null && now - last < minIntervalMs) return
        lastAttemptMs = now
        if (!isOnline()) {
            onResult(Result.failure(IllegalStateException("Offline")))
            return
        }
        inFlight = scope.launch { onResult(repository.load()) }
    }
}
