package io.appforshare.gradle;

import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Same resumable upload API as the website, the npm CLI and the fastlane plugin: create, PUT chunks at offsets,
 * complete, then poll until the build is ready. Retries network errors and 5xx with backoff.
 */
final class Client {
    static final class ApiError extends RuntimeException {
        ApiError(String message) {
            super(message);
        }
    }

    private final String token;
    private final String api;
    private final Consumer<String> log;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).followRedirects(HttpClient.Redirect.NORMAL).build();

    Client(String token, String api, Consumer<String> log) {
        this.token = token;
        this.api = api.replaceAll("/+$", "");
        this.log = log;
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> upload(Path file, String notes, String channel, List<String> groups, List<String> testers) throws IOException, InterruptedException {
        long size = file.toFile().length();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fileName", file.getFileName().toString());
        body.put("size", size);
        if (notes != null && !notes.isBlank()) body.put("releaseNotes", notes.length() > 5000 ? notes.substring(0, 5000) : notes);
        if (channel != null && !channel.isBlank()) body.put("channel", channel);
        if (!groups.isEmpty()) body.put("groups", groups);
        if (!testers.isEmpty()) body.put("testers", testers);
        Map<String, Object> created = request("POST", "/uploads", JsonOutput.toJson(body), null, null);
        String id = (String) created.get("id");
        String uploadToken = (String) created.get("token");
        long chunk = ((Number) created.get("chunkBytes")).longValue();
        long offset = created.get("offset") instanceof Number n ? n.longValue() : 0;
        log.accept(String.format("Uploading %s (%.1f MB)", file.getFileName(), size / 1024.0 / 1024.0));
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            int lastPct = -1;
            while (offset < size) {
                int len = (int) Math.min(chunk, size - offset);
                byte[] buf = new byte[len];
                raf.seek(offset);
                raf.readFully(buf);
                Map<String, Object> res = request("PUT", "/uploads/" + id + "?offset=" + offset, null, buf, uploadToken);
                offset = res.get("offset") instanceof Number n ? n.longValue() : offset + len;
                int pct = (int) (offset * 100 / size);
                if (pct >= lastPct + 10 || offset >= size) {
                    lastPct = pct;
                    log.accept("  " + pct + "%");
                }
            }
        }
        request("POST", "/uploads/" + id + "/complete", null, null, uploadToken);
        log.accept("Processing");
        long deadline = System.currentTimeMillis() + 20 * 60_000L;
        while (true) {
            Map<String, Object> s = request("GET", "/uploads/" + id, null, null, uploadToken);
            Object status = s.get("status");
            if ("FAILED".equals(status)) throw new ApiError(s.get("error") != null ? s.get("error").toString() : "The build could not be processed.");
            if ("READY".equals(status)) {
                // The channel is filled in right after the build is ready.
                for (int i = 0; i < 5 && body.containsKey("channel") && s.get("channel") == null; i++) {
                    Thread.sleep(1000);
                    s = request("GET", "/uploads/" + id, null, null, uploadToken);
                }
                return s;
            }
            if (System.currentTimeMillis() > deadline) throw new ApiError("Processing is taking too long. Check your dashboard.");
            Thread.sleep(2000);
        }
    }

    Map<String, Object> build(String slug) throws IOException, InterruptedException {
        return request("GET", "/ci/builds/" + slug, null, null, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> request(String method, String path, String json, byte[] bytes, String uploadToken) throws IOException, InterruptedException {
        for (int attempt = 1; ; attempt++) {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(api + "/api" + path))
                .timeout(Duration.ofMinutes(5))
                .header("Authorization", "Bearer " + token)
                .header("User-Agent", "appforshare-gradle-plugin/" + AppForSharePlugin.VERSION);
            if (uploadToken != null) b.header("X-Upload-Token", uploadToken);
            if (json != null) b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(json));
            else if (bytes != null) b.header("Content-Type", "application/octet-stream").method(method, HttpRequest.BodyPublishers.ofByteArray(bytes));
            else b.method(method, HttpRequest.BodyPublishers.noBody());
            HttpResponse<String> res;
            try {
                res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                if (attempt >= 6) throw new ApiError("Network error: " + e.getMessage());
                Thread.sleep(Math.min(30_000L, 1000L << attempt));
                continue;
            }
            int code = res.statusCode();
            if (code >= 500 && attempt < 6) {
                Thread.sleep(Math.min(30_000L, 1000L << attempt));
                continue;
            }
            Object parsed = res.body() == null || res.body().isBlank() ? Map.of() : safeParse(res.body());
            Map<String, Object> data = parsed instanceof Map ? (Map<String, Object>) parsed : Map.of();
            // 409 means "already have these bytes" on the resumable upload only; 400 with an offset is a resume hint.
            boolean resumed = code == 409 && path.startsWith("/uploads");
            boolean offsetReply = code == 400 && data.get("offset") instanceof Number;
            if ((code < 200 || code > 299) && !resumed && !offsetReply) {
                Object msg = data.get("message");
                if (msg instanceof List<?> l && !l.isEmpty()) msg = l.get(0);
                throw new ApiError(msg != null ? msg.toString() : "Request failed with " + code);
            }
            return data;
        }
    }

    private static Object safeParse(String s) {
        try {
            return new JsonSlurper().parseText(s);
        } catch (RuntimeException e) {
            return Map.of();
        }
    }
}
