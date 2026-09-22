// Use an integer for version numbers
version = 1

cloudstream {
    // All of these properties are optional, you can safely remove any of them.

    description = "FMHY Video Directory - Browse and stream from curated free movie/TV/anime streaming sites listed on freemediaheckyeah"
    authors = listOf("Ajay902188")

    /**
    * Status int as one of the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta-only
    **/
    status = 1

    tvTypes = listOf(
        "Movie",
        "TvSeries",
        "Anime"
    )

    language = "en"

    iconUrl = "https://fmhy.pages.dev/logo.png"
}
