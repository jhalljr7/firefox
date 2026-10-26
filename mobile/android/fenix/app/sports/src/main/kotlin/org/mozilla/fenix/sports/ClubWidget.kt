/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * CLUB WIDGET / THE HOMEPAGE CARD: fixtures, live scores and celebrations for one [Club].
 *
 * What: the card itself. Generic over the club; the Wrexham widget is `ClubWidget(club = Wrexham, ...)`.
 * Layout, top to bottom: header (crest, name, overflow menu), divider, then a body of one match, a
 * swipeable carousel of upcoming matches, or a message (no games, error, season over).
 * Called by: the app's WrexhamWidgetSection, which passes the AppStore state and [ClubWidgetActions]
 * wired to real app behaviour.
 * Calls: MatchUi.kt (match rows), Celebrations.kt (when to celebrate), KitEffects.kt (the effects).
 */

package org.mozilla.fenix.sports

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import mozilla.components.compose.base.button.FilledButton
import mozilla.components.compose.base.button.TextButton
import mozilla.components.compose.base.menu.DropdownMenu
import mozilla.components.compose.base.menu.MenuItem
import mozilla.components.compose.base.text.Text as MenuText
import mozilla.components.ui.icons.R as iconsR

private const val RESIZE_MS = 350
private const val VIEW_FADE_MS = 340
private const val VIEW_SLIDE_FRACTION = 50
private const val GOAL_GIF_WIDTH = 0.88f
private const val GOAL_GIF_ASPECT = 600f / 205f
private const val WIN_GIF_ASPECT = 1024f / 576f
private val ResizeEasing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1f)
private val HeaderCrestSize = 20.dp
private val MessageArtWidth = 96.dp
private val SeasonOverArtWidth = 140.dp
private val PageDotSize = 6.dp

/**
 * A homepage card for [club]'s fixtures and live scores.
 *
 * The card (header, divider, [CardBody]) has [CardEffects] layered over it; effects that cover the whole screen are
 * drawn by [SportsScreenEffects]. Which effects show, and the live score during a demo, come from [rememberSportsFx].
 *
 * @param club The club the card follows.
 * @param state What to show.
 * @param modifier [Modifier] for the card.
 * @param actions Callbacks for taps on the card.
 */
@Composable
fun ClubWidget(
    club: Club,
    state: ClubWidgetState,
    modifier: Modifier = Modifier,
    actions: ClubWidgetActions = ClubWidgetActions(),
) {
    // fx.match is the match as a demo script has updated it; null means use the state's own.
    val fx = rememberSportsFx(state, club.team)
    val content = fx.match?.let { SportsContent.Matches(listOf(it)) } ?: state.content
    val matches = (content as? SportsContent.Matches)?.matches.orEmpty()
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
                // The first real draw tells demo scripts the card is on screen (see SportsFx.drawn).
                .drawBehind { if (!fx.drawn) fx.drawn = true }
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            // When the body changes height (the carousel's last page, a different state) the card
            // grows or shrinks smoothly instead of jumping.
            Column(
                modifier =
                    Modifier.animateContentSize(tween(RESIZE_MS, easing = ResizeEasing))
                        .padding(bottom = SportsTheme.layout.space.static150)
            ) {
                Header(club = club, actions = actions, currentMatch = matches.getOrNull(pagerState.currentPage))
                HorizontalDivider(modifier = Modifier.padding(horizontal = SportsTheme.layout.space.static150))
                // A switch to a different kind of view fades and slides in (the design's `wfade`). The animation
                // is keyed on the kind of view only and the body always reads the latest `content`, so a score or
                // clock update redraws in place instead of re-fading or showing AnimatedContent's older snapshot.
                AnimatedContent(
                    targetState = content.viewKind(),
                    transitionSpec = {
                        val spec = tween<Float>(VIEW_FADE_MS)
                        (fadeIn(spec) +
                            slideInVertically(tween(VIEW_FADE_MS)) { it / VIEW_SLIDE_FRACTION }) togetherWith
                            fadeOut(tween(0))
                    },
                    label = "ClubWidgetView",
                ) {
                    CardBody(club = club, content = content, pagerState = pagerState, actions = actions)
                }
            }
        }
        CardEffects(fx, club)
    }
    SportsScreenEffects(fx = fx, club = club, cardTop = cardTop, cardHeight = cardHeight)
}

/** What kind of view [this] needs: a single match, a carousel of a given length, or one of the messages. */
private fun SportsContent.viewKind(): Any = (this as? SportsContent.Matches)?.matches?.size ?: this

/**
 * The card's body for [content].
 *
 * @param club The club the card follows.
 * @param content What to show.
 * @param pagerState Carousel position, shared with the header so Share knows which match is showing.
 * @param actions Callbacks for taps.
 */
@Composable
private fun CardBody(
    club: Club,
    content: SportsContent,
    pagerState: PagerState,
    actions: ClubWidgetActions,
) {
    Column {
        when (content) {
            is SportsContent.Matches ->
                if (content.matches.size == 1) {
                    MatchRow(match = content.matches.first(), onOpenUrl = actions.onOpenUrl)
                } else {
                    UpcomingCarousel(club, content.matches, pagerState, actions)
                }
            SportsContent.NoUpcoming ->
                MessageBody(
                    title = stringResource(club.noUpcomingTitle),
                    subtitle = stringResource(R.string.sports_no_upcoming_subtitle),
                    buttonText = stringResource(R.string.sports_full_calendar),
                    imageRes = R.drawable.sports_kit_ball,
                    onClick = { actions.onOpenUrl(club.matchesUrl) },
                )
            SportsContent.Error ->
                MessageBody(
                    title = stringResource(R.string.sports_error_title),
                    subtitle = stringResource(R.string.sports_error_subtitle),
                    buttonText = stringResource(R.string.sports_refresh),
                    imageRes = R.drawable.sports_kit_alert,
                    onClick = actions.onRefresh,
                )
            SportsContent.SeasonOver -> SeasonOverBody(club = club, onClick = { actions.onOpenUrl(club.resultsUrl) })
        }
    }
}

/** Upcoming matches: swipe sideways or follow the dots; the last page adds "Full Calendar". */
@Composable
private fun UpcomingCarousel(
    club: Club,
    matches: List<Match>,
    pagerState: PagerState,
    actions: ClubWidgetActions,
) {
    HorizontalPager(state = pagerState) { page ->
        Column {
            MatchRow(match = matches[page], onOpenUrl = actions.onOpenUrl)
            if (page == matches.lastIndex) {
                FilledButton(
                    text = stringResource(R.string.sports_full_calendar),
                    modifier =
                        Modifier.padding(
                            start = SportsTheme.layout.space.static150,
                            bottom = SportsTheme.layout.space.static150,
                        ),
                    onClick = { actions.onOpenUrl(club.matchesUrl) },
                )
            }
        }
    }
    PageDots(count = matches.size, selected = pagerState.currentPage)
}

/**
 * Card-sized effects drawn over the card: confetti clipped to its rounded shape, and the Kit goal and win GIFs anchored
 * to its bottom edge. The GIFs may stick out above the card (the win GIF is taller than it) without changing the card's
 * size. Each effect is keyed on its play number, so a second goal mid-celebration restarts it from the beginning.
 */
@Composable
private fun BoxScope.CardEffects(fx: SportsFx, club: Club) {
    if (fx.confetti != 0) {
        key(fx.confetti) {
            CardConfetti(
                colors = club.celebrationColors,
                modifier = Modifier.matchParentSize().clip(MaterialTheme.shapes.large),
            )
        }
    }
    Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.BottomCenter) {
        val overflow = Modifier.wrapContentHeight(Alignment.Bottom, unbounded = true)
        if (fx.goalGif != 0) {
            key(fx.goalGif) {
                KitGif(
                    res = R.raw.sports_kit_goal,
                    modifier = overflow.fillMaxWidth(GOAL_GIF_WIDTH).aspectRatio(GOAL_GIF_ASPECT),
                )
            }
        }
        if (fx.kitWin != 0) {
            key(fx.kitWin) {
                KitGif(res = R.raw.sports_kit_win, modifier = overflow.fillMaxWidth().aspectRatio(WIN_GIF_ASPECT))
            }
        }
    }
}

/**
 * Top row: the club's crest and name, and the overflow menu button.
 *
 * @param club The club the card follows.
 * @param actions Callbacks for the menu items.
 * @param currentMatch The match on screen, so Share in the menu shares the one being looked at.
 */
@Composable
private fun Header(
    club: Club,
    actions: ClubWidgetActions,
    currentMatch: Match?,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier =
            Modifier.padding(start = SportsTheme.layout.space.static200, end = SportsTheme.layout.space.static50),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SportsTheme.layout.space.static100),
    ) {
        Crest(team = club.team, size = HeaderCrestSize)
        Text(
            text = club.team.name,
            style = SportsTheme.typography.subtitle2,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    painter = painterResource(iconsR.drawable.mozac_ic_ellipsis_vertical_24),
                    contentDescription = stringResource(R.string.sports_menu_content_description, club.team.name),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ClubMenu(
                club = club,
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                actions = actions,
                currentMatch = currentMatch,
            )
        }
    }
}

/**
 * The overflow menu in the design's order, using the app's standard dropdown. Picking an item closes the menu first.
 * Matches uses the design's soccer-ball icon; the rest use Firefox icons.
 */
@Composable
private fun ClubMenu(
    club: Club,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    actions: ClubWidgetActions,
    currentMatch: Match?,
) {
    @Composable
    fun item(text: Int, icon: Int, critical: Boolean = false, onClick: () -> Unit) =
        MenuItem.IconItem(
            text = MenuText.String(stringResource(text)),
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
                item(R.string.sports_menu_matches, R.drawable.sports_ic_soccer_ball) {
                    actions.onOpenUrl(club.matchesUrl)
                },
                item(R.string.sports_menu_results, iconsR.drawable.mozac_ic_clipboard_24) {
                    actions.onOpenUrl(club.resultsUrl)
                },
                item(R.string.sports_menu_standings, iconsR.drawable.mozac_ic_reading_list_24) {
                    actions.onOpenUrl(club.standingsUrl)
                },
                item(R.string.sports_menu_wallpapers, iconsR.drawable.mozac_ic_image_24) { actions.onWallpapers() },
                item(R.string.sports_menu_share, iconsR.drawable.mozac_ic_share_android_24) {
                    actions.onShare(currentMatch)
                },
                item(R.string.sports_menu_remove, iconsR.drawable.mozac_ic_delete_24, critical = true) {
                    actions.onRemove()
                },
            ),
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    )
}

/** Body for "nothing to show" states: message and button on the left, a Kit illustration bottom right. */
@Composable
private fun MessageBody(
    title: String,
    subtitle: String,
    buttonText: String,
    imageRes: Int,
    onClick: () -> Unit,
) {
    val space = SportsTheme.layout.space
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = space.static200, end = space.static200, top = space.static150),
            verticalArrangement = Arrangement.spacedBy(space.static50),
        ) {
            Text(text = title, style = SportsTheme.typography.body2, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = subtitle,
                style = SportsTheme.typography.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledButton(text = buttonText, modifier = Modifier.padding(top = space.static100), onClick = onClick)
        }
        Image(
            painter = painterResource(imageRes),
            contentDescription = null,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = space.static100).width(MessageArtWidth),
        )
    }
}

/** Body after the season ends: Kit illustration, a sign-off message and "See Results". */
@Composable
private fun SeasonOverBody(
    club: Club,
    onClick: () -> Unit,
) {
    val space = SportsTheme.layout.space
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = space.static200, vertical = space.static150),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(space.static50),
    ) {
        Image(
            painter = painterResource(R.drawable.sports_kit_ball),
            contentDescription = null,
            modifier = Modifier.width(SeasonOverArtWidth),
        )
        Text(
            text = stringResource(club.seasonOverTitle),
            style = SportsTheme.typography.body2,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.sports_season_over_subtitle),
            style = SportsTheme.typography.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledButton(
            text = stringResource(R.string.sports_see_results),
            modifier = Modifier.padding(top = space.static100),
            onClick = onClick,
        )
    }
}

/** Carousel position dots under the upcoming matches; the current page is the dark one. */
@Composable
private fun PageDots(
    count: Int,
    selected: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SportsTheme.layout.space.static100, Alignment.CenterHorizontally),
    ) {
        repeat(count) { index ->
            val color =
                if (index == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
            Box(modifier = Modifier.size(PageDotSize).background(color, CircleShape))
        }
    }
}

/**
 * Shown in place of a club widget right after it's removed, with an Undo button.
 *
 * @param club The removed widget's club.
 * @param onUndo Puts the widget back.
 * @param modifier [Modifier] for the row.
 */
@Composable
fun RemovedClubWidgetRow(
    club: Club,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = SportsTheme.layout.space.static200),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.sports_widget_removed, club.team.name),
            style = SportsTheme.typography.body2,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(text = stringResource(R.string.sports_undo), onClick = onUndo)
    }
}

@PreviewLightDark
@Composable
private fun ClubWidgetPreview() {
    SportsPreviewTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            ClubWidget(
                club = Wrexham,
                state = WrexhamScenario.UPCOMING.state(),
                modifier = Modifier.padding(SportsTheme.layout.space.static200),
            )
        }
    }
}
