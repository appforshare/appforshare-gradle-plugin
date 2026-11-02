package io.appforshare.gradle;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * <pre>
 * appforshare {
 *     apiToken = providers.environmentVariable("APPFORSHARE_TOKEN")   // the default
 *     releaseNotes = "Fixes the login crash"                          // or releaseNotesFile
 *     channel = "beta"
 *     groups = listOf("Client QA")
 *     testers = listOf("pm@client.com")
 *     failOn = "blocked"                                              // or "review"
 * }
 * </pre>
 */
public abstract class AppForShareExtension {
    /** API token (Dashboard, Settings, API tokens). Defaults to the APPFORSHARE_TOKEN environment variable. */
    public abstract Property<String> getApiToken();

    /** API origin, https://appforshare.io unless APPFORSHARE_API is set. */
    public abstract Property<String> getApiUrl();

    /** Release notes shown to testers (Pro and Team). */
    public abstract Property<String> getReleaseNotes();

    /** File with the release notes; used when releaseNotes is not set. */
    public abstract RegularFileProperty getReleaseNotesFile();

    /** Put the build into this channel (beta, live), created on first use (Pro and Team). */
    public abstract Property<String> getChannel();

    /** Tester groups to share the build with when it is ready (Team). */
    public abstract ListProperty<String> getGroups();

    /** Tester emails to share the build with when it is ready (Team). */
    public abstract ListProperty<String> getTesters();

    /** "blocked" or "review": fail the task on the store readiness check. The build stays uploaded. */
    public abstract Property<String> getFailOn();

    /** For appforshareUpload without the Android plugin: the .apk or .aab to send. */
    public abstract RegularFileProperty getFile();
}
