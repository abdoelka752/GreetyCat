package org.greedycat.intelligence.connectivity

import java.net.Proxy
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

class PairingRejected : Exception("The PC rejected this invitation or credential.")

class PairingClient {
    suspend fun pair(address: String, fingerprint: String, invitation: String): PairingCredential = withContext(Dispatchers.IO) {
        val base = PairingProtocol.address(address)
        val pin = PairingProtocol.pin(fingerprint)
        require(PairingProtocol.secret.matches(invitation))
        val result = request(base, pin, "exchange", "POST", body = buildJsonObject { put("invitation", invitation) }.toString())
        PairingProtocol.credential(result, base, pin)
    }
    suspend fun probe(record: PairingCredential) = withContext(Dispatchers.IO) {
        session(record, "GET", "PAIRED")
    }
    suspend fun revoke(record: PairingCredential) = withContext(Dispatchers.IO) {
        session(record, "DELETE", "REVOKED")
    }
    private fun session(record: PairingCredential, method: String, expectedStatus: String) {
        PairingProtocol.validate(record)
        val json = request(record.address, record.fingerprint, "session", method, token = record.token)
        require(json.keys == setOf("schema_version", "pairing_id", "issued_at", "expires_at", "status"))
        require(json.getValue("schema_version").jsonPrimitive.content == "pairing-1")
        require(json.getValue("pairing_id").jsonPrimitive.content == record.pairingId)
        require(json.getValue("status").jsonPrimitive.content == expectedStatus)
        require(json.getValue("issued_at").jsonPrimitive.double == record.issuedAt)
        require(json.getValue("expires_at").jsonPrimitive.double == record.expiresAt)
    }
    private fun request(address: String, fingerprint: String, route: String, method: String,
        body: String? = null, token: String? = null): JsonObject {
        val deadline = System.nanoTime() + 12_000_000_000L
        val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(PinnedTrustManager(fingerprint)), null) }
        val connection = URL("$address/api/pairing/v1/$route").openConnection(Proxy.NO_PROXY) as HttpsURLConnection
        try {
            connection.sslSocketFactory = PairingTls(tls.socketFactory)
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.requestMethod = method
            connection.setRequestProperty("Accept", "application/json")
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            body?.let {
                val bytes = it.toByteArray(Charsets.UTF_8)
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { output -> output.write(bytes) }
            }
            val code = connection.responseCode
            if (code == 401) throw PairingRejected()
            require(code == 200) { "The PC returned an unexpected response." }
            require(connection.contentType?.substringBefore(';') == "application/json")
            val bytes = connection.inputStream.use { it.pairingBytes(deadline) }
            return Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        } finally { connection.disconnect() }
    }
}
