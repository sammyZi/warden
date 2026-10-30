package dev.warden.map

import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.warden.design.Motion
import dev.warden.design.Warden
import dev.warden.engine.NetEvent
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** Where on the globe the viewer looks: yaw = -longitude, pitch = latitude, in degrees. */
@Stable
internal class GlobeCamera(lat: Float, lon: Float) {
    val yaw = Animatable(-lon)
    val pitch = Animatable(lat.coerceIn(-MAX_PITCH, MAX_PITCH))

    suspend fun focus(lat: Float, lon: Float) = kotlinx.coroutines.coroutineScope {
        // Take the short way round.
        val target = yaw.value + ((-lon - yaw.value) % 360f + 540f) % 360f - 180f
        launch { yaw.animateTo(target, Motion.emphasized(FOCUS_MS)) }
        launch { pitch.animateTo(lat.coerceIn(-MAX_PITCH, MAX_PITCH), Motion.emphasized(FOCUS_MS)) }
    }
}

/** One destination country and every recent connection that went there. */
internal class Destination(val country: Country, val events: List<NetEvent>) {
    val connections = events.size
    val blocked = events.count { it.blocked }
    val apps = events.distinctBy { it.uid }.size
    val domains = events.mapNotNull { it.domain }.distinct().size
}

/**
 * Dotted orthographic globe drawn on a Canvas: land as fine dots, arcs from [home] to each
 * destination, dot size by connection count. Drag to spin (with fling), tap a dot to select.
 * Idles into a slow spin unless the system asks for reduced motion.
 */
@Composable
internal fun Globe(
    geo: Geo,
    home: Country?,
    destinations: List<Destination>,
    selected: String?,
    camera: GlobeCamera,
    onSelect: (String?) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    revealKey: Any = Unit,
) {
    val c = Warden.colors
    val reduceMotion = Warden.reduceMotion
    val scope = rememberCoroutineScope()
    var lastTouch by remember { mutableLongStateOf(0L) }
    val currentDestinations by rememberUpdatedState(destinations)

    val reveal = remember { Animatable(if (reduceMotion) 1f else 0f) }
    // Arcs grow out from home and dots swell in, again whenever the filters behind them change.
    LaunchedEffect(revealKey) {
        if (reduceMotion) return@LaunchedEffect
        reveal.snapTo(0f)
        reveal.animateTo(1f, Motion.emphasized(REVEAL_MS))
    }

    // Slow idle spin; frames only run while this screen is on screen.
    if (!reduceMotion) {
        LaunchedEffect(camera) {
            var last = androidx.compose.runtime.withFrameNanos { it }
            while (true) {
                val now = androidx.compose.runtime.withFrameNanos { it }
                if (now / 1_000_000 - lastTouch > IDLE_MS && !camera.yaw.isRunning) {
                    camera.yaw.snapTo(camera.yaw.value + (now - last) / 1e9f * SPIN_DEG_PER_S)
                }
                last = now
            }
        }
    }

    val dotPaint = remember { Paint().apply { strokeCap = Paint.Cap.ROUND; isAntiAlias = true } }
    val front = remember(geo) { FloatArray(geo.land.size / 3 * 2) }
    val limb = remember(geo) { FloatArray(geo.land.size / 3 * 2) }
    val path = remember { Path() }
    val homeVec = remember(home) { home?.let { FloatArray(3).also { v -> Geo.unit(it.lat, it.lon, v, 0) } } }

    Canvas(
        modifier
            .semantics { contentDescription = description }
            .pointerInput(camera) {
                val tracker = VelocityTracker()
                detectDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onDragEnd = {
                        val v = tracker.calculateVelocity().x * degreesPerPx(size.width)
                        scope.launch { camera.yaw.animateDecay(v, exponentialDecay(frictionMultiplier = 2f)) }
                    },
                ) { change, drag ->
                    lastTouch = System.nanoTime() / 1_000_000 // same clock as withFrameNanos
                    tracker.addPosition(change.uptimeMillis, change.position)
                    val k = degreesPerPx(size.width)
                    scope.launch {
                        camera.yaw.snapTo(camera.yaw.value + drag.x * k)
                        camera.pitch.snapTo((camera.pitch.value + drag.y * k).coerceIn(-MAX_PITCH, MAX_PITCH))
                    }
                }
            }
            .pointerInput(camera) {
                detectTapGestures { tap ->
                    lastTouch = System.nanoTime() / 1_000_000
                    val r = radius(size.width.toFloat(), size.height.toFloat(), 12.dp.toPx())
                    val rot = Rotation(camera.yaw.value, camera.pitch.value)
                    val hit = currentDestinations.mapNotNull { d ->
                        val v = FloatArray(3).also { Geo.unit(d.country.lat, d.country.lon, it, 0) }
                        rot.apply(v[0], v[1], v[2])
                        if (rot.z <= 0f) return@mapNotNull null
                        val dist = hypot(size.width / 2f + r * rot.x - tap.x, size.height / 2f - r * rot.y - tap.y)
                        (d.country.iso to dist).takeIf { dist < 28.dp.toPx() }
                    }.minByOrNull { it.second }
                    onSelect(hit?.first)
                }
            },
    ) {
        val r = radius(size.width, size.height, 12.dp.toPx())
        val center = Offset(size.width / 2, size.height / 2)
        val rot = Rotation(camera.yaw.value, camera.pitch.value)

        drawCircle(c.surfaceVariant.copy(alpha = 0.45f), r, center)
        drawCircle(c.outline, r, center, style = Stroke(1.dp.toPx()))
        drawLand(geo.land, rot, center, r, front, limb, dotPaint, c.muted)

        val arc = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round)
        for (d in destinations) {
            if (homeVec == null || d.country.iso == home?.iso) continue
            val to = FloatArray(3).also { Geo.unit(d.country.lat, d.country.lon, it, 0) }
            if (buildArc(path, homeVec, to, rot, center, r, reveal.value)) {
                val color = countryHue(d.country.iso, c.palette)
                drawPath(path, color.copy(alpha = if (selected == null || selected == d.country.iso) 0.85f else 0.25f), style = arc)
            }
        }

        val maxCount = destinations.maxOfOrNull { it.connections } ?: 1
        for (d in destinations) {
            val v = FloatArray(3).also { Geo.unit(d.country.lat, d.country.lon, it, 0) }
            rot.apply(v[0], v[1], v[2])
            if (rot.z <= 0f) continue
            val p = Offset(center.x + r * rot.x, center.y - r * rot.y)
            val dot = (3.dp + 7.dp * sqrt(d.connections / maxCount.toFloat())).toPx() * reveal.value
            drawCircle(countryHue(d.country.iso, c.palette), dot, p)
            if (d.blocked > 0) drawCircle(c.blocked, dot + 3.dp.toPx(), p, style = Stroke(1.5.dp.toPx()))
            if (d.country.iso == selected) drawCircle(c.text, dot + 7.dp.toPx(), p, style = Stroke(2.dp.toPx()))
        }

        if (homeVec != null) {
            rot.apply(homeVec[0], homeVec[1], homeVec[2])
            if (rot.z > 0f) {
                val p = Offset(center.x + r * rot.x, center.y - r * rot.y)
                drawCircle(c.text, 4.dp.toPx(), p)
                drawCircle(c.text, 9.dp.toPx(), p, style = Stroke(1.5.dp.toPx()))
            }
        }
    }
}

/** Rotation by yaw around Y, then pitch around X. Results land in x, y, z (z > 0 faces the viewer). */
private class Rotation(yawDeg: Float, pitchDeg: Float) {
    private val ca = cos(Math.toRadians(yawDeg.toDouble())).toFloat()
    private val sa = sin(Math.toRadians(yawDeg.toDouble())).toFloat()
    private val cb = cos(Math.toRadians(pitchDeg.toDouble())).toFloat()
    private val sb = sin(Math.toRadians(pitchDeg.toDouble())).toFloat()
    var x = 0f
    var y = 0f
    var z = 0f

    fun apply(px: Float, py: Float, pz: Float) {
        val z1 = -px * sa + pz * ca
        x = px * ca + pz * sa
        y = py * cb - z1 * sb
        z = py * sb + z1 * cb
    }
}

// Points split into a bright front set and a faint near-limb set for a cheap depth cue.
// Reused buffers + one native drawPoints call each: no per-frame allocation for ~7k dots.
private fun DrawScope.drawLand(
    land: FloatArray, rot: Rotation, center: Offset, r: Float,
    front: FloatArray, limb: FloatArray, paint: Paint, color: Color,
) {
    var nf = 0
    var nl = 0
    for (i in 0 until land.size / 3) {
        rot.apply(land[i * 3], land[i * 3 + 1], land[i * 3 + 2])
        if (rot.z <= 0f) continue
        val sx = center.x + r * rot.x
        val sy = center.y - r * rot.y
        if (rot.z > LIMB_Z) {
            front[nf++] = sx
            front[nf++] = sy
        } else {
            limb[nl++] = sx
            limb[nl++] = sy
        }
    }
    paint.strokeWidth = (r / 75f).coerceAtLeast(2f)
    drawIntoCanvas {
        paint.color = color.copy(alpha = 0.6f).toArgb()
        it.nativeCanvas.drawPoints(front, 0, nf, paint)
        paint.color = color.copy(alpha = 0.22f).toArgb()
        it.nativeCanvas.drawPoints(limb, 0, nl, paint)
    }
}

/** Great-circle arc lifted off the surface; returns false when nothing of it is visible. */
private fun buildArc(path: Path, a: FloatArray, b: FloatArray, rot: Rotation, center: Offset, r: Float, upTo: Float): Boolean {
    val omega = acos((a[0] * b[0] + a[1] * b[1] + a[2] * b[2]).coerceIn(-1f, 1f))
    if (omega < MIN_ARC) return false
    val s = sin(omega)
    path.reset()
    var drawing = false
    var any = false
    val steps = (ARC_STEPS * upTo).toInt()
    for (i in 0..steps) {
        val t = i / ARC_STEPS.toFloat()
        val wa = sin((1 - t) * omega) / s
        val wb = sin(t * omega) / s
        val lift = 1f + (0.06f + 0.2f * omega / PI.toFloat()) * sin(PI.toFloat() * t)
        rot.apply((wa * a[0] + wb * b[0]) * lift, (wa * a[1] + wb * b[1]) * lift, (wa * a[2] + wb * b[2]) * lift)
        // Visible if in front of the sphere, or lifted outside its silhouette.
        val visible = rot.z > 0f || rot.x * rot.x + rot.y * rot.y > 1f
        val x = center.x + r * rot.x
        val y = center.y - r * rot.y
        if (visible) {
            if (drawing) path.lineTo(x, y) else path.moveTo(x, y)
            drawing = true
            any = true
        } else {
            drawing = false
        }
    }
    return any
}

/**
 * A country keeps one hue wherever it appears (dot, arc, list swatch), whatever its rank.
 * ponytail: hashing 5 hues across many countries repeats colours; the list labels carry identity.
 */
internal fun countryHue(iso: String, palette: List<Color>) = palette[Math.floorMod(iso.hashCode(), palette.size)]

private fun radius(w: Float, h: Float, pad: Float) = minOf(w, h) / 2f - pad

/** Drag the width of the globe = turn it ~180 degrees, so the surface tracks the finger. */
private fun degreesPerPx(width: Int) = 180f / (width * 0.9f)

private const val MAX_PITCH = 70f
private const val LIMB_Z = 0.3f
private const val MIN_ARC = 0.01f
private const val ARC_STEPS = 32
private const val IDLE_MS = 3000
private const val SPIN_DEG_PER_S = 4f
private const val FOCUS_MS = 600
private const val REVEAL_MS = 900
