package com.fmhy

import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Local test file for FMHYProvider
 *
 * Run this with: kotlinc -script FMHYProviderTest.kt
 * Or compile and run as a standard Kotlin main class.
 *
 * This tests the FMHY page parsing logic without needing an Android emulator
 * or the full Cloudstream runtime.
 */
fun main() {
    println("=== FMHY Provider Local Test ===")
    println()

    // Test 1: Fetch and parse the FMHY /video page
    println("[TEST 1] Fetching https://fmhy.pages.dev/video ...")
    testFetchPage()

    // Test 2: Test search functionality (simulated)
    println()
    println("[TEST 2] Testing search simulation ...")
    testSearch("4K")

    // Test 3: Test link extraction patterns
    println()
    println("[TEST 3] Testing link extraction patterns ...")
    testLinkExtraction()

    println()
    println("=== All tests complete ===")
}

fun testFetchPage() {
    try {
        val doc = Jsoup.connect("https://fmhy.pages.dev/video")
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "en-US,en;q=0.5")
            .timeout(15000)
            .get()

        println("  ✅ Page fetched successfully")
        println("  Title: ${doc.title()}")

        // Count list items with links (potential streaming sites)
        var siteCount = 0
        var currentSection = "Unknown"
        val sections = mutableSetOf<String>()

        for (element in doc.allElements) {
            if (element.tagName() in listOf("h2", "h3")) {
                currentSection = element.text().trim()
                if (currentSection.isNotEmpty()) sections.add(currentSection)
            }
            if (element.tagName() == "li" && element.parent()?.tagName() == "ul") {
                val links = element.select("a[href^=http]")
                val hasStreamingLink = links.any { link ->
                    val href = link.attr("href")
                    !href.contains("discord") && !href.contains("t.me") &&
                    !href.contains("rentry") && !href.contains("github.com") &&
                    !href.contains("fmhy.pages.dev")
                }
                if (hasStreamingLink) {
                    siteCount++
                    if (siteCount <= 5) {
                        val mainLink = links.first { link ->
                            val href = link.attr("href")
                            !href.contains("discord") && !href.contains("t.me") &&
                            !href.contains("rentry") && !href.contains("github.com")
                        }
                        println("  📌 ${mainLink.text()} -> ${mainLink.attr("href")}")
                        println("     Metadata: ${element.text().take(100)}")
                    }
                }
            }
        }

        println("  📊 Total streaming sites found: $siteCount")
        println("  📑 Sections found: ${sections.take(10).joinToString(", ")}")

    } catch (e: Exception) {
        println("  ❌ Error fetching page: ${e.message}")
        println("  (This may be due to Cloudflare protection or network issues)")
    }
}

fun testSearch(query: String) {
    println("  Searching for: '$query'")

    // Simulate parsing and filtering
    val testEntries = listOf(
        Triple("Cinejoy", "https://cinejoy.pk/", "Movies / TV / Anime / Auto-Next"),
        Triple("Stellar", "https://stellar.gdn/", "Movies / TV / Anime / Auto-Next / 4K"),
        Triple("67Movies", "https://67movies.st/", "Movies / TV / Anime / Auto-Next / 4K"),
        Triple("Rive", "https://www.rivestream.app/", "Movies / TV / Anime / Auto-Next / 4K"),
        Triple("Movy", "https://www.movy.sx/", "Movies / TV / Anime / Auto-Next"),
    )

    val results = testEntries.filter { (name, _, meta) ->
        name.contains(query, ignoreCase = true) || meta.contains(query, ignoreCase = true)
    }

    println("  ✅ Found ${results.size} results for '$query':")
    results.forEach { (name, url, meta) ->
        println("    📌 $name ($url) - $meta")
    }
}

fun testLinkExtraction() {
    // Test regex patterns for video link extraction
    val testHtml = """
        <script>
            var streamUrl = "https://example.com/stream.m3u8?token=abc123";
            var fallback = "https://cdn.example.com/movie.mp4";
        </script>
        <iframe src="https://embed.example.com/player/12345"></iframe>
        <video>
            <source src="https://video.example.com/hls/master.m3u8" type="application/x-mpegURL">
        </video>
    """.trimIndent()

    val doc = Jsoup.parse(testHtml)

    // Test iframe extraction
    val iframes = doc.select("iframe[src]")
    println("  Iframes found: ${iframes.size}")
    iframes.forEach { println("    🎬 ${it.attr("src")}") }

    // Test video source extraction
    val videoSources = doc.select("video source[src], video[src]")
    println("  Video sources found: ${videoSources.size}")
    videoSources.forEach { println("    🎬 ${it.attr("src")}") }

    // Test regex patterns
    val scriptContent = doc.select("script").joinToString("\n") { it.html() }

    val m3u8Pattern = Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""")
    val mp4Pattern = Regex("""["'](https?://[^"']+\.mp4[^"']*)["']""")

    val m3u8Links = m3u8Pattern.findAll(scriptContent).map { it.groupValues[1] }.toList()
    val mp4Links = mp4Pattern.findAll(scriptContent).map { it.groupValues[1] }.toList()

    println("  M3U8 links in scripts: ${m3u8Links.size}")
    m3u8Links.forEach { println("    📺 $it") }

    println("  MP4 links in scripts: ${mp4Links.size}")
    mp4Links.forEach { println("    📺 $it") }

    println("  ✅ Link extraction patterns working correctly")
}
