/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.home.wrexham.ui

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.navigation.Navigation
import mozilla.components.compose.base.menu.DropdownMenu
import mozilla.components.compose.base.menu.MenuItem
import mozilla.components.compose.base.text.Text as MenuText
import mozilla.components.support.ktx.android.content.share
import org.mozilla.fenix.NavGraphDirections
import org.mozilla.fenix.R
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.openToBrowser
import org.mozilla.fenix.home.wrexham.WrexhamContent
import org.mozilla.fenix.home.wrexham.WrexhamFakeData
import org.mozilla.fenix.home.wrexham.WrexhamLinks
import org.mozilla.fenix.home.wrexham.WrexhamMatch
import org.mozilla.fenix.home.wrexham.WrexhamScenario
import org.mozilla.fenix.home.wrexham.WrexhamSide
import org.mozilla.fenix.home.wrexham.WrexhamTag
import org.mozilla.fenix.home.wrexham.WrexhamTeam
import org.mozilla.fenix.home.wrexham.WrexhamWidgetState
import org.mozilla.fenix.theme.FirefoxTheme
import mozilla.components.ui.icons.R as iconsR

private val LiveRed = Color(0xFFB3242F)

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
 * Homepage section that hosts the Wrexham widget, wires its actions and handles remove with undo.
 *
 * @param state The [WrexhamWidgetState] to display.
 * @param modifier [Modifier] to be applied to the section.
 */
@Composable
fun WrexhamWidgetSection(
    state: WrexhamWidgetState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settings = context.components.settings
    var removed by remember { mutableStateOf(false) }

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

    WrexhamWidget(
        state = state,
        modifier = modifier,
        actions = WrexhamWidgetActions(
            onOpenUrl = { url ->
                context.components.useCases.fenixBrowserUseCases.loadUrlOrSearch(searchTermOrURL = url, newTab = true)
                (context as? Activity)?.let { Navigation.findNavController(it, R.id.container).openToBrowser() }
            },
            onWallpapers = {
                (context as? Activity)?.let {
                    Navigation.findNavController(it, R.id.container)
                        .navigate(NavGraphDirections.actionGlobalWallpaperSettingsFragment())
                }
            },
            onShare = { match ->
                val text = match?.let { "${it.home.name} vs ${it.away.name}, ${it.competition}: ${it.url}" }
                    ?: "Follow Wrexham AFC: ${WrexhamLinks.MATCHES}"
                context.share(text, subject = "Firefox - Wrexham AFC")
            },
            onRemove = {
                settings.showHomepageWrexhamWidget = false
                removed = true
            },
        ),
    )
}

/**
 * Homepage card for Wrexham AFC fixtures and live scores.
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
    val matches = (state.content as? WrexhamContent.Matches)?.matches.orEmpty()
    val pagerState = rememberPagerState { matches.size }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(modifier = Modifier.padding(bottom = 12.dp)) {
            Header(
                title = state.title,
                crestRes = state.crestRes,
                actions = actions,
                currentMatch = matches.getOrNull(pagerState.currentPage),
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))

            when (val content = state.content) {
                is WrexhamContent.Matches -> if (content.matches.size == 1) {
                    MatchBody(match = content.matches.first(), onOpenUrl = actions.onOpenUrl)
                } else {
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
                WrexhamContent.NoUpcoming -> MessageBody(
                    title = "The men's team has no upcoming games.",
                    subtitle = "Check the full calendar for what's next.",
                    buttonText = "Full Calendar",
                    imageRes = R.drawable.wrexham_kit_ball,
                    onClick = { actions.onOpenUrl(WrexhamLinks.MATCHES) },
                )
                WrexhamContent.Error -> MessageBody(
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
}

@Composable
private fun Header(
    title: String,
    crestRes: Int,
    actions: WrexhamWidgetActions,
    currentMatch: WrexhamMatch?,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(painter = painterResource(crestRes), contentDescription = null, modifier = Modifier.size(20.dp))
        Text(
            text = title,
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

@Composable
private fun WrexhamMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    actions: WrexhamWidgetActions,
    currentMatch: WrexhamMatch?,
) {
    fun item(text: String, icon: Int, critical: Boolean = false, onClick: () -> Unit) = MenuItem.IconItem(
        text = MenuText.String(text),
        drawableRes = icon,
        level = if (critical) MenuItem.FixedItem.Level.Critical else MenuItem.FixedItem.Level.Default,
        onClick = {
            onDismissRequest()
            onClick()
        },
    )

    DropdownMenu(
        menuItems = listOf(
            item("Matches", iconsR.drawable.mozac_ic_football_24) { actions.onOpenUrl(WrexhamLinks.MATCHES) },
            item("Results", iconsR.drawable.mozac_ic_clipboard_24) { actions.onOpenUrl(WrexhamLinks.RESULTS) },
            item("Standings", iconsR.drawable.mozac_ic_reading_list_24) { actions.onOpenUrl(WrexhamLinks.STANDINGS) },
            item("Wallpapers", iconsR.drawable.mozac_ic_image_24) { actions.onWallpapers() },
            item("Share", iconsR.drawable.mozac_ic_share_android_24) { actions.onShare(currentMatch) },
            item("Remove Widget", iconsR.drawable.mozac_ic_delete_24, critical = true) { actions.onRemove() },
        ),
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    )
}

@Composable
private fun MatchBody(match: WrexhamMatch, onOpenUrl: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenUrl(match.url) }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            match.tag?.let { Tag(it) }
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

@Composable
private fun Tag(tag: WrexhamTag) {
    if (tag == WrexhamTag.LIVE) {
        Row(
            modifier = Modifier
                .background(LiveRed.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(modifier = Modifier.size(4.dp).background(LiveRed, CircleShape))
            Text(text = tag.label, style = FirefoxTheme.typography.caption, color = LiveRed, fontWeight = FontWeight.Bold)
        }
    } else {
        Text(
            text = tag.label,
            style = FirefoxTheme.typography.caption,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun CenterSlot(match: WrexhamMatch, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (match.homeScore == null || match.awayScore == null) {
            match.dayLabel?.let {
                Text(
                    text = it,
                    style = FirefoxTheme.typography.caption,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (match.redCard == WrexhamSide.HOME) RedCard()
                ScorePill(home = match.homeScore, away = match.awayScore, live = match.isLive)
                if (match.redCard == WrexhamSide.AWAY) RedCard()
            }
        }
        if (match.detail.isNotEmpty()) {
            Text(
                text = match.detail,
                style = FirefoxTheme.typography.caption,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ScorePill(home: Int, away: Int, live: Boolean) {
    val container = if (live) LiveRed else MaterialTheme.colorScheme.surfaceContainerHighest
    val content = if (live) Color.White else MaterialTheme.colorScheme.onSurface
    Text(
        text = "$home - $away",
        style = FirefoxTheme.typography.headline7,
        fontWeight = FontWeight.Bold,
        color = content,
        modifier = Modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 4.dp),
    )
}

@Composable
private fun RedCard() {
    Box(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .size(width = 6.dp, height = 9.dp)
            .background(Color(0xFFE22850), RoundedCornerShape(1.dp)),
    )
}

@Composable
private fun TeamSlot(team: WrexhamTeam, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Image(painter = painterResource(team.crestRes), contentDescription = null, modifier = Modifier.size(32.dp))
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

@Composable
private fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier) {
        Text(text = text, style = FirefoxTheme.typography.button)
    }
}

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

@Composable
private fun PageDots(count: Int, selected: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        repeat(count) { index ->
            val color = if (index == selected) {
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
