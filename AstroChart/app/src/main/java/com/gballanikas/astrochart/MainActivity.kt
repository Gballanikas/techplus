package com.gballanikas.astrochart

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.eclipticGeoMoon
import io.github.cosinekitty.astronomy.equatorialToEcliptic
import io.github.cosinekitty.astronomy.geoVector
import io.github.cosinekitty.astronomy.siderealTime
import io.github.cosinekitty.astronomy.sunPosition
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AstroChartApp() }
    }
}

private data class PointPosition(val name: String, val glyph: String, val longitude: Double)
private data class Aspect(val first: String, val second: String, val angle: Int, val orb: Double, val glyph: String)
private data class AspectRule(val angle: Int, val orb: Double, val glyph: String)
private data class GeoLocation(val name: String, val country: String, val latitude: Double, val longitude: Double, val timezone: String)
private data class ChartAngles(val houses: List<Double>, val ascendant: Double, val mc: Double)
private data class HouseMeaning(val number: Int, val title: String, val meaning: String)

private val bodies = listOf(
    Body.Sun to "☉", Body.Moon to "☽", Body.Mercury to "☿", Body.Venus to "♀",
    Body.Mars to "♂", Body.Jupiter to "♃", Body.Saturn to "♄", Body.Uranus to "♅",
    Body.Neptune to "♆", Body.Pluto to "♇"
)
private val zodiac = listOf("Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo", "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces")
private val zodiacGlyph = listOf("♈︎", "♉︎", "♊︎", "♋︎", "♌︎", "♍︎", "♎︎", "♏︎", "♐︎", "♑︎", "♒︎", "♓︎")
private val aspectRules = listOf(AspectRule(0, 8.0, "☌"), AspectRule(60, 5.0, "⚹"), AspectRule(90, 6.0, "□"), AspectRule(120, 7.0, "△"), AspectRule(180, 8.0, "☍"))
private val planetColor = Color(0xFFFFD166)
private val zodiacColor = Color(0xFFFF3B3B)
private val wheelInk = Color(0xFFB8C2D1)
private val wheelMuted = Color(0xFF586579)
private val wheelBackground = Color(0xFF08111F)
private val conjunctionColor = Color(0xFFFFA62B)
private val sextileColor = Color(0xFF3FA7FF)
private val squareColor = Color(0xFFFF4D4D)
private val trineColor = Color(0xFF45D483)
private val oppositionColor = Color(0xFFFFA62B)
private fun aspectColor(angle: Int) = when (angle) { 0 -> conjunctionColor; 60 -> sextileColor; 90 -> squareColor; 120 -> trineColor; 180 -> oppositionColor; else -> wheelInk }

private val houseMeanings = listOf(
    HouseMeaning(1, "Self / Character", "Personality, appearance, identity, beginnings and how one presents to the world."),
    HouseMeaning(2, "Money / Values", "Possessions, material resources, values, talents and self-worth."),
    HouseMeaning(3, "Communication", "Communication, learning, siblings, immediate surroundings and short journeys."),
    HouseMeaning(4, "Home / Roots", "Home, family, ancestry, roots and the private foundation of life."),
    HouseMeaning(5, "Creativity / Children", "Creativity, pleasure, romance, children, play and self-expression."),
    HouseMeaning(6, "Work / Health", "Daily work, routine, service, practical duties and health."),
    HouseMeaning(7, "Partnerships", "Marriage, close relationships, partnerships, contracts and the other person."),
    HouseMeaning(8, "Shared Resources", "Shared finances, material loss, transformation and matters involving others' resources."),
    HouseMeaning(9, "Beliefs / Travel", "Higher learning, philosophy, worldview, religion and long-distance travel."),
    HouseMeaning(10, "Career / Calling", "Career, vocation, achievements, public role, status and ambitions."),
    HouseMeaning(11, "Friends / Groups", "Friends, groups, communities, networks, hopes and wishes."),
    HouseMeaning(12, "Private / Hidden", "Solitude, retreat, privacy, withdrawal and matters outside ordinary public life.")
)

@Composable
private fun AstroChartApp() {
    var tab by remember { mutableIntStateOf(0) }
    MaterialTheme { Column(Modifier.fillMaxSize().background(Color(0xFF07101C))) {
        Text("AstroChart", Modifier.padding(horizontal = 16.dp, vertical = 12.dp), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
        ScrollableTabRow(selectedTabIndex = tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Real-time") })
            Tab(tab == 1, { tab = 1 }, text = { Text("Birth horoscope") })
        }
        if (tab == 0) RealTimeScreen() else BirthScreen()
    } }
}

@Composable
private fun RealTimeScreen() {
    val positions = remember { calculatePositions(Instant.now()) }
    val aspects = remember(positions) { calculateAspects(positions) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Text("Current sky", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
        Spacer(Modifier.height(6.dp))
        HoroscopeWheel(positions, aspects, null)
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
    var location by remember { mutableStateOf<GeoLocation?>(null) }
    var results by remember { mutableStateOf<List<GeoLocation>>(emptyList()) }
    var message by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var generated by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), textStyle = androidx.compose.material3.LocalTextStyle.current.copy(color = planetColor))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { DatePickerDialog(context, { _, y, m, d -> date = LocalDate.of(y, m + 1, d) }, date.year, date.monthValue - 1, date.dayOfMonth).show() }) { Text("Date: $date", color = planetColor) }
            Button(onClick = { TimePickerDialog(context, { _, h, m -> time = LocalTime.of(h, m) }, time.hour, time.minute, true).show() }) { Text("Time: ${time.toString().take(5)}", color = planetColor) }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(city, { city = it }, label = { Text("City / location") }, modifier = Modifier.weight(1f), textStyle = androidx.compose.material3.LocalTextStyle.current.copy(color = planetColor))
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (city.isBlank()) message = "Enter a city first."
                else {
                    searching = true
                    message = ""
                    searchLocationsAsync(city.trim()) { r, e ->
                        searching = false
                        results = r
                        message = e ?: if (r.isEmpty()) "No locations found." else "Select a location."
                    }
                }
            }) { Text(if (searching) "..." else "Find") }
        }
        results.forEach { r ->
            Button(onClick = { location = r; city = "${r.name}, ${r.country}"; results = emptyList(); message = "${r.timezone} • %.5f, %.5f".format(Locale.US, r.latitude, r.longitude) }, modifier = Modifier.fillMaxWidth()) {
                Text("${r.name}, ${r.country} • ${r.timezone}")
            }
        }
        if (message.isNotBlank()) Text(message, fontSize = 11.sp, color = Color(0xFF9BA7B8), modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(8.dp))
        Button(onClick = { if (location == null) { message = "Please select the birth location with Find first."; generated = false } else generated = true }, modifier = Modifier.fillMaxWidth()) { Text("Calculate horoscope") }

        if (generated && location != null) {
            val loc = location!!
            val zone = runCatching { ZoneId.of(loc.timezone) }.getOrDefault(ZoneId.of("UTC"))
            val instant = LocalDateTime.of(date, time).atZone(zone).toInstant()
            val angles = calculatePlacidusHouses(loc.latitude, loc.longitude, Time.fromMillisecondsSince1970(instant.toEpochMilli()))
            val positions = calculatePositions(instant).toMutableList().apply {
                add(PointPosition("Ascendant", "ASC", angles.ascendant))
                add(PointPosition("MC", "MC", angles.mc))
            }
            val aspects = calculateAspects(positions)
            Spacer(Modifier.height(12.dp))
            HoroscopeWheel(positions, aspects, angles)
            Spacer(Modifier.height(12.dp))
            HouseCuspTable(angles)
            Spacer(Modifier.height(12.dp))
            HouseMeaningCard()
            Spacer(Modifier.height(12.dp))
            AspectMatrix(positions, aspects)
            Spacer(Modifier.height(12.dp))
            PositionList(positions)
            Text("${date} ${time} ${loc.timezone} • %.5f, %.5f".format(Locale.US, loc.latitude, loc.longitude), fontSize = 11.sp, color = Color(0xFF9BA7B8), modifier = Modifier.padding(vertical = 8.dp))
            Text("Placidus houses • house cusps are calculated from the birth date/time, geographic location and local sidereal time.", fontSize = 11.sp, color = Color(0xFF9BA7B8))
        }
    }
}

@Composable
private fun PositionList(positions: List<PointPosition>) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = wheelBackground, contentColor = Color.White)) {
        Column(Modifier.padding(10.dp)) {
            Text("Planetary / point positions", fontWeight = FontWeight.Bold)
            positions.forEach { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.glyph, fontSize = if (p.glyph.length > 2) 17.sp else 22.sp, color = if (p.name == "Ascendant" || p.name == "MC") zodiacColor else planetColor, modifier = Modifier.width(42.dp))
                    Text(p.name, Modifier.width(100.dp), fontWeight = FontWeight.Medium)
                    Text(formatPosition(p.longitude))
                }
            }
        }
    }
}

@Composable
private fun HouseCuspTable(chart: ChartAngles) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = wheelBackground, contentColor = Color.White)) {
        Column(Modifier.padding(10.dp)) {
            Text("Placidus house cusps", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))
            for (i in 0 until 12) {
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("House ${i + 1}", Modifier.width(80.dp), fontWeight = FontWeight.Medium)
                    Text(formatPosition(chart.houses[i]))
                }
            }
        }
    }
}

@Composable
private fun HouseMeaningCard() {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = wheelBackground, contentColor = Color.White)) {
        Column(Modifier.padding(10.dp)) {
            Text("The 12 houses", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))
            houseMeanings.forEach { h ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                    Text("${h.number}.", Modifier.width(28.dp), color = zodiacColor, fontWeight = FontWeight.Bold)
                    Column(Modifier.weight(1f)) {
                        Text(h.title, fontWeight = FontWeight.SemiBold)
                        Text(h.meaning, fontSize = 11.sp, color = Color(0xFFB8C2D1))
                    }
                }
            }
        }
    }
}

@Composable
private fun AspectMatrix(positions: List<PointPosition>, aspects: List<Aspect>) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = wheelBackground, contentColor = Color.White)) {
        Column(Modifier.padding(10.dp)) {
            Text("Aspects", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Column {
                    Row {
                        Text("", Modifier.width(88.dp))
                        positions.forEach { Text(it.glyph, Modifier.width(38.dp), color = if (it.name == "Ascendant" || it.name == "MC") zodiacColor else planetColor, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                    }
                    Divider()
                    positions.forEachIndexed { i, p ->
                        Row {
                            Text(p.glyph + " " + p.name.take(3), Modifier.width(88.dp), color = if (p.name == "Ascendant" || p.name == "MC") zodiacColor else planetColor, fontWeight = FontWeight.Medium)
                            positions.forEachIndexed { j, q ->
                                val a = if (i < j) aspects.firstOrNull { it.first == p.name && it.second == q.name } else if (j < i) aspects.firstOrNull { it.first == q.name && it.second == p.name } else null
                                Text(if (i == j) "•" else a?.glyph ?: "", Modifier.width(38.dp), color = if (a != null) aspectColor(a.angle) else wheelMuted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
private fun HoroscopeWheel(positions: List<PointPosition>, aspects: List<Aspect>, chart: ChartAngles?) {
    val density = LocalDensity.current
    Box(Modifier.fillMaxWidth().height(410.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(370.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = min(size.width, size.height) * .47f
            val signRadius = radius * .87f
            val aspectRadius = radius * .60f
            val planetRadius = radius * .70f
            val degreeRadius = radius * .965f
            val houseRadius = radius * .79f

            drawCircle(wheelBackground, radius, center)
            drawCircle(wheelInk, radius, center, style = Stroke(2.2f))
            drawCircle(wheelMuted, signRadius, center, style = Stroke(1.2f))
            drawCircle(wheelMuted, aspectRadius, center, style = Stroke(1f))
            if (chart != null) drawCircle(wheelMuted, houseRadius, center, style = Stroke(1.2f))

            for (i in 0 until 12) {
                drawLine(wheelMuted, wheelPoint(center, aspectRadius, i * 30.0), wheelPoint(center, signRadius, i * 30.0), strokeWidth = 1f)
            }

            // Five-degree tick marks, with degree labels repeated inside every zodiac sign.
            for (sign in 0 until 12) {
                for (localDeg in 0..30 step 5) {
                    val longitude = sign * 30.0 + localDeg
                    val a = Math.toRadians(180.0 - longitude)
                    val outer = radius * .985f
                    val inner = if (localDeg % 10 == 0) radius * .925f else radius * .945f
                    drawLine(wheelMuted, Offset(center.x + inner * cos(a).toFloat(), center.y + inner * sin(a).toFloat()), Offset(center.x + outer * cos(a).toFloat(), center.y + outer * sin(a).toFloat()), strokeWidth = if (localDeg % 10 == 0) 1.35f else .8f)
                }
            }

            // Natal house cusps are independent of zodiac sign boundaries.
            chart?.houses?.forEachIndexed { i, lon ->
                drawLine(wheelInk, center, wheelPoint(center, signRadius, lon), strokeWidth = if (i == 0 || i == 3 || i == 6 || i == 9) 2.4f else 1.5f)
            }

            aspects.forEach { a ->
                val p1 = positions.firstOrNull { it.name == a.first }
                val p2 = positions.firstOrNull { it.name == a.second }
                if (p1 != null && p2 != null && p1.name != "Ascendant" && p1.name != "MC" && p2.name != "Ascendant" && p2.name != "MC") {
                    drawLine(aspectColor(a.angle), wheelPoint(center, aspectRadius, p1.longitude), wheelPoint(center, aspectRadius, p2.longitude), strokeWidth = if (a.angle == 0) 3f else 2.4f, cap = StrokeCap.Round)
                }
            }

            drawIntoCanvas { canvas ->
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER }

                // Red zodiac glyphs, following the requested Aries-left clockwise orientation.
                paint.color = android.graphics.Color.rgb(255, 59, 59)
                paint.textSize = with(density) { 25.sp.toPx() }
                zodiacGlyph.forEachIndexed { i, g ->
                    val p = wheelPoint(center, radius * .89f, i * 30.0 + 15.0)
                    canvas.nativeCanvas.drawText(g, p.x, p.y + with(density) { 6.dp.toPx() }, paint)
                }

                // Local zodiac degrees: 0°, 10°, 20°, 30° in every sign.
                paint.color = android.graphics.Color.rgb(184, 194, 209)
                paint.textSize = with(density) { 7.5.sp.toPx() }
                for (sign in 0 until 12) {
                    for (localDeg in listOf(0, 10, 20, 30)) {
                        val longitude = sign * 30.0 + localDeg
                        val p = wheelPoint(center, degreeRadius, longitude)
                        canvas.nativeCanvas.drawText("${localDeg}°", p.x, p.y + with(density) { 2.5.dp.toPx() }, paint)
                    }
                }

                // Planet degree is intentionally WITHOUT seconds and is placed to the RIGHT of the planet glyph.
                positions.forEach { p ->
                    val point = wheelPoint(center, planetRadius, p.longitude)
                    paint.color = if (p.name == "Ascendant" || p.name == "MC") android.graphics.Color.rgb(255, 59, 59) else android.graphics.Color.rgb(255, 209, 102)
                    paint.textSize = if (p.name == "Ascendant" || p.name == "MC") with(density) { 9.5.sp.toPx() } else with(density) { 12.5.sp.toPx() }
                    canvas.nativeCanvas.drawText(p.glyph, point.x, point.y + with(density) { 3.dp.toPx() }, paint)
                    paint.color = android.graphics.Color.rgb(184, 194, 209)
                    paint.textSize = with(density) { 7.sp.toPx() }
                    val labelX = point.x + with(density) { 11.dp.toPx() }
                    val labelY = point.y - with(density) { 2.dp.toPx() }
                    canvas.nativeCanvas.drawText(formatDegreeMinutes(p.longitude % 30.0), labelX, labelY, paint)
                }

                // House numbers are drawn in the actual Placidus sectors.
                chart?.houses?.let { h ->
                    paint.color = android.graphics.Color.rgb(255, 255, 255)
                    paint.textSize = with(density) { 10.sp.toPx() }
                    for (i in 0 until 12) {
                        val mid = midpointLongitude(h[i], h[(i + 1) % 12])
                        val p = wheelPoint(center, houseRadius * .86f, mid)
                        canvas.nativeCanvas.drawText("${i + 1}", p.x, p.y + with(density) { 3.dp.toPx() }, paint)
                    }
                    paint.color = android.graphics.Color.rgb(255, 59, 59)
                    paint.textSize = with(density) { 9.5.sp.toPx() }
                    val ap = wheelPoint(center, radius * .78f, chart.ascendant)
                    val mp = wheelPoint(center, radius * .78f, chart.mc)
                    canvas.nativeCanvas.drawText("ASC", ap.x, ap.y, paint)
                    canvas.nativeCanvas.drawText("MC", mp.x, mp.y, paint)
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Text(if (chart == null) "Planet degrees • local zodiac degree scale • aspect colors" else "Placidus house cusps • house numbers • ASC / MC • local zodiac degrees", color = Color(0xFF9BA7B8), fontSize = 11.sp)
    }
}

private fun wheelPoint(center: Offset, radius: Float, longitude: Double): Offset {
    val angle = Math.toRadians(180.0 - longitude)
    return Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
}

private fun calculatePositions(instant: Instant): List<PointPosition> {
    val t = Time.fromMillisecondsSince1970(instant.toEpochMilli())
    return bodies.map { (b, g) ->
        val lon = when (b) {
            Body.Sun -> sunPosition(t).elon
            Body.Moon -> eclipticGeoMoon(t).lon
            else -> equatorialToEcliptic(geoVector(b, t, Aberration.Corrected)).elon
        }
        PointPosition(b.name, g, normalize(lon))
    }
}

private fun calculateAspects(points: List<PointPosition>): List<Aspect> {
    val r = mutableListOf<Aspect>()
    for (i in points.indices) for (j in i + 1 until points.size) {
        val d = angularDistance(points[i].longitude, points[j].longitude)
        val rule = aspectRules.firstOrNull { abs(d - it.angle) <= it.orb }
        if (rule != null) r += Aspect(points[i].name, points[j].name, rule.angle, abs(d - rule.angle), rule.glyph)
    }
    return r
}

private fun angularDistance(a: Double, b: Double): Double { val d = abs(a - b) % 360.0; return min(d, 360.0 - d) }
private fun normalize(v: Double): Double { var x = v % 360.0; if (x < 0) x += 360.0; return x }
private fun formatPosition(lon: Double): String { val i = ((lon / 30).toInt()).coerceIn(0, 11); return "${zodiac[i]} ${formatDegreeMinutesSeconds(lon % 30)}" }
private fun formatDegreeMinutesSeconds(d: Double): String { val s = kotlin.math.round(d * 3600).toInt(); return String.format(Locale.US, "%02d°%02d'%02d\"", (s / 3600).coerceIn(0, 29), (s % 3600) / 60, s % 60) }
private fun formatDegreeMinutes(d: Double): String { val totalMinutes = kotlin.math.round(d * 60.0).toInt(); val degrees = (totalMinutes / 60).coerceIn(0, 29); val minutes = totalMinutes % 60; return String.format(Locale.US, "%02d°%02d'", degrees, minutes) }
private fun midpointLongitude(a: Double, b: Double): Double { return normalize(a + ((b - a + 360.0) % 360.0) / 2.0) }

/**
 * Placidus house cusps using the classic iterative semi-arc method.
 * The four intermediate cusps are 11, 12, 2 and 3; the opposite cusps
 * are exactly 180 degrees away. Houses therefore change with birth time
 * AND birth location, as required for a natal chart.
 */
private fun calculatePlacidusHouses(latitude: Double, longitude: Double, time: Time): ChartAngles {
    require(abs(latitude) < 90.0) { "Invalid latitude" }
    val phi = Math.toRadians(latitude)
    val eps = eclipticObliquity(time)
    val tanPhi = tan(phi)
    val tanEps = tan(eps)
    val ramcDeg = normalize(siderealTime(time) * 15.0 + longitude)
    val ramc = Math.toRadians(ramcDeg)

    val mc = normalize(Math.toDegrees(atan2(sin(ramc), cos(ramc) * cos(eps))))
    val asc = normalize(Math.toDegrees(atan2(-cos(ramc), sin(ramc) * cos(eps) + tanPhi * sin(eps))))

    val h = DoubleArray(12)
    h[0] = asc
    h[9] = mc
    h[3] = normalize(mc + 180.0)
    h[6] = normalize(asc + 180.0)

    // If the requested latitude is outside the mathematical domain of Placidus,
    // keep the chart usable with equal houses rather than drawing invalid NaNs.
    val polarLimit = abs(latitude) >= 66.0
    if (polarLimit) {
        for (i in 1 until 12) h[i] = normalize(asc + i * 30.0)
        h[9] = mc
        h[3] = normalize(mc + 180.0)
        return ChartAngles(h.toList(), asc, mc)
    }

    h[10] = placidusIntermediate(ramcDeg, tanPhi, tanEps, initialOffset = 30.0, divisor = 3.0, upper = true)
    h[11] = placidusIntermediate(ramcDeg, tanPhi, tanEps, initialOffset = 60.0, divisor = 1.5, upper = true)
    h[1] = placidusIntermediate(ramcDeg, tanPhi, tanEps, initialOffset = 120.0, divisor = 1.5, upper = false)
    h[2] = placidusIntermediate(ramcDeg, tanPhi, tanEps, initialOffset = 150.0, divisor = 3.0, upper = false)
    h[4] = normalize(h[10] + 180.0)
    h[5] = normalize(h[11] + 180.0)
    h[7] = normalize(h[1] + 180.0)
    h[8] = normalize(h[2] + 180.0)

    if (h.any { !it.isFinite() }) {
        for (i in 1 until 12) h[i] = normalize(asc + i * 30.0)
        h[9] = mc
        h[3] = normalize(mc + 180.0)
    }
    return ChartAngles(h.toList(), asc, mc)
}

private fun placidusIntermediate(ramcDeg: Double, tanPhi: Double, tanEps: Double, initialOffset: Double, divisor: Double, upper: Boolean): Double {
    var ra = Math.toRadians(normalize(ramcDeg + initialOffset))
    val ramc = Math.toRadians(ramcDeg)
    repeat(20) {
        val x = (if (upper) -sin(ra) else sin(ra)) * tanEps * tanPhi
        val arg = x.coerceIn(-1.0, 1.0)
        val arc = acos(arg)
        val next = if (upper) ramc + arc / divisor else ramc + Math.PI - arc / divisor
        if (abs(normalize(Math.toDegrees(next - ra))) < 1e-8) {
            ra = next
            return@repeat
        }
        ra = next
    }
    return normalize(Math.toDegrees(atan2(sin(ra), cos(ra) * cos(Math.toRadians(23.4392911)))))
}

private fun eclipticObliquity(time: Time): Double {
    val t = time.tt / 36525.0
    val asec = ((((-0.0000000434 * t - 0.000000576) * t + 0.00200340) * t - 0.0001831) * t - 46.836769) * t + 84381.406
    return Math.toRadians((asec / 3600.0) + time.nutationEps() / 3600.0)
}

private fun searchLocationsAsync(query: String, callback: (List<GeoLocation>, String?) -> Unit) {
    Thread {
        try {
            val u = URL("https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(query, "UTF-8")}&count=5&language=en&format=json")
            val c = (u.openConnection() as HttpURLConnection).apply { requestMethod = "GET"; connectTimeout = 10000; readTimeout = 10000 }
            val text = BufferedReader(InputStreamReader(c.inputStream)).use { it.readText() }
            c.disconnect()
            val arr = org.json.JSONObject(text).optJSONArray("results")
            val out = mutableListOf<GeoLocation>()
            if (arr != null) for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                val lat = x.optDouble("latitude", Double.NaN)
                val lon = x.optDouble("longitude", Double.NaN)
                val tz = x.optString("timezone")
                if (lat.isFinite() && lon.isFinite() && tz.isNotBlank()) out += GeoLocation(x.optString("name"), x.optString("country"), lat, lon, tz)
            }
            Handler(Looper.getMainLooper()).post { callback(out, null) }
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post { callback(emptyList(), "Location search failed: ${e.message ?: "network error"}") }
        }
    }.start()
}
