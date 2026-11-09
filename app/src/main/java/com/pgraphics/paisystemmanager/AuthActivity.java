package com.pgraphics.paisystemmanager;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;
import android.util.Log;

public class AuthActivity extends Activity {

    private static final String TAG = "PAI_AUTH";

    private static final int BLUE = Color.rgb(35, 115, 255);
    private static final int CYAN = Color.rgb(35, 205, 255);
    private static final int BACKGROUND = Color.rgb(7, 13, 29);
    private static final int WHITE = Color.WHITE;
    private static final int MUTED = Color.rgb(155, 170, 195);

    private static final long NO_SESSION_SPLASH_DELAY_MS = 550L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private LinearLayout root;
    private LinearLayout form;

    private EditText nameInput;
    private EditText emailInput;
    private EditText passwordInput;
    private EditText confirmPasswordInput;

    private TextView title;
    private TextView subtitle;
    private TextView switchMode;
    private TextView startupStatus;

    private Button actionButton;
    private TextView forgotPassword;

    private boolean registrationMode = false;
    private boolean busy = false;
    private boolean callbackProcessing = false;
    private boolean navigationStarted = false;
    private boolean authInterfaceBuilt = false;
    private boolean startupScreenVisible = false;

    private AuthSessionManager sessionManager;

    private final Runnable revealAuthAfterDelay = () -> {
        if (!isFinishing() && !isDestroyed()
                && !navigationStarted && !callbackProcessing) {
            showAuthenticationInterface();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);

        sessionManager = AuthSessionManager.getInstance(this);

        // Always show the branded startup screen first. The login form is
        // deliberately not constructed until session restoration is resolved.
        showStartupScreen();

        if (!processAuthenticationIntent(getIntent())) {
            restoreSavedSession();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        if (isAuthenticationCallback(intent)) {
            mainHandler.removeCallbacks(revealAuthAfterDelay);
            showStartupScreen();
            processAuthenticationIntent(intent);
        }
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacks(revealAuthAfterDelay);
        super.onDestroy();
    }

    // =========================================================
    // BRANDED STARTUP SCREEN
    // =========================================================

    private void showStartupScreen() {
        startupScreenVisible = true;

        LinearLayout splash = new LinearLayout(this);
        splash.setOrientation(LinearLayout.VERTICAL);
        splash.setGravity(Gravity.CENTER);
        splash.setPadding(dp(28), dp(30), dp(28), dp(30));
        splash.setBackgroundColor(BACKGROUND);

        // Use the existing official launcher artwork. This is not a recreated
        // text logo; the drawable must remain the approved P AI artwork with ™.
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.p_ai_launcher);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setAlpha(0.25f);
        logo.setScaleX(0.94f);
        logo.setScaleY(0.94f);

        LinearLayout.LayoutParams logoParams =
                new LinearLayout.LayoutParams(dp(190), dp(190));
        logoParams.gravity = Gravity.CENTER;
        splash.addView(logo, logoParams);

        TextView brand = new TextView(this);
        brand.setText("P AI SYSTEM MANAGER");
        brand.setTextColor(WHITE);
        brand.setTextSize(20);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        brand.setGravity(Gravity.CENTER);
        brand.setLetterSpacing(0.06f);

        LinearLayout.LayoutParams brandParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        brandParams.topMargin = dp(16);
        splash.addView(brand, brandParams);

        TextView tagline = new TextView(this);
        tagline.setText("Your intelligent personal assistant");
        tagline.setTextColor(MUTED);
        tagline.setTextSize(13);
        tagline.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams taglineParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        taglineParams.topMargin = dp(7);
        splash.addView(tagline, taglineParams);

        ProgressBar progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        progress.setIndeterminateTintList(
                android.content.res.ColorStateList.valueOf(CYAN));

        LinearLayout.LayoutParams progressParams =
                new LinearLayout.LayoutParams(dp(34), dp(34));
        progressParams.gravity = Gravity.CENTER;
        progressParams.topMargin = dp(30);
        splash.addView(progress, progressParams);

        startupStatus = new TextView(this);
        startupStatus.setText("Preparing your workspace...");
        startupStatus.setTextColor(MUTED);
        startupStatus.setTextSize(12);
        startupStatus.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams statusParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(13);
        splash.addView(startupStatus, statusParams);

        setContentView(splash);

        logo.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(700)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void updateStartupStatus(String message) {
        if (startupStatus != null && startupScreenVisible) {
            startupStatus.setText(message);
        }
    }

    private void showAuthenticationInterface() {
        if (isFinishing() || isDestroyed() || navigationStarted) return;

        mainHandler.removeCallbacks(revealAuthAfterDelay);
        startupScreenVisible = false;

        if (!authInterfaceBuilt) {
            buildInterface();
            authInterfaceBuilt = true;
        } else {
            setContentView(root);
        }
    }

    // =========================================================
    // SUPABASE DEEP LINK HANDLING
    // =========================================================

    private boolean isAuthenticationCallback(Intent intent) {
        if (intent == null) return false;

        Uri uri = intent.getData();
        if (uri == null) return false;

        return "com.pgraphics.paisystemmanager".equals(uri.getScheme())
                && "auth".equals(uri.getHost())
                && "/callback".equals(uri.getPath());
    }

    private boolean processAuthenticationIntent(Intent intent) {
        if (!isAuthenticationCallback(intent)) return false;

        if (callbackProcessing) return true;

        callbackProcessing = true;
        updateStartupStatus("Verifying your email confirmation...");

        try {
            sessionManager.handleAuthCallback(
                    intent.getData(),
                    new AuthSessionManager.AuthCallback() {
                        @Override
                        public void onComplete(
                                AuthSessionManager.AuthResult result) {

                            runOnUiThread(() -> {
                                callbackProcessing = false;

                                if (isFinishing() || isDestroyed()
                                        || navigationStarted) {
                                    return;
                                }

                                if (result != null && result.success) {
                                    openMainActivity();
                                } else {
                                    String message = result == null
                                            ? "Email confirmation failed."
                                            : result.message;

                                    showAuthenticationInterface();
                                    showMessage(message == null
                                            || message.trim().isEmpty()
                                            ? "Unable to confirm your email."
                                            : message);
                                }
                            });
                        }
                    });
        } catch (Exception e) {
            callbackProcessing = false;
            Log.e(TAG, "Authentication callback failed", e);

            showAuthenticationInterface();
            showMessage("Unable to process email confirmation.");
        }

        return true;
    }

    // =========================================================
    // AUTHENTICATION INTERFACE
    // =========================================================

    private void buildInterface() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(30), dp(24), dp(25));
        root.setBackgroundColor(BACKGROUND);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_HORIZONTAL);

        // The login and registration screens use the same official artwork.
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.p_ai_launcher);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);

        LinearLayout.LayoutParams logoParams =
                new LinearLayout.LayoutParams(dp(112), dp(112));
        logoParams.gravity = Gravity.CENTER;
        logoParams.bottomMargin = dp(16);
        container.addView(logo, logoParams);

        TextView brand = new TextView(this);
        brand.setText("P AI SYSTEM MANAGER");
        brand.setTextColor(WHITE);
        brand.setTextSize(22);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        brand.setGravity(Gravity.CENTER);
        container.addView(brand);

        subtitle = new TextView(this);
        subtitle.setText("Your intelligent personal assistant");
        subtitle.setTextColor(MUTED);
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(8);
        subtitleParams.bottomMargin = dp(35);
        container.addView(subtitle, subtitleParams);

        form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(25), dp(20), dp(25));
        form.setBackground(background(Color.rgb(19, 27, 44), 20));

        title = new TextView(this);
        title.setTextColor(WHITE);
        title.setTextSize(23);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        form.addView(title);

        TextView description = new TextView(this);
        description.setText("Enter your details to continue.");
        description.setTextColor(MUTED);
        description.setTextSize(13);

        LinearLayout.LayoutParams descriptionParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = dp(7);
        descriptionParams.bottomMargin = dp(20);
        form.addView(description, descriptionParams);

        nameInput = createInput("Full name");
        emailInput = createInput("Email address");
        passwordInput = createInput("Password");
        confirmPasswordInput = createInput("Confirm password");

        form.addView(nameInput);
        form.addView(emailInput);
        form.addView(passwordInput);
        form.addView(confirmPasswordInput);

        forgotPassword = new TextView(this);
        forgotPassword.setText("Forgot password?");
        forgotPassword.setTextColor(CYAN);
        forgotPassword.setTextSize(14);
        forgotPassword.setGravity(Gravity.END);
        forgotPassword.setPadding(0, dp(12), 0, dp(18));
        forgotPassword.setOnClickListener(v -> resetPassword());
        form.addView(forgotPassword);

        actionButton = new Button(this);
        actionButton.setTextColor(WHITE);
        actionButton.setTextSize(15);
        actionButton.setAllCaps(false);
        actionButton.setBackground(background(BLUE, 12));

        form.addView(
                actionButton,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(52)));

        switchMode = new TextView(this);
        switchMode.setTextColor(MUTED);
        switchMode.setTextSize(14);
        switchMode.setGravity(Gravity.CENTER);
        switchMode.setPadding(0, dp(22), 0, dp(5));
        switchMode.setOnClickListener(v -> {
            if (busy) return;
            registrationMode = !registrationMode;
            updateAuthenticationMode();
        });
        form.addView(switchMode);

        container.addView(form);

        TextView footer = new TextView(this);
        footer.setText("POWERED BY P GRAPHICS");
        footer.setTextColor(Color.rgb(100, 120, 155));
        footer.setTextSize(11);
        footer.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams footerParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        footerParams.topMargin = dp(30);
        container.addView(footer, footerParams);

        scrollView.addView(container);

        root.addView(
                scrollView,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1));

        updateAuthenticationMode();
        setContentView(root);
    }

    // =========================================================
    // INPUT CREATION
    // =========================================================

    private EditText createInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setHintTextColor(MUTED);
        input.setTextColor(WHITE);
        input.setTextSize(15);
        input.setSingleLine(true);
        input.setPadding(dp(15), 0, dp(15), 0);
        input.setBackground(background(Color.rgb(29, 40, 61), 10));

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(53));
        params.bottomMargin = dp(13);
        input.setLayoutParams(params);

        if (hint.toLowerCase().contains("password")) {
            input.setInputType(
                    android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        } else if (hint.toLowerCase().contains("email")) {
            input.setInputType(
                    android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        } else {
            input.setInputType(
                    android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        }

        return input;
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    // =========================================================
    // LOGIN / REGISTRATION MODE
    // =========================================================

    private void updateAuthenticationMode() {
        if (registrationMode) {
            title.setText("Create Account");
            actionButton.setText("Create Account");
            nameInput.setVisibility(View.VISIBLE);
            confirmPasswordInput.setVisibility(View.VISIBLE);
            forgotPassword.setVisibility(View.GONE);
            switchMode.setText("Already have an account? Sign in");
        } else {
            title.setText("Welcome Back");
            actionButton.setText("Sign In");
            nameInput.setVisibility(View.GONE);
            confirmPasswordInput.setVisibility(View.GONE);
            forgotPassword.setVisibility(View.VISIBLE);
            switchMode.setText("New to P AI? Create an account");
        }

        actionButton.setOnClickListener(v -> authenticate());
    }

    // =========================================================
    // AUTHENTICATION VALIDATION
    // =========================================================

    private void authenticate() {
        if (busy) return;

        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (email.isEmpty() || password.isEmpty()) {
            showMessage("Please enter your email and password.");
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showMessage("Enter a valid email address.");
            return;
        }

        if (password.length() < 6) {
            showMessage("Password must contain at least 6 characters.");
            return;
        }

        if (registrationMode) {
            String name = nameInput.getText().toString().trim();
            String confirmation =
                    confirmPasswordInput.getText().toString();

            if (name.isEmpty()) {
                showMessage("Please enter your full name.");
                return;
            }

            if (!password.equals(confirmation)) {
                showMessage("Passwords do not match.");
                return;
            }

            performSignUp(name, email, password);
        } else {
            performSignIn(email, password);
        }
    }

    // =========================================================
    // SIGN UP
    // =========================================================

    private void performSignUp(String name, String email, String password) {
        setBusy(true);

        try {
            sessionManager.signUp(
                    name,
                    email,
                    password,
                    new AuthSessionManager.AuthCallback() {
                        @Override
                        public void onComplete(
                                AuthSessionManager.AuthResult result) {
                            runOnUiThread(() -> {
                                if (isFinishing() || isDestroyed()) return;

                                setBusy(false);

                                if (result == null) {
                                    showMessage("Registration failed.");
                                    return;
                                }

                                if (result.success) {
                                    if (result.requiresEmailConfirmation) {
                                        showMessage(
                                                "Account created! Please check your email and confirm your account.");
                                    } else {
                                        showMessage(
                                                "Account created successfully!");
                                        openMainActivity();
                                    }
                                } else {
                                    showMessage(result.message == null
                                            ? "Registration failed."
                                            : result.message);
                                }
                            });
                        }
                    });
        } catch (Exception e) {
            setBusy(false);
            Log.e(TAG, "Registration error", e);
            showMessage("Unable to create your account.");
        }
    }

    // =========================================================
    // SIGN IN
    // =========================================================

    private void performSignIn(String email, String password) {
        setBusy(true);

        try {
            sessionManager.signIn(
                    email,
                    password,
                    new AuthSessionManager.AuthCallback() {
                        @Override
                        public void onComplete(
                                AuthSessionManager.AuthResult result) {
                            runOnUiThread(() -> {
                                if (isFinishing() || isDestroyed()) return;

                                setBusy(false);

                                if (result != null && result.success) {
                                    showMessage("Welcome back!");
                                    openMainActivity();
                                } else {
                                    showMessage(result == null
                                            || result.message == null
                                            ? "Sign in failed."
                                            : result.message);
                                }
                            });
                        }
                    });
        } catch (Exception e) {
            setBusy(false);
            Log.e(TAG, "Login error", e);
            showMessage("Unable to sign in.");
        }
    }

    // =========================================================
    // PASSWORD RECOVERY
    // =========================================================

    private void resetPassword() {
        if (busy) return;

        String email = emailInput.getText().toString().trim();

        if (email.isEmpty()) {
            showMessage("Enter your email address first.");
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showMessage("Enter a valid email address.");
            return;
        }

        setBusy(true);

        try {
            sessionManager.sendPasswordReset(
                    email,
                    new AuthSessionManager.AuthCallback() {
                        @Override
                        public void onComplete(
                                AuthSessionManager.AuthResult result) {
                            runOnUiThread(() -> {
                                if (isFinishing() || isDestroyed()) return;

                                setBusy(false);

                                if (result != null) {
                                    showMessage(result.message == null
                                            ? "Password reset request processed."
                                            : result.message);
                                } else {
                                    showMessage("Password reset failed.");
                                }
                            });
                        }
                    });
        } catch (Exception e) {
            setBusy(false);
            Log.e(TAG, "Password recovery error", e);
            showMessage("Unable to process password reset.");
        }
    }

    // =========================================================
    // RESTORE SAVED SESSION — AUTH FLASH FIX
    // =========================================================

    private void restoreSavedSession() {
        try {
            if (!sessionManager.hasSavedSession()) {
                updateStartupStatus("Preparing sign in...");

                // A short branded transition avoids showing a half-built form
                // while the startup screen is being replaced.
                mainHandler.postDelayed(
                        revealAuthAfterDelay,
                        NO_SESSION_SPLASH_DELAY_MS);
                return;
            }

            updateStartupStatus("Restoring your secure session...");

            sessionManager.restoreSession(
                    new AuthSessionManager.AuthCallback() {
                        @Override
                        public void onComplete(
                                AuthSessionManager.AuthResult result) {
                            runOnUiThread(() -> {
                                if (isFinishing() || isDestroyed()
                                        || navigationStarted) {
                                    return;
                                }

                                if (result != null && result.success) {
                                    openMainActivity();
                                } else {
                                    showAuthenticationInterface();
                                }
                            });
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Session restoration failed", e);

            if (!isFinishing() && !isDestroyed()) {
                showAuthenticationInterface();
                showMessage(
                        "Your saved session could not be restored. Please sign in again.");
            }
        }
    }

    // =========================================================
    // MAIN ACTIVITY NAVIGATION
    // =========================================================

    private void openMainActivity() {
        if (isFinishing() || isDestroyed() || navigationStarted) return;

        navigationStarted = true;
        mainHandler.removeCallbacks(revealAuthAfterDelay);

        Intent intent = new Intent(AuthActivity.this, MainActivity.class);
        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        startActivity(intent);
        finish();
    }

    // =========================================================
    // LOADING STATE
    // =========================================================

    private void setBusy(boolean value) {
        busy = value;

        if (actionButton == null) return;

        actionButton.setEnabled(!value);
        actionButton.setAlpha(value ? 0.6f : 1f);
        actionButton.setText(
                value
                        ? "Please wait..."
                        : registrationMode
                                ? "Create Account"
                                : "Sign In");
    }

    // =========================================================
    // USER MESSAGES
    // =========================================================

    private void showMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            message = "Something went wrong. Please try again.";
        }

        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    // =========================================================
    // DISPLAY DIMENSIONS
    // =========================================================

    private int dp(int value) {
        return (int) (
                value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
