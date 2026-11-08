package org.greedycat.intelligence.connectivity

import java.net.Proxy
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.greedycat.intelligence.data.SyncEvent

class PairingRejected : Exception("The PC rejected this invitation or credential.")

class PairingClient {
    suspend fun sendReview(record: PairingCredential, source: String, event: SyncEvent): SyncResponse = withContext(Dispatchers.IO) {
        PairingProtocol.validate(record)
        val result = request(record.address, record.fingerprint, "reviews", "POST", token = record.token,
            body = buildJsonObject {
                put("schema_version", "sync-1"); put("source_id", source); put("event_id", event.eventId)
                put("payload_sha256", event.sha256); put("payload_json", event.payload)
            }.toString(), sync = true)
        require(result.getValue("schema_version").jsonPrimitive.content == "sync-1")
        require(result.getValue("event_id").jsonPrimitive.content == event.eventId)
        require(result.getValue("payload_sha256").jsonPrimitive.content == event.sha256)
        when (result.getValue("status").jsonPrimitive.content) {
            "STORED", "REPLAY" -> SyncResponse(true)
            "CONFLICT" -> {
                val code = result.getValue("code").jsonPrimitive.content
                require(code in setOf("EVENT_CONFLICT", "ROUND_IDENTITY_CONFLICT", "ROUND_NUMBER_CONFLICT", "MISSING_PREDECESSOR", "REVISION_CONFLICT", "CORRECTION_TIME_CONFLICT", "EVIDENCE_IDENTITY_CONFLICT", "STORAGE_LIMIT", "INVALID_REVIEW"))
                SyncResponse(false, code, result.getValue("retryable").jsonPrimitive.boolean)
            }
            else -> error("Unexpected sync response.")
        }
    }
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
        body: String? = null, token: String? = null, sync: Boolean = false): JsonObject {
        val deadline = System.nanoTime() + 12_000_000_000L
        val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(PinnedTrustManager(fingerprint)), null) }
        val connection = URL("$address/api/${if (sync) "sync" else "pairing"}/v1/$route").openConnection(Proxy.NO_PROXY) as HttpsURLConnection
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
            require(code == 200 || sync && code in setOf(409, 422)) { "The PC returned an unexpected response." }
            require(connection.contentType?.substringBefore(';') == "application/json")
            val bytes = (if (code >= 400) connection.errorStream else connection.inputStream).use { it.pairingBytes(deadline) }
            val response = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
            if (sync) validateSyncHttpStatus(code, response["status"]?.jsonPrimitive?.content)
            return response
        } finally { connection.disconnect() }
    }
}
