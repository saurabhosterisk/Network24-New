package com.network24.player.core.api

import com.network24.player.core.api.ApiService
import okhttp3.OkHttpClient
import okhttp3.ConnectionSpec
import okhttp3.Dns
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object ApiClient {
    private val apiCache = ConcurrentHashMap<String, ApiService>()

    fun get(baseUrl: String): ApiService {
        return apiCache.getOrPut(baseUrl) {
            // Log level BODY bada data print karta hai logcat mein.
            //API ke call ko log me dekhne ke liye !important
            // Agar app slow ho toh isko 'Level.BASIC' ya 'Level.NONE' kar sakte hain.
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.NONE
            }

            val client = OkHttpClient.Builder()
                // 🔥 Timeout ko 20 seconds se badha kar 90 seconds kar diya hai
                .connectTimeout(90, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(90, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                // Fire OS 6 devices can prefer a broken IPv6 route even when
                // the IPTV host is reachable over IPv4.
                .dns(object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> {
                        val addresses = Dns.SYSTEM.lookup(hostname)
                        val ipv4 = addresses.filterIsInstance<Inet4Address>()
                        return if (ipv4.isNotEmpty()) ipv4 else addresses
                    }
                })
                .connectionSpecs(
                    listOf(
                        ConnectionSpec.CLEARTEXT,
                        ConnectionSpec.COMPATIBLE_TLS,
                        ConnectionSpec.MODERN_TLS
                    )
                )
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android) N24Player")
                            .build()
                    )
                }
                .addInterceptor(logging)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            retrofit.create(ApiService::class.java)
        }
    }

    fun clear() {
        apiCache.clear()
    }
}
