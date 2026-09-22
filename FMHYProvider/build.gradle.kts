version = 1

cloudstream {
    language = "en"

    description = "FMHY Video Directory - Browse and stream from curated free movie/TV streaming sites listed on freemediaheckyeah"

    authors = listOf("FMHY Extension Dev")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     */
    status = 1 // running

    tvTypes = listOf(
        "Movie",
        "TvSeries",
        "Anime",
    )

    iconUrl = "https://fmhy.pages.dev/logo.png"
}
