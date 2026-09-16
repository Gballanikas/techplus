from pathlib import Path

SOURCE = Path('app/src/main/java/com/gballanikas/astrochart/MainActivity.kt')

CORRECTED = '''private fun calculatePlacidusHouses(latitude: Double, longitude: Double, time: Time): ChartAngles {
    require(abs(latitude) < 90.0) { "Invalid latitude" }

    val phi = Math.toRadians(latitude)
    val eps = eclipticObliquity(time)
    val tanPhi = tan(phi)
    val ramcDeg = normalize(siderealTime(time) * 15.0 + longitude)
    val ramc = Math.toRadians(ramcDeg)

    val mc = normalize(Math.toDegrees(atan2(sin(ramc), cos(ramc) * cos(eps))))
    val asc = normalize(Math.toDegrees(atan2(-cos(ramc), sin(ramc) * cos(eps) + tanPhi * sin(eps))) + 180.0)

    val h = DoubleArray(12)
    h[0] = asc
    h[3] = normalize(mc + 180.0)
    h[6] = normalize(asc + 180.0)
    h[9] = mc

    // Placidus is mathematically undefined beyond the polar circle.
    if (abs(latitude) >= 66.5633) {
        for (i in 1 until 12) h[i] = Double.NaN
        return ChartAngles(h.toList(), asc, mc)
    }

    // Classic Placidus semi-arc calculation: 11/12 divide the diurnal
    // semi-arc; 2/3 divide the nocturnal semi-arc. Opposite cusps are 180°.
    h[10] = placidusCusp(ramc, phi, eps, 30.0, 3.0, false)
    h[11] = placidusCusp(ramc, phi, eps, 60.0, 1.5, false)
    h[1]  = placidusCusp(ramc, phi, eps, 120.0, 1.5, true)
    h[2]  = placidusCusp(ramc, phi, eps, 150.0, 3.0, true)

    h[4] = normalize(h[10] + 180.0)
    h[5] = normalize(h[11] + 180.0)
    h[7] = normalize(h[1] + 180.0)
    h[8] = normalize(h[2] + 180.0)

    return ChartAngles(h.toList(), asc, mc)
}

private fun placidusCusp(
    ramc: Double,
    phi: Double,
    eps: Double,
    initialOffsetDeg: Double,
    divisor: Double,
    negativeBranch: Boolean
): Double {
    var ra = ramc + Math.toRadians(initialOffsetDeg)
    val tanPhi = tan(phi)
    val tanEps = tan(eps)

    repeat(20) {
        // tan(delta) = sin(RA) * tan(eps)
        val tanDelta = sin(ra) * tanEps
        val delta = atan2(tanDelta, 1.0)
        val ad = asin((tanPhi * tan(delta)).coerceIn(-1.0, 1.0))

        val next = if (!negativeBranch) {
            ramc + (Math.PI / 2.0 + ad) / divisor
        } else {
            ramc + Math.PI - (Math.PI / 2.0 - ad) / divisor
        }

        val difference = abs(Math.atan2(sin(next - ra), cos(next - ra)))
        ra = next
        if (difference < 1e-10) return@repeat
    }

    return normalize(Math.toDegrees(atan2(sin(ra), cos(ra) * cos(eps))))
}

private fun eclipticObliquity(time: Time): Double {
    val t = time.tt / 36525.0
    val asec = ((((-0.0000000434 * t - 0.000000576) * t + 0.00200340) * t - 0.0001831) * t - 46.836769) * t + 84381.406
    return Math.toRadians((asec / 3600.0) + time.nutationEps() / 3600.0)
}
'''

s = SOURCE.read_text()
marker = 'import androidx.compose.ui.graphics.drawscope.drawIntoCanvas\n'
if 'import androidx.compose.ui.graphics.nativeCanvas' not in s:
    s = s.replace(marker, marker + 'import androidx.compose.ui.graphics.nativeCanvas\n', 1)
start = s.index('private fun calculatePlacidusHouses(')
end = s.index('private fun searchLocationsAsync(', start)
SOURCE.write_text(s[:start] + CORRECTED + '\n' + s[end:])
print('Applied exact Placidus house calculation to', SOURCE)
