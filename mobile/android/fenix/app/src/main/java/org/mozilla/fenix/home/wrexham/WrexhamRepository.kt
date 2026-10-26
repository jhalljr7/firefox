/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.home.wrexham

import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import mozilla.components.support.base.log.logger.Logger

/*
 * WHERE THE WIDGET'S DATA COMES FROM
 *
 * The card asks a [WrexhamRepository] for its state. Today that's [FakeWrexhamRepository]
 * (Secret Settings scenarios). When the Merino club endpoint and its spec exist:
 *
 * 1. Implement [WrexhamMatchesClient] on the app-services Merino binding, the way the World Cup
 *    widget's `AppServicesWorldCupMatchesClient` wrapped `WorldCupClient` (removed in commit
 *    a91ad60e864c; read it with `git show a91ad60e864c^:<path>` under home/sports/client/).
 * 2. Correct the DTOs and status mapping in WrexhamMerinoApi.kt against the spec.
 * 3. Return [MerinoWrexhamRepository] from [wrexhamRepository]. Nothing in the UI changes.
 *
 * When it's fetched: `WrexhamWidgetSection` loads on every homepage resume and, while the homepage
 * stays on screen, again after [refreshIntervalMs] (sooner when a match is on). That matches the
 * World Cup widget, which fetched on `HomeFragment.onResume` behind a 60s throttle. If this ever
 * moves toward production, the loading belongs in an AppStore middleware like the World Cup's
 * `SportsWidgetMiddleware` (throttle, offline check, de-duplicated requests, cached response).
 */

/** Supplies the Wrexham widget's state. */
fun interface WrexhamRepository {
    /** Loads the latest state. Never throws: failures come back as [WrexhamContent.Error]. */
    suspend fun load(): WrexhamWidgetState
}

/**
 * Picks the data source for the widget. The single switch between dummy and real data.
 *
 * @param scenario The Secret Settings scenario to show while there's no real data.
 */
fun wrexhamRepository(scenario: WrexhamScenario): WrexhamRepository = FakeWrexhamRepository(scenario)

/** Dummy data for one Secret Settings scenario. */
class FakeWrexhamRepository(private val scenario: WrexhamScenario) : WrexhamRepository {
    override suspend fun load() = WrexhamFakeData.forScenario(scenario)
}

/**
 * Real data from Merino, shaped after the World Cup widget's `WorldCupMatchesRepository`. Not used yet; see the steps
 * at the top of this file.
 *
 * @param client Fetches the raw JSON.
 * @param json Decoder that ignores fields we don't use, so new API fields don't break us.
 * @param today Today's date, for keeping a finished match on the card for the rest of its day.
 */
class MerinoWrexhamRepository(
    private val client: WrexhamMatchesClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val today: () -> LocalDate = LocalDate::now,
) : WrexhamRepository {
    private val logger = Logger("MerinoWrexhamRepository")

    override suspend fun load(): WrexhamWidgetState {
        val content =
            withContext(Dispatchers.IO) {
                runCatching {
                    val body = client.fetchMatches(Wrexham.key) ?: error("Wrexham matches request returned no body")
                    json.decodeFromString<WrexhamMatchesResponseDto>(body).toContent(today())
                }
                    .getOrElse {
                        logger.error("Failed to fetch Wrexham matches", it)
                        WrexhamContent.Error
                    }
            }
        return WrexhamWidgetState(content = content)
    }
}

/**
 * How long to wait before loading again while the homepage stays on screen. Merino refreshes match data every 3
 * minutes, so polling every 60s during a match keeps the card at most a minute behind it; 60s was also the World Cup
 * widget's fetch throttle.
 */
fun WrexhamWidgetState.refreshIntervalMs(): Long {
    val matches = (content as? WrexhamContent.Matches)?.matches.orEmpty()
    return when {
        matches.any { it.status.isLive } -> 60_000L
        matches.any { it.kickoff.toLocalDate() == LocalDate.now() } -> 5 * 60_000L
        else -> 30 * 60_000L
    }
}
