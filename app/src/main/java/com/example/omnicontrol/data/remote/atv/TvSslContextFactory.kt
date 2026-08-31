package com.example.omnicontrol.data.remote.atv

import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.net.Socket
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager

/**
 * Builds the SSLSocketFactory used for both the pairing connection (port 6467) and the
 * persistent remote connection (port 6466). Both connections present the same client
 * certificate and both trust the TV's certificate unconditionally — Android TVs use a
 * self-signed cert for this local protocol, there's no public CA to validate against, and
 * this is exactly what the reference client implementations (the official Google TV app
 * included) do for this specific protocol.
 */
object TvSslContextFactory {

    fun create(identity: ClientIdentity): SSLSocketFactory {
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(identity.keyStore, null)
        val delegate = keyManagerFactory.keyManagers
            .filterIsInstance<X509ExtendedKeyManager>()
            .firstOrNull()
            ?: error("No X509 key manager available for client certificate")

        val keyManager = SingleAliasKeyManager(identity.alias, delegate)
        val trustManager = TrustAllX509TrustManager

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(arrayOf<KeyManager>(keyManager), arrayOf<TrustManager>(trustManager), null)
        return sslContext.socketFactory
    }
}

/**
 * AndroidKeyStore may hold other app keys besides ours. Wrapping the delegate ensures the
 * handshake always offers our specific alias instead of leaving the choice to whatever the
 * default X509KeyManager picks first.
 */
private class SingleAliasKeyManager(
    private val alias: String,
    private val delegate: X509ExtendedKeyManager
) : X509ExtendedKeyManager() {
    override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?): String = alias
    override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: javax.net.ssl.SSLEngine?): String = alias
    override fun getCertificateChain(alias: String?): Array<X509Certificate>? = delegate.getCertificateChain(this.alias)
    override fun getPrivateKey(alias: String?): PrivateKey? = delegate.getPrivateKey(this.alias)
    override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String> = arrayOf(alias)
    override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
    override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
}

private object TrustAllX509TrustManager : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
}
