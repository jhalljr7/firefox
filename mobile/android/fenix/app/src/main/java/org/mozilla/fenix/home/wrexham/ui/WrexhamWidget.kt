/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.home.wrexham.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import org.mozilla.fenix.home.wrexham.WrexhamFakeData
import org.mozilla.fenix.home.wrexham.WrexhamMatch
import org.mozilla.fenix.home.wrexham.WrexhamTeam
import org.mozilla.fenix.home.wrexham.WrexhamWidgetState
import org.mozilla.fenix.theme.FirefoxTheme
import mozilla.components.ui.icons.R as iconsR

/**
 * Homepage card showing Wrexham AFC's next match.
 *
 * @param state The [WrexhamWidgetState] to display.
 * @param modifier [Modifier] to be applied to the card.
 */
@Composable
fun WrexhamWidget(
    state: WrexhamWidgetState,
    modifier: Modifier = Modifier,
) {
    val match = state.matches.firstOrNull() ?: return

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(modifier = Modifier.padding(bottom = 12.dp)) {
            Header(title = state.title, crestRes = state.crestRes)

            HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))

            MatchRow(match = match, modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp))

            PageDots(count = state.matches.size, selected = 0)
        }
    }
}

@Composable
private fun Header(title: String, crestRes: Int) {
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            painter = painterResource(crestRes),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = title,
            style = FirefoxTheme.typography.subtitle2,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = {}) {
            Icon(
                painter = painterResource(iconsR.drawable.mozac_ic_ellipsis_vertical_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MatchRow(match: WrexhamMatch, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = match.competition,
            style = FirefoxTheme.typography.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamSlot(team = match.home, modifier = Modifier.weight(1f))
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = match.dayLabel,
                    style = FirefoxTheme.typography.caption,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = match.timeLabel,
                    style = FirefoxTheme.typography.caption,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            TeamSlot(team = match.away, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun TeamSlot(team: WrexhamTeam, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Image(
            painter = painterResource(team.crestRes),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
        )
        Text(
            text = team.name,
            style = FirefoxTheme.typography.body2,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PageDots(count: Int, selected: Int) {
    if (count < 2) return
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
            WrexhamWidget(state = WrexhamFakeData.upcoming, modifier = Modifier.padding(16.dp))
        }
    }
}
