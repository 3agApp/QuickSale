package me.sourov.quicksale.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException

class CertificateTrustFailureTest {

    @Test
    fun `recognises the chain failure android reports for an untrusted store`() {
        // The shape Conscrypt throws: the certificate exception is two levels down, and the
        // message on top ("Chain validation failed") is the only thing a caller would see.
        val error = SSLHandshakeException("Chain validation failed").apply {
            initCause(
                CertificateException("Chain validation failed").apply {
                    initCause(CertPathValidatorException("Trust anchor for certification path not found."))
                }
            )
        }
        assertTrue(isCertificateTrustFailure(error))
    }

    @Test
    fun `recognises a bare certificate exception`() {
        assertTrue(isCertificateTrustFailure(CertificateException("expired")))
    }

    @Test
    fun `leaves ordinary network failures alone`() {
        assertFalse(isCertificateTrustFailure(UnknownHostException("dev1.example.test")))
        assertFalse(isCertificateTrustFailure(SocketTimeoutException("timeout")))
        assertFalse(isCertificateTrustFailure(IOException("unexpected end of stream")))
    }

    @Test
    fun `does not spin on a cause that points at itself`() {
        val error = object : IOException("looping") {
            override val cause: Throwable get() = this
        }
        assertFalse(isCertificateTrustFailure(error))
    }
}
