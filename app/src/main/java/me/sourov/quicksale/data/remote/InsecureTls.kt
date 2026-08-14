package me.sourov.quicksale.data.remote

import okhttp3.OkHttpClient
import java.net.HttpURLConnection
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Skips TLS certificate checks for stores that can't present a chain Android will accept.
 *
 * A store on a self-signed certificate, an internal CA, or a chain rooted somewhere the device
 * predates fails the handshake with "Chain validation failed", and nothing else in the app gets a
 * chance to run — the till simply cannot see the store. That is the normal state of a staging site,
 * and it is what happened to the rugged terminals when the shop's certificate was reissued under
 * Let's Encrypt's newer roots, which their Android is too old to carry.
 *
 * So [me.sourov.quicksale.data.settings.StoreSettings] carries a switch, defaulted on because the
 * fleet needs it on and a till that can't reach the store is worth nothing. What it costs is
 * precise: the traffic is still TLS and still encrypted, but the app no longer knows *who* it is
 * encrypted to, so anything on the path can present its own certificate and read the consumer key
 * and secret. Turning it off restores ordinary validation for a store that doesn't need the
 * exemption.
 *
 * Two entry points, because two stacks fetch from the store. [applyTo] covers the REST calls
 * ([WooHttp] and its `HttpURLConnection`); [imageOkHttpClient] covers Coil, which would otherwise
 * fail every product photo on the same handshake. The REST side is told per request from the store
 * settings it already holds — so an unsaved switch is honoured by "Test connection" — while the
 * image client is built once and reads [allowed], which [me.sourov.quicksale.AppContainer] keeps in
 * step with the saved setting.
 */
@Suppress("CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier")
object InsecureTls {

    /** Mirrors the saved `allowInsecureTls` setting for the stack that can't be told per request. */
    @Volatile
    var allowed: Boolean = true

    private val trustEverything = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private val acceptAnyHostname = HostnameVerifier { _, _ -> true }

    private val trustEverythingSockets: SSLSocketFactory by lazy {
        SSLContext.getInstance("TLS")
            .apply { init(null, arrayOf<TrustManager>(trustEverything), SecureRandom()) }
            .socketFactory
    }

    /** Drops certificate and hostname checks for this one connection. */
    fun applyTo(connection: HttpURLConnection) {
        if (connection !is HttpsURLConnection) return
        connection.sslSocketFactory = trustEverythingSockets
        connection.hostnameVerifier = acceptAnyHostname
    }

    /**
     * The platform's own trust manager, used whenever [allowed] is false.
     *
     * Kept so the image client can be built once and still validate normally: without a delegate to
     * fall back on, turning the switch off would leave images unverified until the app restarted.
     */
    private val platformTrustManager: X509TrustManager by lazy {
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(null as KeyStore?) }
            .trustManagers
            .filterIsInstance<X509TrustManager>()
            .first()
    }

    /** Validates unless [allowed] is set, so the choice is made at handshake time, not at build time. */
    private val toggledTrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            if (!allowed) platformTrustManager.checkClientTrusted(chain, authType)
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            if (!allowed) platformTrustManager.checkServerTrusted(chain, authType)
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> =
            platformTrustManager.acceptedIssuers
    }

    private val toggledSockets: SSLSocketFactory by lazy {
        SSLContext.getInstance("TLS")
            .apply { init(null, arrayOf<TrustManager>(toggledTrustManager), SecureRandom()) }
            .socketFactory
    }

    /** The client Coil loads product images with — see [me.sourov.quicksale.QuickSaleApplication]. */
    fun imageOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .sslSocketFactory(toggledSockets, toggledTrustManager)
        .hostnameVerifier { hostname, session ->
            allowed || HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)
        }
        .build()
}

/**
 * True when [error], or something it wraps, is the handshake refusing an untrusted certificate.
 *
 * Android words this several ways depending on where the chain broke — "Chain validation failed",
 * "Trust anchor for certification path not found" — and buries the certificate exception under an
 * `SSLHandshakeException`, so the cause chain is walked rather than the message matched. The depth
 * limit is only there so a self-referencing cause can't spin.
 */
internal fun isCertificateTrustFailure(error: Throwable): Boolean =
    generateSequence(error) { current -> current.cause?.takeIf { it !== current } }
        .take(8)
        .any { it is SSLException || it is CertificateException }
