/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.home.wrexham.ui

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.widget.ImageView
import androidx.annotation.RawRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.abs
import kotlin.math.cos
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mozilla.fenix.R
import org.mozilla.fenix.home.wrexham.WrexhamContent
import org.mozilla.fenix.home.wrexham.WrexhamFakeData
import org.mozilla.fenix.home.wrexham.WrexhamGoalStyle
import org.mozilla.fenix.home.wrexham.WrexhamMatch
import org.mozilla.fenix.home.wrexham.WrexhamWidgetState
import org.mozilla.fenix.home.wrexham.isFinished

/*
 * WREXHAM WIDGET ANIMATIONS (prototype only, never meant to land)
 *
 * This file holds every animation the Wrexham widget plays. The motion, timings and colours are
 * copied from the designer's HTML prototype (`wrexham-mobile-android-widget.html` in the guacal
 * design drop; local copy in ~/work/wrexham-design/). Where a number below looks arbitrary, it is
 * the value from that prototype's CSS or JavaScript.
 *
 * How it fits together:
 *
 * 1. The card shows a match. New versions of it arrive either from the data source (each reload)
 *    or, in demo scenarios, from a script that fakes a live feed.
 * 2. [rememberWrexhamFx] compares each new version with the last one ([celebrationFor]): Wrexham
 *    scored, or just won. If so it switches on the matching effect flags on a [WrexhamFx].
 * 3. `WrexhamWidget` (WrexhamWidget.kt) reads those flags and shows the matching effect:
 *    - card-sized effects ([KitGif] for the goal and win GIFs, [CardConfetti]) sit on top of the card;
 *    - screen-sized effects ([WinnerConfetti], [FoxRunner]) go through [WrexhamScreenEffects], a
 *      full-window Popup, because a homepage list item can't draw outside its own bounds.
 *
 * The three Kit animations are GIFs in res/raw (wrexham_anim_goal, _win, _winner), exported by
 * the designer. Everything else (confetti, the running fox, the GOAL banner) is drawn in Compose.
 */

/**
 * Observable state for one Wrexham card's animations. Every property except [lastSeen] is Compose state, so changing it
 * recomposes the card. Each `Boolean` flag means "this effect is on screen"; the composable that draws an effect starts
 * its animation when it enters composition, so turning a flag on is the same as pressing play.
 */
internal class WrexhamFx {
    /**
     * The match as a demo script has updated it (new score, ticking clock), or null to show the widget state's own
     * match. Real data never sets this; it arrives as a new widget state instead.
     */
    var match by mutableStateOf<WrexhamMatch?>(null)

    /**
     * True once the card has been drawn on screen. Demo scripts wait for this, because on a cold start the homepage
     * composes behind the splash screen and a script started at composition would be over before anyone could see it.
     */
    var drawn by mutableStateOf(false)

    /** The Kit goal GIF (fox dribbling a ball) is playing over the card. */
    var goalGif by mutableStateOf(false)

    /** Confetti is falling inside the card. */
    var confetti by mutableStateOf(false)

    /** The Kit "win" GIF (fox popping up) is playing on the card's bottom edge. */
    var kitWin by mutableStateOf(false)

    /** The Kit full-screen confetti GIF is playing over the whole homepage. */
    var winnerScreen by mutableStateOf(false)

    /** The Compose-drawn fox, ball and "GOAL!" banner are running across the screen. */
    var fox by mutableStateOf(false)

    /** The last match the card showed, to compare the next update against. Not drawn, so plain. */
    var lastSeen: WrexhamMatch? = null

    /** Turns an effect on for [durationMs], then off, without blocking the caller. */
    private fun CoroutineScope.flash(durationMs: Long, set: (Boolean) -> Unit) = launch {
        set(true)
        delay(durationMs)
        set(false)
    }

    /**
     * Plays [celebration]. The timings are the design prototype's:
     * - Goal, Kit style: the goal GIF and card confetti start together (`playGoal()`).
     * - Goal, fox style: the fox, ball and banner run across the screen (`runFox()`).
     * - Win: full-screen confetti for 4s; 3s in, while it's still falling, the Kit fox pops up on the card's bottom
     *   edge and plays once (`playWinner()`).
     */
    fun CoroutineScope.play(celebration: WrexhamCelebration, goalStyle: WrexhamGoalStyle) {
        when (celebration) {
            WrexhamCelebration.GOAL ->
                when (goalStyle) {
                    WrexhamGoalStyle.KIT -> {
                        flash(GOAL_GIF_MS) { goalGif = it }
                        flash(CONFETTI_MS) { confetti = it }
                    }
                    WrexhamGoalStyle.FOX -> flash(FOX_MS) { fox = it }
                }
            WrexhamCelebration.WIN -> {
                flash(WINNER_SCREEN_MS) { winnerScreen = it }
                launch {
                    delay(3000)
                    flash(KIT_WIN_MS) { kitWin = it }
                }
            }
        }
    }
}

/** The moments worth celebrating. */
internal enum class WrexhamCelebration {
    GOAL,
    WIN,
}

/**
 * Decides whether going from [before] to [after] deserves a celebration. Works the same for real polled data and for
 * demo scripts, since both just produce a newer version of the match.
 *
 * Nothing plays for the first version the card sees ([before] is null) or across different matches, so opening the
 * homepage after a goal shows the new score without replaying the goal.
 *
 * @return [WrexhamCelebration.WIN] when the match has just finished with Wrexham winning, [WrexhamCelebration.GOAL]
 *   when Wrexham's score went up, otherwise null.
 */
internal fun celebrationFor(before: WrexhamMatch?, after: WrexhamMatch): WrexhamCelebration? =
    when {
        before == null || before.id != after.id -> null
        !before.status.isFinished && after.wrexhamWon -> WrexhamCelebration.WIN
        (after.wrexhamGoals ?: 0) > (before.wrexhamGoals ?: 0) -> WrexhamCelebration.GOAL
        else -> null
    }

// How long each effect stays on screen. The GIF durations are the GIFs' real lengths, so each is
// removed right as its single play ends.
/** Length of wrexham_anim_goal.gif. */
private const val GOAL_GIF_MS = 2000L

/** Card confetti duration, from the design's `startConfetti()`. */
private const val CONFETTI_MS = 3600L

/** Length of wrexham_anim_win.gif. */
private const val KIT_WIN_MS = 4040L

/** How long the full-screen confetti shows. The GIF itself is 6.76s; the design cuts it at 4s. */
private const val WINNER_SCREEN_MS = 4000L

/** How long the Compose fox runner is on screen; its run takes 1.9s. */
private const val FOX_MS = 2000L

/** The card's match when it shows exactly one (live, finished or a single fixture), else null. */
internal val WrexhamWidgetState.singleMatch: WrexhamMatch?
    get() = (content as? WrexhamContent.Matches)?.matches?.singleOrNull()

/**
 * Creates the card's [WrexhamFx] and keeps it in step with [state]:
 * 1. Runs the state's demo script, if any, once the card is on screen. The script only produces updated matches (see
 *    [WrexhamFakeData.updates]); it doesn't trigger any effect itself.
 * 2. Watches the match the card is showing and plays a celebration when [celebrationFor] says so. Celebrations run in
 *    the composition's scope, so a newer update arriving mid-celebration doesn't cut it short.
 *
 * Demo scripts restart whenever the card re-enters composition, so they replay on every visit.
 *
 * @param state The widget state on display.
 * @return The [WrexhamFx] the card reads to know what to draw.
 */
@Composable
internal fun rememberWrexhamFx(state: WrexhamWidgetState): WrexhamFx {
    val fx = remember { WrexhamFx() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state, fx.drawn) {
        fx.match = null
        val script = state.script ?: return@LaunchedEffect
        val start = state.singleMatch ?: return@LaunchedEffect
        if (!fx.drawn) return@LaunchedEffect
        WrexhamFakeData.updates(script, start).collect { fx.match = it }
    }

    val shown = fx.match ?: state.singleMatch
    LaunchedEffect(shown) {
        if (shown == null) return@LaunchedEffect
        celebrationFor(fx.lastSeen, shown)?.let { with(fx) { scope.play(it, state.goalStyle) } }
        fx.lastSeen = shown
    }
    return fx
}

/**
 * Plays one of the designer's Kit GIFs from res/raw exactly once, then holds its last frame. Callers remove it from
 * composition when it should disappear.
 *
 * Uses the platform's [AnimatedImageDrawable] in an [ImageView] because Compose's own Image can't play GIFs. That needs
 * Android 9 (API 28); on older versions nothing is shown. A fresh drawable is decoded each time this enters
 * composition, so every play starts on frame 0.
 *
 * @param res The GIF in res/raw.
 * @param modifier Sets the size and position. Give it the GIF's aspect ratio.
 * @param crop True to fill the whole area and crop the overflow (full-screen confetti); false to fit the whole GIF
 *   inside the area.
 */
@Composable
internal fun KitGif(@RawRes res: Int, modifier: Modifier = Modifier, crop: Boolean = false) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                scaleType = if (crop) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
                val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.resources, res))
                (drawable as? AnimatedImageDrawable)?.apply {
                    // 0 extra repeats = play once. The GIF files themselves are set to loop forever.
                    repeatCount = 0
                    start()
                }
                setImageDrawable(drawable)
            }
        },
        modifier = modifier,
    )
}

/**
 * One piece of card confetti: where it starts and how it moves. The units come from the design's canvas code: speeds
 * are in dp per frame at 60fps, angles in radians.
 *
 * @property x Start position across the card, 0 (left) to 1 (right).
 * @property y Start position as a fraction of card height; negative, so pieces start above the card and fall in at
 *   different times.
 * @property vx Sideways drift per frame.
 * @property vy Fall speed per frame.
 * @property size Width of the piece in dp.
 * @property rotation Starting angle.
 * @property spin Angle added per frame.
 * @property color Fill colour.
 * @property round True for a dot, false for a paper strip.
 * @property flip Phase of the strip's flutter: its height shrinks and grows as it "turns over".
 * @property sway Phase of the side-to-side wobble.
 */
private class ConfettiPiece(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val size: Float,
    val rotation: Float,
    val spin: Float,
    val color: Color,
    val round: Boolean,
    val flip: Float,
    val sway: Float,
)

/** Card confetti colours from the design: Wrexham red, white, gold, purple, pink, green. */
private val confettiColors =
    listOf(
        Color(0xFFC8102E),
        Color.White,
        Color(0xFFF6C343),
        Color(0xFF764EDD),
        Color(0xFFFF8A9C),
        Color(0xFF4CC38A),
    )

/**
 * 110 pieces of confetti falling inside the card for [CONFETTI_MS], fading out over the last 0.8s. A port of
 * `startConfetti()` from the design prototype. The caller clips it to the card's shape.
 *
 * The prototype moves each piece a little every frame. Here the position is worked out directly from elapsed time, so
 * the motion stays the same even if frames are dropped.
 */
@Composable
internal fun CardConfetti(modifier: Modifier = Modifier) {
    val pieces = remember {
        List(110) {
            ConfettiPiece(
                x = Random.nextFloat(),
                y = -Random.nextFloat() * 0.9f,
                vx = (Random.nextFloat() - 0.5f) * 0.8f,
                vy = 1.6f + Random.nextFloat() * 2.2f,
                size = 4f + Random.nextFloat() * 5f,
                rotation = Random.nextFloat() * 6f,
                spin = (Random.nextFloat() - 0.5f) * 0.25f,
                color = confettiColors.random(),
                round = Random.nextFloat() < 0.25f,
                flip = Random.nextFloat() * 6.28f,
                sway = Random.nextFloat() * 6.28f,
            )
        }
    }

    // Milliseconds since the confetti started, updated every frame. Reading it in the Canvas
    // below makes the Canvas redraw each frame.
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (elapsed < CONFETTI_MS) {
            elapsed = withFrameMillis { it } - start
        }
    }

    val dp = LocalDensity.current.density
    Canvas(modifier) {
        // 1 until the last 800ms, then down to 0.
        val fade = ((CONFETTI_MS - elapsed) / 800f).coerceIn(0f, 1f)
        // Elapsed time counted in 60fps frames, the unit the design's speeds are written in.
        val f = elapsed / 16.67f
        pieces.forEach { p ->
            // Sideways: steady drift plus a sine wobble. The cos() term is the running total of the
            // prototype's per-frame `x += sin(sway) * 0.6` as sway grows by 0.05 a frame.
            val x = p.x * size.width + (p.vx * f - 12f * (cos(p.sway + 0.05f * f) - cos(p.sway))) * dp
            val y = p.y * size.height + (p.vy * f - 10f) * dp
            val s = p.size * dp
            rotateRad(p.rotation + p.spin * f, pivot = Offset(x, y)) {
                if (p.round) {
                    drawCircle(p.color, radius = s / 2, center = Offset(x, y), alpha = fade)
                } else {
                    // Strip height follows |cos|, so the strip looks like it flips as it falls.
                    val h = s * 0.55f * abs(cos(p.flip + 0.15f * f)) + dp
                    drawRect(p.color, topLeft = Offset(x - s / 2, y - h / 2), size = Size(s, h), alpha = fade)
                }
            }
        }
    }
}

/**
 * Effects that cover the whole screen, not just the card: the winner confetti and the running fox.
 *
 * The card is an item in the homepage's scrolling list, and list items can't draw outside their bounds. So this opens a
 * [Popup], a separate window, pinned to the top-left of the app window and filling it. It isn't focusable, but it does
 * swallow taps while it's up, which is at most ~4s. The Popup only exists while an effect is playing.
 *
 * @param fx Effects currently playing.
 * @param cardTop Top of the card in window pixels, used to place the fox at the card's height.
 * @param cardHeight Height of the card in pixels.
 */
@Composable
internal fun WrexhamScreenEffects(fx: WrexhamFx, cardTop: Float, cardHeight: Int) {
    if (!fx.winnerScreen && !fx.fox) return
    Popup(
        popupPositionProvider = WindowOrigin,
        properties = PopupProperties(focusable = false, clippingEnabled = false),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (fx.winnerScreen) WinnerConfetti()
            // The fox runs just above the card's vertical middle.
            if (fx.fox) FoxRunner(foxTop = cardTop + cardHeight / 2f - with(LocalDensity.current) { 40.dp.toPx() })
        }
    }
}

/** Places the Popup at the window's top-left corner, wherever the card is. */
private object WindowOrigin : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ) = IntOffset.Zero
}

/**
 * The Kit full-screen confetti GIF (transparent background, so the homepage shows through). Fully visible for 3.4s,
 * then fades out over 0.6s. [WrexhamFx.play] removes it at 4s.
 */
@Composable
private fun WinnerConfetti() {
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        delay(3400)
        alpha.animateTo(0f, tween(600))
    }
    KitGif(
        res = R.raw.wrexham_anim_winner,
        crop = true,
        modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value },
    )
}

// Helpers for the keyframe animations below. Each animation runs a single 0-to-1 progress value
// and these turn it into CSS-style keyframes.

/** How far [t] is through the keyframe window [from]..[to], as 0 to 1 (clamped outside it). */
private fun segment(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

/** The value [t] of the way from [a] to [b]. */
private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * The design's CSS goal effect (`runFox()` in the prototype), all drawn in Compose, no Kit asset:
 *
 * - **Fox:** a Firefox logo with an orange-to-pink gradient tail behind it, running left to right across the whole
 *   screen in 1.9s.
 * - **Ball:** a soccer ball that drops in, lands, bounces once and rolls a little, over 1.6s. It stays at a fixed spot
 *   (64% across, where the prototype puts it for a Wrexham goal); the fox runs past it.
 * - **"GOAL!" banner:** a red pill 150dp above the fox. It pops in a little oversized, settles, holds, then drifts up
 *   and fades, over 1.8s.
 *
 * All sizes are the prototype's CSS pixels used as dp. The prototype's phone is 390 wide, so the run's start and end
 * points are stretched to this screen's width.
 *
 * @param foxTop Top of the fox in window pixels.
 */
@Composable
private fun FoxRunner(foxTop: Float) {
    // One 0-to-1 progress value per moving part; easings are the prototype's CSS cubic-beziers.
    val run = remember { Animatable(0f) }
    val ball = remember { Animatable(0f) }
    val banner = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { run.animateTo(1f, tween(1900, easing = CubicBezierEasing(0.4f, 0.1f, 0.6f, 1f))) }
        launch { ball.animateTo(1f, tween(1600, easing = CubicBezierEasing(0.42f, 0f, 1f, 1f))) }
        banner.animateTo(1f, tween(1800, easing = LinearEasing))
    }
    val density = LocalDensity.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.toFloat()
    val px = { v: Float -> with(density) { v.dp.roundToPx() } }

    // Fox: a 120x80dp box that starts off-screen left and ends off-screen right.
    val foxLeft = lerp(-200f, screenWidth + 130f, run.value)
    Box(modifier = Modifier.offset { IntOffset(px(foxLeft), foxTop.toInt()) }.size(120.dp, 80.dp)) {
        // Tail: 190dp wide, so it sticks out past the box's left edge. Without the unbounded
        // wrapContentWidth the 120dp box would squash it, leaving a gap behind the logo.
        Box(
            modifier =
                Modifier.offset(x = (-116).dp, y = 10.dp)
                    .wrapContentWidth(Alignment.Start, unbounded = true)
                    .size(190.dp, 46.dp)
                    .background(
                        Brush.horizontalGradient(
                            0f to Color.Transparent,
                            0.55f to Color(0xFFFF9640),
                            1f to Color(0xFFF3367D),
                        ),
                        RoundedCornerShape(
                            topStartPercent = 50,
                            bottomStartPercent = 50,
                            topEndPercent = 12,
                            bottomEndPercent = 12,
                        ),
                    )
        )
        Image(
            painter = painterResource(R.drawable.ic_firefox),
            contentDescription = null,
            modifier = Modifier.align(Alignment.TopEnd).size(62.dp),
        )
    }

    // Ball keyframes (the prototype's `@keyframes ball`), offsets in dp:
    //   0-60%:   falls from 60dp above, growing from 40% to full size and fading in over the first 30%
    //   60-75%:  bounces up 26dp while moving 12dp right
    //   75-100%: lands again, 22dp right of where it first landed
    val b = ball.value
    val ballX =
        when {
            b < 0.6f -> 0f
            b < 0.75f -> lerp(0f, 12f, segment(b, 0.6f, 0.75f))
            else -> lerp(12f, 22f, segment(b, 0.75f, 1f))
        }
    val ballY =
        when {
            b < 0.6f -> lerp(-60f, 0f, segment(b, 0f, 0.6f))
            b < 0.75f -> lerp(0f, -26f, segment(b, 0.6f, 0.75f))
            else -> lerp(-26f, 0f, segment(b, 0.75f, 1f))
        }
    Image(
        painter = painterResource(R.drawable.wrexham_ic_matches),
        contentDescription = null,
        modifier =
            Modifier.offset { IntOffset(px(screenWidth * 0.64f + ballX), foxTop.toInt() + px(60f + ballY)) }
                .graphicsLayer {
                    val grow = lerp(0.4f, 1f, segment(b, 0f, 0.6f))
                    scaleX = grow
                    scaleY = grow
                    alpha = segment(b, 0f, 0.3f)
                }
                .size(30.dp)
                .background(Color.White, CircleShape),
    )

    // Banner keyframes (the prototype's `@keyframes banner`):
    //   0-15%:   pops in from 50% to 110% size, rising 20dp and fading in
    //   15-25%:  settles back to 100%
    //   25-85%:  holds
    //   85-100%: drifts up 20dp and fades out
    val t = banner.value
    val bannerScale =
        when {
            t < 0.15f -> lerp(0.5f, 1.1f, segment(t, 0f, 0.15f))
            else -> lerp(1.1f, 1f, segment(t, 0.15f, 0.25f))
        }
    val bannerShift =
        when {
            t < 0.15f -> lerp(20f, 0f, segment(t, 0f, 0.15f))
            t < 0.85f -> 0f
            else -> lerp(0f, -20f, segment(t, 0.85f, 1f))
        }
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "GOAL!",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.08.em,
            modifier =
                Modifier.align(Alignment.TopCenter)
                    .offset { IntOffset(0, foxTop.toInt() - px(150f - bannerShift)) }
                    .graphicsLayer {
                        scaleX = bannerScale
                        scaleY = bannerScale
                        alpha = if (t < 0.15f) segment(t, 0f, 0.15f) else 1f - segment(t, 0.85f, 1f)
                    }
                    .background(Color(0xFFBA0C2F), RoundedCornerShape(30.dp))
                    .padding(horizontal = 22.dp, vertical = 8.dp),
        )
    }
}
