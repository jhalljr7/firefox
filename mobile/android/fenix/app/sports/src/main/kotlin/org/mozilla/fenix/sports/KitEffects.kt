/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * SPORTS CORE / EFFECTS: the animations a celebration plays.
 *
 * What: [KitGif] (plays the designer's Kit GIFs from res/raw: sports_kit_goal, _win, _winner),
 * [CardConfetti], and [SportsScreenEffects] (full-screen winner confetti, plus the running fox and
 * "GOAL!" banner, drawn in Compose from the designer's HTML prototype).
 * Called by: ClubWidget.kt, which layers the card-sized effects over the card. Screen-sized effects
 * use a separate window because a homepage list item can't draw outside its own bounds.
 * Assets: the Kit GIFs and crests are internal design assets.
 */

package org.mozilla.fenix.sports

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.widget.ImageView
import androidx.annotation.RawRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * Plays one of the designer's Kit GIFs from res/raw exactly once, then holds its last frame. Callers remove it from
 * composition when it should disappear.
 *
 * Uses the platform's [AnimatedImageDrawable] in an [ImageView] because Compose's Image can't play GIFs. That needs
 * Android 9 (API 28); on older versions nothing is shown. A fresh drawable is decoded each time this enters
 * composition, so every play starts on frame 0.
 *
 * @param res The GIF in res/raw.
 * @param modifier Sets the size and position. Give it the GIF's aspect ratio.
 * @param crop True to fill the area and crop the overflow (full-screen confetti); false to fit the whole GIF inside the
 *   area.
 */
@Composable
fun KitGif(
    @RawRes res: Int,
    modifier: Modifier = Modifier,
    crop: Boolean = false,
) {
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

// Card confetti, a port of the design's `startConfetti()`. Speeds are in dp per 60fps frame,
// angles in radians, as in the prototype's canvas code.
private const val CONFETTI_PIECES = 110
private const val CONFETTI_FADE_MS = 800f
private const val FRAME_MS = 16.67f
private const val DRIFT_RANGE = 0.8f
private const val MIN_FALL_SPEED = 1.6f
private const val FALL_SPEED_RANGE = 2.2f
private const val MIN_PIECE_DP = 4f
private const val PIECE_RANGE_DP = 5f
private const val SPIN_RANGE = 0.25f
private const val DOT_SHARE = 0.25f
private const val START_SPREAD = 0.9f
private const val START_OFFSET_DP = 10f
private const val SWAY_PER_FRAME = 0.05f
private const val SWAY_AMPLITUDE = 12f
private const val FLIP_PER_FRAME = 0.15f
private const val STRIP_HEIGHT = 0.55f
private const val FULL_TURN = (2 * PI).toFloat()

/**
 * One piece of card confetti, with a random start and motion.
 *
 * x/y are start positions as fractions of the card (y is negative, so pieces start above the card and fall in at
 * different times); `flip` is the phase of a strip's flutter (its height follows |cos|, so it looks like it turns over)
 * and `sway` the phase of its side-to-side wobble.
 */
private class ConfettiPiece(random: Random, colors: List<Color>) {
    val x = random.nextFloat()
    val y = -random.nextFloat() * START_SPREAD
    val vx = (random.nextFloat() - 0.5f) * DRIFT_RANGE
    val vy = MIN_FALL_SPEED + random.nextFloat() * FALL_SPEED_RANGE
    val size = MIN_PIECE_DP + random.nextFloat() * PIECE_RANGE_DP
    val rotation = random.nextFloat() * FULL_TURN
    val spin = (random.nextFloat() - 0.5f) * SPIN_RANGE
    val color = colors.random(random)
    val round = random.nextFloat() < DOT_SHARE
    val flip = random.nextFloat() * FULL_TURN
    val sway = random.nextFloat() * FULL_TURN
}

/**
 * Confetti falling inside the card for [CONFETTI_MS], fading out over the last 0.8s. The caller clips it to the card's
 * shape. The prototype moves each piece a little every frame; here the position is worked out from elapsed time, so the
 * motion is the same even if frames are dropped.
 *
 * @param colors Colours to pick from, e.g. [Club.celebrationColors].
 * @param modifier Sets the area; the caller clips it to the card's shape.
 */
@Composable
fun CardConfetti(
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    val pieces = remember(colors) { List(CONFETTI_PIECES) { ConfettiPiece(Random.Default, colors) } }

    // Milliseconds since the confetti started. Reading it inside the Canvas redraws each frame.
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (elapsed < CONFETTI_MS) {
            elapsed = withFrameMillis { it } - start
        }
    }

    val dp = LocalDensity.current.density
    Canvas(modifier) {
        val fade = ((CONFETTI_MS - elapsed) / CONFETTI_FADE_MS).coerceIn(0f, 1f)
        val f = elapsed / FRAME_MS
        pieces.forEach { p ->
            // Sideways: steady drift plus a wobble. The cos() term is the running total of the
            // prototype's per-frame `x += sin(sway) * 0.6` as sway grows by 0.05 a frame.
            val wobble = SWAY_AMPLITUDE * (cos(p.sway + SWAY_PER_FRAME * f) - cos(p.sway))
            val x = p.x * size.width + (p.vx * f - wobble) * dp
            val y = p.y * size.height + (p.vy * f - START_OFFSET_DP) * dp
            val s = p.size * dp
            rotateRad(p.rotation + p.spin * f, pivot = Offset(x, y)) {
                if (p.round) {
                    drawCircle(p.color, radius = s / 2, center = Offset(x, y), alpha = fade)
                } else {
                    val h = s * STRIP_HEIGHT * abs(cos(p.flip + FLIP_PER_FRAME * f)) + dp
                    drawRect(p.color, topLeft = Offset(x - s / 2, y - h / 2), size = Size(s, h), alpha = fade)
                }
            }
        }
    }
}

/**
 * Effects that cover the whole screen, not just the card: the winner confetti and the running fox. Opens a [Popup] (a
 * separate window) pinned to the app window's top-left corner and filling it. It isn't focusable, but it does swallow
 * taps while it's up, which is at most ~4s. The Popup only exists while an effect is playing.
 *
 * @param fx Effects currently playing.
 * @param club The club celebrating; the "GOAL!" banner uses its first celebration colour.
 * @param cardTop Top of the card in window pixels, used to run the fox at the card's height.
 * @param cardHeight Height of the card in pixels.
 */
@Composable
fun SportsScreenEffects(
    fx: SportsFx,
    club: Club,
    cardTop: Float,
    cardHeight: Int,
) {
    if (fx.winnerScreen == 0 && fx.fox == 0) return
    Popup(
        popupPositionProvider = WindowOrigin,
        properties = PopupProperties(focusable = false, clippingEnabled = false),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (fx.winnerScreen != 0) key(fx.winnerScreen) { WinnerConfetti() }
            // The fox runs just above the card's vertical middle.
            val foxTop = cardTop + cardHeight / 2f - with(LocalDensity.current) { FOX_ABOVE_CENTER_DP.dp.toPx() }
            if (fx.fox != 0) key(fx.fox) { FoxRunner(foxTop = foxTop, bannerColor = club.celebrationColors.first()) }
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

private const val WINNER_FADE_MS = 600

/**
 * The Kit full-screen confetti GIF (transparent background, so the homepage shows through). Fully visible, then fades
 * out over its last 0.6s; [SportsFx.play] removes it at 4s.
 */
@Composable
private fun WinnerConfetti() {
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        delay(WINNER_SCREEN_MS - WINNER_FADE_MS)
        alpha.animateTo(0f, tween(WINNER_FADE_MS))
    }
    KitGif(
        res = R.raw.sports_kit_winner,
        crop = true,
        modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value },
    )
}

// The fox runner: the design's CSS goal effect (`runFox()`), drawn in Compose. Sizes are the
// prototype's CSS pixels used as dp. Its phone is 390 wide, so the run's start and end points are
// stretched to this screen's width.
private const val FOX_ABOVE_CENTER_DP = 40f
private const val RUN_MS = 1900
private const val BALL_MS = 1600
private const val BANNER_MS = 1800
private const val FOX_START_DP = -200f
private const val FOX_END_PAST_EDGE_DP = 130f
private const val FOX_WIDTH_DP = 120
private const val FOX_HEIGHT_DP = 80
private const val FOX_LOGO_DP = 62
private const val TAIL_LEFT_DP = -116
private const val TAIL_TOP_DP = 10
private const val TAIL_WIDTH_DP = 190
private const val TAIL_HEIGHT_DP = 46
private const val TAIL_ORANGE_AT = 0.55f
private const val TAIL_ROUND_PERCENT = 50
private const val TAIL_TIP_PERCENT = 12
private const val BALL_ACROSS = 0.64f
private const val BALL_BELOW_FOX_DP = 60f
private const val BALL_DP = 30
private const val BANNER_ABOVE_FOX_DP = 150f
private const val BANNER_TEXT_SP = 22
private const val BANNER_LETTER_SPACING_EM = 0.08f
private const val BANNER_CORNER_DP = 30

private val TailOrange = Color(0xFFFF9640)
private val TailPink = Color(0xFFF3367D)

/**
 * A value at a point in an animation's progress (0 to 1), as in a CSS `@keyframes` rule.
 *
 * @property at Progress, 0 to 1.
 * @property value The value at that point.
 */
private class Keyframe(val at: Float, val value: Float)

/** The value at progress [t], interpolating linearly between the keyframes either side of it. */
private fun List<Keyframe>.valueAt(t: Float): Float {
    if (t <= first().at) return first().value
    val next = firstOrNull { it.at >= t } ?: return last().value
    val previous = last { it.at < t }
    return lerp(previous.value, next.value, (t - previous.at) / (next.at - previous.at))
}

/** The value [t] of the way from [a] to [b]. */
private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

// The prototype's `@keyframes ball`, offsets in dp: falls in from 60dp above while growing from 40%
// and fading in, lands at 60%, bounces up 26dp while moving 12dp right, lands again 22dp right.
private val BallX =
    listOf(
        Keyframe(at = 0f, value = 0f),
        Keyframe(at = 0.6f, value = 0f),
        Keyframe(at = 0.75f, value = 12f),
        Keyframe(at = 1f, value = 22f),
    )
private val BallY =
    listOf(
        Keyframe(at = 0f, value = -60f),
        Keyframe(at = 0.6f, value = 0f),
        Keyframe(at = 0.75f, value = -26f),
        Keyframe(at = 1f, value = 0f),
    )
private val BallScale = listOf(Keyframe(at = 0f, value = 0.4f), Keyframe(at = 0.6f, value = 1f))
private val BallAlpha = listOf(Keyframe(at = 0f, value = 0f), Keyframe(at = 0.3f, value = 1f))

// The prototype's `@keyframes banner`: pops in from 50% to 110% size while rising 20dp and fading
// in, settles to 100%, holds, then drifts up 20dp and fades out.
private val BannerScale =
    listOf(Keyframe(at = 0f, value = 0.5f), Keyframe(at = 0.15f, value = 1.1f), Keyframe(at = 0.25f, value = 1f))
private val BannerShift =
    listOf(
        Keyframe(at = 0f, value = 20f),
        Keyframe(at = 0.15f, value = 0f),
        Keyframe(at = 0.85f, value = 0f),
        Keyframe(at = 1f, value = -20f),
    )
private val BannerAlpha =
    listOf(
        Keyframe(at = 0f, value = 0f),
        Keyframe(at = 0.15f, value = 1f),
        Keyframe(at = 0.85f, value = 1f),
        Keyframe(at = 1f, value = 0f),
    )

private val RunEasing = CubicBezierEasing(0.4f, 0.1f, 0.6f, 1f)
private val BallEasing = CubicBezierEasing(0.42f, 0f, 1f, 1f)

/** Runs a 0-to-1 progress value once over [durationMs] when it enters composition. */
@Composable
private fun rememberProgress(durationMs: Int, easing: Easing): Float {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(durationMs, easing = easing)) }
    return progress.value
}

/** Fox, ball and banner together; each animates itself when it appears. */
@Composable
private fun FoxRunner(foxTop: Float, bannerColor: Color) {
    val screenWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp().value }
    RunningFox(foxTop, screenWidth)
    BouncingBall(foxTop, screenWidth)
    GoalBanner(foxTop, bannerColor)
}

/** A Firefox logo with an orange-to-pink tail, running left to right across the screen in 1.9s. */
@Composable
private fun RunningFox(foxTop: Float, screenWidth: Float) {
    val left = lerp(FOX_START_DP, screenWidth + FOX_END_PAST_EDGE_DP, rememberProgress(RUN_MS, RunEasing))
    val density = LocalDensity.current
    Box(
        modifier =
            Modifier.offset { IntOffset(with(density) { left.dp.roundToPx() }, foxTop.toInt()) }
                .size(FOX_WIDTH_DP.dp, FOX_HEIGHT_DP.dp)
    ) {
        // The tail is wider than the fox's box and sticks out past its left edge; without the
        // unbounded wrapContentWidth the box would squash it and leave a gap behind the logo.
        Box(
            modifier =
                Modifier.offset(x = TAIL_LEFT_DP.dp, y = TAIL_TOP_DP.dp)
                    .wrapContentWidth(Alignment.Start, unbounded = true)
                    .size(TAIL_WIDTH_DP.dp, TAIL_HEIGHT_DP.dp)
                    .background(
                        Brush.horizontalGradient(0f to Color.Transparent, TAIL_ORANGE_AT to TailOrange, 1f to TailPink),
                        RoundedCornerShape(
                            topStartPercent = TAIL_ROUND_PERCENT,
                            bottomStartPercent = TAIL_ROUND_PERCENT,
                            topEndPercent = TAIL_TIP_PERCENT,
                            bottomEndPercent = TAIL_TIP_PERCENT,
                        ),
                    )
        )
        Image(
            painter = painterResource(R.drawable.sports_ic_firefox_logo),
            contentDescription = null,
            modifier = Modifier.align(Alignment.TopEnd).size(FOX_LOGO_DP.dp),
        )
    }
}

/** A soccer ball at a fixed spot (where the prototype puts it for the followed team's goal) that the fox runs past. */
@Composable
private fun BouncingBall(foxTop: Float, screenWidth: Float) {
    val t = rememberProgress(BALL_MS, BallEasing)
    val density = LocalDensity.current
    Image(
        painter = painterResource(R.drawable.sports_ic_soccer_ball),
        contentDescription = null,
        modifier =
            Modifier.offset {
                    with(density) {
                        IntOffset(
                            (screenWidth * BALL_ACROSS + BallX.valueAt(t)).dp.roundToPx(),
                            foxTop.toInt() + (BALL_BELOW_FOX_DP + BallY.valueAt(t)).dp.roundToPx(),
                        )
                    }
                }
                .graphicsLayer {
                    scaleX = BallScale.valueAt(t)
                    scaleY = BallScale.valueAt(t)
                    alpha = BallAlpha.valueAt(t)
                }
                .size(BALL_DP.dp)
                .background(Color.White, CircleShape),
    )
}

/** A red "GOAL!" pill 150dp above the fox. */
@Composable
private fun GoalBanner(foxTop: Float, color: Color) {
    val t = rememberProgress(BANNER_MS, LinearEasing)
    val density = LocalDensity.current
    val top = foxTop.toInt() - with(density) { (BANNER_ABOVE_FOX_DP - BannerShift.valueAt(t)).dp.roundToPx() }
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.sports_goal_banner),
            color = Color.White,
            fontSize = BANNER_TEXT_SP.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = BANNER_LETTER_SPACING_EM.em,
            modifier =
                Modifier.align(Alignment.TopCenter)
                    .offset { IntOffset(0, top) }
                    .graphicsLayer {
                        scaleX = BannerScale.valueAt(t)
                        scaleY = BannerScale.valueAt(t)
                        alpha = BannerAlpha.valueAt(t)
                    }
                    .background(color, RoundedCornerShape(BANNER_CORNER_DP.dp))
                    .padding(
                        horizontal = SportsTheme.layout.space.static300,
                        vertical = SportsTheme.layout.space.static100,
                    ),
        )
    }
}
