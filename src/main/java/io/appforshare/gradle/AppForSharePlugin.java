package io.appforshare.gradle;

import com.android.build.api.artifact.SingleArtifact;
import com.android.build.api.variant.ApplicationAndroidComponentsExtension;
import com.android.build.api.variant.ApplicationVariant;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

/**
 * id("io.appforshare.upload"). With the Android application plugin every variant gets appforshareUpload&lt;Variant&gt;
 * (its APK) and appforshareUpload&lt;Variant&gt;Bundle (its .aab); both build the variant first. Without it,
 * appforshareUpload sends appforshare.file.
 */
public class AppForSharePlugin implements Plugin<Project> {
    public static final String VERSION = "1.0.0";
    static final String GROUP = "appforshare";

    @Override
    public void apply(Project project) {
        AppForShareExtension ext = project.getExtensions().create("appforshare", AppForShareExtension.class);
        ext.getApiToken().convention(project.getProviders().environmentVariable("APPFORSHARE_TOKEN"));
        ext.getApiUrl().convention(project.getProviders().environmentVariable("APPFORSHARE_API").orElse("https://appforshare.io"));
        ext.getChannel().convention(project.getProviders().environmentVariable("APPFORSHARE_CHANNEL"));

        TaskProvider<UploadTask> plain = project.getTasks().register("appforshareUpload", UploadTask.class, t -> {
            configure(project, t, ext, "appforshareUpload");
            t.setDescription("Uploads appforshare.file to AppForShare and prints the install link.");
            t.getFile().set(ext.getFile());
        });

        project.getPlugins().withId("com.android.application", p -> {
            ApplicationAndroidComponentsExtension android = project.getExtensions().getByType(ApplicationAndroidComponentsExtension.class);
            android.onVariants(android.selector().all(), (ApplicationVariant variant) -> {
                String name = variant.getName();
                String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);
                project.getTasks().register("appforshareUpload" + cap, UploadTask.class, t -> {
                    configure(project, t, ext, "appforshareUpload" + cap);
                    t.setDescription("Builds the " + name + " APK and uploads it to AppForShare.");
                    t.getApkDirectory().set(variant.getArtifacts().get(SingleArtifact.APK.INSTANCE));
                });
                project.getTasks().register("appforshareUpload" + cap + "Bundle", UploadTask.class, t -> {
                    configure(project, t, ext, "appforshareUpload" + cap + "Bundle");
                    t.setDescription("Builds the " + name + " .aab and uploads it to AppForShare (a universal APK is made from it).");
                    t.getFile().set(variant.getArtifacts().get(SingleArtifact.BUNDLE.INSTANCE));
                });
            });
            // The plain task is for projects without Android; keep it out of the way there.
            plain.configure(t -> t.setDescription("Uploads appforshare.file (use appforshareUpload<Variant> for Android variants)."));
        });
    }

    private static void configure(Project project, UploadTask t, AppForShareExtension ext, String name) {
        t.setGroup(GROUP);
        t.getApiToken().set(ext.getApiToken());
        t.getApiUrl().set(ext.getApiUrl());
        t.getReleaseNotes().set(ext.getReleaseNotes());
        t.getReleaseNotesFile().set(ext.getReleaseNotesFile());
        t.getChannel().set(ext.getChannel());
        t.getGroups().set(ext.getGroups());
        t.getTesters().set(ext.getTesters());
        t.getFailOn().set(ext.getFailOn());
        t.getResultFile().set(project.getLayout().getBuildDirectory().file("appforshare/" + name + ".json"));
    }
}
