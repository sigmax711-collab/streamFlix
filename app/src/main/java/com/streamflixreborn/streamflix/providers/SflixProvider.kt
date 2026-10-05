package com.streamflixreborn.streamflix.providers

import android.util.Log
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.BuildConfig
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.extractors.Extractor
import com.streamflixreborn.streamflix.extractors.VidsrcRuExtractor
import com.streamflixreborn.streamflix.extractors.VideasyExtractor
import com.streamflixreborn.streamflix.models.Category
import com.streamflixreborn.streamflix.models.Episode
import com.streamflixreborn.streamflix.models.Genre
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.People
import com.streamflixreborn.streamflix.models.Season
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.DnsResolver
import com.streamflixreborn.streamflix.utils.UserPreferences
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

object SflixProvider : Provider {

    private const val DEFAULT_BASE_URL = "https://sflix.win/"

    override val baseUrl: String
        get() = UserPreferences.resolveProviderBaseUrl(
            name,
            DEFAULT_BASE_URL
        )
    override val name = "SFlix"
    override val logo = "https://img.sflix.to/xxrz/400x400/100/66/35/66356c25ce98cb12993249e21742b129/66356c25ce98cb12993249e21742b129.png"
    override val language = "en"

    private val service: SflixService
        get() = SflixService.build(baseUrl)


    override suspend fun getHome(): List<Category> {
        val document = service.getHome()

        val categories = mutableListOf<Category>()

        categories.add(
            Category(
                name = Category.FEATURED,
                list = document.select("div.swiper-wrapper > div.swiper-slide").map {
                    val id = it.selectFirst("a")
                        ?.attr("href")?: ""
                    val title = it.selectFirst("h2.film-title")
                        ?.text() ?: ""
                    val overview = it.selectFirst("p.sc-desc")
                        ?.text()
                    val info = it.select("div.sc-detail > div.scd-item").toInfo()
                    val poster = it.selectFirst("img.film-poster-img")
                        ?.attr("src")
                    val banner = it.selectFirst("div.slide-photo img")
                        ?.attr("src")

                    if (it.isMovie()) {
                        Movie(
                            id = id,
                            title = title,
                            overview = overview,
                            released = info.released,
                            quality = info.quality,
                            rating = info.rating,
                            poster = poster,
                            banner = banner,
                        )
                    } else {
                        TvShow(
                            id = id,
                            title = title,
                            overview = overview,
                            quality = info.quality,
                            rating = info.rating,
                            poster = poster,
                            banner = banner,

                            seasons = info.lastEpisode?.let { lastEpisode ->
                                listOf(
                                    Season(
                                        id = "",
                                        number = lastEpisode.season,

                                        episodes = listOf(
                                            Episode(
                                                id = "",
                                                number = lastEpisode.episode,
                                            )
                                        )
                                    )
                                )
                            } ?: listOf(),
                        )
                    }
                },
            )
        )

        categories.add(
            Category(
                name = "Trending Movies",
                list = document.select("div#trending-movies div.flw-item").map {
                    val info = it.select("div.film-detail > div.fd-infor > span").toInfo()

                    Movie(
                        id = it.selectFirst("a")
                            ?.attr("href")?: "",
                        title = it.selectFirst("h3.film-name")
                            ?.text() ?: "",
                        released = info.released,
                        quality = info.quality,
                        rating = info.rating,
                        poster = it.selectFirst("div.film-poster > img.film-poster-img")
                            ?.imageUrl(),
                    )
                },
            )
        )

        categories.add(
            Category(
                name = "Trending TV Shows",
                list = document.select("div#trending-tv div.flw-item").map {
                    val info = it.select("div.film-detail > div.fd-infor > span").toInfo()

                    TvShow(
                        id = it.selectFirst("a")
                            ?.attr("href")?: "",
                        title = it.selectFirst("h3.film-name")
                            ?.text() ?: "",
                        quality = info.quality,
                        rating = info.rating,
                        poster = it.selectFirst("div.film-poster > img.film-poster-img")
                            ?.imageUrl(),

                        seasons = info.lastEpisode?.let { lastEpisode ->
                            listOf(
                                Season(
                                    id = "",
                                    number = lastEpisode.season,

                                    episodes = listOf(
                                        Episode(
                                            id = "",
                                            number = lastEpisode.episode,
                                        )
                                    )
                                )
                            )
                        } ?: listOf()
                    )
                },
            )
        )

        categories.add(
            Category(
                name = "Latest Movies",
                list = document.select("section.section-id-02")
                    .find { it.selectFirst("h2.cat-heading")?.ownText() == "Latest Movies" }
                    ?.select("div.flw-item")
                    ?.map {
                        val info = it.select("div.film-detail > div.fd-infor > span").toInfo()

                        Movie(
                            id = it.selectFirst("a")
                                ?.attr("href")?: "",
                            title = it.selectFirst("h3.film-name")
                                ?.text() ?: "",
                            released = info.released,
                            quality = info.quality,
                            rating = info.rating,
                            poster = it.selectFirst("div.film-poster > img.film-poster-img")
                                ?.attr("data-src"),
                        )
                    } ?: listOf(),
            )
        )

        categories.add(
            Category(
                name = "Latest TV Shows",
                list = document.select("section.section-id-02")
                    .find { it.selectFirst("h2.cat-heading")?.ownText() == "Latest TV Shows" }
                    ?.select("div.flw-item")
                    ?.map {
                        val info = it.select("div.film-detail > div.fd-infor > span").toInfo()

                        TvShow(
                            id = it.selectFirst("a")
                                ?.attr("href")?: "",
                            title = it.selectFirst("h3.film-name")
                                ?.text() ?: "",
                            quality = info.quality,
                            rating = info.rating,
                            poster = it.selectFirst("div.film-poster > img.film-poster-img")
                                ?.attr("data-src"),

                            seasons = info.lastEpisode?.let { lastEpisode ->
                                listOf(
                                    Season(
                                        id = "",
                                        number = lastEpisode.season,

                                        episodes = listOf(
                                            Episode(
                                                id = "",
                                                number = lastEpisode.episode,
                                            )
                                        )
                                    )
                                )
                            } ?: listOf()
                        )
                    } ?: listOf(),
            )
        )

        return categories
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isEmpty()) {
            val document = service.getHome()

            val genres = document.select("div#sidebar_subs_genre li.nav-item a.nav-link")
                .map {
                    Genre(
                        id = it.attr("href")
                            .substringAfterLast("/"),
                        name = it.text(),
                    )
                }
                .sortedBy { it.name }

            return genres
        }

        val document = service.search(query.replace(" ", "-"), page)

        val results = document.select("div.flw-item").map {
            val id = it.selectFirst("a")
                ?.attr("href")?: ""
            val title = it.selectFirst("h2.film-name")
                ?.text() ?: ""
            val info = it.select("div.film-detail > div.fd-infor > span").toInfo()
            val poster = it.selectFirst("div.film-poster > img.film-poster-img")
                ?.attr("data-src")

            if (it.isMovie()) {
                Movie(
                    id = id,
                    title = title,
                    released = info.released,
                    quality = info.quality,
                    rating = info.rating,
                    poster = poster,
                )
            } else {
                TvShow(
                    id = id,
                    title = title,
                    quality = info.quality,
                    rating = info.rating,
                    poster = poster,

                    seasons = info.lastEpisode?.let { lastEpisode ->
                        listOf(
                            Season(
                                id = "",
                                number = lastEpisode.season,

                                episodes = listOf(
                                    Episode(
                                        id = "",
                                        number = lastEpisode.episode,
                                    )
                                )
                            )
                        )
                    } ?: listOf(),
                )
            }
        }

        return results
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        val document = service.getMovies(page)

        val movies = document.select("div.flw-item").map {
            val info = it.select("div.film-detail > div.fd-infor > span").toInfo()

            Movie(
                id = it.selectFirst("a")
                    ?.attr("href")?: "",
                title = it.selectFirst("h2.film-name")
                    ?.text() ?: "",
                released = info.released,
                quality = info.quality,
                rating = info.rating,
                poster = it.selectFirst("div.film-poster > img.film-poster-img")
                    ?.imageUrl(),
            )
        }

        return movies
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        val document = service.getTvShows(page)

        val tvShows = document.select("div.flw-item").map {
            val info = it.select("div.film-detail > div.fd-infor > span").toInfo()

            TvShow(
                id = it.selectFirst("a")
                    ?.attr("href")?: "",
                title = it.selectFirst("h2.film-name")
                    ?.text() ?: "",
                quality = info.quality,
                rating = info.rating,
                poster = it.selectFirst("div.film-poster > img.film-poster-img")
                    ?.imageUrl(),

                seasons = info.lastEpisode?.let { lastEpisode ->
                    listOf(
                        Season(
                            id = "",
                            number = lastEpisode.season,

                            episodes = listOf(
                                Episode(
                                    id = "",
                                    number = lastEpisode.episode,
                                )
                            )
                        )
                    )
                } ?: listOf()
            )
        }

        return tvShows
    }


    override suspend fun getMovie(id: String): Movie {
        val document = service.getPage(resolveSflixUrl(id))

        val movie = Movie(
            id = id,
            title = document.selectFirst("h2.heading-name")
                ?.text() ?: "",
            overview = document.selectFirst("div.description")
                ?.ownText(),
            released = document.select("div.elements > .row > div > .row-line")
                .find { it.select(".type").text().contains("Released") }
                ?.ownText()?.trim(),
            runtime = document.select("div.elements > .row > div > .row-line")
                .find { it.select(".type").text().contains("Duration") }
                ?.ownText()?.removeSuffix("min")?.trim()?.toIntOrNull(),
            trailer = document.selectFirst("iframe#iframe-trailer")
                ?.attr("data-src")?.substringAfterLast("/")
                ?.let { "https://www.youtube.com/watch?v=${it}" },
            quality = document.selectFirst(".fs-item > .quality")
                ?.text()?.trim(),
            rating = document.selectFirst(".fs-item > .imdb")
                ?.text()?.trim()?.removePrefix("IMDB:")?.toDoubleOrNull(),
            poster = document.selectFirst("div.detail_page-watch img.film-poster-img")
                ?.imageUrl(),
            banner = document.selectFirst("div.detail-container > div.cover_follow")
                ?.attr("style")?.substringAfter("background-image: url(")?.substringBefore(");"),

            genres = document.select("div.elements > .row > div > .row-line")
                .find { it.select(".type").text().contains("Genre") }
                ?.select("a")?.map {
                    Genre(
                        id = it.attr("href").substringAfter("/genre/"),
                        name = it.text(),
                    )
                } ?: listOf(),
            cast = document.select("div.elements > .row > div > .row-line")
                .find { it.select(".type").text().contains("Cast") }
                ?.select("a")?.map {
                    People(
                        id = it.attr("href").substringAfter("/cast/"),
                        name = it.text(),
                    )
                } ?: listOf(),
            recommendations = document.select("div.film_related div.flw-item").map {
                val info = it.select("div.film-detail > div.fd-infor > span").toInfo()

                if (it.isMovie()) {
                    Movie(
                        id = it.selectFirst("a")
                            ?.attr("href")?: "",
                        title = it.selectFirst("h3.film-name")
                            ?.text() ?: "",
                        released = info.released,
                        quality = info.quality,
                        rating = info.rating,
                        poster = it.selectFirst("div.film-poster > img.film-poster-img")
                            ?.imageUrl(),
                    )
                } else {
                    TvShow(
                        id = it.selectFirst("a")
                            ?.attr("href")?: "",
                        title = it.selectFirst("h3.film-name")
                            ?.text() ?: "",
                        quality = info.quality,
                        rating = info.rating,
                        poster = it.selectFirst("div.film-poster > img.film-poster-img")
                            ?.imageUrl(),

                        seasons = info.lastEpisode?.let { lastEpisode ->
                            listOf(
                                Season(
                                    id = "",
                                    number = lastEpisode.season,

                                    episodes = listOf(
                                        Episode(
                                            id = "",
                                            number = lastEpisode.episode,
                                        )
                                    )
                                )
                            )
                        } ?: listOf(),
                    )
                }
            },
        )

        return movie
    }


    override suspend fun getTvShow(id: String): TvShow {
        val document = service.getPage(resolveSflixUrl(id))

        val tvShow = TvShow(
            id = id,
            title = document.selectFirst("h2.heading-name")
                ?.text() ?: "",
            overview = document.selectFirst("div.description")
                ?.ownText(),
            released = document.select("div.elements > .row > div > .row-line")
                .find { it.select(".type").text().contains("Released") }
                ?.ownText()?.trim(),
            runtime = document.select("div.elements > .row > div > .row-line")
                .find { it.select(".type").text().contains("Duration") }
                ?.ownText()?.removeSuffix("min")?.trim()?.toIntOrNull(),
            trailer = document.selectFirst("iframe#iframe-trailer")
                ?.attr("data-src")?.substringAfterLast("/")
                ?.let { "https://www.youtube.com/watch?v=${it}" },
            quality = document.selectFirst(".fs-item > .quality")
                ?.text()?.trim(),
            rating = document.selectFirst(".fs-item > .imdb")
                ?.text()?.trim()?.removePrefix("IMDB:")?.toDoubleOrNull(),
            poster = document.selectFirst("div.detail_page-watch img.film-poster-img")
                ?.imageUrl(),
            banner = document.selectFirst("div.detail-container > div.cover_follow")
                ?.attr("style")?.substringAfter("background-image: url(")?.substringBefore(");"),

            seasons = document
                .select("#show-seasons .ss-item[data-ss][data-id], .ss-item[data-ss][data-id]")
                .mapNotNull { seasonElement ->
                    val seasonNumber =
                        seasonElement.attr("data-ss").toIntOrNull()
                            ?: return@mapNotNull null

                    val seasonToken =
                        seasonElement.attr("data-id").trim()

                    if (seasonToken.isBlank()) {
                        return@mapNotNull null
                    }

                    Season(
                        id = seasonToken,
                        number = seasonNumber,
                        title = seasonElement.text().trim()
                            .ifBlank { "Season $seasonNumber" },
                    )
                }
                .distinctBy { it.number },
            genres = document.select("div.elements > .row > div > .row-line")
                .find { it.select(".type").text().contains("Genre") }
                ?.select("a")?.map {
                    Genre(
                        id = it.attr("href").substringAfter("/genre/"),
                        name = it.text(),
                    )
                } ?: listOf(),
            cast = document.select("div.elements > .row > div > .row-line")
                .find { it.select(".type").text().contains("Cast") }
                ?.select("a")?.map {
                    People(
                        id = it.attr("href").substringAfter("/cast/"),
                        name = it.text(),
                    )
                } ?: listOf(),
            recommendations = document.select("div.film_related div.flw-item").map {
                val info = it.select("div.film-detail > div.fd-infor > span").toInfo()

                if (it.isMovie()) {
                    Movie(
                        id = it.selectFirst("a")
                            ?.attr("href")?: "",
                        title = it.selectFirst("h3.film-name")
                            ?.text() ?: "",
                        released = info.released,
                        quality = info.quality,
                        rating = info.rating,
                        poster = it.selectFirst("div.film-poster > img.film-poster-img")
                            ?.imageUrl(),
                    )
                } else {
                    TvShow(
                        id = it.selectFirst("a")
                            ?.attr("href")?: "",
                        title = it.selectFirst("h3.film-name")
                            ?.text() ?: "",
                        quality = info.quality,
                        rating = info.rating,
                        poster = it.selectFirst("div.film-poster > img.film-poster-img")
                            ?.imageUrl(),

                        seasons = info.lastEpisode?.let { lastEpisode ->
                            listOf(
                                Season(
                                    id = "",
                                    number = lastEpisode.season,

                                    episodes = listOf(
                                        Episode(
                                            id = "",
                                            number = lastEpisode.episode,
                                        )
                                    )
                                )
                            )
                        } ?: listOf(),
                    )
                }
            },
        )

        return tvShow
    }

    override suspend fun getEpisodesBySeason(
        seasonId: String,
    ): List<Episode> {
        val document = service.getSeasonEpisodes(seasonId)

        return document
            .select("div.flw-item")
            .mapNotNull { episodeElement ->

                val anchor =
                    episodeElement.selectFirst("a.eps-item[href]")
                        ?: return@mapNotNull null

                val episodeUrl =
                    anchor.attr("href")
                        .trim()
                        .toAbsoluteUrl()

                if (episodeUrl.isBlank()) {
                    return@mapNotNull null
                }

                val episodeNumber =
                    episodeElement
                        .selectFirst(".episode-number")
                        ?.text()
                        ?.let {
                            Regex("""\d+""")
                                .find(it)
                                ?.value
                                ?.toIntOrNull()
                        }
                        ?: Regex("""/(\d+)-(\d+)/?$""")
                            .find(episodeUrl)
                            ?.groupValues
                            ?.getOrNull(2)
                            ?.toIntOrNull()
                        ?: return@mapNotNull null

                val title =
                    episodeElement
                        .selectFirst("h3.film-name")
                        ?.text()
                        ?.trim()
                        ?.ifBlank { null }
                        ?: "Episode $episodeNumber"

                val posterElement =
                    episodeElement.selectFirst("img")

                val poster = posterElement?.imageUrl()

                Episode(
                    id = episodeUrl,
                    number = episodeNumber,
                    title = title,
                    poster = poster,
                )
            }
            .distinctBy { it.id }
    }


    override suspend fun getGenre(id: String, page: Int): Genre {
        val document = service.getGenre(id, page)

        val genre = Genre(
            id = id,
            name = document.selectFirst("h2.cat-heading")
                ?.text()?.removeSuffix(" Movies and TV Shows") ?: "",

            shows = document.select("div.flw-item").map {
                val showId = it.selectFirst("a")
                    ?.attr("href")?: ""
                val showTitle = it.selectFirst("h2.film-name")
                    ?.text() ?: ""
                val showInfo = it.select("div.film-detail > div.fd-infor > span").toInfo()
                val showPoster = it.selectFirst("div.film-poster > img.film-poster-img")
                    ?.imageUrl()

                if (it.isMovie()) {
                    Movie(
                        id = showId,
                        title = showTitle,
                        released = showInfo.released,
                        quality = showInfo.quality,
                        rating = showInfo.rating,
                        poster = showPoster,
                    )
                } else {
                    TvShow(
                        id = showId,
                        title = showTitle,
                        quality = showInfo.quality,
                        rating = showInfo.rating,
                        poster = showPoster,

                        seasons = showInfo.lastEpisode?.let { lastEpisode ->
                            listOf(
                                Season(
                                    id = "",
                                    number = lastEpisode.season,

                                    episodes = listOf(
                                        Episode(
                                            id = "",
                                            number = lastEpisode.episode,
                                        )
                                    )
                                )
                            )
                        } ?: listOf(),
                    )
                }
            }
        )

        return genre
    }


    override suspend fun getPeople(id: String, page: Int): People {
        val document = service.getPeople(id, page)

        val people = People(
            id = id,
            name = document.selectFirst("h2.cat-heading")
                ?.text() ?: "",

            filmography = document.select("div.flw-item").map {
                val showId = it.selectFirst("a")
                    ?.attr("href") ?: ""
                val showTitle = it.selectFirst("h2.film-name")
                    ?.text() ?: ""
                val showInfo = it.select("div.film-detail > div.fd-infor > span").toInfo()
                val showPoster = it.selectFirst("div.film-poster > img.film-poster-img")
                    ?.imageUrl()

                if (it.isMovie()) {
                    Movie(
                        id = showId,
                        title = showTitle,
                        released = showInfo.released,
                        quality = showInfo.quality,
                        rating = showInfo.rating,
                        poster = showPoster,
                    )
                } else {
                    TvShow(
                        id = showId,
                        title = showTitle,
                        quality = showInfo.quality,
                        rating = showInfo.rating,
                        poster = showPoster,

                        seasons = showInfo.lastEpisode?.let { lastEpisode ->
                            listOf(
                                Season(
                                    id = "",
                                    number = lastEpisode.season,

                                    episodes = listOf(
                                        Episode(
                                            id = "",
                                            number = lastEpisode.episode,
                                        )
                                    )
                                )
                            )
                        } ?: listOf(),
                    )
                }
            },
        )

        return people
    }

    private fun Document.getPlayerListUrl(): String? {
        val match = Regex(
            """(?:const|let|var)\s+pl_url\s*=\s*['"]([^'"]+)['"]""",
            RegexOption.IGNORE_CASE,
        ).find(html())

        return match
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.toAbsoluteUrl()
    }

    private suspend fun getCurrentServers(
        pageUrl: String,
    ): List<Video.Server> {
        val page =
            service.getPage(pageUrl.toAbsoluteUrl())

        val playerListUrl =
            page.getPlayerListUrl()
                ?: throw Exception("SFlix player URL not found")

        val serverDocument =
            service.getPage(playerListUrl)

        return serverDocument
            .select("li.sv-item[data-id], .sv-item[data-id]")
            .mapNotNull { element ->

                val source =
                    element.attr("data-id").trim()

                if (source.isBlank()) {
                    return@mapNotNull null
                }

                val name =
                    element.attr("data-srv")
                        .trim()
                        .ifBlank {
                            element
                                .selectFirst("span")
                                ?.text()
                                ?.trim()
                                .orEmpty()
                        }
                        .ifBlank { "SFlix" }

                Video.Server(
                    id = source,
                    name = name,
                )
            }
            .distinctBy { it.id }
    }

    override suspend fun getServers(
        id: String,
        videoType: Video.Type,
    ): List<Video.Server> {
        val rawServers = getCurrentServers(id)

        if (rawServers.isEmpty()) {
            throw Exception("No links found")
        }

        /*
         * sflix.win currently exposes:
         *
         * UpCloud / Vidmoly -> 0123movie.space wrappers
         * Vidsrc            -> vidsrc.cc
         * Videasy           -> player.videasy.net
         *
         * The raw wrapper URLs must not be passed directly to the generic
         * extractor matcher. Use the native Streamflix extractors instead.
         */

        val ids = extractSflixIds(rawServers)
        val tmdbId = ids.tmdbId

        val normalizedVideoType =
            videoType.withSflixIds(ids)

        val servers = mutableListOf<Video.Server>()

        // -------------------------------------------------
        // Vidsrc
        // -------------------------------------------------

        rawServers
            .firstOrNull {
                it.name.equals("Vidsrc", ignoreCase = true)
            }
            ?.let { raw ->
                val server =
                    if (tmdbId != null) {
                        VidsrcRuExtractor()
                            .server(normalizedVideoType)
                    } else {
                        Video.Server(
                            id = "Vidsrc.to",
                            name = "Vidsrc",
                            src = rewriteSflixVidsrc(raw.id),
                        )
                    }

                servers += server.copy(
                    name = "Vidsrc"
                )
            }

        // -------------------------------------------------
        // Videasy
        // -------------------------------------------------

        if (
            tmdbId != null &&
            rawServers.any {
                it.name.equals("Videasy", ignoreCase = true)
            }
        ) {
            servers +=
                VideasyExtractor()
                    .servers(
                        normalizedVideoType,
                        "en"
                    )
                    .map { server ->
                        server.copy(
                            name = server.name
                                .replace(" (Videasy)", "")
                        )
                    }
        }

        if (servers.isEmpty()) {
            throw Exception(
                "No supported SFlix playback links found"
            )
        }

        return servers.distinctBy {
            it.src.ifBlank { it.id }
        }
    }


    override suspend fun getVideo(
        server: Video.Server,
    ): Video {
        server.video?.let {
            return it
        }

        val source =
            server.src.ifBlank {
                server.id
            }

        return Extractor.extract(
            source,
            server
        )
    }


    private data class SflixIds(
        val tmdbId: String?,
        val imdbId: String?,
    )


    private fun extractSflixIds(
        servers: List<Video.Server>,
    ): SflixIds {
        val sources =
            servers.map {
                it.src.ifBlank { it.id }
            }

        val imdbId =
            sources
                .asSequence()
                .mapNotNull { source ->
                    Regex(
                        """tt\d{5,12}""",
                        RegexOption.IGNORE_CASE,
                    )
                        .find(source)
                        ?.value
                }
                .firstOrNull()

        val tmdbFromVideasy =
            sources
                .asSequence()
                .filter {
                    it.contains(
                        "videasy",
                        ignoreCase = true
                    )
                }
                .mapNotNull { source ->
                    Regex(
                        """/(?:movie|tv)/(\d+)""",
                        RegexOption.IGNORE_CASE,
                    )
                        .find(source)
                        ?.groupValues
                        ?.getOrNull(1)
                }
                .firstOrNull()

        /*
         * Current 0123movie wrappers contain both IDs, e.g.
         *
         * /mv/tt35538033/1423191/
         * /vmf/tt35538033/1423191/
         */
        val tmdbFromWrapper =
            sources
                .asSequence()
                .mapNotNull { source ->
                    Regex(
                        """/tt\d{5,12}/(\d+)(?:/|$)""",
                        RegexOption.IGNORE_CASE,
                    )
                        .find(source)
                        ?.groupValues
                        ?.getOrNull(1)
                }
                .firstOrNull()

        return SflixIds(
            tmdbId =
                tmdbFromVideasy
                    ?: tmdbFromWrapper,
            imdbId = imdbId,
        )
    }


    private fun Video.Type.withSflixIds(
        ids: SflixIds,
    ): Video.Type {
        return when (this) {
            is Video.Type.Movie ->
                copy(
                    id = ids.tmdbId ?: id,
                    imdbId = ids.imdbId ?: imdbId,
                )

            is Video.Type.Episode ->
                copy(
                    tvShow = tvShow.copy(
                        id = ids.tmdbId ?: tvShow.id,
                        imdbId = ids.imdbId ?: tvShow.imdbId,
                    )
                )
        }
    }


    private fun rewriteSflixVidsrc(
        source: String,
    ): String {
        val value = source.trim()

        val suffix =
            value.substringAfter(
                "vidsrc.cc",
                ""
            )

        if (suffix.isBlank()) {
            return value
        }

        return "https://vidsrc.to" +
            suffix.removePrefix("/v2")
    }


    private fun resolveSflixUrl(value: String): String {
        val url = value.trim()

        return when {
            url.startsWith("https://", ignoreCase = true) ||
                url.startsWith("http://", ignoreCase = true) -> url

            else ->
                baseUrl.trimEnd('/') + "/" + url.trimStart('/')
        }
    }

    private fun Element.imageUrl(): String? {
        return attr("src")
            .trim()
            .takeIf { it.isNotBlank() }
            ?: attr("data-src")
                .trim()
                .takeIf { it.isNotBlank() }
    }

    private fun Element.isMovie(): Boolean = this.selectFirst("a")?.attr("href")
        ?.contains("/movie/") ?: false

    private fun Elements.toInfo() = this.map { it.text() }.let {
        object {
            val rating = it.find { s -> s.matches("^\\d(?:\\.\\d)?\$".toRegex()) }?.toDoubleOrNull()

            val quality = it.find { s -> s in listOf("HD", "SD", "CAM", "TS", "HDRip") }

            val released = it.find { s -> s.matches("\\d{4}".toRegex()) }

            val lastEpisode = it.find { s -> s.matches("S\\d+\\s*:E\\d+".toRegex()) }?.let { s ->
                val result = Regex("S(\\d+)\\s*:E(\\d+)").find(s)?.groupValues
                object {
                    val season = result?.getOrNull(1)?.toIntOrNull() ?: 0

                    val episode = result?.getOrNull(2)?.toIntOrNull() ?: 0
                }
            }
        }
    }

    private fun String.toAbsoluteUrl(): String {
        val value = trim()

        if (
            value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true)
        ) {
            return value
        }

        return "${baseUrl.trimEnd('/')}/${value.trimStart('/')}"
    }

    private fun String.toNumericalId(): String = this.substringAfterLast("-")


    private interface SflixService {

        companion object {
            fun build(baseUrl: String): SflixService {
                val client = OkHttpClient.Builder()
                    .readTimeout(30, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .addInterceptor { chain ->
                        val original = chain.request()

                        val request = original.newBuilder()
                            .header(
                                "User-Agent",
                                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                                    "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                            )
                            .header(
                                "Accept",
                                "text/html,application/xhtml+xml,application/xml;q=0.9," +
                                    "image/avif,image/webp,*/*;q=0.8"
                            )
                            .header("Accept-Language", "en-US,en;q=0.9")
                            .build()

                        Log.d(
                            "SFlixHttp",
                            "--> ${request.method} ${request.url}"
                        )

                        val response = chain.proceed(request)

                        Log.d(
                            "SFlixHttp",
                            "<-- ${response.code} ${response.request.url} " +
                                "server=${response.header("server")} " +
                                "cf-ray=${response.header("cf-ray")} " +
                                "location=${response.header("location")}"
                        )

                        if (!response.isSuccessful) {
                            val preview = try {
                                response.peekBody(2048L).string()
                                    .replace("\n", " ")
                                    .replace("\r", " ")
                            } catch (_: Exception) {
                                "<unable to read body>"
                            }

                            Log.d(
                                "SFlixHttp",
                                "ERROR BODY: $preview"
                            )
                        }

                        response
                    }
                    .dns(DnsResolver.doh)
                    .build()

                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl.trimEnd('/') + "/")
                    .addConverterFactory(JsoupConverterFactory.create())
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build()

                return retrofit.create(SflixService::class.java)
            }
        }

        @GET("home")
        suspend fun getHome(): Document

        @GET
        suspend fun getPage(
            @Url url: String,
        ): Document

        @GET("search/{query}")
        suspend fun search(@Path("query") query: String, @Query("page") page: Int): Document

        @GET("movie")
        suspend fun getMovies(@Query("page") page: Int): Document

        @GET("tv-series/")
        suspend fun getTvShows(@Query("page") page: Int): Document


        @GET("{id}")
        suspend fun getMovie(@Path("id") id: String): Document

        @GET("ajax/episode/list/{id}")
        suspend fun getMovieServers(@Path("id") movieId: String): Document


        @GET("{id}")
        suspend fun getTvShow(@Path("id") id: String): Document

        @GET("ajax/season/list/{id}")
        suspend fun getTvShowSeasons(@Path("id") tvShowId: String): Document

        @GET("ajax/ajax.php")
        suspend fun getSeasonEpisodes(
            @Query("episode") seasonToken: String,
        ): Document

        @GET("ajax/episode/servers/{id}")
        suspend fun getEpisodeServers(@Path("id") episodeId: String): Document


        @GET("genre/{id}")
        suspend fun getGenre(@Path("id") id: String, @Query("page") page: Int): Document


        @GET("cast/{id}")
        suspend fun getPeople(@Path("id") id: String, @Query("page") page: Int): Document


        @GET("ajax/episode/sources/{id}")
        suspend fun getLink(@Path("id") id: String): Link

        @GET
        suspend fun getEmbed(
            @Url url: String,
        ): Embed


        data class Link(
            val type: String = "",
            val link: String = "",
            val sources: List<String> = listOf(),
            val tracks: List<String> = listOf(),
            val title: String = "",
        )

        data class Embed(
            val sources: List<Source>,
            val tracks: List<Track>,
            val t: Int,
            val server: Int,
        ) {
            data class Source(
                val file: String,
                val type: String,
            )

            data class Track(
                val file: String,
                val label: String,
                val kind: String,
                val default: Boolean?,
            )
        }
    }
}
