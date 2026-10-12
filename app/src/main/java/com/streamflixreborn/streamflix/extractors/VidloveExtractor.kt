package com.streamflixreborn.streamflix.extractors

import com.streamflixreborn.streamflix.models.Video
import androidx.media3.common.MimeTypes
import okhttp3.OkHttpClient
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url

class VidloveExtractor : Extractor() {
    override val name = "Vidlove"
    override val mainUrl = "https://api.vidlove.cc"

    data class ServerConfig(
        val name: String,
        val sourceKey: String,
        val isHevc: Boolean = false
    )

    private val serversConfig = listOf(
        ServerConfig("Barbarian King 2.0", "moviebox2", isHevc = true),
        ServerConfig("Archer Queen", "vidapi"),
        ServerConfig("Mega Knight", "megaknight"),
        ServerConfig("Grand Warden", "warden"),
        ServerConfig("P.E.K.K.A", "cinefreak"),
        ServerConfig("Royal Champion", "ipcloud"),
        ServerConfig("Ice Wizard", "tcloud")
    )

    fun servers(videoType: Video.Type): List<Video.Server> {
        return serversConfig.map { config ->
            val hevcParam = if (config.isHevc) "&hevc=1" else ""
            val url = when (videoType) {
                is Video.Type.Movie -> {
                    "$mainUrl/movie?id=${videoType.id}&mode=json&sources=${config.sourceKey}$hevcParam"
                }
                is Video.Type.Episode -> {
                    "$mainUrl/tv?id=${videoType.tvShow.id}&season=${videoType.season.number}&episode=${videoType.number}&mode=json&sources=${config.sourceKey}$hevcParam"
                }
            }
            
            Video.Server(
                id = "${config.name} (Vidlove)",
                name = "${config.name} (Vidlove)",
                src = url
            )
        }
    }

    override suspend fun extract(link: String): Video {
        val service = Service.build(mainUrl)
        val jsonString = service.get(link)
        val json = JSONObject(jsonString)

        val sourceObj = json.optJSONObject("source") ?: throw Exception("No video source found")
        val url = sourceObj.optString("url")
        if (url.isNullOrEmpty()) {
            throw Exception("No video source found")
        }

        val subtitlesList = mutableListOf<Video.Subtitle>()
        val subtitlesArray = json.optJSONArray("subtitles")
        if (subtitlesArray != null) {
            for (i in 0 until subtitlesArray.length()) {
                val sub = subtitlesArray.optJSONObject(i) ?: continue
                val label = sub.optString("label", "Unknown")
                val file = sub.optString("file")
                if (file.isNotEmpty()) {
                    subtitlesList.add(Video.Subtitle(label, file))
                }
            }
        }

        return Video(
            source = url,
            type = MimeTypes.APPLICATION_M3U8,
            subtitles = subtitlesList,
            headers = mapOf("Referer" to "https://player.vidlove.cc/")
        )
    }

    private interface Service {
        companion object {
            fun build(baseUrl: String): Service {
                val client = OkHttpClient.Builder().build()
                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(ScalarsConverterFactory.create())
                    .client(client)
                    .build()

                return retrofit.create(Service::class.java)
            }
        }

        @GET
        suspend fun get(
            @Url url: String
        ): String
    }
}
