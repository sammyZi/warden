package dev.warden.map

import java.io.InputStream
import java.net.InetAddress
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.sin

internal data class Country(val iso: String, val lat: Float, val lon: Float)

/**
 * Offline IP-to-country index plus the land dots for the globe, built by tools/geo/build_geo.py
 * from DB-IP Lite (CC BY 4.0) and Natural Earth (public domain). Nothing is looked up online.
 */
internal class Geo(
    val countries: List<Country>,
    /** Land dots as unit vectors: x, y, z per dot. */
    val land: FloatArray,
    private val v4: IntArray,
    private val v4Country: ByteArray,
    private val v6: LongArray,
    private val v6Country: ByteArray,
) {
    private val byIso = countries.associateBy { it.iso }

    fun country(iso: String): Country? = byIso[iso.uppercase()]

    /** Country for a literal IP address, or null for private/unknown ranges and anything else. */
    fun lookup(ip: String): Country? {
        // Only ever parse literals: InetAddress.getByName on a hostname would do a DNS lookup.
        if (!isIpLiteral(ip)) return null
        val bytes = runCatching { InetAddress.getByName(ip).address }.getOrNull() ?: return null
        val index = when {
            bytes.size == 4 -> v4Country.at(search(v4, ByteBuffer.wrap(bytes).int xor Int.MIN_VALUE))
            // NAT64 (64:ff9b::/96) wraps an IPv4 address; common on IPv6-only mobile networks.
            bytes.copyOf(12).contentEquals(NAT64) -> v4Country.at(search(v4, ByteBuffer.wrap(bytes, 12, 4).int xor Int.MIN_VALUE))
            // ponytail: high 64 bits only; country boundaries finer than /64 are rare enough to ignore.
            else -> v6Country.at(search(v6, ByteBuffer.wrap(bytes).long xor Long.MIN_VALUE))
        }
        return index?.let { countries.getOrNull(it) }
    }

    private fun isIpLiteral(ip: String) =
        IPV4.matches(ip) || (':' in ip && ip.all { it.isLetterOrDigit() || it == ':' || it == '.' })

    private fun ByteArray.at(i: Int) = if (i < 0) null else (this[i].toInt() and 0xFF).takeIf { it != UNKNOWN }

    companion object {
        private const val UNKNOWN = 255
        private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")
        private val NAT64 = byteArrayOf(0, 0x64, 0xff.toByte(), 0x9b.toByte(), 0, 0, 0, 0, 0, 0, 0, 0)

        // Starts are stored with the sign bit flipped, so signed order equals unsigned order.
        private fun search(starts: IntArray, key: Int) = java.util.Arrays.binarySearch(starts, key).let { if (it >= 0) it else -it - 2 }
        private fun search(starts: LongArray, key: Long) = java.util.Arrays.binarySearch(starts, key).let { if (it >= 0) it else -it - 2 }

        fun load(open: (String) -> InputStream): Geo {
            val countries = open("geo/countries.tsv").bufferedReader().readLines().map {
                val (iso, lat, lon) = it.split('\t')
                Country(iso, lat.toFloat(), lon.toFloat())
            }
            val l = ByteBuffer.wrap(open("geo/land.bin").use { it.readBytes() })
            val land = FloatArray(l.remaining() / 8 * 3)
            for (i in 0 until land.size / 3) unit(l.float, l.float, land, i * 3)

            val b4 = ByteBuffer.wrap(open("geo/ip4.bin").use { it.readBytes() })
            val v4 = IntArray(b4.int)
            val c4 = ByteArray(v4.size)
            for (i in v4.indices) {
                v4[i] = b4.int xor Int.MIN_VALUE
                c4[i] = b4.get()
            }
            val b6 = ByteBuffer.wrap(open("geo/ip6.bin").use { it.readBytes() })
            val v6 = LongArray(b6.int)
            val c6 = ByteArray(v6.size)
            for (i in v6.indices) {
                v6[i] = b6.long xor Long.MIN_VALUE
                c6[i] = b6.get()
            }
            return Geo(countries, land, v4, c4, v6, c6)
        }

        /** Writes the unit vector for (lat, lon) degrees into [out] at [at]; lon 0 faces the viewer. */
        fun unit(lat: Float, lon: Float, out: FloatArray, at: Int) {
            val phi = Math.toRadians(lat.toDouble())
            val lambda = Math.toRadians(lon.toDouble())
            out[at] = (cos(phi) * sin(lambda)).toFloat()
            out[at + 1] = sin(phi).toFloat()
            out[at + 2] = (cos(phi) * cos(lambda)).toFloat()
        }
    }
}
