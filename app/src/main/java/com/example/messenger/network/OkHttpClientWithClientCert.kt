package com.example.messenger.network

import android.content.Context
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Создаёт OkHttpClient, который:
 * - использует client.p12 (PKCS#12) как client cert + private key
 * - доверяет CA из ca.pem
 *
 * DEV only: client.p12 находится в res/raw и содержит приватный ключ — не использовать в production.
 */
fun buildOkHttpWithClientP12(
    context: Context,
    clientP12ResId: Int,
    clientP12Password: String,
    caPemResId: Int
): OkHttpClient {
    // Load client PKCS12 (client cert + private key)
    val clientKeyStore = KeyStore.getInstance("PKCS12")
    context.resources.openRawResource(clientP12ResId).use { input ->
        clientKeyStore.load(input, clientP12Password.toCharArray())
    }

    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
    kmf.init(clientKeyStore, clientP12Password.toCharArray())
    val keyManagers = kmf.keyManagers

    // Load CA (trust anchor)
    val caKeyStore = KeyStore.getInstance(KeyStore.getDefaultType())
    caKeyStore.load(null, null)
    val cf = CertificateFactory.getInstance("X.509")
    context.resources.openRawResource(caPemResId).use { caInput ->
        val ca = cf.generateCertificate(caInput)
        caKeyStore.setCertificateEntry("local_ca", ca)
    }

    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    tmf.init(caKeyStore)
    val trustManagers = tmf.trustManagers
    val x509Tm = trustManagers.filterIsInstance<X509TrustManager>().first()

    // Init SSLContext with client KeyManagers and TrustManagers trusting the CA
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(keyManagers, arrayOf(x509Tm), SecureRandom())

    return OkHttpClient.Builder()
        .sslSocketFactory(sslContext.socketFactory, x509Tm)
        .hostnameVerifier { hostname, session ->
            // Для dev: проверяем hostname корректно (CN/SAN сертификата должен соответствовать hostname)
            javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)
        }
        .build()
}