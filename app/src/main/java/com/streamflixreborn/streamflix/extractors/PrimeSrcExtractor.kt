package com.streamflixreborn.streamflix.extractors

import android.net.Uri
import com.streamflixreborn.streamflix.StreamFlixApp
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.WebViewResolver
import com.streamflixreborn.streamflix.utils.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONTokener
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url

class PrimeSrcExtractor : Extractor() {

    override val name = "PrimeSrc"
    override val mainUrl = "https://primesrc.me"

    suspend fun servers(videoType: Video.Type): List<Video.Server> {
        val apiUrl = when (videoType) {
            is Video.Type.Episode ->
                "$mainUrl/api/v1/s?tmdb=${videoType.tvShow.id}" +
                    "&season=${videoType.season.number}" +
                    "&episode=${videoType.number}&type=tv"

            is Video.Type.Movie ->
                "$mainUrl/api/v1/s?tmdb=${videoType.id}&type=movie"
        }

        return try {
            val service = Service.build(mainUrl)
            val serversResponse = service.getServers(apiUrl)

            val nameCount = mutableMapOf<String, Int>()

            serversResponse.servers.map { server ->
                val count =
                    nameCount.getOrDefault(server.name, 0) + 1

                nameCount[server.name] = count

                val suffix =
                    if (count > 1) " $count" else ""

                val displayName =
                    "${server.name}$suffix (PrimeSrc)"

                /*
                 * PRIMESRC_WEBVIEW_TURNSTILE_V1
                 *
                 * Keep the exact PrimeSrc key in the fragment.
                 * PrimeSrc itself ignores the fragment, while extract()
                 * can still try the old native /api/v1/l route first.
                 *
                 * If that route returns the current Cloudflare/Turnstile
                 * 403, the normal PrimeSrc embed page is allowed to perform
                 * its own browser verification and create the final iframe.
                 */
                val embedUrl = when (videoType) {
                    is Video.Type.Movie ->
                        buildString {
                            append("$mainUrl/embed/movie")
                            append("?tmdb=")
                            append(Uri.encode(videoType.id.toString()))
                            append("&fallback=false")
                            append("&serverOrder=")
                            append(Uri.encode(server.name))
                        }

                    is Video.Type.Episode ->
                        buildString {
                            append("$mainUrl/embed/tv")
                            append("?tmdb=")
                            append(
                                Uri.encode(
                                    videoType.tvShow.id.toString()
                                )
                            )
                            append("&season=")
                            append(
                                Uri.encode(
                                    videoType.season.number.toString()
                                )
                            )
                            append("&episode=")
                            append(
                                Uri.encode(
                                    videoType.number.toString()
                                )
                            )
                            append("&fallback=false")
                            append("&serverOrder=")
                            append(Uri.encode(server.name))
                        }
                }

                Video.Server(
                    id =
                        "${server.name}-${server.key} (PrimeSrc)",
                    name = displayName,
                    src =
                        "$embedUrl#streamflixKey=" +
                            Uri.encode(server.key),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun server(videoType: Video.Type): Video.Server {
        return servers(videoType).first()
    }

    override suspend fun extract(link: String): Video {
        /*
         * First keep the old/native path.
         *
         * If PrimeSrc ever removes the challenge again, this remains
         * the fastest path and preserves the exact selected duplicate.
         */
        val parsed = Uri.parse(link)

        val key =
            parsed.fragment
                ?.substringAfter(
                    "streamflixKey=",
                    "",
                )
                ?.takeIf { it.isNotBlank() }
                ?.let(Uri::decode)

        if (!key.isNullOrBlank()) {
            val directApi =
                "$mainUrl/api/v1/l?key=${Uri.encode(key)}"

            /*
             * PRIMESRC_SHARED_SESSION_V2
             *
             * PrimeSrc /api/v1/l may return a Cloudflare 403 until its
             * browser session has been verified.
             *
             * Use Streamflix's shared NetworkClient here so WebView and
             * OkHttp see the same CookieManager session/cf_clearance.
             *
             * If the native request is still rejected, return null and
             * continue into the existing PrimeSrc WebView flow below.
             */
            val nativeResult =
                try {
                    resolvePrimeSrcLinkNative(directApi)
                } catch (_: Exception) {
                    null
                }

            nativeResult
                ?.takeIf {
                    it.isNotBlank() &&
                        !it.startsWith(
                            mainUrl,
                            ignoreCase = true,
                        )
                }
                ?.let { resolved ->
                    return Extractor.extract(resolved)
                }
        }

        /*
         * Current PrimeSrc behavior:
         *
         * /api/v1/l?key=...
         *   -> HTTP 403
         *   -> browser Turnstile
         *   -> PrimeSrc retries with token
         *   -> external player iframe
         *
         * Let the real PrimeSrc page perform that flow instead of trying
         * to reproduce or bypass its challenge ourselves.
         */
        return extractWithPrimeSrcWebView(
            link.substringBefore('#')
        )
    }

    /*
     * Native PrimeSrc link lookup using the same browser identity and
     * CookieManager-backed CookieJar as WebViewResolver.
     *
     * Observed PrimeSrc flow:
     *
     *   /api/v1/l?key=...
     *        -> 200 JSON { link: ... }
     *
     * or:
     *
     *   /api/v1/l?key=...
     *        -> 403 Cloudflare
     *        -> fall through to WebView
     *        -> PrimeSrc solves challenge/token itself
     *        -> external player iframe
     */
    private suspend fun resolvePrimeSrcLinkNative(
        apiUrl: String,
    ): String? =
        withContext(Dispatchers.IO) {
            val request =
                Request.Builder()
                    .url(apiUrl)
                    .header(
                        "User-Agent",
                        NetworkClient.USER_AGENT,
                    )
                    .header(
                        "Accept",
                        "application/json, text/plain, */*",
                    )
                    .header(
                        "Accept-Language",
                        "en-US,en;q=0.9",
                    )
                    /*
                     * PrimeSrc's own browser request currently originates
                     * from its service-worker context.
                     */
                    .header(
                        "Referer",
                        "$mainUrl/sw.js",
                    )
                    .header(
                        "Origin",
                        mainUrl,
                    )
                    .header(
                        "X-Requested-With",
                        StreamFlixApp.instance.packageName,
                    )
                    .header(
                        "Sec-Fetch-Dest",
                        "empty",
                    )
                    .header(
                        "Sec-Fetch-Mode",
                        "cors",
                    )
                    .header(
                        "Sec-Fetch-Site",
                        "same-origin",
                    )
                    .build()

            NetworkClient.default
                .newCall(request)
                .execute()
                .use { response ->
                    /*
                     * 403/503 deliberately do not throw here.
                     * They mean the browser/WebView path should take over.
                     */
                    if (!response.isSuccessful) {
                        return@withContext null
                    }

                    val body =
                        response.body
                            ?.string()
                            .orEmpty()

                    if (body.isBlank()) {
                        return@withContext null
                    }

                    val link =
                        runCatching {
                            JSONObject(body)
                                .optString("link")
                                .trim()
                        }.getOrNull()
                            .orEmpty()

                    link.takeIf {
                        it.startsWith("http://") ||
                            it.startsWith("https://")
                    }
                }
        }

    private suspend fun extractWithPrimeSrcWebView(
        embedUrl: String,
    ): Video {
        val resolver =
            WebViewResolver(StreamFlixApp.instance)

        val result =
            resolver.getResult(
                url = embedUrl,

                headers =
                    mapOf(
                        "Referer" to "$mainUrl/",
                    ),

                /*
                 * PrimeSrc uses external splash/ad links in the main frame.
                 * Do not allow those to replace the PrimeSrc embed page.
                 *
                 * External subframes stay allowed because the actual
                 * playback host is loaded as an iframe.
                 */
                shouldAllowNavigation = { targetUrl, isMainFrame ->
                    if (!isMainFrame) {
                        true
                    } else if (targetUrl == "about:blank") {
                        true
                    } else {
                        val host =
                            runCatching {
                                Uri.parse(targetUrl)
                                    .host
                                    .orEmpty()
                                    .lowercase()
                            }.getOrDefault("")

                        host == "primesrc.me" ||
                            host.endsWith(".primesrc.me")
                    }
                },

                completion = { _, html, _ ->
                    html.contains(
                        "data-streamflix-primesrc-ready=\"1\"",
                        ignoreCase = true,
                    ) ||
                        html.contains(
                            "data-streamflix-primesrc-ready='1'",
                            ignoreCase = true,
                        )
                },

                valueScript =
                    """
                    (function() {
                        return (
                            window.__streamflixPrimeSrcCandidate ||
                            ""
                        );
                    })();
                    """.trimIndent(),

                pageReadyScriptProvider = { _, _, _ ->
                    """
                    (function() {
                        try {
                            /*
                             * PrimeSrc currently puts advertising destinations
                             * on the splash/play anchors.
                             *
                             * Keep React's onClick handler but prevent the
                             * browser from leaving PrimeSrc.
                             */
                            document
                                .querySelectorAll(
                                    "a.spla, a.splash2"
                                )
                                .forEach(function(anchor) {
                                    anchor.setAttribute(
                                        "href",
                                        "javascript:void(0)"
                                    );

                                    anchor.setAttribute(
                                        "target",
                                        "_self"
                                    );
                                });

                            const frames = Array.from(
                                document.querySelectorAll(
                                    "iframe[src]"
                                )
                            );

                            let candidate = "";

                            for (const frame of frames) {
                                const raw =
                                    frame.src ||
                                    frame.getAttribute("src") ||
                                    "";

                                if (!raw) continue;

                                let url;

                                try {
                                    url =
                                        new URL(
                                            raw,
                                            window.location.href
                                        ).href;
                                } catch (_) {
                                    continue;
                                }

                                const lower =
                                    url.toLowerCase();

                                /*
                                 * Ignore PrimeSrc itself and verification
                                 * iframes. What remains is the actual
                                 * external playback host.
                                 */
                                if (
                                    lower.includes(
                                        "challenges.cloudflare.com"
                                    ) ||
                                    lower.includes(
                                        "primesrc.me"
                                    ) ||
                                    lower.includes(
                                        "sorrowfulpsychology.com"
                                    )
                                ) {
                                    continue;
                                }

                                candidate = url;
                                break;
                            }

                            if (candidate) {
                                window.__streamflixPrimeSrcCandidate =
                                    candidate;

                                document.documentElement.setAttribute(
                                    "data-streamflix-primesrc-ready",
                                    "1"
                                );
                            }
                        } catch (_) {
                        }
                    })();
                    """.trimIndent()
                },

                /*
                 * PrimeSrc's Turnstile uses interaction-only mode.
                 * Therefore the WebView must be visible when verification
                 * is requested.
                 */
                showImmediately = true,
            )

        val resolved =
            decodeJavascriptValue(
                result.evaluatedValue
            )
                ?.trim()
                ?.takeIf {
                    it.startsWith("http://") ||
                        it.startsWith("https://")
                }
                ?: throw Exception(
                    "PrimeSrc WebView did not resolve an external player"
                )

        if (
            resolved.startsWith(
                mainUrl,
                ignoreCase = true,
            )
        ) {
            throw Exception(
                "PrimeSrc resolved back to itself: $resolved"
            )
        }

        return Extractor.extract(resolved)
    }

    private fun decodeJavascriptValue(
        value: String?,
    ): String? {
        val encoded =
            value
                ?.trim()
                ?.takeIf {
                    it.isNotBlank() &&
                        it != "null"
                }
                ?: return null

        return when (
            val decoded =
                runCatching {
                    JSONTokener(encoded)
                        .nextValue()
                }.getOrNull()
        ) {
            is String -> decoded

            null ->
                encoded.removeSurrounding("\"")

            else ->
                decoded.toString()
        }
    }

    private interface Service {
        companion object {
            fun build(baseUrl: String): Service {
                val client =
                    OkHttpClient.Builder()
                        .build()

                val retrofit =
                    Retrofit.Builder()
                        .baseUrl(baseUrl)
                        .client(client)
                        .addConverterFactory(
                            GsonConverterFactory.create()
                        )
                        .build()

                return retrofit.create(
                    Service::class.java
                )
            }
        }

        @GET
        suspend fun getServers(
            @Url url: String,
        ): ServersResponse

        @GET
        suspend fun getLink(
            @Url url: String,
        ): LinkResponse
    }

    data class ServersResponse(
        val servers: List<Server>,
    )

    data class Server(
        val name: String,
        val key: String,
    )

    data class LinkResponse(
        val link: String,
    )
}
