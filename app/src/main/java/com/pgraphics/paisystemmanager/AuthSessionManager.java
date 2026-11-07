package com.pgraphics.paisystemmanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
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

    private static final String KEY_ALIAS =
            "PAI_AUTH_ENCRYPTION_KEY";

    private static final String PREF_NAME =
            "pai_secure_auth";

    private static final String SESSION_KEY =
            "encrypted_session";

    private static final String IV_KEY =
            "session_iv";

    private static final int TIMEOUT = 20000;

    private final Context context;

    private final ExecutorService executor;

    private final Handler mainHandler;

    private final SharedPreferences preferences;

    private static AuthSessionManager instance;

    public interface AuthCallback {
        void onComplete(AuthResult result);
    }

    public static class AuthResult {

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
            this.requiresEmailConfirmation =
                    requiresEmailConfirmation;
        }
    }

    private AuthSessionManager(Context context) {

        this.context = context.getApplicationContext();

        this.executor = Executors.newSingleThreadExecutor();

        this.mainHandler = new Handler(Looper.getMainLooper());

        this.preferences = this.context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
        );
    }

    public static synchronized AuthSessionManager getInstance(
            Context context
    ) {

        if (instance == null) {
            instance = new AuthSessionManager(context);
        }

        return instance;
    }

    // ==========================================
    // USER REGISTRATION
    // ==========================================

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

                JSONObject response = request(
                        "/auth/v1/signup",
                        "POST",
                        body,
                        null
                );

                String userId = "";
                String returnedEmail = email;
                String accessToken = "";

                JSONObject user = response.optJSONObject("user");

                if (user == null) {
                    user = response;
                }

                userId = user.optString("id", "");

                returnedEmail = user.optString(
                        "email",
                        email
                );

                JSONObject session = response;

                accessToken = session.optString(
                        "access_token",
                        ""
                );

                if (!accessToken.isEmpty()) {

                    saveSession(response);

                    deliver(callback, new AuthResult(
                            true,
                            "Registration successful.",
                            userId,
                            returnedEmail,
                            fullName,
                            accessToken,
                            false
                    ));

                } else {

                    deliver(callback, new AuthResult(
                            true,
                            "Registration received. Please check your email to confirm your account.",
                            userId,
                            returnedEmail,
                            fullName,
                            "",
                            true
                    ));
                }

            } catch (Exception e) {

                deliver(callback, failure(e.getMessage()));
            }
        });
    }

    // ==========================================
    // USER LOGIN
    // ==========================================

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

                    returnedEmail = user.optString(
                            "email",
                            email
                    );

                    JSONObject metadata =
                            user.optJSONObject("user_metadata");

                    if (metadata != null) {
                        fullName = metadata.optString(
                                "full_name",
                                ""
                        );
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

                deliver(callback, failure(e.getMessage()));
            }
        });
    }

    // ==========================================
    // RESTORE EXISTING SESSION
    // ==========================================

    public void restoreSession(AuthCallback callback) {

        executor.execute(() -> {

            try {

                JSONObject savedSession = readSession();

                if (savedSession == null) {

                    deliver(callback, failure(
                            "No saved login session."
                    ));

                    return;
                }

                String refreshToken = savedSession.optString(
                        "refresh_token",
                        ""
                );

                if (refreshToken.isEmpty()) {

                    clearSession();

                    deliver(callback, failure(
                            "Your login session has expired."
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
                        fullName = metadata.optString(
                                "full_name",
                                ""
                        );
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

                if (e instanceof AuthException
                        && ((AuthException) e).invalidSession) {

                    clearSession();
                }

                deliver(callback, failure(e.getMessage()));
            }
        });
    }

    // ==========================================
    // PASSWORD RECOVERY
    // ==========================================

    public void sendPasswordReset(
            String email,
            AuthCallback callback
    ) {

        executor.execute(() -> {

            try {

                JSONObject body = new JSONObject();

                body.put("email", email.trim());

                request(
                        "/auth/v1/recover",
                        "POST",
                        body,
                        null
                );

                deliver(callback, new AuthResult(
                        true,
                        "If an account exists for this email, a password reset message will be sent.",
                        "",
                        email,
                        "",
                        "",
                        false
                ));

            } catch (Exception e) {

                deliver(callback, failure(e.getMessage()));
            }
        });
    }

    // ==========================================
    // LOGOUT
    // ==========================================

    public void signOut(AuthCallback callback) {

        executor.execute(() -> {

            try {

                JSONObject savedSession = readSession();

                if (savedSession != null) {

                    String accessToken = savedSession.optString(
                            "access_token",
                            ""
                    );

                    if (!accessToken.isEmpty()) {

                        try {

                            request(
                                    "/auth/v1/logout",
                                    "POST",
                                    new JSONObject(),
                                    accessToken
                            );

                        } catch (Exception ignored) {
                            // Local logout must still succeed.
                        }
                    }
                }

                clearSession();

                deliver(callback, new AuthResult(
                        true,
                        "You have been logged out.",
                        "",
                        "",
                        "",
                        "",
                        false
                ));

            } catch (Exception e) {

                clearSession();

                deliver(callback, new AuthResult(
                        true,
                        "You have been logged out.",
                        "",
                        "",
                        "",
                        "",
                        false
                ));
            }
        });
    }

    // ==========================================
    // CHECK LOCAL SESSION
    // ==========================================

    public boolean hasSavedSession() {

        JSONObject session = readSession();

        return session != null
                && !session.optString(
                        "refresh_token",
                        ""
                ).isEmpty();
    }

    // ==========================================
    // SUPABASE HTTP CONNECTION
    // ==========================================

    private JSONObject request(
            String endpoint,
            String method,
            JSONObject body,
            String accessToken
    ) throws Exception {

        HttpURLConnection connection = null;

        try {

            URL url = new URL(
                    SUPABASE_URL + endpoint
            );

            connection = (HttpURLConnection)
                    url.openConnection();

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

            if (accessToken != null
                    && !accessToken.isEmpty()) {

                connection.setRequestProperty(
                        "Authorization",
                        "Bearer " + accessToken
                );
            }

            if (body != null) {

                connection.setDoOutput(true);

                byte[] data = body.toString().getBytes(
                        StandardCharsets.UTF_8
                );

                try (OutputStream output =
                             connection.getOutputStream()) {

                    output.write(data);
                    output.flush();
                }
            }

            int status = connection.getResponseCode();

            InputStream stream;

            if (status >= 200 && status < 300) {
                stream = connection.getInputStream();
            } else {
                stream = connection.getErrorStream();
            }

            String responseText = readStream(stream);

            JSONObject response;

            try {

                response = responseText.isEmpty()
                        ? new JSONObject()
                        : new JSONObject(responseText);

            } catch (JSONException e) {

                throw new Exception(
                        "Invalid response received from Supabase."
                );
            }

            if (status < 200 || status >= 300) {

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

                throw new AuthException(
                        message,
                        status == 400 || status == 401
                );
            }

            return response;

        } finally {

            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readStream(InputStream stream)
            throws Exception {

        if (stream == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        stream,
                        StandardCharsets.UTF_8
                )
        )) {

            String line;

            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        }

        return result.toString();
    }

    // ==========================================
    // ENCRYPTED SESSION STORAGE
    // ==========================================

    private void saveSession(JSONObject session)
            throws Exception {

        SecretKey key = getEncryptionKey();

        Cipher cipher = Cipher.getInstance(
                "AES/GCM/NoPadding"
        );

        cipher.init(
                Cipher.ENCRYPT_MODE,
                key
        );

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

    private JSONObject readSession() {

        try {

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

            SecretKey key = getEncryptionKey();

            Cipher cipher = Cipher.getInstance(
                    "AES/GCM/NoPadding"
            );

            GCMParameterSpec spec =
                    new GCMParameterSpec(128, iv);

            cipher.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    spec
            );

            byte[] decrypted = cipher.doFinal(encrypted);

            String json = new String(
                    decrypted,
                    StandardCharsets.UTF_8
            );

            return new JSONObject(json);

        } catch (Exception e) {

            clearSession();

            return null;
        }
    }

    private SecretKey getEncryptionKey()
            throws Exception {

        KeyStore keyStore = KeyStore.getInstance(
                "AndroidKeyStore"
        );

        keyStore.load(null);

        if (keyStore.containsAlias(KEY_ALIAS)) {

            return (SecretKey) keyStore.getKey(
                    KEY_ALIAS,
                    null
            );
        }

        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
        );

        KeyGenParameterSpec specification =
                new KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT
                                | KeyProperties.PURPOSE_DECRYPT
                )
                        .setBlockModes(
                                KeyProperties.BLOCK_MODE_GCM
                        )
                        .setEncryptionPaddings(
                                KeyProperties.ENCRYPTION_PADDING_NONE
                        )
                        .setRandomizedEncryptionRequired(true)
                        .build();

        generator.init(specification);

        return generator.generateKey();
    }

    private void clearSession() {

        preferences.edit()
                .remove(SESSION_KEY)
                .remove(IV_KEY)
                .apply();
    }

    // ==========================================
    // CALLBACK HELPERS
    // ==========================================

    private void deliver(
            AuthCallback callback,
            AuthResult result
    ) {

        if (callback == null) {
            return;
        }

        mainHandler.post(() -> callback.onComplete(result));
    }

    private AuthResult failure(String message) {

        return new AuthResult(
                false,
                message == null
                        ? "An unexpected authentication error occurred."
                        : message,
                "",
                "",
                "",
                "",
                false
        );
    }

    private static class AuthException extends Exception {

        final boolean invalidSession;

        AuthException(
                String message,
                boolean invalidSession
        ) {

            super(message);

            this.invalidSession = invalidSession;
        }
    }
}