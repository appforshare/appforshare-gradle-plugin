plugins {
    id("com.android.application")
    id("io.appforshare.upload")
}

android {
    namespace = "io.appforshare.sample"
    compileSdk = 36
    defaultConfig {
        applicationId = providers.gradleProperty("sampleAppId").getOrElse("io.appforshare.sample")
        minSdk = 24
        targetSdk = 36
        versionCode = providers.gradleProperty("sampleVersionCode").getOrElse("1").toInt()
        versionName = "1.0"
    }
}

appforshare {
    releaseNotes = providers.gradleProperty("notes")
    channel = providers.gradleProperty("afsChannel")
    groups = providers.gradleProperty("afsGroups").map { it.split(",") }.orElse(emptyList())
    failOn = providers.gradleProperty("afsFailOn")
}
