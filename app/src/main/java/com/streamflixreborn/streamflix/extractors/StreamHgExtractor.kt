package com.streamflixreborn.streamflix.extractors

import com.streamflixreborn.streamflix.models.Video
import java.net.URI

/**
 * STREAMHG_ROTATING_BRIDGE_V10
 *
 * hgcloud.to is a JavaScript bridge for the StreamHG family. The same embed id
 * is served in parallel by multiple StreamHG frontends. Their player markup is
 * compatible with StreamWishExtractor, so preserve the embed path and try the
 * known frontends in order.
 *
 * Example:
 *   https://hgcloud.to/e/683dv982vg7d
 *   -> https://hanerix.com/e/683dv982vg7d
 *   -> https://audinifer.com/e/683dv982vg7d
 *   -> https://vibuxer.com/e/683dv982vg7d
 */
class StreamHgExtractor : Extractor() {

    override val name = "StreamHG"
    override val mainUrl = "https://hgcloud.to"
    override val aliasUrls = STREAMHG_FRONTENDS

    override suspend fun extract(link: String): Video {
        val uri = runCatching { URI(link) }
            .getOrElse { throw Exception("Invalid StreamHG URL: $link", it) }

        val host = uri.host.orEmpty()
            .lowercase()
            .removePrefix("www.")

        val pathAndQuery = buildString {
            append(uri.rawPath.orEmpty().ifBlank { "/" })
            if (!uri.rawQuery.isNullOrBlank()) {
                append('?')
                append(uri.rawQuery)
            }
        }

        val candidates = when {
            host == "hgcloud.to" || host.endsWith(".hgcloud.to") ->
                STREAMHG_FRONTENDS.map { base -> base + pathAndQuery }

            STREAMHG_FRONTENDS.any { base ->
                host == URI(base).host.orEmpty().lowercase().removePrefix("www.")
            } -> {
                val currentBase = "${uri.scheme ?: "https"}://${uri.host}"
                listOf(link) + STREAMHG_FRONTENDS
                    .filterNot { it.equals(currentBase, ignoreCase = true) }
                    .map { it + pathAndQuery }
            }

            else -> listOf(link)
        }.distinct()

        var lastError: Throwable? = null

        for (candidate in candidates) {
            try {
                // StreamHG uses the packed JWPlayer family already handled by
                // StreamWishExtractor. This class only adds the rotating bridge.
                return StreamWishExtractor().extract(candidate)
            } catch (error: Throwable) {
                lastError = error
            }
        }

        throw Exception(
            "StreamHG extraction failed for ${candidates.joinToString()}",
            lastError,
        )
    }

    private companion object {
        val STREAMHG_FRONTENDS = listOf(
            "https://hanerix.com",
            "https://audinifer.com",
            "https://vibuxer.com",
        )
    }
}
