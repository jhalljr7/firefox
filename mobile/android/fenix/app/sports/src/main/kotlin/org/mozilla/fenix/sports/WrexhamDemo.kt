/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * DEMO ONLY / WREXHAM SCENARIOS: the dummy states shown until the Merino club endpoint exists.
 *
 * What: one [WrexhamScenario] per design state from the Android PDF; [FakeWrexhamRepository] serves
 * whichever one is picked in Secret Settings or in [WrexhamScenarioPicker] (the widget menu's demo item).
 * Used by: the app's Components.kt (the repository), SecretSettingsFragment (the scenario list) and
 * Settings (the saved choice).
 * Remove with DemoScript.kt once real data exists; Components.kt then passes a MerinoSportsRepository.
 */

package org.mozilla.fenix.sports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import mozilla.components.compose.base.button.RadioButton

/**
 * Dummy scenarios for the Wrexham widget, used until the Merino club endpoint exists. Switch between them in Secret
 * Settings > "Wrexham widget scenario"; that list is built from this enum, so a new entry shows up automatically. Each
 * is a design state from the Android PDF.
 *
 * @property label Name shown in the Secret Settings list.
 * @property script Demo sequence that plays once the card is on screen, or null for a still state.
 * @property content Builds the scenario's card content from the demo fixtures.
 */
enum class WrexhamScenario(
    val label: String,
    val script: DemoScript? = null,
    val content: DemoFixtures.() -> SportsContent,
) {
    UPCOMING("Upcoming matches", content = { upcoming() }),
    LIVE("Live", content = { westHam(1, 2, MatchStatus.Live("29")) }),
    HALFTIME("Halftime", content = { westHam(1, 1, MatchStatus.Halftime) }),
    FULL_TIME_RED_CARD("Full time with red card", content = { westHam(1, 1, MatchStatus.Final, redCard = Side.HOME) }),
    CUP_EXTRA_TIME("FA Cup extra time", content = { manUnitedCupTie(MatchStatus.ExtraTime("112")) }),
    CUP_PENALTIES(
        "FA Cup penalties",
        content = { westHam(1, 1, MatchStatus.FinalAfterPenalties(4, 5), cup = true, redCard = Side.HOME) },
    ),
    LIVE_CLOCK("Live, Wrexham scores (plays out)", DemoScript.LIVE_CLOCK, { westHam(1, 1, MatchStatus.Live("29")) }),
    GOAL("Goal celebration", DemoScript.GOAL, { westHam(3, 1, MatchStatus.Live("87")) }),
    WINNER("Winner celebration", DemoScript.WINNER, { westHam(2, 2, MatchStatus.Penalties(4, 4), cup = true) }),
    SHOOTOUT("FA Cup shootout (plays out)", DemoScript.SHOOTOUT, { manUnitedCupTie(MatchStatus.Penalties(0, 0)) }),
    DELAYED("Delayed", content = { westHam(0, 0, MatchStatus.Delayed(at(0, 15))) }),
    SUSPENDED("Suspended", content = { westHam(1, 1, MatchStatus.Suspended) }),
    POSTPONED("Postponed", content = { westHam(0, 0, MatchStatus.Postponed(at(7, 14, 45))) }),
    NO_UPCOMING("No upcoming games", content = { SportsContent.NoUpcoming }),
    ERROR("Error", content = { SportsContent.Error }),
    SEASON_OVER("Season over", content = { SportsContent.SeasonOver });

    /**
     * Builds this scenario's widget state. Kickoffs are relative to today, so "Today" stays true. Each scenario's
     * matches get their own ids, so switching scenarios never looks like a score change to the celebrations.
     */
    fun state(today: LocalDate = LocalDate.now()): ClubWidgetState =
        ClubWidgetState(content = DemoFixtures(today, idBase = ordinal * IDS_PER_SCENARIO).content(), script = script)

    companion object {
        private const val IDS_PER_SCENARIO = 10L

        /** The scenario saved under [name] in settings, or [UPCOMING] if there is none. */
        fun from(name: String?): WrexhamScenario = entries.firstOrNull { it.name == name } ?: UPCOMING
    }
}

/**
 * Demo builds only: a picker of all [WrexhamScenario]s, opened from the widget's menu so anyone trying the build can
 * switch states (and trigger the celebrations) without digging into Secret Settings.
 *
 * @param current The scenario on screen now, shown as selected.
 * @param onPick Called with the chosen scenario.
 * @param onDismiss Called when the picker is closed without a choice.
 */
@Composable
fun WrexhamScenarioPicker(
    current: WrexhamScenario,
    onPick: (WrexhamScenario) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text(stringResource(R.string.sports_demo_picker_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                WrexhamScenario.entries.forEach { scenario ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onPick(scenario) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = scenario == current, onClick = { onPick(scenario) })
                        Text(text = scenario.label, style = SportsTheme.typography.body2)
                    }
                }
            }
        },
    )
}

/** Dummy data for one [WrexhamScenario], so the scenarios are always visible in the debug build. */
class FakeWrexhamRepository(private val scenario: () -> WrexhamScenario) : SportsRepository {
    override suspend fun load(): Result<ClubWidgetState> = Result.success(scenario().state())
}

/**
 * Teams and match builders shared by the scenarios. The details don't have to agree across scenarios; the real API
 * replaces all of this. Wrexham is the away side in the single-match ones.
 */
class DemoFixtures(private val today: LocalDate, private val idBase: Long) {
    private val wrexham = Wrexham.team
    private val charlton = Team("CHA", "Charlton", R.drawable.sports_crest_charlton)
    private val manUnited = Team("MUN", "Man United", R.drawable.sports_crest_manunited)
    private val southampton = Team("SOU", "Southampton", R.drawable.sports_crest_southampton)
    private val westHam = Team("WHU", "West Ham", R.drawable.sports_crest_west_ham)

    /** A kickoff [daysFromToday] days away at [hour]:[minute]. */
    fun at(daysFromToday: Long, hour: Int, minute: Int = 0): LocalDateTime =
        today.plusDays(daysFromToday).atTime(LocalTime.of(hour, minute))

    private val kickoff = at(0, KICKOFF_HOUR, KICKOFF_MINUTE)

    /** Three upcoming fixtures: today, later this week and next week. */
    fun upcoming(): SportsContent =
        SportsContent.Matches(
            listOf(
                Match(id = idBase + 3, EFL, charlton, wrexham, kickoff, url = Wrexham.matchesUrl),
                Match(
                    id = idBase + 4,
                    FA_CUP,
                    wrexham,
                    manUnited,
                    at(daysFromToday = 5, hour = 18),
                    url = Wrexham.matchesUrl,
                ),
                Match(
                    id = idBase + 5,
                    EFL,
                    wrexham,
                    southampton,
                    at(daysFromToday = 12, hour = KICKOFF_HOUR, minute = KICKOFF_MINUTE),
                    url = Wrexham.matchesUrl,
                ),
            )
        )

    /** West Ham v Wrexham today. */
    fun westHam(home: Int, away: Int, status: MatchStatus, cup: Boolean = false, redCard: Side? = null): SportsContent =
        single(
            Match(
                id = idBase + 1,
                competition = if (cup) FA_CUP else EFL,
                home = westHam,
                away = wrexham,
                kickoff = kickoff,
                status = status,
                homeScore = home,
                awayScore = away,
                redCard = redCard,
                url = Wrexham.matchesUrl,
            )
        )

    /** Man United v Wrexham, FA Cup, level at 1 - 1. */
    fun manUnitedCupTie(status: MatchStatus): SportsContent =
        single(
            Match(
                id = idBase + 2,
                FA_CUP,
                manUnited,
                wrexham,
                kickoff,
                status,
                homeScore = 1,
                awayScore = 1,
                url = Wrexham.matchesUrl,
            )
        )

    private fun single(match: Match) = SportsContent.Matches(listOf(match))

    private companion object {
        const val EFL = "EFL Championship"
        const val FA_CUP = "FA Cup"
        const val KICKOFF_HOUR = 14
        const val KICKOFF_MINUTE = 45
    }
}
