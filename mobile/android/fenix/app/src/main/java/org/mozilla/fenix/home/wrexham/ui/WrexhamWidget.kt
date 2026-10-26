/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * WREXHAM AFC HOMEPAGE WIDGET: UI (prototype only, never meant to land)
 *
 * A local demo of the Wrexham AFC widget from the design drop (Android PDF + HTML prototype),
 * built on dummy data because no club data endpoint exists yet. The real implementation path is
 * bug 2084809 / FXDROID-10994; this is for demos and design checks only. Handoff doc with build
 * steps and context: ~/vault/Briefs/wrexham-widget-prototype-handoff.md.
 *
 * Where things live:
 * - WrexhamWidgetState.kt: the data model. WrexhamFakeData.kt: dummy scenarios.
 * - WrexhamRepository.kt / WrexhamMerinoApi.kt: where data comes from, and the API hook point.
 * - This file: the card. [WrexhamWidgetSection] is what the homepage renders (right after the
 *   shortcuts): it loads the data, wires taps to real app actions and wraps [WrexhamWidget].
 *   Display text ("29'", "Today", "FT") is formatted here from the structured model.
 * - WrexhamEffects.kt: the celebrations and what triggers them.
 *
 * Card layout, top to bottom: header (crest, title, overflow menu), divider, body. The body is
 * one match, a swipeable carousel of upcoming matches, or a message (no games, error, season over).
 */

package org.mozilla.fenix.home.wrexham.ui

import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.Navigation
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay
import mozilla.components.compose.base.menu.DropdownMenu
import mozilla.components.compose.base.menu.MenuItem
import mozilla.components.compose.base.text.Text as MenuText
import mozilla.components.support.ktx.android.content.share
import mozilla.components.ui.icons.R as iconsR
import org.mozilla.fenix.NavGraphDirections
import org.mozilla.fenix.R
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.openToBrowser
import org.mozilla.fenix.home.wrexham.Wrexham
import org.mozilla.fenix.home.wrexham.WrexhamContent
import org.mozilla.fenix.home.wrexham.WrexhamFakeData
import org.mozilla.fenix.home.wrexham.WrexhamLinks
import org.mozilla.fenix.home.wrexham.WrexhamMatch
import org.mozilla.fenix.home.wrexham.WrexhamMatchStatus
import org.mozilla.fenix.home.wrexham.WrexhamRepository
import org.mozilla.fenix.home.wrexham.WrexhamScenario
import org.mozilla.fenix.home.wrexham.WrexhamSide
import org.mozilla.fenix.home.wrexham.WrexhamTeam
import org.mozilla.fenix.home.wrexham.WrexhamWidgetState
import org.mozilla.fenix.home.wrexham.isLive
import org.mozilla.fenix.home.wrexham.refreshIntervalMs
import org.mozilla.fenix.home.wrexham.wrexhamRepository
import org.mozilla.fenix.theme.FirefoxTheme

/** Red used for live matches: the LIVE tag, its dot and the live score pill. */
private val LiveRed = Color(0xFFB3242F)
/** Fast-out, gentle-settle easing the design prototype uses for the card resizing. */
private val SwiftEasing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1f)

/**
 * Opacity for things that pulse while a match is live: the dot in the LIVE tag and the running match minute. Fades 1 ->
 * 0.55 -> 1 every 1.6s, the design's `@keyframes pulse`.
 */
@Composable
private fun livePulseAlpha(): Float {
    val alpha by
        rememberInfiniteTransition(label = "WrexhamLivePulse")
            .animateFloat(
                initialValue = 1f,
                targetValue = 0.55f,
                animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                label = "alpha",
            )
    return alpha
}

/**
 * Callbacks for the Wrexham widget.
 *
 * @property onOpenUrl Opens a club page in a new tab.
 * @property onWallpapers Opens the wallpaper settings.
 * @property onShare Shares a match.
 * @property onRemove Hides the widget.
 * @property onRefresh Retries loading match data.
 */
data class WrexhamWidgetActions(
    val onOpenUrl: (String) -> Unit = {},
    val onWallpapers: () -> Unit = {},
    val onShare: (WrexhamMatch?) -> Unit = {},
    val onRemove: () -> Unit = {},
    val onRefresh: () -> Unit = {},
)

/**
 * Homepage section that hosts the Wrexham widget, wires its actions and handles remove with undo. This is the entry
 * point Homepage.kt calls; [WrexhamWidget] is the card UI underneath it.
 *
 * Removing the widget hides it right away and shows an "Undo" row in its place. The hidden setting persists, so after
 * leaving the homepage the widget stays gone until it's turned back on in Settings > Homepage.
 *
 * @param scenario Which dummy scenario to show until real data exists (see [wrexhamRepository]).
 * @param modifier [Modifier] to be applied to the section.
 */
@Composable
fun WrexhamWidgetSection(
    scenario: WrexhamScenario,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settings = context.components.settings
    var removed by remember { mutableStateOf(false) }
    // Bumped by the error card's Refresh button to force an immediate reload.
    var refreshes by remember { mutableIntStateOf(0) }
    val state = rememberWrexhamWidgetState(remember(scenario) { wrexhamRepository(scenario) }, refreshes)

    if (removed) {
        RemovedRow(
            onUndo = {
                settings.showHomepageWrexhamWidget = true
                removed = false
            },
            modifier = modifier,
        )
        return
    }
    // Nothing to show until the first load finishes. With dummy data that's a single frame.
    if (state == null) return

    WrexhamWidget(
        state = state,
        modifier = modifier,
        actions =
            WrexhamWidgetActions(
                // Every link opens the club site in a new tab. The site has one page per team with
                // Fixtures, Results and Table tabs, so Matches, Results and Standings share a URL for now.
                onOpenUrl = { url ->
                    context.components.useCases.fenixBrowserUseCases.loadUrlOrSearch(
                        searchTermOrURL = url,
                        newTab = true,
                    )
                    (context as? Activity)?.let { Navigation.findNavController(it, R.id.container).openToBrowser() }
                },
                onWallpapers = {
                    (context as? Activity)?.let {
                        Navigation.findNavController(it, R.id.container)
                            .navigate(NavGraphDirections.actionGlobalWallpaperSettingsFragment())
                    }
                },
                onShare = { match ->
                    val text =
                        match?.let { "${it.home.name} vs ${it.away.name}, ${it.competition}: ${it.url}" }
                            ?: "Follow Wrexham AFC: ${WrexhamLinks.MATCHES}"
                    context.share(text, subject = "Firefox - Wrexham AFC")
                },
                onRemove = {
                    settings.showHomepageWrexhamWidget = false
                    removed = true
                },
                onRefresh = { refreshes++ },
            ),
    )
}

/**
 * Loads the widget's state from [repository]: on every homepage resume, then again after [refreshIntervalMs] for as
 * long as the homepage stays resumed (every minute during a match). Loading stops while the homepage is in the
 * background. This is the same moment the World Cup widget fetched (`HomeFragment.onResume`); see WrexhamRepository.kt
 * for the production version.
 *
 * @param repository Where the data comes from.
 * @param refreshes Changing this reloads straight away (the Refresh button).
 * @return The latest state, or null before the first load finishes.
 */
@Composable
private fun rememberWrexhamWidgetState(repository: WrexhamRepository, refreshes: Int): WrexhamWidgetState? {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by
        produceState<WrexhamWidgetState?>(initialValue = null, repository, refreshes) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    val latest = repository.load()
                    value = latest
                    delay(latest.refreshIntervalMs())
                }
            }
        }
    return state
}

/**
 * Homepage card for Wrexham AFC fixtures and live scores.
 *
 * The card (a Surface with header, divider and [CardBody]) has [CardEffects] layered over it. Effects that cover the
 * whole screen are drawn separately by [WrexhamScreenEffects]. Which effects are showing, and the live score during a
 * demo, come from [rememberWrexhamFx]. The header always shows Wrexham; the widget follows one club.
 *
 * @param state The [WrexhamWidgetState] to display.
 * @param modifier [Modifier] to be applied to the card.
 * @param actions Callbacks for taps on the card.
 */
@Composable
fun WrexhamWidget(
    state: WrexhamWidgetState,
    modifier: Modifier = Modifier,
    actions: WrexhamWidgetActions = WrexhamWidgetActions(),
) {
    // Plays celebrations when the match changes, and runs the demo script if there is one.
    // fx.match is the match as a demo script has updated it; null means use the state's own.
    val fx = rememberWrexhamFx(state)
    val displayContent = fx.match?.let { WrexhamContent.Matches(listOf(it)) } ?: state.content
    val matches = (displayContent as? WrexhamContent.Matches)?.matches.orEmpty()
    val pagerState = rememberPagerState { matches.size }
    // Where the card sits in the window, so the full-screen fox can run at the card's height.
    var cardTop by remember { mutableFloatStateOf(0f) }
    var cardHeight by remember { mutableIntStateOf(0) }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .onGloballyPositioned {
                    cardTop = it.positionInWindow().y
                    cardHeight = it.size.height
                }
                // The first real draw tells the demo scripts the card is on screen (see WrexhamFx.drawn).
                .drawBehind { if (!fx.drawn) fx.drawn = true }
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            // animateContentSize: when the body changes height (carousel page with the
            // "Full Calendar" button, or a different scenario) the card grows or shrinks smoothly.
            Column(modifier = Modifier.animateContentSize(tween(350, easing = SwiftEasing)).padding(bottom = 12.dp)) {
                Header(
                    actions = actions,
                    currentMatch = matches.getOrNull(pagerState.currentPage),
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))

                // When the body switches to a different kind of view it fades and slides in 5dp
                // (the design's `wfade`, 0.34s). contentKey makes a live score or clock change
                // count as the same view, so those update in place instead of re-fading the body.
                AnimatedContent(
                    targetState = displayContent,
                    contentKey = { content -> (content as? WrexhamContent.Matches)?.matches?.size ?: content },
                    transitionSpec = {
                        (fadeIn(tween(340)) + slideInVertically(tween(340)) { it / 50 }) togetherWith fadeOut(tween(0))
                    },
                    label = "WrexhamViewChange",
                ) { content ->
                    CardBody(content = content, liveMatch = fx.match, pagerState = pagerState, actions = actions)
                }
            }
        }
        CardEffects(fx)
    }
    // Full-screen effects (winner confetti, running fox) in their own window above the homepage.
    WrexhamScreenEffects(fx = fx, cardTop = cardTop, cardHeight = cardHeight)
}

/**
 * The card's body for [content].
 *
 * @param liveMatch The match as a demo script has updated it, if any. Read here rather than from [content] because
 *   AnimatedContent keeps its first snapshot of a view it treats as unchanged, so `content` alone would show a stale
 *   score.
 * @param pagerState Carousel position, shared with the header so Share knows which match is showing.
 */
@Composable
private fun CardBody(
    content: WrexhamContent,
    liveMatch: WrexhamMatch?,
    pagerState: PagerState,
    actions: WrexhamWidgetActions,
) {
    Column {
        when (content) {
            is WrexhamContent.Matches ->
                if (content.matches.size == 1) {
                    MatchBody(match = liveMatch ?: content.matches.first(), onOpenUrl = actions.onOpenUrl)
                } else {
                    // Upcoming matches: swipe sideways; the last page adds "Full Calendar".
                    HorizontalPager(state = pagerState) { page ->
                        Column {
                            MatchBody(match = content.matches[page], onOpenUrl = actions.onOpenUrl)
                            if (page == content.matches.lastIndex) {
                                PillButton(
                                    text = "Full Calendar",
                                    onClick = { actions.onOpenUrl(WrexhamLinks.MATCHES) },
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                    PageDots(count = content.matches.size, selected = pagerState.currentPage)
                }
            WrexhamContent.NoUpcoming ->
                MessageBody(
                    title = "The men's team has no upcoming games.",
                    subtitle = "Check the full calendar for what's next.",
                    buttonText = "Full Calendar",
                    imageRes = R.drawable.wrexham_kit_ball,
                    onClick = { actions.onOpenUrl(WrexhamLinks.MATCHES) },
                )
            WrexhamContent.Error ->
                MessageBody(
                    title = "Match info is not available right now",
                    subtitle = "Try refreshing in a few minutes.",
                    buttonText = "Refresh",
                    imageRes = R.drawable.wrexham_kit_alert,
                    onClick = actions.onRefresh,
                )
            WrexhamContent.SeasonOver -> SeasonOverBody(onClick = { actions.onOpenUrl(WrexhamLinks.RESULTS) })
        }
    }
}

/**
 * Card-sized effect layers drawn over the card: confetti clipped to its rounded shape, and the Kit goal and win GIFs
 * anchored to its bottom edge. The GIFs may stick out above the card (the win GIF is taller than it) without changing
 * the card's size.
 */
@Composable
private fun BoxScope.CardEffects(fx: WrexhamFx) {
    if (fx.confetti) CardConfetti(modifier = Modifier.matchParentSize().clip(MaterialTheme.shapes.large))
    Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.BottomCenter) {
        val overflow = Modifier.wrapContentHeight(Alignment.Bottom, unbounded = true)
        if (fx.goalGif) {
            KitGif(res = R.raw.wrexham_anim_goal, modifier = overflow.fillMaxWidth(0.88f).aspectRatio(600f / 205f))
        }
        if (fx.kitWin) {
            KitGif(res = R.raw.wrexham_anim_win, modifier = overflow.fillMaxWidth().aspectRatio(1024f / 576f))
        }
    }
}

/**
 * Top row of the card: Wrexham's crest and name, and the overflow (three-dot) menu button.
 *
 * @param actions Callbacks for the menu items.
 * @param currentMatch The match on screen, so "Share" in the menu shares the one being looked at.
 */
@Composable
private fun Header(
    actions: WrexhamWidgetActions,
    currentMatch: WrexhamMatch?,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Crest(team = Wrexham, size = 20.dp)
        Text(
            text = Wrexham.name,
            style = FirefoxTheme.typography.subtitle2,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    painter = painterResource(iconsR.drawable.mozac_ic_ellipsis_vertical_24),
                    contentDescription = "Wrexham AFC widget menu",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            WrexhamMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                actions = actions,
                currentMatch = currentMatch,
            )
        }
    }
}

/**
 * The overflow menu: Matches, Results, Standings, Wallpapers, Share and Remove Widget, in the design's order. Uses the
 * app's standard dropdown menu. Picking an item closes the menu first. Matches uses the design's soccer-ball icon; the
 * others use the closest Firefox icons.
 */
@Composable
private fun WrexhamMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    actions: WrexhamWidgetActions,
    currentMatch: WrexhamMatch?,
) {
    fun item(text: String, icon: Int, critical: Boolean = false, onClick: () -> Unit) =
        MenuItem.IconItem(
            text = MenuText.String(text),
            drawableRes = icon,
            level = if (critical) MenuItem.FixedItem.Level.Critical else MenuItem.FixedItem.Level.Default,
            onClick = {
                onDismissRequest()
                onClick()
            },
        )

    DropdownMenu(
        menuItems =
            listOf(
                item("Matches", R.drawable.wrexham_ic_matches) { actions.onOpenUrl(WrexhamLinks.MATCHES) },
                item("Results", iconsR.drawable.mozac_ic_clipboard_24) { actions.onOpenUrl(WrexhamLinks.RESULTS) },
                item("Standings", iconsR.drawable.mozac_ic_reading_list_24) {
                    actions.onOpenUrl(WrexhamLinks.STANDINGS)
                },
                item("Wallpapers", iconsR.drawable.mozac_ic_image_24) { actions.onWallpapers() },
                item("Share", iconsR.drawable.mozac_ic_share_android_24) { actions.onShare(currentMatch) },
                item("Remove Widget", iconsR.drawable.mozac_ic_delete_24, critical = true) { actions.onRemove() },
            ),
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    )
}

/**
 * One match: a line with an optional status tag and the competition, then home team, the centre (score or kickoff time)
 * and away team in three equal columns. Tapping it opens the match page.
 */
@Composable
private fun MatchBody(match: WrexhamMatch, onOpenUrl: (String) -> Unit) {
    Column(
        modifier =
            Modifier.fillMaxWidth().clickable { onOpenUrl(match.url) }.padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            match.status.tagLabel?.let { Tag(label = it, live = match.status.isLive) }
            Text(
                text = match.competition,
                style = FirefoxTheme.typography.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamSlot(team = match.home, modifier = Modifier.weight(1f))
            CenterSlot(match = match, modifier = Modifier.weight(1f))
            TeamSlot(team = match.away, modifier = Modifier.weight(1f))
        }
    }
}

/** Label for the tag before the competition name, or null when the match doesn't need one. */
private val WrexhamMatchStatus.tagLabel: String?
    get() =
        when {
            isLive -> "LIVE"
            this is WrexhamMatchStatus.Delayed -> "Delayed"
            this is WrexhamMatchStatus.Suspended -> "Suspended"
            this is WrexhamMatchStatus.Postponed -> "Postponed"
            else -> null
        }

/**
 * Status tag before the competition name. LIVE is red text on a pale red background with a pulsing dot; Delayed,
 * Suspended and Postponed are inverted (dark chip, light text).
 */
@Composable
private fun Tag(label: String, live: Boolean) {
    if (live) {
        Row(
            modifier =
                Modifier.background(LiveRed.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(modifier = Modifier.size(4.dp).alpha(livePulseAlpha()).background(LiveRed, CircleShape))
            Text(text = label, style = FirefoxTheme.typography.caption, color = LiveRed, fontWeight = FontWeight.Bold)
        }
    } else {
        Text(
            text = label,
            style = FirefoxTheme.typography.caption,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            modifier =
                Modifier.background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * Middle column of a match. Before kickoff: the day ("Today", "Tomorrow", a date) above the time. Once there's a score:
 * the score pill, a red card marker beside the side that got one, and the [statusLine] under it. The match minute
 * pulses while the clock is running in normal time.
 */
@Composable
private fun CenterSlot(match: WrexhamMatch, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (match.homeScore == null || match.awayScore == null) {
            Text(
                text = dayLabel(match.kickoff.toLocalDate()),
                style = FirefoxTheme.typography.caption,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (match.redCard == WrexhamSide.HOME) RedCard()
                ScorePill(home = match.homeScore, away = match.awayScore, live = match.status.isLive)
                if (match.redCard == WrexhamSide.AWAY) RedCard()
            }
        }
        val line = match.statusLine()
        if (line.isNotEmpty()) {
            Text(
                text = line,
                style = FirefoxTheme.typography.caption,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = if (match.status is WrexhamMatchStatus.Live) Modifier.alpha(livePulseAlpha()) else Modifier,
            )
        }
    }
}

private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val shortDateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
private val mediumDateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

/** "Today", "Tomorrow", or the date in the user's short format. */
private fun dayLabel(day: LocalDate): String =
    when (day) {
        LocalDate.now() -> "Today"
        LocalDate.now().plusDays(1) -> "Tomorrow"
        else -> day.format(shortDateFormat)
    }

/**
 * The line under the score or kickoff day, in the design's wording: the kickoff time, "29'", "Halftime", "ET 112'",
 * "FT", penalty scores, or the new time for a delayed or postponed match. Empty for a suspended match.
 */
private fun WrexhamMatch.statusLine(): String =
    when (val s = status) {
        WrexhamMatchStatus.Scheduled -> kickoff.format(timeFormat)
        is WrexhamMatchStatus.Live -> "${s.clock}'"
        WrexhamMatchStatus.Halftime -> "Halftime"
        is WrexhamMatchStatus.ExtraTime -> "ET ${s.clock}'"
        is WrexhamMatchStatus.Penalties -> "${s.home} - ${s.away}\n(Penalties)"
        WrexhamMatchStatus.Final -> "FT"
        is WrexhamMatchStatus.FinalAfterPenalties -> "${s.home} - ${s.away}\n(FT - Penalties)"
        is WrexhamMatchStatus.Delayed -> "Starting\n${s.newKickoff.format(timeFormat)}"
        WrexhamMatchStatus.Suspended -> ""
        is WrexhamMatchStatus.Postponed ->
            "${s.newKickoff.format(mediumDateFormat)}\n${s.newKickoff.format(timeFormat)}"
    }

/**
 * The score, e.g. "1 - 2", in a pill: red while the match is live, neutral grey otherwise.
 *
 * Whenever the score changes it "pops", jumping to 80% size and springing back with a slight overshoot over 0.5s (the
 * design's `@keyframes pop`). It also pops once when it first appears, as in the design.
 */
@Composable
private fun ScorePill(home: Int, away: Int, live: Boolean) {
    val container = if (live) LiveRed else MaterialTheme.colorScheme.surfaceContainerHighest
    val content = if (live) Color.White else MaterialTheme.colorScheme.onSurface
    val pop = remember { Animatable(1f) }
    LaunchedEffect(home, away) {
        pop.snapTo(0.8f)
        pop.animateTo(1f, tween(500, easing = CubicBezierEasing(0.3f, 1.6f, 0.5f, 1f)))
    }
    Text(
        text = "$home - $away",
        style = FirefoxTheme.typography.headline7,
        fontWeight = FontWeight.Bold,
        color = content,
        modifier =
            Modifier.scale(pop.value)
                .background(container, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 4.dp),
    )
}

/** Small red rectangle shown beside the score on the side that has had a player sent off. */
@Composable
private fun RedCard() {
    Box(
        modifier =
            Modifier.padding(horizontal = 6.dp)
                .size(width = 6.dp, height = 9.dp)
                .background(Color(0xFFE22850), RoundedCornerShape(1.dp))
    )
}

/** A club's crest, or an empty space of the same size when we don't ship one. */
@Composable
private fun Crest(team: WrexhamTeam, size: Dp) {
    val crest = team.crestRes
    if (crest == null) {
        Spacer(modifier = Modifier.size(size))
    } else {
        Image(painter = painterResource(crest), contentDescription = null, modifier = Modifier.size(size))
    }
}

/** A team column: crest above the club name (one line, cut off with an ellipsis if too long). */
@Composable
private fun TeamSlot(team: WrexhamTeam, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Crest(team = team, size = 32.dp)
        Text(
            text = team.name,
            style = FirefoxTheme.typography.caption,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Body for "nothing to show" states (no upcoming games, error): message and button on the left, a Kit illustration in
 * the bottom-right corner.
 */
@Composable
private fun MessageBody(
    title: String,
    subtitle: String,
    buttonText: String,
    imageRes: Int,
    onClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = title, style = FirefoxTheme.typography.body2, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = subtitle,
                style = FirefoxTheme.typography.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            PillButton(text = buttonText, onClick = onClick)
        }
        Image(
            painter = painterResource(imageRes),
            contentDescription = null,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp).width(96.dp),
        )
    }
}

/** Body shown after the season ends: Kit illustration, a sign-off message and "See Results". */
@Composable
private fun SeasonOverBody(onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.wrexham_kit_ball),
            contentDescription = null,
            modifier = Modifier.width(140.dp),
        )
        Text(
            text = "That's a wrap on the EFL Championship.",
            style = FirefoxTheme.typography.body2,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Stay tuned for what's next.",
            style = FirefoxTheme.typography.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        PillButton(text = "See Results", onClick = onClick)
    }
}

/** The app's standard filled button, used for every call to action on the card. */
@Composable
private fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier) {
        Text(text = text, style = FirefoxTheme.typography.button)
    }
}

/** Shown in place of the card right after it's removed, with an Undo button. */
@Composable
private fun RemovedRow(onUndo: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Wrexham AFC widget removed",
            style = FirefoxTheme.typography.body2,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onUndo) { Text("Undo") }
    }
}

/** Carousel position dots under the upcoming matches; the current page is the dark one. */
@Composable
private fun PageDots(count: Int, selected: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        repeat(count) { index ->
            val color =
                if (index == selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                }
            Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
        }
    }
}

@PreviewLightDark
@Composable
private fun WrexhamWidgetPreview() {
    FirefoxTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            WrexhamWidget(
                state = WrexhamFakeData.forScenario(WrexhamScenario.UPCOMING),
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
