package com.hollowhaven.remnant;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AiVideoWorkerClient {
    interface Callback { void done(boolean ok, String message, String value); }

    private final Context context;
    AiVideoWorkerClient(Context context) { this.context = context.getApplicationContext(); }

    void health(String baseUrl, Callback cb) { new Thread(() -> {
        try {
            String root = normalize(baseUrl);
            HttpURLConnection c = open(root + "/api/health", "GET");
            int code = c.getResponseCode();
            String body = read(code >= 400 ? c.getErrorStream() : c.getInputStream());
            if (code / 100 != 2) throw new IOException("AI bridge HTTP " + code + ": " + body);
            JSONObject d = new JSONObject(body);
            if (!d.optBoolean("ok", false)) throw new IOException("AI bridge reported unhealthy.");
            if (!d.optBoolean("configured", false)) throw new IOException("AI bridge is not configured with its server-side WaveSpeed key.");
            cb.done(true, "Wan 2.2 Animate 2 bridge online.", "ok");
        } catch (Throwable e) {
            cb.done(false, e.getMessage() == null ? "AI service health check failed" : e.getMessage(), "");
        }
    }, "wan-health").start(); }

    void render(String baseUrl, Uri reference, Uri driving, String prompt, int steps, Callback cb) { new Thread(() -> {
        try {
            String root = normalize(baseUrl);
            String boundary = "----Hollowhaven" + UUID.randomUUID();
            HttpURLConnection c = (HttpURLConnection) new URL(root + "/api/generate").openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(20000);
            c.setReadTimeout(120000);
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            c.setRequestProperty("Accept", "application/json");
            try (OutputStream out = new BufferedOutputStream(c.getOutputStream())) {
                writeFilePart(out, boundary, "image", "reference.png", "image/png", reference);
                writeFilePart(out, boundary, "video", "driving.mp4", "video/mp4", driving);
                writeTextPart(out, boundary, "resolution", "480p");
                if (prompt != null && !prompt.trim().isEmpty()) writeTextPart(out, boundary, "prompt", prompt.trim());
                writeTextPart(out, boundary, "motion_prompt", "Animate the Remnant naturally using the driving video motion. Preserve the exact creature identity, anatomy, proportions, colors, face, vines, leaves, feathers and glowing core.");
                out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            }
            int code = c.getResponseCode();
            String body = read(code >= 400 ? c.getErrorStream() : c.getInputStream());
            if (code / 100 != 2) throw new IOException("AI bridge HTTP " + code + ": " + body);
            JSONObject rootJson = new JSONObject(body);
            String id = rootJson.optString("id", "");
            if (id.isEmpty()) throw new IOException("AI bridge did not return a prediction id.");
            cb.done(true, "queued", id);
        } catch (Throwable e) {
            cb.done(false, e.getMessage() == null ? "Render request failed" : e.getMessage(), "");
        }
    }, "wan-render").start(); }

    void pollUntilDone(String baseUrl, String jobId, Callback cb) { new Thread(() -> {
        try {
            String root = normalize(baseUrl);
            String url = root + "/api/result/" + URLEncoder.encode(jobId, "UTF-8");
            int delay = 2500;
            for (int i = 0; i < 720; i++) {
                HttpURLConnection c = null;
                int code;
                String body;
                try {
                    c = open(url, "GET");
                    code = c.getResponseCode();
                    body = read(code >= 400 ? c.getErrorStream() : c.getInputStream());
                } catch (IOException transientError) {
                    Thread.sleep(Math.min(10000, delay));
                    delay = Math.min(10000, delay + 500);
                    continue;
                }
                if (code == 429 || code == 500 || code == 502 || code == 503 || code == 504) {
                    Thread.sleep(Math.min(10000, delay));
                    delay = Math.min(10000, delay + 500);
                    continue;
                }
                if (code / 100 != 2) throw new IOException("AI result HTTP " + code + ": " + body);

                JSONObject data = new JSONObject(body);
                String status = data.optString("status", "processing");
                if ("completed".equalsIgnoreCase(status)) {
                    JSONArray outs = data.optJSONArray("outputs");
                    if (outs == null || outs.length() == 0) throw new IOException("AI completed without output.");
                    String result = outs.optString(0, "");
                    if (result.isEmpty()) throw new IOException("AI returned an empty output.");
                    cb.done(true, "completed", result);
                    return;
                }
                if ("failed".equalsIgnoreCase(status) || "cancelled".equalsIgnoreCase(status) || "timeout".equalsIgnoreCase(status) || "deleted".equalsIgnoreCase(status)) {
                    throw new IOException(data.optString("error", status));
                }
                cb.done(true, "progress", status);
                Thread.sleep(delay);
                delay = Math.min(10000, delay + 500);
            }
            throw new IOException("AI job polling exceeded 60 minutes.");
        } catch (Throwable e) {
            cb.done(false, e.getMessage() == null ? "Job polling failed" : e.getMessage(), "");
        }
    }, "wan-poll").start(); }

    File downloadResult(String url) throws Exception {
        HttpURLConnection c = open(url, "GET");
        int code = c.getResponseCode();
        if (code / 100 != 2) throw new IOException("Result download HTTP " + code);
        return downloadStream(c.getInputStream());
    }

    private File downloadStream(InputStream in) throws Exception {
        File out = new File(context.getCacheDir(), "remnant-ai-" + System.currentTimeMillis() + ".mp4");
        try (InputStream x = in; OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
            byte[] b = new byte[64 * 1024];
            int n; long total = 0;
            while ((n = x.read(b)) != -1) {
                total += n;
                if (total > 200L * 1024L * 1024L) throw new IOException("Result exceeds 200 MB.");
                os.write(b, 0, n);
            }
        }
        return out;
    }

    private HttpURLConnection open(String url, String method) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15000);
        c.setReadTimeout(120000);
        c.setRequestProperty("Accept", "application/json");
        return c;
    }

    private static String normalize(String u) {
        String x = u == null ? "" : u.trim();
        while (x.endsWith("/")) x = x.substring(0, x.length() - 1);
        if (!x.startsWith("https://") && !x.startsWith("http://")) {
            throw new IllegalArgumentException("AI bridge URL must start with http:// or https://.");
        }
        return x;
    }

    private static void writeTextPart(OutputStream out, String b, String name, String value) throws IOException {
        out.write(("--" + b + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private void writeFilePart(OutputStream out, String b, String field, String filename, String mime, Uri uri) throws IOException {
        out.write(("--" + b + "\r\nContent-Disposition: form-data; name=\"" + field + "\"; filename=\"" + filename + "\"\r\nContent-Type: " + mime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Could not open selected input.");
            byte[] x = new byte[64 * 1024];
            int n; long total = 0;
            while ((n = in.read(x)) != -1) {
                total += n;
                if (total > 200L * 1024L * 1024L) throw new IOException("Input exceeds 200 MB.");
                out.write(x, 0, n);
            }
        }
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) return "";
        try (InputStream x = in; ByteArrayOutputStream o = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192]; int n;
            while ((n = x.read(b)) != -1) o.write(b, 0, n);
            return o.toString(StandardCharsets.UTF_8);
        }
    }
}
