package me.sourov.quicksale.data.remote

import okhttp3.OkHttpClient
import java.net.HttpURLConnection
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * QuickSale talks to the store over HTTPS without checking the certificate behind it.
 *
 * The stores this till is pointed at don't reliably present a chain Android will accept —
 * self-signed, an internal CA, or one that lapsed overnight — and the handshake fails before any
 * app code runs, so the till simply cannot see the store. "Chain validation failed" on a counter at
 * a trade fair is not a problem anyone can fix from the counter, so validation is off outright
 * rather than behind a setting someone has to find first.
 *
 * What that costs is specific, and worth being clear about: the traffic is still TLS and still
 * encrypted, but the app no longer knows *who* it is encrypted to. Anything on the network path can
 * present its own certificate and read the consumer key and secret in the request. Treat the store
 * credentials as exposed on any network you don't control.
 *
 * Two entry points, because two stacks fetch from the store: [applyTo] covers the REST calls and
 * their `HttpURLConnection` ([WooHttp]), [imageOkHttpClient] covers Coil, which would otherwise
 * fail every product photo on the same handshake the JSON just skipped.
 */
@Suppress("CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier")
object InsecureTls {

    private val trustEverything = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private val acceptAnyHostname = HostnameVerifier { _, _ -> true }

    private val socketFactory: SSLSocketFactory by lazy {
        SSLContext.getInstance("TLS")
            .apply { init(null, arrayOf<TrustManager>(trustEverything), SecureRandom()) }
            .socketFactory
    }

    /** Drops certificate and hostname checks for this connection. */
    fun applyTo(connection: HttpURLConnection) {
        if (connection !is HttpsURLConnection) return
        connection.sslSocketFactory = socketFactory
        connection.hostnameVerifier = acceptAnyHostname
    }

    /** The client Coil loads product images with — see [me.sourov.quicksale.QuickSaleApplication]. */
    fun imageOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .sslSocketFactory(socketFactory, trustEverything)
        .hostnameVerifier(acceptAnyHostname)
        .build()
}
