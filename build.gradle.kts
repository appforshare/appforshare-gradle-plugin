plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "2.0.0"
}

group = "io.appforshare"
version = "1.0.0"

repositories {
    google()
    mavenCentral()
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(17) }
    withSourcesJar()
}

dependencies {
    // Only the public variant API; the app's own Android Gradle Plugin is used at run time.
    compileOnly("com.android.tools.build:gradle-api:8.7.2")
}

gradlePlugin {
    website = "https://appforshare.io/docs/ci"
    vcsUrl = "https://github.com/appforshare/appforshare-gradle-plugin"
    plugins {
        create("appforshare") {
            id = "io.appforshare.upload"
            implementationClass = "io.appforshare.gradle.AppForSharePlugin"
            displayName = "AppForShare upload"
            description = "Upload Android APK and AAB builds to AppForShare and get an install link for testers."
            tags = listOf("android", "distribution", "testing", "apk", "aab", "beta")
        }
    }
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}
