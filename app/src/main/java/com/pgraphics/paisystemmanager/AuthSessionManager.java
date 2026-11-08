package com.pgraphics.paisystemmanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class AuthSessionManager {

    private static final String SUPABASE_URL =
            "https://pqomftvsddjhebdassjy.supabase.co";

    private static final String SUPABASE_KEY =
            "sb_publishable_ml3kl9b6o06ImrHwcIa9hg_PbSasRZw";

    private static final String AUTH_REDIRECT_URL =
            "com.pgraphics.paisystemmanager://auth/callback";

    private static final String KEY_ALIAS = "PAI_AUTH_ENCRYPTION_KEY";
    private static final String PREF_NAME = "pai_secure_auth";
    private static final String SESSION_KEY = "encrypted_session";
    private static final String IV_KEY = "session_iv";

    private static final int TIMEOUT = 20000;

    private static AuthSessionManager instance;

    private final Context context;
    private final SharedPreferences preferences;
    private final ExecutorService executor;
    private final Handler mainHandler;

    public interface AuthCallback {
        void onComplete(AuthResult result);
    }

    public static final class AuthResult {
        public final boolean success;
        public final String message;
        public final String userId;
        public final String email;
        public final String fullName;
        public final String accessToken;
        public final boolean requiresEmailConfirmation;

        AuthResult(
                boolean success,
                String message,
                String userId,
                String email,
                String fullName,
                String accessToken,
                boolean requiresEmailConfirmation
        ) {
            this.success = success;
            this.message = message;
            this.userId = userId;
            this.email = email;
            this.fullName = fullName;
            this.accessToken = accessToken;
            this.requiresEmailConfirmation = requiresEmailConfirmation;
        }
    }

    private AuthSessionManager(Context context) {
        this.context = context.getApplicationContext();

        preferences = this.context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
        );

        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized AuthSessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new AuthSessionManager(context);
        }

        return instance;
    }

    // =========================================================
    // REGISTRATION
    // =========================================================

    public void signUp(
            String fullName,
            String email,
            String password,
            AuthCallback callback
    ) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();

                body.put("email", email.trim());
                body.put("password", password);

                JSONObject metadata = new JSONObject();
                metadata.put("full_name", fullName.trim());

                body.put("data", metadata);

                String endpoint = "/auth/v1/signup?redirect_to="
                        + URLEncoder.encode(
                                AUTH_REDIRECT_URL,
                                "UTF-8"
                        );

                JSONObject response = request(
                        endpoint,
                        "POST",
                        body,
                        null
                );

                JSONObject user = response.optJSONObject("user");

                if (user == null) {
                    user = response;
                }

                String accessToken =
                        response.optString("access_token", "");

                String userId = user.optString("id", "");
                String returnedEmail = user.optString("email", email);
                String returnedName = "";

                JSONObject userMetadata =
                        user.optJSONObject("user_metadata");

                if (userMetadata != null) {
                    returnedName =
                            userMetadata.optString("full_name", "");
                }

                if (!accessToken.isEmpty()) {
                    saveSession(response);

                    deliver(callback, new AuthResult(
                            true,
                            "Your account has been created successfully.",
                            userId,
                            returnedEmail,
                            returnedName,
                            accessToken,
                            false
                    ));

                } else {
                    deliver(callback, new AuthResult(
                            true,
                            "Registration successful. Please check your email and confirm your account.",
                            userId,
                            returnedEmail,
                            returnedName,
                            "",
                            true
                    ));
                }

            } catch (Exception e) {
                deliverFailure(callback, e);
            }
        });
    }

    // =========================================================
    // LOGIN
    // =========================================================

    public void signIn(
            String email,
            String password,
            AuthCallback callback
    ) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();

                body.put("email", email.trim());
                body.put("password", password);

                JSONObject response = request(
                        "/auth/v1/token?grant_type=password",
                        "POST",
                        body,
                        null
                );

                saveSession(response);

                JSONObject user = response.optJSONObject("user");

                String userId = "";
                String returnedEmail = email;
                String fullName = "";

                if (user != null) {
                    userId = user.optString("id", "");
                    returnedEmail =
                            user.optString("email", email);

                    JSONObject metadata =
                            user.optJSONObject("user_metadata");

                    if (metadata != null) {
                        fullName =
                                metadata.optString("full_name", "");
                    }
                }

                deliver(callback, new AuthResult(
                        true,
                        "Login successful.",
                        userId,
                        returnedEmail,
                        fullName,
                        response.optString("access_token", ""),
                        false
                ));

            } catch (Exception e) {
                deliverFailure(callback, e);
            }
        });
    }

    // =========================================================
    // RESTORE SAVED SESSION
    // =========================================================

    public void restoreSession(AuthCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject savedSession = readSession();

                if (savedSession == null) {
                    deliver(callback, failure(
                            "No saved login session was found."
                    ));
                    return;
                }

                String refreshToken =
                        savedSession.optString("refresh_token", "");

                if (refreshToken.isEmpty()) {
                    clearSession();

                    deliver(callback, failure(
                            "Your saved session has expired. Please log in again."
                    ));
                    return;
                }

                JSONObject body = new JSONObject();
                body.put("refresh_token", refreshToken);

                JSONObject response = request(
                        "/auth/v1/token?grant_type=refresh_token",
                        "POST",
                        body,
                        null
                );

                saveSession(response);

                JSONObject user = response.optJSONObject("user");

                String userId = "";
                String email = "";
                String fullName = "";

                if (user != null) {
                    userId = user.optString("id", "");
                    email = user.optString("email", "");

                    JSONObject metadata =
                            user.optJSONObject("user_metadata");

                    if (metadata != null) {
                        fullName =
                                metadata.optString("full_name", "");
                    }
                }

                deliver(callback, new AuthResult(
                        true,
                        "Session restored successfully.",
                        userId,
                        email,
                        fullName,
                        response.optString("access_token", ""),
                        false
                ));

            } catch (Exception e) {
                clearSession();
                deliverFailure(callback, e);
            }
        });
    }

    // =========================================================
    // EMAIL CONFIRMATION DEEP LINK
    // =========================================================

    public void handleAuthCallback(
            Uri uri,
            AuthCallback callback
    ) {
        if (uri == null) {
            deliver(callback, failure(
                    "The confirmation link is invalid."
            ));
            return;
        }

        executor.execute(() -> {
            try {
                String accessToken = getCallbackParameter(
                        uri,
                        "access_token"
                );

                String refreshToken = getCallbackParameter(
                        uri,
                        "refresh_token"
                );

                String errorDescription = getCallbackParameter(
                        uri,
                        "error_description"
                );

                String errorCode = getCallbackParameter(
                        uri,
                        "error_code"
                );

                String error = getCallbackParameter(
                        uri,
                        "error"
                );

                if (!error.isEmpty()
                        || !errorCode.isEmpty()
                        || !errorDescription.isEmpty()) {

                    String message = !errorDescription.isEmpty()
                            ? errorDescription
                            : (!error.isEmpty() ? error : errorCode);

                    deliver(callback, failure(
                            "Email confirmation failed: " + message
                    ));
                    return;
                }

                if (accessToken.isEmpty()) {
                    String authorizationCode =
                            getCallbackParameter(uri, "code");

                    if (!authorizationCode.isEmpty()) {
                        deliver(callback, failure(
                                "The confirmation link returned an authorization code instead of a session. The current authentication flow requires an access-token confirmation link."
                        ));
                    } else {
                        deliver(callback, failure(
                                "The confirmation link did not contain an access token. Please request a new confirmation email."
                        ));
                    }

                    return;
                }

                if (refreshToken.isEmpty()) {
                    deliver(callback, failure(
                            "The confirmation link did not contain a refresh token. Please request a new confirmation email."
                    ));
                    return;
                }

                JSONObject user = request(
                        "/auth/v1/user",
                        "GET",
                        null,
                        accessToken
                );

                JSONObject session = new JSONObject();

                session.put("access_token", accessToken);
                session.put("refresh_token", refreshToken);
                session.put("token_type", "bearer");
                session.put("user", user);

                saveSession(session);

                String userId = user.optString("id", "");
                String email = user.optString("email", "");
                String fullName = "";

                JSONObject metadata =
                        user.optJSONObject("user_metadata");

                if (metadata != null) {
                    fullName =
                            metadata.optString("full_name", "");
                }

                deliver(callback, new AuthResult(
                        true,
                        "Your email has been confirmed successfully.",
                        userId,
                        email,
                        fullName,
                        accessToken,
                        false
                ));

            } catch (Exception e) {
                deliverFailure(callback, e);
            }
        });
    }

    private String getCallbackParameter(Uri uri, String name)
            throws Exception {

        String value = uri.getQueryParameter(name);

        if (value != null && !value.isEmpty()) {
            return value;
        }

        String fragment = uri.getFragment();

        if (fragment == null || fragment.isEmpty()) {
            return "";
        }

        String[] parameters = fragment.split("&");

        for (String parameter : parameters) {
            String[] parts = parameter.split("=", 2);

            if (parts.length == 2) {
                String key = URLDecoder.decode(
                        parts[0],
                        "UTF-8"
                );

                if (name.equals(key)) {
                    return URLDecoder.decode(
                            parts[1],
                            "UTF-8"
                    );
                }
            }
        }

        return "";
    }

    // =========================================================
    // PASSWORD RESET
    // =========================================================

    public void sendPasswordReset(
            String email,
            AuthCallback callback
    ) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("email", email.trim());

                String endpoint = "/auth/v1/recover?redirect_to="
                        + URLEncoder.encode(
                                AUTH_REDIRECT_URL,
                                "UTF-8"
                        );

                request(
                        endpoint,
                        "POST",
                        body,
                        null
                );

                deliver(callback, new AuthResult(
                        true,
                        "If an account exists for that email, a password-reset message has been sent.",
                        "",
                        email,
                        "",
                        "",
                        false
                ));

            } catch (Exception e) {
                deliverFailure(callback, e);
            }
        });
    }

    // =========================================================
    // LOGOUT
    // =========================================================

    public void signOut(AuthCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject session = readSession();

                if (session != null) {
                    String accessToken =
                            session.optString("access_token", "");

                    if (!accessToken.isEmpty()) {
                        try {
                            request(
                                    "/auth/v1/logout",
                                    "POST",
                                    new JSONObject(),
                                    accessToken
                            );
                        } catch (Exception ignored) {
                            // Always clear the local session.
                        }
                    }
                }

                clearSession();

                deliver(callback, new AuthResult(
                        true,
                        "You have been signed out.",
                        "",
                        "",
                        "",
                        "",
                        false
                ));

            } catch (Exception e) {
                clearSession();
                deliverFailure(callback, e);
            }
        });
    }

    public boolean hasSavedSession() {
        try {
            JSONObject session = readSession();

            return session != null
                    && !session.optString(
                            "refresh_token",
                            ""
                    ).isEmpty();

        } catch (Exception e) {
            return false;
        }
    }

    // =========================================================
    // SUPABASE HTTP REQUEST
    // =========================================================

    private JSONObject request(
            String endpoint,
            String method,
            JSONObject body,
            String accessToken
    ) throws Exception {

        URL url = new URL(SUPABASE_URL + endpoint);

        HttpURLConnection connection =
                (HttpURLConnection) url.openConnection();

        connection.setRequestMethod(method);
        connection.setConnectTimeout(TIMEOUT);
        connection.setReadTimeout(TIMEOUT);

        connection.setRequestProperty(
                "apikey",
                SUPABASE_KEY
        );

        connection.setRequestProperty(
                "Content-Type",
                "application/json"
        );

        connection.setRequestProperty(
                "Accept",
                "application/json"
        );

        if (accessToken != null && !accessToken.isEmpty()) {
            connection.setRequestProperty(
                    "Authorization",
                    "Bearer " + accessToken
            );
        }

        if (body != null) {
            connection.setDoOutput(true);

            try (OutputStream output =
                         connection.getOutputStream()) {

                output.write(
                        body.toString().getBytes(
              StandardCharsets.UTF_8
                        )
                );
            }
        }

        int statusCode = connection.getResponseCode();

        InputStream inputStream =
                statusCode >= 200 && statusCode < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream();

        StringBuilder responseText = new StringBuilder();

        if (inputStream != null) {
            try (BufferedReader reader =
                         new BufferedReader(
                                 new InputStreamReader(
                                         inputStream,
                                         StandardCharsets.UTF_8
                                 )
                         )) {

                String line;

                while ((line = reader.readLine()) != null) {
                    responseText.append(line);
                }
            }
        }

        connection.disconnect();

        JSONObject response;

        try {
            response = responseText.length() > 0
                    ? new JSONObject(responseText.toString())
                    : new JSONObject();

        } catch (Exception e) {
            response = new JSONObject();
        }

        if (statusCode < 200 || statusCode >= 300) {
            String message = response.optString(
                    "msg",
                    response.optString(
                            "message",
                            response.optString(
                                    "error_description",
                                    response.optString(
                                            "error",
                                            "Authentication request failed."
                                    )
                            )
                    )
            );

            throw new AuthException(message);
        }

        return response;
    }

    // =========================================================
    // ENCRYPTED SESSION STORAGE
    // =========================================================

    private void saveSession(JSONObject session) throws Exception {
        SecretKey secretKey = getOrCreateSecretKey();

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

        cipher.init(Cipher.ENCRYPT_MODE, secretKey);

        byte[] encrypted = cipher.doFinal(
                session.toString().getBytes(
                        StandardCharsets.UTF_8
                )
        );

        byte[] iv = cipher.getIV();

        preferences.edit()
                .putString(
                        SESSION_KEY,
                        Base64.encodeToString(
                                encrypted,
                                Base64.NO_WRAP
                        )
                )
                .putString(
                        IV_KEY,
                        Base64.encodeToString(
                                iv,
                                Base64.NO_WRAP
                        )
                )
                .apply();
    }

    private JSONObject readSession() throws Exception {
        String encryptedText = preferences.getString(
                SESSION_KEY,
                null
        );

        String ivText = preferences.getString(
                IV_KEY,
                null
        );

        if (encryptedText == null || ivText == null) {
            return null;
        }

        byte[] encrypted = Base64.decode(
                encryptedText,
                Base64.NO_WRAP
        );

        byte[] iv = Base64.decode(
                ivText,
                Base64.NO_WRAP
        );

        SecretKey secretKey = getOrCreateSecretKey();

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

        cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey,
                new GCMParameterSpec(128, iv)
        );

        byte[] decrypted = cipher.doFinal(encrypted);

        return new JSONObject(
                new String(
                        decrypted,
                        StandardCharsets.UTF_8
                )
        );
    }

    private SecretKey getOrCreateSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(
                    KEY_ALIAS,
                    null
            );
        }

        KeyGenerator keyGenerator =
                KeyGenerator.getInstance(
                        "AES",
                        "AndroidKeyStore"
                );

        android.security.keystore.KeyGenParameterSpec specification =
                new android.security.keystore.KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        android.security.keystore.KeyProperties.PURPOSE_ENCRYPT
                                | android.security.keystore.KeyProperties.PURPOSE_DECRYPT
                )
                        .setBlockModes(
                                android.security.keystore.KeyProperties.BLOCK_MODE_GCM
                        )
                        .setEncryptionPaddings(
                                android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE
                        )
                        .setRandomizedEncryptionRequired(true)
                        .build();

        keyGenerator.init(specification);

        return keyGenerator.generateKey();
    }

    private void clearSession() {
        preferences.edit()
                .remove(SESSION_KEY)
                .remove(IV_KEY)
                .apply();
    }

    // =========================================================
    // CALLBACK HELPERS
    // =========================================================

    private void deliver(
            AuthCallback callback,
            AuthResult result
    ) {
        if (callback == null) {
            return;
        }

        mainHandler.post(() -> callback.onComplete(result));
    }

    private void deliverFailure(
            AuthCallback callback,
            Exception exception
    ) {
        String message = exception.getMessage();

        if (message == null || message.trim().isEmpty()) {
            message = "An unexpected authentication error occurred.";
        }

        deliver(callback, failure(message));
    }

    private AuthResult failure(String message) {
        return new AuthResult(
                false,
                message,
                "",
                "",
                "",
                "",
                false
        );
    }

    private static class AuthException extends Exception {
        AuthException(String message) {
            super(message);
        }
    }
}
