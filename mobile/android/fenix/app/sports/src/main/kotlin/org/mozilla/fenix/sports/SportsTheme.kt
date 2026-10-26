/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * SPORTS CORE / THEME: design tokens (typography, spacing) for every composable in the module.
 */

package org.mozilla.fenix.sports

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import mozilla.components.compose.base.theme.AcornTheme
import mozilla.components.compose.base.theme.AcornTypography
import mozilla.components.compose.base.theme.layout.AcornLayout

/**
 * The module's window onto the Acorn design system, mirroring the app's `FirefoxTheme`, which this module can't see.
 * Exempt from the `AcornThemeUsage` detekt rule for the same reason `FirefoxTheme.kt` is.
 */
internal object SportsTheme {
    val typography: AcornTypography
        get() = AcornTheme.typography

    val layout: AcornLayout
        @Composable @ReadOnlyComposable get() = AcornTheme.layout
}

/** Wraps previews in the Acorn theme, as the app's `FirefoxTheme {}` does for app previews. */
@Composable internal fun SportsPreviewTheme(content: @Composable () -> Unit) = AcornTheme(content = content)
