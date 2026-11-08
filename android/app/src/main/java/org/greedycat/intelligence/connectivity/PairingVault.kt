package org.greedycat.intelligence.connectivity

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.json.*

/** AES-GCM file with a non-exportable AndroidKeyStore key; no backup or plaintext fallback. */
class PairingVault(context: Context, name: String = "pc-pairing-v1") {
    init { require(name.matches(Regex("[a-z0-9-]{1,80}"))) }
    private val alias = "org.greedycat.intelligence.$name"
    private val file = AtomicFile(File(context.noBackupFilesDir, "$name.enc"))
    private val aad = "greedycat-pairing-v1".toByteArray()
    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        check(create) { "Pairing encryption key is unavailable." }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
            generateKey()
        }
    }
    @Synchronized fun save(record: PairingCredential) {
        PairingProtocol.validate(record)
        val json = buildJsonObject {
            put("address", record.address); put("fingerprint", record.fingerprint); put("credential", record.json())
        }.toString().toByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(true)); updateAAD(aad) }
        val bytes = byteArrayOf(1) + cipher.iv + cipher.doFinal(json)
        check(cipher.iv.size == 12 && bytes.size <= 4096)
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
    }
    @Synchronized fun load(): PairingCredential? {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
        val bytes = file.openRead().use { it.pairingBytes() }
        require(bytes.size in 30..4096 && bytes[0].toInt() == 1)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, bytes.copyOfRange(1, 13))); updateAAD(aad)
        }
        val json = Json.parseToJsonElement(cipher.doFinal(bytes.copyOfRange(13, bytes.size)).toString(Charsets.UTF_8)).jsonObject
        require(json.keys == setOf("address", "fingerprint", "credential"))
        return PairingProtocol.credential(json.getValue("credential").jsonObject,
            json.getValue("address").jsonPrimitive.content, json.getValue("fingerprint").jsonPrimitive.content)
    }
    @Synchronized fun forget() { file.delete(); check(!file.baseFile.exists()) }
}
