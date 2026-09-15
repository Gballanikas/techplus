package com.gballanikas.astrochart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AstroChartApp() }
    }
}

private data class PointPosition(val name: String, val glyph: String, val longitude: Double)

private val bodies = listOf(
    Body.Sun to "☉", Body.Moon to "☽", Body.Mercury to "☿", Body.Venus to "♀",
    Body.Mars to "♂", Body.Jupiter to "♃", Body.Saturn to "♄", Body.Uranus to "♅",
    Body.Neptune to "♆", Body.Pluto to "♇"
)

@Composable
private fun AstroChartApp() {
    val positions = calculatePositions(Instant.now())
    MaterialTheme {
        Column(Modifier.fillMaxSize().background(Color(0xFFFAF7FB)).padding(16.dp)) {
            Text("AstroChart", fontSize = 24.sp)
            Text("Astronomy Engine compilation test", modifier = Modifier.padding(top = 8.dp))
            positions.forEach { p -> Text("${p.glyph} ${p.name}: ${"%.6f".format(p.longitude)}°") }
        }
    }
}

private fun calculatePositions(instant: Instant): List<PointPosition> {
    val time = Time.fromMillisecondsSince1970(instant.toEpochMilli())
    return bodies.map { (body, glyph) ->
        val longitude = when (body) {
            Body.Sun -> sunPosition(time).elon
            Body.Moon -> eclipticGeoMoon(time).elon
            else -> equatorialToEcliptic(geoVector(body, time, Aberration.Corrected)).elon
        }
        PointPosition(body.name, glyph, normalize(longitude))
    }
}

private fun normalize(value: Double): Double = ((value % 360.0) + 360.0) % 360.0
