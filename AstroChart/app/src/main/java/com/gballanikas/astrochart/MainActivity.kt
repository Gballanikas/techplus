package com.gballanikas.astrochart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.ecliptic
import io.github.cosinekitty.astronomy.geoVector
import io.github.cosinekitty.astronomy.sunPosition
import java.time.Instant

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AstroChartApp() }
    }
}

private data class PointPosition(val name: String, val glyph: String, val longitude: Double)
private data class Aspect(val a: String, val b: String, val symbol: String, val angle: Int)

private val bodies = listOf(
    Body.Sun to "☉", Body.Moon to "☽", Body.Mercury to "☿", Body.Venus to "♀",
    Body.Mars to "♂", Body.Jupiter to "♃", Body.Saturn to "♄", Body.Uranus to "♅",
    Body.Neptune to "♆", Body.Pluto to "♇"
)
private val names = listOf("Sun", "Moon", "Mercury", "Venus", "Mars", "Jupiter", "Saturn", "Uranus", "Neptune", "Pluto")
private val signs = arrayOf("♈ Aries", "♉ Taurus", "♊ Gemini", "♋ Cancer", "♌ Leo", "♍ Virgo", "♎ Libra", "♏ Scorpio", "♐ Sagittarius", "♑ Capricorn", "♒ Aquarius", "♓ Pisces")
private val aspectDefs = listOf(0 to "☌", 60 to "⚹", 90 to "□", 120 to "△", 180 to "☍")

@Composable
private fun AstroChartApp() {
    var tab by remember { mutableIntStateOf(0) }
    var city by remember { mutableStateOf("Abu Dhabi") }
    var date by remember { mutableStateOf(Instant.now()) }
    val positions = remember(date) { calculatePositions(date) }

    MaterialTheme {
        Column(Modifier.fillMaxSize().background(Color(0xFFFAF7FB))) {
            Text("AstroChart", Modifier.padding(16.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, onClick = { tab = 0 }, text = { Text("Real Time") })
                Tab(tab == 1, onClick = { tab = 1 }, text = { Text("Birth Horoscope") })
            }
            if (tab == 0) RealTimePage(positions) else BirthPage(city, { city = it }, positions)
        }
    }
}

@Composable
private fun RealTimePage(positions: List<PointPosition>) {
    Column(Modifier.fillMaxSize().padding(10.dp)) {
        Text("Current planetary positions", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        AspectMatrix(positions)
        Spacer(Modifier.height(14.dp))
        PositionList(positions)
    }
}

@Composable
private fun BirthPage(city: String, onCity: (String) -> Unit, positions: List<PointPosition>) {
    Column(Modifier.fillMaxSize().padding(10.dp).horizontalScroll(rememberScrollState())) {
        OutlinedTextField(city, onCity, label = { Text("Birth location") }, singleLine = true)
        Spacer(Modifier.height(10.dp))
        Text("Birth chart", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        HoroscopeWheel(positions)
        Spacer(Modifier.height(8.dp))
        AspectMatrix(positions)
        Spacer(Modifier.height(12.dp))
        PositionList(positions)
    }
}

@Composable
private fun AspectMatrix(positions: List<PointPosition>) {
    val aspects = calculateAspects(positions)
    Text("ANGLES / ASPECTS", fontWeight = FontWeight.Bold)
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        Column {
            Box(Modifier.size(54.dp))
            positions.forEach { Text(it.glyph, Modifier.size(54.dp).padding(12.dp), fontSize = 18.sp) }
        }
        positions.forEach { col ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(col.glyph, Modifier.size(54.dp).padding(12.dp), fontSize = 18.sp)
                positions.forEach { row ->
                    val found = aspects.firstOrNull { (it.a == col.name && it.b == row.name) || (it.a == row.name && it.b == col.name) }
                    Text(if (col.name == row.name) "•" else found?.symbol ?: "", Modifier.size(54.dp).padding(12.dp), fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun PositionList(positions: List<PointPosition>) {
    positions.forEach {
        val d = normalize(it.longitude)
        val sign = signs[(d / 30).toInt()]
        Text("${it.glyph}  ${it.name.padEnd(9)}  ${formatDms(d % 30.0)}  $sign", fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp))
    }
}

@Composable
private fun HoroscopeWheel(positions: List<PointPosition>) {
    Box(Modifier.fillMaxWidth().height(330.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(310.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension * .43f
            drawCircle(Color.White, r, c)
            drawCircle(Color.Black, r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            drawCircle(Color.Black, r * .72f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
            for (i in 0 until 12) {
                val a = Math.toRadians(i * 30.0 - 90.0)
                val p1 = Offset(c.x + r * kotlin.math.cos(a).toFloat(), c.y + r * kotlin.math.sin(a).toFloat())
                val p2 = Offset(c.x + r * .72f * kotlin.math.cos(a).toFloat(), c.y + r * .72f * kotlin.math.sin(a).toFloat())
                drawLine(Color(0xFF777777), p1, p2, 1f)
            }
            positions.forEachIndexed { i, p ->
                val a = Math.toRadians(p.longitude - 90.0)
                val pr = r * .62f
                val point = Offset(c.x + pr * kotlin.math.cos(a).toFloat(), c.y + pr * kotlin.math.sin(a).toFloat())
                drawCircle(Color(0xFF7B1FA2), 5f, point)
                if (i > 0) {
                    val prev = positions[i - 1]
                    val b = Math.toRadians(prev.longitude - 90.0)
                    val prevPoint = Offset(c.x + pr * kotlin.math.cos(b).toFloat(), c.y + pr * kotlin.math.sin(b).toFloat())
                    drawLine(Color(0xFFB71C1C), prevPoint, point, 1f, cap = StrokeCap.Round)
                }
            }
        }
    }
}

private fun calculatePositions(instant: Instant): List<PointPosition> {
    val time = Time.fromMillisecondsSince1970(instant.toEpochMilli())
    return bodies.mapIndexed { i, pair ->
        val longitude = when (pair.first) {
            Body.Sun -> sunPosition(time).elon
            Body.Moon -> io.github.cosinekitty.astronomy.eclipticGeoMoon(time).elon
            else -> ecliptic(geoVector(pair.first, time, Aberration.Corrected)).elon
        }
        PointPosition(names[i], pair.second, normalize(longitude))
    }
}

private fun calculateAspects(p: List<PointPosition>): List<Aspect> {
    val result = mutableListOf<Aspect>()
    for (i in p.indices) for (j in i + 1 until p.size) {
        val d0 = kotlin.math.abs(p[i].longitude - p[j].longitude)
        val d = minOf(d0, 360.0 - d0)
        val best = aspectDefs.minByOrNull { kotlin.math.abs(d - it.first) }!!
        val orb = kotlin.math.abs(d - best.first)
        val maxOrb = when (best.first) { 0, 180 -> 8.0; 120 -> 7.0; 90 -> 6.0; else -> 5.0 }
        if (orb <= maxOrb) result += Aspect(p[i].name, p[j].name, best.second, best.first)
    }
    return result
}

private fun normalize(v: Double): Double = ((v % 360.0) + 360.0) % 360.0
private fun formatDms(deg: Double): String {
    val d = deg.toInt(); val minutes = (deg - d) * 60.0; val m = minutes.toInt(); val s = ((minutes - m) * 60.0).toInt()
    return "%02d°%02d'%02d\"".format(d, m, s)
}
