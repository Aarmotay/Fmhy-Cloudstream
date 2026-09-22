package com.fmhy

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element
import org.jsoup.nodes.Document

/**
 * FMHYProvider - A Cloudstream 3 extension that scrapes the FMHY video directory
 * (https://fmhy.pages.dev/video) and presents curated streaming sites as browsable
 * categories. Users can then open individual streaming sites directly.
 *
 * Architecture:
 * - The homepage displays FMHY's categorized sections (Stream Aggregators, Dedicated Server, etc.)
 * - Each "search result" is a streaming site entry with its name, URL, and metadata
 * - "Loading" a site shows its details (supported content types, features)
 * - "loadLinks" provides the direct URL to the streaming site for the user to visit
 */
@Suppress("DEPRECATION")
class FMHYProvider : MainAPI() {
    override var mainUrl = "https://fmhy.pages.dev"
    override var name = "FMHY Video Directory"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = false

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime
    )

    // User-Agent and headers for requests
    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.5",
        "Sec-Fetch-Site" to "none",
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Dest" to "document"
    )

    /**
     * Data class representing a parsed streaming site entry from FMHY
     */
    data class StreamingSite(
        val name: String,
        val url: String,
        val mirrors: List<String> = emptyList(),
        val categories: List<String> = emptyList(), // Movies, TV, Anime
        val features: List<String> = emptyList(),   // Auto-Next, 4K, etc.
        val section: String = "",                    // Which FMHY section it belongs to
        val discordUrl: String? = null,
        val telegramUrl: String? = null,
        val hasAds: Boolean = false
    )

    /**
     * Cache the parsed FMHY page to avoid repeated fetching
     */
    private var cachedSites: List<StreamingSite>? = null
    private var cacheTimestamp: Long = 0
    private val CACHE_DURATION = 30 * 60 * 1000L // 30 minutes

    /**
     * Fetches and parses the FMHY /video page into structured StreamingSite objects.
     */
    private suspend fun fetchAndParseSites(): List<StreamingSite> {
        val now = System.currentTimeMillis()
        cachedSites?.let {
            if (now - cacheTimestamp < CACHE_DURATION) return it
        }

        val document = app.get("$mainUrl/video", headers = headers).document
        val sites = mutableListOf<StreamingSite>()

        // The FMHY page is structured with h2/h3 section headers followed by
        // unordered lists of site entries. Each list item contains links and metadata.
        var currentSection = "General"

        // Parse the main content area
        val contentElements = document.select(".vp-doc, .content, main, article, #VPContent")
        val contentArea: Element? = if (contentElements.isNotEmpty()) contentElements.first() else document.body()

        contentArea?.let { content ->
            // Iterate through all elements to track sections and parse list items
            for (element in content.allElements) {
                when {
                    element.tagName() in listOf("h2", "h3") -> {
                        val sectionText = element.text().trim()
                        if (sectionText.isNotEmpty()) {
                            currentSection = sectionText
                        }
                    }
                    element.tagName() == "li" && element.parent()?.tagName() == "ul" -> {
                        parseSiteEntry(element, currentSection)?.let { sites.add(it) }
                    }
                }
            }
        }

        // If DOM parsing yielded few results, try a fallback regex-based approach
        if (sites.size < 10) {
            val html = document.html()
            val fallbackSites = parseWithRegex(html)
            if (fallbackSites.size > sites.size) {
                sites.clear()
                sites.addAll(fallbackSites)
            }
        }

        cachedSites = sites
        cacheTimestamp = now
        return sites
    }

    /**
     * Parses a single <li> element into a StreamingSite object.
     * FMHY format example:
     *   <li><a href="https://cinejoy.pk/">Cinejoy</a> - Movies / TV / Anime / Auto-Next</li>
     */
    private fun parseSiteEntry(li: Element, section: String): StreamingSite? {
        val links = li.select("a[href]")
        if (links.isEmpty()) return null

        // The first meaningful link (not discord/telegram/status) is the main site
        val mainLink = links.firstOrNull { link ->
            val href = link.attr("href")
            !href.contains("discord") &&
            !href.contains("telegram") &&
            !href.contains("t.me") &&
            !href.contains("rentry") &&
            !href.contains("github.com") &&
            !href.contains("greasyfork") &&
            !href.contains("fmhy.pages.dev") &&
            href.startsWith("http")
        } ?: return null

        val name = mainLink.text().trim()
        if (name.isEmpty() || name.length < 2) return null

        val url = mainLink.attr("href").trim()
        if (!url.startsWith("http")) return null

        // Collect mirror URLs (links with text like "2", "3", etc.)
        val mirrors = links.filter { link ->
            val text = link.text().trim()
            val href = link.attr("href")
            (text.matches(Regex("\\d+")) || text == name) &&
            href != url &&
            href.startsWith("http") &&
            !href.contains("discord") &&
            !href.contains("t.me") &&
            !href.contains("rentry")
        }.map { it.attr("href") }

        // Parse the full text of the li to extract categories and features
        val fullText = li.text()
        val categories = mutableListOf<String>()
        val features = mutableListOf<String>()

        if (fullText.contains("Movies", ignoreCase = true)) categories.add("Movies")
        if (fullText.contains("TV", ignoreCase = true)) categories.add("TV")
        if (fullText.contains("Anime", ignoreCase = true)) categories.add("Anime")
        if (fullText.contains("Auto-Next", ignoreCase = true)) features.add("Auto-Next")
        if (fullText.contains("4K", ignoreCase = true)) features.add("4K")

        // Discord / Telegram links
        val discordUrl = links.firstOrNull { it.attr("href").contains("discord") }?.attr("href")
        val telegramUrl = links.firstOrNull {
            it.attr("href").contains("t.me") || it.attr("href").contains("telegram")
        }?.attr("href")

        return StreamingSite(
            name = name,
            url = url,
            mirrors = mirrors,
            categories = categories,
            features = features,
            section = section,
            discordUrl = discordUrl,
            telegramUrl = telegramUrl
        )
    }

    /**
     * Fallback regex-based parser for when DOM parsing doesn't work well
     * (e.g., if the page is rendered via JavaScript and raw HTML doesn't
     * have proper structure).
     */
    private fun parseWithRegex(html: String): List<StreamingSite> {
        val sites = mutableListOf<StreamingSite>()
        // Match patterns like: <a href="URL">SiteName</a> - description
        val pattern = Regex(
            """<a[^>]+href="(https?://[^"]+)"[^>]*>([^<]+)</a>\s*[-–]\s*([^<]+)""",
            RegexOption.IGNORE_CASE
        )

        for (match in pattern.findAll(html)) {
            val url = match.groupValues[1]
            val name = match.groupValues[2].trim()
            val meta = match.groupValues[3].trim()

            // Skip non-site links
            if (url.contains("discord") || url.contains("t.me") ||
                url.contains("rentry") || url.contains("github.com") ||
                url.contains("fmhy.pages.dev") || name.length < 2
            ) continue

            val categories = mutableListOf<String>()
            val features = mutableListOf<String>()

            if (meta.contains("Movies", ignoreCase = true)) categories.add("Movies")
            if (meta.contains("TV", ignoreCase = true)) categories.add("TV")
            if (meta.contains("Anime", ignoreCase = true)) categories.add("Anime")
            if (meta.contains("Auto-Next", ignoreCase = true)) features.add("Auto-Next")
            if (meta.contains("4K", ignoreCase = true)) features.add("4K")

            if (categories.isNotEmpty()) {
                sites.add(
                    StreamingSite(
                        name = name,
                        url = url,
                        categories = categories,
                        features = features,
                        section = "General"
                    )
                )
            }
        }
        return sites
    }

    // =========================================================================
    // Cloudstream MainAPI Implementation
    // =========================================================================

    /**
     * getMainPage() - Generates the homepage with categorized sections.
     * Each section corresponds to a category from the FMHY page
     * (e.g., Stream Aggregators, Dedicated Server, P-Stream Forks).
     */
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val sites = fetchAndParseSites()

        // Group sites by their FMHY section
        val sectionGroups = sites.groupBy { it.section }

        val homePageLists = sectionGroups.map { (section, siteList) ->
            HomePageList(
                name = section,
                list = siteList.map { site -> site.toSearchResponse() },
                isHorizontalImages = false
            )
        }

        // Also add category-based groups
        val movieSites = sites.filter { "Movies" in it.categories }
        val tvSites = sites.filter { "TV" in it.categories }
        val animeSites = sites.filter { "Anime" in it.categories }
        val fourKSites = sites.filter { "4K" in it.features }

        val categoryLists = mutableListOf<HomePageList>()

        if (fourKSites.isNotEmpty()) {
            categoryLists.add(
                HomePageList(
                    name = "⭐ 4K Capable Sites",
                    list = fourKSites.map { it.toSearchResponse() },
                    isHorizontalImages = true
                )
            )
        }

        return HomePageResponse(categoryLists + homePageLists)
    }

    /**
     * search() - Searches across all parsed streaming sites by name, URL,
     * categories, and features.
     */
    override suspend fun search(query: String): List<SearchResponse> {
        val sites = fetchAndParseSites()
        val lowerQuery = query.lowercase()

        return sites.filter { site ->
            site.name.lowercase().contains(lowerQuery) ||
            site.url.lowercase().contains(lowerQuery) ||
            site.categories.any { it.lowercase().contains(lowerQuery) } ||
            site.features.any { it.lowercase().contains(lowerQuery) } ||
            site.section.lowercase().contains(lowerQuery)
        }.map { it.toSearchResponse() }
    }

    /**
     * load() - Loads detailed information about a specific streaming site.
     * Shows the site name, URL, supported content types, features,
     * mirror links, and community links.
     */
    override suspend fun load(url: String): LoadResponse {
        val sites = fetchAndParseSites()
        val site = sites.firstOrNull { it.url == url }

        if (site != null) {
            // Build a rich description with all available metadata
            val description = buildString {
                appendLine("🌐 ${site.url}")
                appendLine()

                if (site.categories.isNotEmpty()) {
                    appendLine("📂 Content: ${site.categories.joinToString(" / ")}")
                }
                if (site.features.isNotEmpty()) {
                    appendLine("✨ Features: ${site.features.joinToString(" / ")}")
                }
                if (site.section.isNotEmpty()) {
                    appendLine("📑 Section: ${site.section}")
                }
                if (site.mirrors.isNotEmpty()) {
                    appendLine()
                    appendLine("🔗 Mirror Sites:")
                    site.mirrors.forEachIndexed { index, mirror ->
                        appendLine("  ${index + 1}. $mirror")
                    }
                }
                if (site.discordUrl != null) {
                    appendLine()
                    appendLine("💬 Discord: ${site.discordUrl}")
                }
                if (site.telegramUrl != null) {
                    appendLine("📱 Telegram: ${site.telegramUrl}")
                }
            }

            // Determine TvType based on categories
            val tvType = when {
                site.categories.contains("Anime") -> TvType.Anime
                site.categories.contains("TV") -> TvType.TvSeries
                else -> TvType.Movie
            }

            return newMovieLoadResponse(
                name = site.name,
                url = site.url,
                type = tvType,
                dataUrl = site.url
            ) {
                this.plot = description
                this.tags = site.categories + site.features
                // Use a generated poster placeholder
                this.posterUrl = "https://www.google.com/s2/favicons?domain=${
                    site.url.removePrefix("https://").removePrefix("http://").split("/").first()
                }&sz=256"
            }
        }

        // Fallback: if the site wasn't found in our cache, try to fetch info directly
        return newMovieLoadResponse(
            name = "Streaming Site",
            url = url,
            type = TvType.Movie,
            dataUrl = url
        ) {
            this.plot = "Direct link to streaming site: $url"
        }
    }

    /**
     * loadLinks() - Provides the streaming site URL as a playable link.
     * Since FMHY is a directory of other sites (not a host itself),
     * we provide the site URL as a "link" that opens in the user's browser
     * or Cloudstream's WebView.
     *
     * For sites we recognize, we also attempt to extract embeddable players.
     */
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // The data URL is the streaming site URL itself
        val siteUrl = data

        // Provide the main site link
        callback.invoke(
            ExtractorLink(
                source = name,
                name = "Open Site: ${siteUrl.extractDomain()}",
                url = siteUrl,
                referer = mainUrl,
                quality = Qualities.Unknown.value,
                isM3u8 = false
            )
        )

        // Also provide mirror links if available
        val sites = fetchAndParseSites()
        val site = sites.firstOrNull { it.url == siteUrl }

        site?.mirrors?.forEachIndexed { index, mirror ->
            callback.invoke(
                ExtractorLink(
                    source = name,
                    name = "Mirror ${index + 1}: ${mirror.extractDomain()}",
                    url = mirror,
                    referer = mainUrl,
                    quality = Qualities.Unknown.value,
                    isM3u8 = false
                )
            )
        }

        // Try to fetch and parse the target site for actual video embeds
        try {
            val response = app.get(siteUrl, headers = headers, timeout = 10)
            if (response.isSuccessful) {
                val doc = response.document
                extractVideoLinks(doc, siteUrl, callback, subtitleCallback)
            }
        } catch (e: Exception) {
            // Site may be down or behind Cloudflare - that's OK, we still provided the direct link
        }

        return true
    }

    /**
     * Attempts to extract video embed links from a streaming site's HTML.
     * Looks for common embed patterns: iframe src, video src, .m3u8/.mp4 links.
     */
    private suspend fun extractVideoLinks(
        doc: Document,
        referer: String,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        // Look for iframe embeds
        doc.select("iframe[src]").forEach { iframe ->
            val src = iframe.attr("src").let {
                if (it.startsWith("//")) "https:$it" else it
            }
            if (src.startsWith("http")) {
                loadExtractor(src, referer, subtitleCallback, callback)
            }
        }

        // Look for direct video sources
        doc.select("video source[src], video[src]").forEach { video ->
            val src = video.attr("src").let {
                if (it.startsWith("//")) "https:$it" else it
            }
            if (src.startsWith("http")) {
                val isM3u8 = src.contains(".m3u8")
                callback.invoke(
                    ExtractorLink(
                        source = name,
                        name = "Direct Video",
                        url = src,
                        referer = referer,
                        quality = Qualities.Unknown.value,
                        isM3u8 = isM3u8
                    )
                )
            }
        }

        // Look for .m3u8 and .mp4 URLs in scripts
        val scriptContent = doc.select("script").joinToString("\n") { it.html() }
        val m3u8Pattern = Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""")
        val mp4Pattern = Regex("""["'](https?://[^"']+\.mp4[^"']*)["']""")

        m3u8Pattern.findAll(scriptContent).forEach { match ->
            val url = match.groupValues[1]
            callback.invoke(
                ExtractorLink(
                    source = name,
                    name = "HLS Stream",
                    url = url,
                    referer = referer,
                    quality = Qualities.Unknown.value,
                    isM3u8 = true
                )
            )
        }

        mp4Pattern.findAll(scriptContent).forEach { match ->
            val url = match.groupValues[1]
            callback.invoke(
                ExtractorLink(
                    source = name,
                    name = "MP4 Stream",
                    url = url,
                    referer = referer,
                    quality = Qualities.Unknown.value,
                    isM3u8 = false
                )
            )
        }
    }

    // =========================================================================
    // Helper Functions
    // =========================================================================

    /**
     * Converts a StreamingSite to a Cloudstream SearchResponse for display.
     */
    private fun StreamingSite.toSearchResponse(): SearchResponse {
        val posterUrl = "https://www.google.com/s2/favicons?domain=${
            url.removePrefix("https://").removePrefix("http://").split("/").first()
        }&sz=256"

        val qualityTag = if ("4K" in features) "4K" else null
        val displayName = buildString {
            append(name)
            if (features.isNotEmpty()) {
                append(" [${features.joinToString(", ")}]")
            }
        }

        return newMovieSearchResponse(
            name = displayName,
            url = url,
            type = when {
                categories.contains("Anime") -> TvType.Anime
                categories.contains("TV") -> TvType.TvSeries
                else -> TvType.Movie
            }
        ) {
            this.posterUrl = posterUrl
        }
    }

    /**
     * Extracts the domain name from a URL for display purposes.
     */
    private fun String.extractDomain(): String {
        return try {
            this.removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("www.")
                .split("/")
                .first()
        } catch (e: Exception) {
            this
        }
    }
}
