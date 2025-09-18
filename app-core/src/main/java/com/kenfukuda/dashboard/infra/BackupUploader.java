package com.kenfukuda.dashboard.infra;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * Simple BackupUploader that uploads a file to a given URL using HTTP PUT.
 * This is intentionally minimal so it can work with presigned S3 URLs or simple HTTP endpoints.
 */
public class BackupUploader {

    private final int maxRetries;

    public BackupUploader() {
        this(1);
    }

    public BackupUploader(int maxRetries) {
        this.maxRetries = Math.max(1, maxRetries);
    }

    public void upload(Path file, URL uploadUrl) throws IOException {
        upload(file, uploadUrl, Map.of());
    }

    public void upload(Path file, URL uploadUrl, Map<String, String> headers) throws IOException {
        byte[] data = Files.readAllBytes(file);
        IOException lastEx = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            HttpURLConnection conn = (HttpURLConnection) uploadUrl.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            conn.setRequestProperty("Content-Length", String.valueOf(data.length));
            // apply headers
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    conn.setRequestProperty(e.getKey(), e.getValue());
                }
            }
            try (var out = conn.getOutputStream()) {
                out.write(data);
            } catch (IOException e) {
                lastEx = e;
                // retry loop
                if (attempt == maxRetries) throw e; else continue;
            }
            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) return;
            InputStream err = conn.getErrorStream();
            String msg;
            try {
                msg = err == null ? "status=" + code : new String(err.readAllBytes());
            } catch (Exception ex) {
                msg = "status=" + code;
            }
            lastEx = new IOException("Upload failed: " + msg);
            if (attempt == maxRetries) throw lastEx;
            try { Thread.sleep(Duration.ofMillis(200).toMillis()); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw new IOException("Interrupted"); }
        }
        if (lastEx != null) throw lastEx; else throw new IOException("Unknown upload error");
    }
}
