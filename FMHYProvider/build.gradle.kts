plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.lagradost.cloudstream3.gradle")
}

android {
    namespace = "com.fmhy"
    compileSdk = 35
    defaultConfig {
        minSdk = 21
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(false)
        freeCompilerArgs.addAll(
            "-Xno-call-assertions",
            "-Xno-param-assertions",
            "-Xno-receiver-assertions"
        )
    }
}

cloudstream {
    setRepo(
        System.getenv("GITHUB_REPOSITORY")
            ?: "Aarmotay/Fmhy-Cloudstream"
    )

    description = "FMHY Video Directory - Browse curated free streaming sites from freemediaheckyeah"
    authors = listOf("Ajay902188")
    status = 1
    tvTypes = listOf("Movie", "TvSeries", "Anime")
    language = "en"
    iconUrl = "https://fmhy.pages.dev/logo.png"
}

dependencies {
    val cloudstream by configurations
    cloudstream("com.lagradost:cloudstream3:pre-release")
    implementation("com.github.Blatzar:NiceHttp:0.4.11")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
}
