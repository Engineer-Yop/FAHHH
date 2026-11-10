package com.example.sanivend;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Emails the 6-digit code through EmailJS's REST API
 * (POST https://api.emailjs.com/api/v1.0/email/send). Runs off the main thread and
 * reports back on it.
 */
public final class EmailCodeSender {

    private static final String TAG = "EmailCodeSender";
    private static final ExecutorService NET = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback {
        /** @param error null when the email was accepted by EmailJS */
        void done(String error);
    }

    private EmailCodeSender() {}

    public static void send(final String toEmail, final String code, final Callback callback) {
        if (!EmailJsConfig.isSetUp()) {
            callback.done("Email sending isn't set up yet (EmailJsConfig.java needs the EmailJS IDs).");
            return;
        }
        NET.execute(() -> {
            String error;
            try {
                error = post(body(toEmail, code));
            } catch (Exception e) {
                Log.w(TAG, "Send failed", e);
                error = "Couldn't reach the email service. Check the internet connection.";
            }
            final String result = error;
            MAIN.post(() -> callback.done(result));
        });
    }

    static String body(String toEmail, String code) throws JSONException {
        JSONObject params = new JSONObject()
                .put("to_email", toEmail)
                .put("code", code)
                .put("minutes", String.valueOf(VerificationCodes.VALID_MS / 60000))
                .put("app_name", "SaniVend");
        JSONObject body = new JSONObject()
                .put("service_id", EmailJsConfig.SERVICE_ID)
                .put("template_id", EmailJsConfig.TEMPLATE_ID)
                .put("user_id", EmailJsConfig.PUBLIC_KEY)
                .put("template_params", params);
        if (!EmailJsConfig.PRIVATE_KEY.isEmpty()) body.put("accessToken", EmailJsConfig.PRIVATE_KEY);
        return body.toString();
    }

    /** @return null on success, otherwise a message for the user */
    private static String post(String json) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(EmailJsConfig.SEND_URL).openConnection();
        try {
            c.setRequestMethod("POST");
            c.setConnectTimeout(15000);
            c.setReadTimeout(15000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = c.getOutputStream()) {
                out.write(bytes);
            }
            int status = c.getResponseCode();
            if (status == 200) return null;
            String reply = read(c.getErrorStream());
            Log.w(TAG, "EmailJS answered " + status + ": " + reply);
            if (status == 403 && reply.toLowerCase().contains("non-browser")) {
                return "EmailJS blocks apps: turn on \"Allow EmailJS API for non-browser applications\" "
                        + "in EmailJS → Account → Security.";
            }
            if (status == 429) return "Too many emails right now. Wait a minute and tap Resend.";
            return "The email couldn't be sent (" + status + (reply.isEmpty() ? "" : ": " + reply) + ").";
        } finally {
            c.disconnect();
        }
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) return "";
        try (InputStream s = in; ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            byte[] b = new byte[1024];
            int n;
            while ((n = s.read(b)) > 0) buf.write(b, 0, n);
            return buf.toString("UTF-8").trim();
        }
    }
}
