package org.greedycat.intelligence.connectivity

import java.net.URI
import java.io.InputStream
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.UUID
import javax.net.ssl.X509TrustManager
import kotlinx.serialization.json.*

internal fun InputStream.pairingBytes(deadlineNanos: Long = Long.MAX_VALUE): ByteArray {
    val buffer = ByteArray(4097)
    var size = 0
    while (size < buffer.size) {
        check(System.nanoTime() < deadlineNanos) { "Pairing response deadline exceeded." }
        val count = read(buffer, size, buffer.size - size)
        if (count < 0) break
        size += count
    }
    require(size <= 4096)
    return buffer.copyOf(size)
}

/** Deliberately numeric/private IPv4 only: no DNS, redirects or arbitrary URLs. */
object PairingProtocol {
    val secret = Regex("[A-Za-z0-9_-]{43}")
    fun address(input: String): String {
        val uri = URI(input.trim())
        require(uri.scheme == "https" && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
        require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/")
        val host = requireNotNull(uri.host)
        val parts = host.split('.')
        require(parts.size == 4 && parts.all { it.matches(Regex("0|[1-9][0-9]{0,2}")) && it.toInt() in 0..255 })
        val a = parts.map(String::toInt)
        require(a[0] == 127 || a[0] == 10 || a[0] == 192 && a[1] == 168 || a[0] == 172 && a[1] in 16..31)
        require(uri.port == -1 || uri.port in 1..65535)
        return "https://$host:${if (uri.port == -1) 443 else uri.port}"
    }
    fun pin(input: String): String = input.trim().lowercase().also { require(it.matches(Regex("[0-9a-f]{64}"))) }
    fun validate(record: PairingCredential): PairingCredential {
        require(address(record.address) == record.address && pin(record.fingerprint) == record.fingerprint)
        require(UUID.fromString(record.pairingId).toString() == record.pairingId)
        require(secret.matches(record.token))
        require(record.issuedAt.isFinite() && record.expiresAt.isFinite() && record.issuedAt > 0)
        require(record.expiresAt > record.issuedAt && record.expiresAt - record.issuedAt <= 86401)
        return record
    }
    fun credential(json: JsonObject, address: String, pin: String): PairingCredential {
        require(json.keys == setOf("schema_version", "pairing_id", "token", "issued_at", "expires_at"))
        require(listOf("schema_version", "pairing_id", "token").all { json.getValue(it).jsonPrimitive.isString })
        require(listOf("issued_at", "expires_at").none { json.getValue(it).jsonPrimitive.isString })
        require(json.getValue("schema_version").jsonPrimitive.content == "pairing-1")
        return validate(PairingCredential(address, pin, json.getValue("pairing_id").jsonPrimitive.content,
            json.getValue("token").jsonPrimitive.content, json.getValue("issued_at").jsonPrimitive.double,
            json.getValue("expires_at").jsonPrimitive.double))
    }
}

/** No token-bearing toString/data-class copy dumps. Stored only encrypted. */
class PairingCredential(val address: String, val fingerprint: String, val pairingId: String,
    val token: String, val issuedAt: Double, val expiresAt: Double) {
    fun json(): JsonObject = buildJsonObject {
        put("schema_version", "pairing-1"); put("pairing_id", pairingId); put("token", token)
        put("issued_at", issuedAt); put("expires_at", expiresAt)
    }
}

/** Pin is an explicit operator-approved trust anchor, never obtained through untrusted TLS. */
class PinnedTrustManager(expected: String) : X509TrustManager {
    private val fingerprint = PairingProtocol.pin(expected)
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("Client certificates are not supported.")
    }
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val cert = chain?.singleOrNull() ?: throw CertificateException("A pinned self-signed identity is required.")
        val actual = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        val expected = fingerprint.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        if (!MessageDigest.isEqual(actual, expected)) throw CertificateException("Certificate fingerprint differs.")
        cert.checkValidity()
        cert.verify(cert.publicKey)
        val usages = cert.extendedKeyUsage
        if (usages == null || "1.3.6.1.5.5.7.3.1" !in usages) throw CertificateException("Server authentication is required.")
        // HttpsURLConnection still performs its default hostname/IP verification.
    }
}
