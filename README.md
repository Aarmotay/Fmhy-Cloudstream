# FMHY Provider - Cloudstream 3 Extension

A Cloudstream 3 extension that scrapes the [FMHY Video Directory](https://fmhy.pages.dev/video) and presents curated free streaming sites as browsable categories.

## Features

- 📂 **Browse by Category** - Stream Aggregators, Dedicated Servers, P-Stream Forks, and more
- 🔍 **Search** - Find streaming sites by name, content type, or features
- ⭐ **4K Sites** - Highlighted section for sites supporting 4K content
- 🔗 **Mirror Links** - Automatic mirror detection for each site
- 🎬 **Video Extraction** - Attempts to extract playable streams from target sites
- 🛡️ **Anti-Bot Bypass** - Realistic headers and Cloudflare handling

## Installation

### Add Repository to Cloudstream

1. Open Cloudstream 3
2. Go to **Settings** → **Extensions** → **Add Repository**
3. Enter the repository URL:
   ```
   https://github.com/YOUR_USERNAME/ext/releases/download/builds/repo.json
   ```
4. Install the "FMHY Provider" extension

## Project Structure

```
ext/
├── build.gradle.kts                          # Root build file
├── settings.gradle.kts                       # Module includes
├── gradle.properties                         # Gradle config
├── .github/
│   └── workflows/
│       └── build.yml                         # CI/CD pipeline
└── FMHYProvider/
    ├── build.gradle.kts                      # Plugin metadata
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   └── kotlin/com/fmhy/
        │       ├── FMHYProvider.kt           # Core provider logic
        │       └── FMHYProviderPlugin.kt     # Plugin entry point
        └── test/
            └── kotlin/com/fmhy/
                └── FMHYProviderTest.kt       # Local tests
```

## How It Works

1. **Scrapes FMHY** - Fetches `https://fmhy.pages.dev/video` and parses the DOM
2. **Categorizes Sites** - Groups streaming sites by section (Stream Aggregators, Dedicated Server, etc.)
3. **Extracts Metadata** - Parses content types (Movies/TV/Anime), features (4K, Auto-Next), mirrors
4. **Provides Links** - When a user selects a site, provides the direct URL and mirrors
5. **Attempts Extraction** - Tries to find embedded video players (.m3u8, .mp4) on target sites

## Development

### Local Testing

The test file can be run independently to verify parsing logic:

```bash
# Compile and run the test
cd FMHYProvider/src/test/kotlin/com/fmhy/
kotlinc FMHYProviderTest.kt -include-runtime -d test.jar && java -jar test.jar
```

### Building

```bash
./gradlew make
```

The compiled `.cs3` file will be in the `builds/` directory.

## GitHub Deployment

1. Push this repo to your GitHub account
2. Go to **Settings** → **Actions** → **General** → **Workflow permissions**
3. Set to **"Read and write permissions"**
4. The GitHub Action will automatically build and release on every push to `main`

## License

MIT
