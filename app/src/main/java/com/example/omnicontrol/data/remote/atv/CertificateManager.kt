package com.example.omnicontrol.data.remote.atv

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Calendar
import javax.security.auth.x500.X500Principal

/**
 * The Android TV Remote v2 protocol authenticates the client by a self-signed TLS certificate:
 * the same cert is presented every time, and each TV independently remembers which certs it has
 * paired with. We only need to generate this once per install; AndroidKeyStore both generates
 * the keypair and hands back a self-signed certificate for it, so no extra crypto library
 * (e.g. Bouncy Castle) is needed.
 */
object CertificateManager {

    private const val KEYSTORE_ALIAS = "omnicontrol_atv_remote_client"
    private const val PROVIDER = "AndroidKeyStore"

    @Synchronized
    fun getOrCreateClientIdentity(): ClientIdentity {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
            generateKeyPair()
        }
        val privateKey = keyStore.getKey(KEYSTORE_ALIAS, null) as PrivateKey
        val certificate = keyStore.getCertificate(KEYSTORE_ALIAS) as X509Certificate
        return ClientIdentity(keyStore, KEYSTORE_ALIAS, privateKey, certificate)
    }

    private fun generateKeyPair() {
        val notBefore = Calendar.getInstance()
        val notAfter = Calendar.getInstance().apply { add(Calendar.YEAR, 10) }

        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setKeySize(2048)
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .setCertificateSubject(X500Principal("CN=OmniControl"))
            .setCertificateSerialNumber(BigInteger.ONE)
            .setCertificateNotBefore(notBefore.time)
            .setCertificateNotAfter(notAfter.time)
            .build()

        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, PROVIDER)
        generator.initialize(spec)
        generator.generateKeyPair()
    }
}

data class ClientIdentity(
    val keyStore: KeyStore,
    val alias: String,
    val privateKey: PrivateKey,
    val certificate: X509Certificate
)
