// Test app for the plugin: builds the plugin from the parent folder. Used by apps/api/scripts/e2e/gradle-plugin.ts.
pluginManagement {
    includeBuild("..")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "appforshare-sample"
include(":app")
