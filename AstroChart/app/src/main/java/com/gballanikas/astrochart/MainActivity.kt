package com.gballanikas.astrochart

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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

private data class PointPosition(
    val name: String,
    val glyph: String,
    val longitude: Double
)

private data class Aspect(
    val first: String,
    val second: String,
    val angle: Int,
    val orb: Double,
    val glyph: String
)

private val bodies = listOf(
    Body.Sun to "☉", Body.Moon to "☽", Body.Mercury to "☿", Body.Venus to "♀",
    Body.Mars to "♂", Body.Jupiter to "♃", Body.Saturn to "♄", Body.Uranus to "♅",
    Body.Neptune to "♆", Body.Pluto to "♇"
)

private val zodiac = listOf("Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo", "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces")
private val zodiacGlyph = listOf("♈", "♉", "♊", "♋", "♌", "♍", "♎", "♏", "♐", "♑", "♒", "♓")

@Composable
private fun AstroChartApp() {
    var tab by remember { mutableIntStateOf(0) }
    MaterialTheme {
        Column(Modifier.fillMaxSize().background(Color(0xFFFAF7FB))) {
            Text(
                "AstroChart",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            ScrollableTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Real-time") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Birth horoscope") })
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
        Text("Current planetary positions", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        AspectMatrix(positions, aspects)
        Spacer(Modifier.height(14.dp))
        PositionList(positions)
        Spacer(Modifier.height(12.dp))
        Text("Main aspects: conjunction 0°, sextile 60°, square 90°, trine 120°, opposition 180°", fontSize = 12.sp)
    }
}

@Composable
private fun BirthScreen() {
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
                DatePickerDialog(this@BirthScreenActivityContext(), { _, y, m, d -> date = LocalDate.of(y, m + 1, d) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
            }) { Text("Date: ${date}") }
            Button(onClick = {
                TimePickerDialog(this@BirthScreenActivityContext(), { _, h, m -> time = LocalTime.of(h, m) }, time.hour, time.minute, true).show()
            }) { Text("Time: ${time}") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(city, { city = it }, label = { Text("City / location") }, modifier = Modifier.fillMaxWidth())
        Text("Enter a worldwide city or location. Location/time-zone lookup will be added to the birth-chart service layer.", fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(8.dp))
        Button(onClick = { generated = true }, modifier = Modifier.fillMaxWidth()) { Text("Calculate horoscope") }
        if (generated) {
            val instant = LocalDateTime.of(date, time).toInstant(ZoneOffset.UTC)
            val positions = calculatePositions(instant)
            val aspects = calculateAspects(positions)
            Spacer(Modifier.height(12.dp))
            HoroscopeWheel(positions)
            Spacer(Modifier.height(12.dp))
            AspectMatrix(positions, aspects)
            Spacer(Modifier.height(12.dp))
            PositionList(positions)
            Text("Birth chart currently uses the entered UTC-equivalent time; city coordinates, DST/time-zone resolution, houses, Ascendant/MC, Chiron, Vertex and Part of Fortune are the next calculation layer.", fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

// A small helper keeps the date/time dialogs tied to the hosting activity without storing an Activity in Compose state.
private fun BirthScreenActivityContext(): android.content.Context = throw IllegalStateException("Date/time picker context is supplied by the activity host")

@Composable
private fun PositionList(positions: List<PointPosition>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Text("Planetary positions", fontWeight = FontWeight.Bold)
            positions.forEach { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.glyph, fontSize = 22.sp, modifier = Modifier.width(34.dp))
                    Text(p.name, modifier = Modifier.width(80.dp), fontWeight = FontWeight.Medium)
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
            Text("Aspect / angle matrix", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Column {
                    Row {
                        Text("", Modifier.width(78.dp))
                        positions.forEach { Text(it.glyph, Modifier.width(32.dp), fontWeight = FontWeight.Bold) }
                    }
                    positions.forEachIndexed { i, p ->
                        Row {
                            Text(p.glyph + " " + p.name.take(3), Modifier.width(78.dp))
                            positions.forEachIndexed { j, q ->
                                val a = if (i < j) aspects.firstOrNull { it.first == p.name && it.second == q.name } else null
                                Text(a?.glyph ?: if (i == j) "•" else "", Modifier.width(32.dp), fontSize = 17.sp)
                            }
                        }
                    }
                }
            }
            if (aspects.isNotEmpty()) {
                Divider(Modifier.padding(vertical = 6.dp))
                aspects.forEach { Text("${it.first} ${it.glyph} ${it.second}  ${it.angle}°  orb ${"%.1f".format(it.orb)}°", fontSize = 12.sp) }
            } else Text("No major aspects within the configured orbs.", fontSize = 12.sp)
        }
    }
}

@Composable
private fun HoroscopeWheel(positions: List<PointPosition>) {
    Box(Modifier.fillMaxWidth().height(330.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(310.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = min(size.width, size.height) * .46f
            drawCircle(Color.White, r, c)
            drawCircle(Color(0xFF5D3B66), r, c, style = Stroke(3f))
            drawCircle(Color(0xFFB8A9BD), r * .78f, c, style = Stroke(1.5f))
            for (i in 0..11) {
                val a = Math.toRadians((i * 30.0 - 90.0))
                val p = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
                drawLine(Color(0xFF8E7A91), c, p, strokeWidth = 1f)
                val ta = Math.toRadians((i * 30.0 + 15.0 - 90.0))
                val tx = c.x + r * .88f * cos(ta).toFloat()
                val ty = c.y + r * .88f * sin(ta).toFloat()
                drawCircle(Color(0xFFEFE7F0), 16f, Offset(tx, ty))
            }
            val inner = r * .78f
            positions.forEachIndexed { index, p ->
                val a = Math.toRadians(p.longitude - 90.0)
                val pos = Offset(c.x + inner * cos(a).toFloat(), c.y + inner * sin(a).toFloat())
                drawCircle(Color(0xFF5D3B66), 8f, pos)
            }
        }
    }
    Text("Classic zodiac wheel preview", fontSize = 12.sp)
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
        val d = angularDistance(points[i].longitude, points[j].longitude)
        val candidates = listOf(0 to 8.0 to "☌", 60 to 5.0 to "⚹", 90 to 6.0 to "□", 120 to 7.0 to "△", 180 to 8.0 to "☍")
        val hit = candidates.firstOrNull { abs(d - it.first.first) <= it.first.second }
        if (hit != null) result += Aspect(points[i].name, points[j].name, hit.first.first, abs(d - hit.first.first), hit.second)
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
    val deg = inside.toInt()
    val minFloat = (inside - deg) * 60.0
    val min = minFloat.toInt()
    val sec = ((minFloat - min) * 60.0).toInt()
    return "${zodiacGlyph[sign]} ${zodiac[sign]} ${deg}° ${min}' ${sec}\""
}

private fun normalize(value: Double): Double = ((value % 360.0) + 360.0) % 360.0
