# AppForShare Gradle plugin

Upload Android builds to [AppForShare](https://appforshare.io) from Gradle and get an install link for your testers.
Every variant gets two tasks that build it first:

- `appforshareUpload<Variant>` sends the APK (for example `appforshareUploadDebug`)
- `appforshareUpload<Variant>Bundle` sends the .aab; AppForShare turns it into a universal APK testers can install

```kotlin
// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("io.appforshare.upload") version "1.0.0"
}

appforshare {
    releaseNotes = "Fixes the login crash"   // or releaseNotesFile = file("notes.txt")
    channel = "beta"                          // its link always opens the newest build in it
    groups = listOf("Client QA")              // Team: share with tester groups when ready
    testers = listOf("pm@client.com")         // Team: share with testers when ready
    failOn = "blocked"                        // or "review": fail on the store readiness check
}
```

```sh
APPFORSHARE_TOKEN=afs_... ./gradlew appforshareUploadRelease
```

The token comes from Dashboard, Settings, API tokens (Pro and Team plans). `APPFORSHARE_API` and
`APPFORSHARE_CHANNEL` are read from the environment too. After the task, `build/appforshare/<task>.json` holds
`url`, `slug`, `readiness` and `channelUrl` for later steps. With `failOn` the build is still uploaded and its
link works; only the task fails. The store readiness check is guidance about current Google Play rules, not a
promise of approval.

Without the Android plugin, `appforshareUpload` sends `appforshare { file = ... }`.

Works with the Android Gradle Plugin 8 and 9, and with the configuration cache. MIT license.
