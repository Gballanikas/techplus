package com.gballanikas.astrochart

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.eclipticGeoMoon
import io.github.cosinekitty.astronomy.equatorialToEcliptic
import io.github.cosinekitty.astronomy.geoVector
import io.github.cosinekitty.astronomy.sunPosition
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AstroChartApp() }
    }
}

private data class PointPosition(val name: String, val glyph: String, val longitude: Double)
private data class Aspect(val first: String, val second: String, val angle: Int, val orb: Double, val glyph: String)
private data class AspectRule(val angle: Int, val orb: Double, val glyph: String)

private val bodies = listOf(
    Body.Sun to "☉", Body.Moon to "☽", Body.Mercury to "☿", Body.Venus to "♀",
    Body.Mars to "♂", Body.Jupiter to "♃", Body.Saturn to "♄", Body.Uranus to "♅",
    Body.Neptune to "♆", Body.Pluto to "♇"
)

private val zodiac = listOf("Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo", "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces")
private val zodiacGlyph = listOf("♈", "♉", "♊", "♋", "♌", "♍", "♎", "♏", "♐", "♑", "♒", "♓")

private val aspectRules = listOf(
    AspectRule(0, 8.0, "☌"),
    AspectRule(60, 5.0, "⚹"),
    AspectRule(90, 6.0, "□"),
    AspectRule(120, 7.0, "△"),
    AspectRule(180, 8.0, "☍")
)

private val planetColor = Color(0xFFFFD166)
private val wheelInk = Color(0xFFB8C2D1)
private val wheelMuted = Color(0xFF586579)
private val wheelBackground = Color(0xFF08111F)
private val conjunctionColor = Color(0xFFFFA62B)
private val sextileColor = Color(0xFF3FA7FF)
private val squareColor = Color(0xFFFF4D4D)
private val trineColor = Color(0xFF45D483)
private val oppositionColor = Color(0xFFFFA62B)

private fun aspectColor(angle: Int): Color = when (angle) {
    0 -> conjunctionColor
    60 -> sextileColor
    90 -> squareColor
    120 -> trineColor
    180 -> oppositionColor
    else -> wheelInk
}

@Composable
private fun AstroChartApp() {
    var tab by remember { mutableIntStateOf(0) }
    MaterialTheme {
        Column(Modifier.fillMaxSize().background(Color(0xFF07101C))) {
            Text(
                "AstroChart",
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            ScrollableTabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Real-time") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Birth horoscope") })
            }
            if (tab == 0) RealTimeScreen() else BirthScreen()
        }
    }
}

@Composable
private fun RealTimeScreen() {
    val positions = remember { calculatePositions(Instant.now()) }
    val aspects = remember(positions) { calculateAspects(positions) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Text("Current sky", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
        Spacer(Modifier.height(6.dp))
        HoroscopeWheel(positions, aspects)
        Spacer(Modifier.height(10.dp))
        AspectMatrix(positions, aspects)
        Spacer(Modifier.height(12.dp))
        PositionList(positions)
    }
}

@Composable
private fun BirthScreen() {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var time by remember { mutableStateOf(LocalTime.NOON) }
    var city by remember { mutableStateOf("") }
    var generated by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                DatePickerDialog(
                    context,
                    { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
                    date.year,
                    date.monthValue - 1,
                    date.dayOfMonth
                ).show()
            }) { Text("Date: $date") }
            Button(onClick = {
                TimePickerDialog(
                    context,
                    { _, h, m -> time = LocalTime.of(h, m) },
                    time.hour,
                    time.minute,
                    true
                ).show()
            }) { Text("Time: ${time.toString().take(5)}") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(city, { city = it }, label = { Text("City / location") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Button(onClick = { generated = true }, Modifier.fillMaxWidth()) { Text("Calculate horoscope") }
        if (generated) {
            val instant = LocalDateTime.of(date, time).toInstant(ZoneOffset.UTC)
            val positions = calculatePositions(instant)
            val aspects = calculateAspects(positions)
            Spacer(Modifier.height(12.dp))
            HoroscopeWheel(positions, aspects)
            Spacer(Modifier.height(12.dp))
            AspectMatrix(positions, aspects)
            Spacer(Modifier.height(12.dp))
            PositionList(positions)
            Text(
                "Birth calculation currently treats the entered time as UTC. The next layer will resolve the selected city's coordinates, time zone/DST, Placidus houses, Ascendant/MC, Chiron, Vertex and Part of Fortune.",
                fontSize = 12.sp,
                color = Color(0xFF9BA7B8),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun PositionList(positions: List<PointPosition>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Text("Planetary positions", fontWeight = FontWeight.Bold)
            positions.forEach { p ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(p.glyph, fontSize = 22.sp, color = planetColor, modifier = Modifier.width(34.dp))
                    Text(p.name, Modifier.width(82.dp), fontWeight = FontWeight.Medium)
                    Text(formatPosition(p.longitude))
                }
            }
        }
    }
}

@Composable
private fun AspectMatrix(positions: List<PointPosition>, aspects: List<Aspect>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Text("Aspects", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Column {
                    Row {
                        Text("", Modifier.width(78.dp))
                        positions.forEach {
                            Text(
                                it.glyph,
                                Modifier.width(34.dp),
                                color = planetColor,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Divider()
                    positions.forEachIndexed { i, p ->
                        Row {
                            Text(
                                p.glyph + " " + p.name.take(3),
                                Modifier.width(78.dp),
                                color = planetColor,
                                fontWeight = FontWeight.Medium
                            )
                            positions.forEachIndexed { j, q ->
                                val a = if (i < j) {
                                    aspects.firstOrNull { it.first == p.name && it.second == q.name }
                                } else if (j < i) {
                                    aspects.firstOrNull { it.first == q.name && it.second == p.name }
                                } else null
                                Text(
                                    when {
                                        i == j -> "•"
                                        a != null -> a.glyph
                                        else -> ""
                                    },
                                    Modifier.width(34.dp),
                                    color = if (a != null) aspectColor(a.angle) else wheelMuted,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
            Divider(Modifier.padding(vertical = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AspectLegendItem("☌", "Conjunction", conjunctionColor)
                AspectLegendItem("⚹", "Sextile", sextileColor)
                AspectLegendItem("□", "Square", squareColor)
                AspectLegendItem("△", "Trine", trineColor)
                AspectLegendItem("☍", "Opposition", oppositionColor)
            }
        }
    }
}

@Composable
private fun AspectLegendItem(glyph: String, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(glyph, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(3.dp))
        Text(label, fontSize = 10.sp)
    }
}

@Composable
private fun HoroscopeWheel(positions: List<PointPosition>, aspects: List<Aspect>) {
    Box(Modifier.fillMaxWidth().height(350.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(330.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = min(size.width, size.height) * .47f
            val signRadius = radius * .87f
            val aspectRadius = radius * .56f
            val planetRadius = radius * .66f

            drawCircle(wheelBackground, radius, center)
            drawCircle(wheelInk, radius, center, style = Stroke(2.2f))
            drawCircle(wheelMuted, signRadius, center, style = Stroke(1.2f))
            drawCircle(wheelMuted, aspectRadius, center, style = Stroke(1.0f))

            for (i in 0 until 12) {
                val a = Math.toRadians(i * 30.0 - 90.0)
                val inner = Offset(
                    center.x + signRadius * cos(a).toFloat(),
                    center.y + signRadius * sin(a).toFloat()
                )
                drawLine(wheelMuted, center, inner, strokeWidth = 1f)
            }

            aspects.forEach { aspect ->
                val first = positions.firstOrNull { it.name == aspect.first } ?: return@forEach
                val second = positions.firstOrNull { it.name == aspect.second } ?: return@forEach
                val p1 = wheelPoint(center, aspectRadius, first.longitude)
                val p2 = wheelPoint(center, aspectRadius, second.longitude)
                drawLine(
                    aspectColor(aspect.angle),
                    p1,
                    p2,
                    strokeWidth = when (aspect.angle) {
                        0 -> 3.0f
                        90 -> 2.6f
                        120 -> 2.5f
                        else -> 2.2f
                    },
                    cap = StrokeCap.Round
                )
            }

            drawIntoCanvas { canvas ->
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    textAlign = Paint.Align.CENTER
                }

                paint.color = android.graphics.Color.rgb(145, 158, 177)
                paint.textSize = 27f
                zodiacGlyph.forEachIndexed { i, glyph ->
                    val angle = Math.toRadians(i * 30.0 + 15.0 - 90.0)
                    val p = Offset(
                        center.x + radius * .91f * cos(angle).toFloat(),
                        center.y + radius * .91f * sin(angle).toFloat()
                    )
                    canvas.nativeCanvas.drawText(glyph, p.x, p.y + 9f, paint)
                }

                paint.color = android.graphics.Color.rgb(255, 209, 102)
                paint.textSize = 25f
                positions.forEach { p ->
                    val point = wheelPoint(center, planetRadius, p.longitude)
                    canvas.nativeCanvas.drawText(p.glyph, point.x, point.y + 8f, paint)
                }
            }
        }
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Planet glyphs", color = planetColor, fontSize = 11.sp)
        Text("  •  ", color = wheelMuted, fontSize = 11.sp)
        Text("aspect lines use the matrix colors", color = Color(0xFF9BA7B8), fontSize = 11.sp)
    }
}

private fun wheelPoint(center: Offset, radius: Float, longitude: Double): Offset {
    val angle = Math.toRadians(longitude - 90.0)
    return Offset(
        center.x + radius * cos(angle).toFloat(),
        center.y + radius * sin(angle).toFloat()
    )
}

private fun calculatePositions(instant: Instant): List<PointPosition> {
    val time = Time.fromMillisecondsSince1970(instant.toEpochMilli())
    return bodies.map { (body, glyph) ->
        val longitude = when (body) {
            Body.Sun -> sunPosition(time).elon
            Body.Moon -> eclipticGeoMoon(time).lon
            else -> equatorialToEcliptic(geoVector(body, time, Aberration.Corrected)).elon
        }
        PointPosition(body.name, glyph, normalize(longitude))
    }
}

private fun calculateAspects(points: List<PointPosition>): List<Aspect> {
    val result = mutableListOf<Aspect>()
    for (i in 0 until points.size) for (j in i + 1 until points.size) {
        val distance = angularDistance(points[i].longitude, points[j].longitude)
        val rule = aspectRules.firstOrNull { abs(distance - it.angle) <= it.orb }
        if (rule != null) {
            result += Aspect(
                points[i].name,
                points[j].name,
                rule.angle,
                abs(distance - rule.angle),
                rule.glyph
            )
        }
    }
    return result
}

private fun angularDistance(a: Double, b: Double): Double {
    val d = abs(normalize(a - b))
    return min(d, 360.0 - d)
}

private fun formatPosition(longitude: Double): String {
    val sign = (longitude / 30.0).toInt().coerceIn(0, 11)
    val inside = longitude - sign * 30.0
    val degrees = inside.toInt()
    val minuteFloat = (inside - degrees) * 60.0
    val minutes = minuteFloat.toInt()
    val seconds = ((minuteFloat - minutes) * 60.0).toInt()
    return "${zodiacGlyph[sign]} ${zodiac[sign]} $degrees° $minutes' $seconds\""
}

private fun normalize(value: Double): Double = ((value % 360.0) + 360.0) % 360.0
