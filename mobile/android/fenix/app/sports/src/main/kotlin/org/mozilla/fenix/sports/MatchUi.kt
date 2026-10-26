/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * SPORTS CORE / MATCH UI: reusable pieces for showing one match.
 *
 * What: [MatchRow] (tag, competition, teams and centre column), the status tag, score pill, status
 * line and [Crest]. Display text ("29'", "Today", "FT") is formatted here from the model, in the app's
 * language and the phone's 12/24-hour setting.
 * Called by: ClubWidget.kt. Any future sports widget (a league table, a global scores card) can reuse it.
 */

package org.mozilla.fenix.sports

import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Red for live matches: the LIVE tag, its dot and the live score pill. */
internal val LiveRed = Color(0xFFB3242F)
private val RedCardRed = Color(0xFFE22850)

private const val PULSE_HALF_PERIOD_MS = 800
private const val PULSE_MIN_ALPHA = 0.55f
private const val LIVE_TAG_TINT = 0.12f
private const val POP_MS = 500
private const val POP_START_SCALE = 0.8f
private val PopEasing = CubicBezierEasing(0.3f, 1.6f, 0.5f, 1f)
private val CrestSize = 32.dp
private val LiveDotSize = 4.dp
private val TagCorner = 4.dp
private val RedCardWidth = 6.dp
private val RedCardHeight = 9.dp

/**
 * Opacity for things that pulse while a match is live: the dot in the LIVE tag and the running match minute. Fades 1 ->
 * 0.55 -> 1 every 1.6s, the design's `@keyframes pulse`.
 */
@Composable
private fun livePulseAlpha(): Float {
    val alpha by
        rememberInfiniteTransition(label = "SportsLivePulse")
            .animateFloat(
                initialValue = 1f,
                targetValue = PULSE_MIN_ALPHA,
                animationSpec = infiniteRepeatable(tween(PULSE_HALF_PERIOD_MS), RepeatMode.Reverse),
                label = "alpha",
            )
    return alpha
}

/**
 * One match: a line with an optional status tag and the competition, then home team, the centre (score or kickoff time)
 * and away team in three equal columns. Tapping it opens [Match.url].
 */
@Composable
fun MatchRow(
    match: Match,
    onOpenUrl: (String) -> Unit,
) {
    val space = SportsTheme.layout.space
    Column(
        modifier = Modifier.fillMaxWidth().clickable { onOpenUrl(match.url) }.padding(space.static150),
        verticalArrangement = Arrangement.spacedBy(space.static150),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space.static100),
        ) {
            match.status.tagLabel()?.let { StatusTag(label = it, live = match.status.isLive) }
            Text(
                text = match.competition,
                style = SportsTheme.typography.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamColumn(team = match.home, modifier = Modifier.weight(1f))
            CenterColumn(match = match, modifier = Modifier.weight(1f))
            TeamColumn(team = match.away, modifier = Modifier.weight(1f))
        }
    }
}

/** Label for the tag before the competition name, or null when the match doesn't need one. */
@Composable
private fun MatchStatus.tagLabel(): String? =
    when {
        isLive -> stringResource(R.string.sports_tag_live)
        this is MatchStatus.Delayed -> stringResource(R.string.sports_tag_delayed)
        this is MatchStatus.Suspended -> stringResource(R.string.sports_tag_suspended)
        this is MatchStatus.Postponed -> stringResource(R.string.sports_tag_postponed)
        else -> null
    }

/**
 * Status tag before the competition name. LIVE is red text on a pale red background with a pulsing dot; Delayed,
 * Suspended and Postponed are inverted (dark chip, light text).
 */
@Composable
private fun StatusTag(
    label: String,
    live: Boolean,
) {
    val space = SportsTheme.layout.space
    val shape = RoundedCornerShape(TagCorner)
    if (live) {
        Row(
            modifier =
                Modifier.background(LiveRed.copy(alpha = LIVE_TAG_TINT), shape)
                    .padding(horizontal = space.static100, vertical = space.static25),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space.static50),
        ) {
            Box(modifier = Modifier.size(LiveDotSize).alpha(livePulseAlpha()).background(LiveRed, CircleShape))
            Text(text = label, style = SportsTheme.typography.caption, color = LiveRed, fontWeight = FontWeight.Bold)
        }
    } else {
        Text(
            text = label,
            style = SportsTheme.typography.caption,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            modifier =
                Modifier.background(MaterialTheme.colorScheme.inverseSurface, shape)
                    .padding(horizontal = space.static100, vertical = space.static25),
        )
    }
}

/**
 * Middle column of a match. Before kickoff: the day ("Today", "Tomorrow", a date) above the time. Once there's a score:
 * the score pill, a red card marker beside the side that got one, and the [statusLine] under it. The match minute
 * pulses while the clock runs in normal time.
 */
@Composable
private fun CenterColumn(
    match: Match,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        val home = match.homeScore
        val away = match.awayScore
        if (home == null || away == null) {
            Text(
                text = dayLabel(match.kickoff.toLocalDate()),
                style = SportsTheme.typography.caption,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            ScoreRow(match = match, home = home, away = away)
        }
        StatusLine(match)
    }
}

/** The score pill with a red card marker on the side that has had a player sent off. */
@Composable
private fun ScoreRow(
    match: Match,
    home: Int,
    away: Int,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (match.redCard == Side.HOME) RedCard()
        ScorePill(match = match, home = home, away = away)
        if (match.redCard == Side.AWAY) RedCard()
    }
}

/** The [statusLine] under the score or kickoff day, pulsing while the clock runs in normal time. */
@Composable
private fun StatusLine(match: Match) {
    val line = match.statusLine()
    if (line.isEmpty()) return
    Text(
        text = line,
        style = SportsTheme.typography.caption,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = if (match.status is MatchStatus.Live) Modifier.alpha(livePulseAlpha()) else Modifier,
    )
}

/**
 * Date and time formats for the app's current language and the phone's 12/24-hour setting. Built in composition rather
 * than once at class load, so Firefox's in-app language switch and the clock setting are both respected.
 */
private class MatchFormats(locale: Locale, is24Hour: Boolean) {
    val time: DateTimeFormatter =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (is24Hour) "Hm" else "hm"), locale)
    val shortDate: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)
    val mediumDate: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
}

@Composable
private fun rememberMatchFormats(): MatchFormats {
    val locale = LocalConfiguration.current.locales[0]
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    return remember(locale, is24Hour) { MatchFormats(locale, is24Hour) }
}

/** "Today", "Tomorrow", or the date in the user's short format. */
@Composable
private fun dayLabel(day: LocalDate): String {
    val formats = rememberMatchFormats()
    val today = LocalDate.now()
    return when (day) {
        today -> stringResource(R.string.sports_today)
        today.plusDays(1) -> stringResource(R.string.sports_tomorrow)
        else -> day.format(formats.shortDate)
    }
}

/**
 * The line under the score or kickoff day, in the design's wording: the kickoff time, "29'", "Halftime", "ET 112'",
 * "FT", penalty scores, or the new time for a delayed or postponed match. Empty for a suspended match.
 */
@Composable
private fun Match.statusLine(): String {
    val formats = rememberMatchFormats()
    return when (val s = status) {
        MatchStatus.Scheduled -> kickoff.format(formats.time)
        is MatchStatus.Live -> stringResource(R.string.sports_clock, s.clock)
        MatchStatus.Halftime -> stringResource(R.string.sports_halftime)
        is MatchStatus.ExtraTime -> stringResource(R.string.sports_extra_time_clock, s.clock)
        is MatchStatus.Penalties -> stringResource(R.string.sports_penalties, s.home, s.away)
        MatchStatus.Final -> stringResource(R.string.sports_full_time)
        is MatchStatus.FinalAfterPenalties -> stringResource(R.string.sports_full_time_penalties, s.home, s.away)
        is MatchStatus.Delayed -> stringResource(R.string.sports_delayed_starting, s.newKickoff.format(formats.time))
        MatchStatus.Suspended -> ""
        is MatchStatus.Postponed -> "${s.newKickoff.format(formats.mediumDate)}\n${s.newKickoff.format(formats.time)}"
    }
}

/**
 * The score, e.g. "1 - 2", in a pill: red while the match is live, neutral otherwise. Screen readers hear "West Ham 1,
 * Wrexham AFC 2" instead of "1 dash 2".
 *
 * Whenever the score changes it "pops": it jumps to 80% size and springs back with a slight overshoot over 0.5s (the
 * design's `@keyframes pop`). It also pops when it first appears, as in the design.
 */
@Composable
private fun ScorePill(
    match: Match,
    home: Int,
    away: Int,
) {
    val live = match.status.isLive
    val pop = remember { Animatable(1f) }
    LaunchedEffect(home, away) {
        pop.snapTo(POP_START_SCALE)
        pop.animateTo(1f, tween(POP_MS, easing = PopEasing))
    }
    val description =
        stringResource(R.string.sports_score_content_description, match.home.name, home, match.away.name, away)
    Text(
        text = "$home - $away",
        style = SportsTheme.typography.headline7,
        fontWeight = FontWeight.Bold,
        color = if (live) Color.White else MaterialTheme.colorScheme.onSurface,
        modifier =
            Modifier.scale(pop.value)
                .semantics { contentDescription = description }
                .background(
                    if (live) LiveRed else MaterialTheme.colorScheme.surfaceContainerHighest,
                    RoundedCornerShape(percent = 50),
                )
                .padding(horizontal = SportsTheme.layout.space.static200, vertical = SportsTheme.layout.space.static50),
    )
}

/** Small red rectangle beside the score on the side that has had a player sent off. */
@Composable
private fun RedCard() {
    val description = stringResource(R.string.sports_red_card_content_description)
    Box(
        modifier =
            Modifier.padding(horizontal = SportsTheme.layout.space.static100)
                .size(width = RedCardWidth, height = RedCardHeight)
                .background(RedCardRed, RoundedCornerShape(1.dp))
                .semantics { contentDescription = description }
    )
}

/** A team's crest, or an empty space of the same size when we don't ship one. Decorative: the name is next to it. */
@Composable
fun Crest(
    team: Team,
    size: Dp,
) {
    val crest = team.crestRes
    if (crest == null) {
        Spacer(modifier = Modifier.size(size))
    } else {
        Image(painter = painterResource(crest), contentDescription = null, modifier = Modifier.size(size))
    }
}

/** A team column: crest above the team name (one line, cut off with an ellipsis if too long). */
@Composable
private fun TeamColumn(
    team: Team,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SportsTheme.layout.space.static100),
    ) {
        Crest(team = team, size = CrestSize)
        Text(
            text = team.name,
            style = SportsTheme.typography.caption,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
