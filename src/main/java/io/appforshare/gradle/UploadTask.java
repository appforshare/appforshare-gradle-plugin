package io.appforshare.gradle;

import groovy.json.JsonOutput;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/** Uploads one .apk or .aab to AppForShare, prints the install link and writes it to build/appforshare/. */
@DisableCachingByDefault(because = "Uploading is a side effect")
public abstract class UploadTask extends DefaultTask {
    /** A single file (appforshareUpload{@literal <Variant>}Bundle, or appforshareUpload with appforshare.file). */
    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getFile();

    /** The APK folder of a variant; the one universal APK in it is sent. */
    @InputDirectory
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getApkDirectory();

    @Internal
    public abstract Property<String> getApiToken();

    @Input
    public abstract Property<String> getApiUrl();

    @Input
    @Optional
    public abstract Property<String> getReleaseNotes();

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getReleaseNotesFile();

    @Input
    @Optional
    public abstract Property<String> getChannel();

    @Input
    public abstract ListProperty<String> getGroups();

    @Input
    public abstract ListProperty<String> getTesters();

    @Input
    @Optional
    public abstract Property<String> getFailOn();

    /** JSON with url, slug, readiness and channelUrl, for later steps of the build. */
    @OutputFile
    public abstract RegularFileProperty getResultFile();

    public UploadTask() {
        getOutputs().upToDateWhen(t -> false);
    }

    private File target() {
        if (getFile().isPresent()) return getFile().get().getAsFile();
        if (!getApkDirectory().isPresent()) throw new GradleException("No build to upload. Set appforshare { file = ... } or apply the Android application plugin.");
        File[] apks = getApkDirectory().get().getAsFile().listFiles((d, n) -> n.endsWith(".apk"));
        if (apks == null || apks.length == 0) throw new GradleException("No .apk in " + getApkDirectory().get().getAsFile());
        if (apks.length == 1) return apks[0];
        // Split APKs: send the universal one when there is one.
        return Arrays.stream(apks).filter(f -> f.getName().contains("universal")).findFirst()
            .orElseThrow(() -> new GradleException("This variant makes " + apks.length + " APKs (splits). Turn on a universal APK, or upload the .aab with the Bundle task."));
    }

    @TaskAction
    public void upload() throws IOException, InterruptedException {
        String token = getApiToken().getOrNull();
        if (token == null || token.isBlank()) throw new GradleException("No AppForShare API token. Set APPFORSHARE_TOKEN or appforshare { apiToken = ... }. Create one in Dashboard, Settings.");
        String failOn = getFailOn().getOrNull();
        if (failOn != null && !failOn.equals("blocked") && !failOn.equals("review")) throw new GradleException("failOn takes \"blocked\" or \"review\"");
        File file = target();
        String notes = getReleaseNotes().getOrNull();
        if ((notes == null || notes.isBlank()) && getReleaseNotesFile().isPresent()) {
            notes = Files.readString(getReleaseNotesFile().get().getAsFile().toPath(), StandardCharsets.UTF_8);
        }
        Client client = new Client(token, getApiUrl().get(), msg -> getLogger().lifecycle(msg));
        Map<String, Object> status;
        try {
            status = client.upload(file.toPath(), notes, getChannel().getOrNull(), getGroups().get(), getTesters().get());
        } catch (Client.ApiError e) {
            throw new GradleException("AppForShare: " + e.getMessage(), e);
        }
        String url = String.valueOf(status.get("url"));
        String slug = String.valueOf(status.get("slug"));
        String readiness = "unknown";
        List<?> findings = List.of();
        try {
            Map<String, Object> detail = client.build(slug);
            if (detail.get("readinessReport") instanceof Map<?, ?> r) {
                readiness = String.valueOf(r.get("status"));
                if (r.get("findings") instanceof List<?> f) findings = f;
            }
        } catch (Client.ApiError e) {
            if (failOn != null) throw new GradleException("AppForShare: store readiness not read: " + e.getMessage(), e);
            getLogger().warn("Store readiness not read: " + e.getMessage());
        }
        Object channel = status.get("channel");
        String channelUrl = channel instanceof Map<?, ?> c ? String.valueOf(c.get("url")) : null;
        getLogger().lifecycle("Install link: " + url);
        if (channel instanceof Map<?, ?> c) getLogger().lifecycle("Channel " + c.get("name") + ": " + channelUrl);
        else if (getChannel().isPresent()) getLogger().warn("Not put into channel " + getChannel().get() + ". Check the build page.");
        getLogger().lifecycle("Store readiness: " + readiness);
        for (Object f : findings) {
            if (f instanceof Map<?, ?> m) getLogger().warn("[" + m.get("level") + "] " + m.get("title") + ": " + m.get("fix"));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("url", url);
        result.put("slug", slug);
        result.put("readiness", readiness);
        result.put("channelUrl", channelUrl);
        File out = getResultFile().get().getAsFile();
        out.getParentFile().mkdirs();
        Files.writeString(out.toPath(), JsonOutput.prettyPrint(JsonOutput.toJson(result)), StandardCharsets.UTF_8);
        if (("blocked".equals(failOn) && "blocked".equals(readiness)) || ("review".equals(failOn) && ("blocked".equals(readiness) || "review".equals(readiness)))) {
            throw new GradleException("Store readiness is \"" + readiness + "\" (failOn " + failOn + "). The build is uploaded and its link works: " + url);
        }
    }
}
