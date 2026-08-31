package com.example.omnicontrol.data.remote.atv

import com.example.omnicontrol.proto.polo.Configuration
import com.example.omnicontrol.proto.polo.Options
import com.example.omnicontrol.proto.polo.OuterMessage
import com.example.omnicontrol.proto.polo.PairingRequest
import com.example.omnicontrol.proto.polo.Secret
import com.google.protobuf.ByteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket

class AndroidTvPairingException(message: String) : Exception(message)

/**
 * Pairing handshake against an Android TV's Remote Service on port 6467 ("polo" protocol,
 * see polo.proto). This is a from-scratch Kotlin implementation, written by tracing the
 * message flow and the pairing-code hash algorithm documented in the Apache-2.0-licensed
 * https://github.com/tronikos/androidtvremote2 (a Python client for the same protocol) —
 * no code from that project is copied here, only the protocol behavior.
 *
 * Flow: connect() opens the TLS socket and negotiates through to configurationAck — this is
 * what makes the TV display a 6-digit code on screen. The socket is kept open; call
 * finishPairing() once the user has read that code off the TV.
 */
class AndroidTvPairingClient(
    private val ipAddress: String,
    private val identity: ClientIdentity,
    private val clientName: String = "OmniControl",
    private val port: Int = 6467
) {
    private var socket: SSLSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    suspend fun startPairing() = withContext(Dispatchers.IO) {
        val sslSocket = TvSslContextFactory.create(identity).createSocket(ipAddress, port) as SSLSocket
        sslSocket.soTimeout = 15_000
        sslSocket.startHandshake()
        socket = sslSocket
        input = sslSocket.inputStream
        output = sslSocket.outputStream

        send(
            outerMessage()
                .setPairingRequest(
                    PairingRequest.newBuilder()
                        .setServiceName("atvremote")
                        .setClientName(clientName)
                )
        )
        expect(receive(), "pairing request") { it.hasPairingRequestAck() }

        send(
            outerMessage()
                .setOptions(
                    Options.newBuilder()
                        .setPreferredRole(Options.RoleType.ROLE_TYPE_INPUT)
                        .addInputEncodings(hexEncoding())
                )
        )
        expect(receive(), "pairing options") { it.hasOptions() }

        send(
            outerMessage()
                .setConfiguration(
                    Configuration.newBuilder()
                        .setClientRole(Options.RoleType.ROLE_TYPE_INPUT)
                        .setEncoding(hexEncoding())
                )
        )
        expect(receive(), "pairing configuration") { it.hasConfigurationAck() }
        // The TV is now showing a 6-digit pairing code on its screen.
    }

    suspend fun finishPairing(pairingCode: String) = withContext(Dispatchers.IO) {
        val code = pairingCode.trim().uppercase()
        require(code.length == 6) { "Pairing code must be 6 characters" }
        require(code.all { it.isDigit() || it in 'A'..'F' }) { "Pairing code must be hexadecimal" }

        val sslSocket = socket ?: throw AndroidTvPairingException("Pairing was not started")
        val serverCert = sslSocket.session.peerCertificates.firstOrNull() as? X509Certificate
            ?: throw AndroidTvPairingException("Could not read the TV's certificate")

        val hash = computeSecretHash(identity.certificate, serverCert, code)
        val expectedFirstByte = code.substring(0, 2).toInt(16)
        if ((hash[0].toInt() and 0xFF) != expectedFirstByte) {
            throw AndroidTvPairingException(
                "That code doesn't match what this TV expects — double-check the digits shown on screen"
            )
        }

        send(outerMessage().setSecret(Secret.newBuilder().setSecret(ByteString.copyFrom(hash))))
        expect(receive(), "pairing secret") { it.hasSecretAck() }
    }

    fun close() {
        runCatching { socket?.close() }
        socket = null
        input = null
        output = null
    }

    private fun outerMessage(): OuterMessage.Builder =
        OuterMessage.newBuilder()
            .setProtocolVersion(2)
            .setStatus(OuterMessage.Status.STATUS_OK)

    private fun hexEncoding(): Options.Encoding.Builder =
        Options.Encoding.newBuilder()
            .setType(Options.Encoding.EncodingType.ENCODING_TYPE_HEXADECIMAL)
            .setSymbolLength(6)

    private fun send(message: OuterMessage.Builder) {
        val out = output ?: throw AndroidTvPairingException("Not connected")
        message.build().writeDelimitedTo(out)
        out.flush()
    }

    private fun receive(): OuterMessage? {
        val inp = input ?: throw AndroidTvPairingException("Not connected")
        val msg = OuterMessage.parseDelimitedFrom(inp) ?: return null
        if (msg.status != OuterMessage.Status.STATUS_OK) {
            throw AndroidTvPairingException("TV reported a pairing error (${msg.status})")
        }
        return msg
    }

    private inline fun expect(msg: OuterMessage?, step: String, predicate: (OuterMessage) -> Boolean) {
        if (msg == null) throw AndroidTvPairingException("TV closed the connection during $step")
        if (!predicate(msg)) throw AndroidTvPairingException("Unexpected reply during $step")
    }

    /**
     * Reproduces the pairing-code hash the TV expects: SHA-256 of the client's and server's
     * RSA public key (modulus + exponent, as minimal big-endian hex with the exponent
     * zero-padded to an even digit count) followed by the last two bytes of the pairing code.
     */
    private fun computeSecretHash(clientCert: X509Certificate, serverCert: X509Certificate, pairingCode: String): ByteArray {
        val clientKey = clientCert.publicKey as RSAPublicKey
        val serverKey = serverCert.publicKey as RSAPublicKey

        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(hexToBytes(clientKey.modulus.toString(16)))
        digest.update(hexToBytes("0" + clientKey.publicExponent.toString(16)))
        digest.update(hexToBytes(serverKey.modulus.toString(16)))
        digest.update(hexToBytes("0" + serverKey.publicExponent.toString(16)))
        digest.update(hexToBytes(pairingCode.substring(2)))
        return digest.digest()
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = if (hex.length % 2 != 0) "0$hex" else hex
        return ByteArray(clean.length / 2) { i ->
            ((Character.digit(clean[i * 2], 16) shl 4) + Character.digit(clean[i * 2 + 1], 16)).toByte()
        }
    }
}
