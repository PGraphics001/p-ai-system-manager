package com.pgraphics.paisystemmanager;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class AuthActivity extends Activity {

    private static final int BACKGROUND = Color.rgb(15, 15, 20);
    private static final int CARD = Color.rgb(27, 27, 36);
    private static final int ACCENT = Color.rgb(210, 160, 65);
    private static final int WHITE = Color.WHITE;
    private static final int MUTED = Color.rgb(165, 165, 175);

    private AuthSessionManager authManager;

    private LinearLayout root;
    private LinearLayout form;
    private LinearLayout fieldsContainer;

    private EditText nameInput;
    private EditText emailInput;
    private EditText passwordInput;
    private EditText confirmPasswordInput;

    private TextView title;
    private TextView subtitle;
    private TextView switchMode;
    private TextView forgotPassword;

    private Button actionButton;

    private ProgressBar progressBar;

    private boolean registrationMode = false;
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        authManager = AuthSessionManager.getInstance(this);

        buildInterface();

        restoreExistingSession();
    }

    // ==========================================
    // BUILD AUTHENTICATION INTERFACE
    // ==========================================

    private void buildInterface() {

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(20), dp(24), dp(20));
        root.setBackgroundColor(BACKGROUND);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout.LayoutParams scrollParams =
                new LinearLayout.LayoutParams(
                        -1,
                        -1
                );

        root.addView(scrollView, scrollParams);

        form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setGravity(Gravity.CENTER_HORIZONTAL);
        form.setPadding(dp(20), dp(30), dp(20), dp(30));
        form.setBackground(roundedBackground(CARD, 22));

        scrollView.addView(form);

        TextView logo = new TextView(this);
        logo.setText("P AI");
        logo.setTextSize(38);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        logo.setTextColor(ACCENT);
        logo.setGravity(Gravity.CENTER);

        form.addView(logo, matchWrap());

        TextView brand = new TextView(this);
        brand.setText("SYSTEM MANAGER");
        brand.setTextSize(13);
        brand.setLetterSpacing(0.18f);
        brand.setTextColor(WHITE);
        brand.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams brandParams = matchWrap();
        brandParams.topMargin = dp(5);

        form.addView(brand, brandParams);

        title = new TextView(this);
        title.setTextSize(25);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(WHITE);
        title.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(35);

        form.addView(title, titleParams);

        subtitle = new TextView(this);
        subtitle.setTextSize(14);
        subtitle.setTextColor(MUTED);
        subtitle.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(10);
        subtitleParams.bottomMargin = dp(25);

        form.addView(subtitle, subtitleParams);

        fieldsContainer = new LinearLayout(this);
        fieldsContainer.setOrientation(LinearLayout.VERTICAL);

        form.addView(fieldsContainer, matchWrap());

        nameInput = createInput(
                "Full name",
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_WORDS
        );

        emailInput = createInput(
                "Email address",
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        );

        passwordInput = createInput(
                "Password",
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_PASSWORD
        );

        confirmPasswordInput = createInput(
                "Confirm password",
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_PASSWORD
        );

        actionButton = new Button(this);
        actionButton.setTextColor(Color.BLACK);
        actionButton.setTextSize(15);
        actionButton.setTypeface(Typeface.DEFAULT_BOLD);
        actionButton.setBackground(roundedBackground(ACCENT, 14));

        LinearLayout.LayoutParams buttonParams = matchWrap();
        buttonParams.topMargin = dp(20);
        buttonParams.height = dp(54);

        form.addView(actionButton, buttonParams);

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);

        LinearLayout.LayoutParams progressParams =
                new LinearLayout.LayoutParams(
                        -2,
                        dp(40)
                );

        progressParams.gravity = Gravity.CENTER;

        form.addView(progressBar, progressParams);

        forgotPassword = createLink("Forgot password?");

        LinearLayout.LayoutParams forgotParams = matchWrap();
        forgotParams.topMargin = dp(18);

        form.addView(forgotPassword, forgotParams);

        switchMode = createLink("");

        LinearLayout.LayoutParams switchParams = matchWrap();
        switchParams.topMargin = dp(22);

        form.addView(switchMode, switchParams);

        TextView footer = new TextView(this);
        footer.setText("SECURE ACCESS • P GRAPHICS");
        footer.setTextColor(MUTED);
        footer.setTextSize(10);
        footer.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams footerParams = matchWrap();
        footerParams.topMargin = dp(30);

        form.addView(footer, footerParams);

        actionButton.setOnClickListener(v -> performAuthentication());

        forgotPassword.setOnClickListener(v -> performPasswordReset());

        switchMode.setOnClickListener(v -> switchAuthenticationMode());

        updateAuthenticationMode();
    }

    // ==========================================
    // INPUT CREATION
    // ==========================================

    private EditText createInput(
            String hint,
            int inputType
    ) {

        EditText input = new EditText(this);

        input.setHint(hint);
        input.setHintTextColor(MUTED);
        input.setTextColor(WHITE);
        input.setTextSize(15);
        input.setSingleLine(true);
        input.setInputType(inputType);
        input.setPadding(
                dp(15),
                dp(12),
                dp(15),
                dp(12)
        );

        input.setBackground(roundedBackground(
                Color.rgb(42, 42, 53),
                12
        ));

        LinearLayout.LayoutParams params = matchWrap();

        params.height = dp(54);
        params.bottomMargin = dp(13);

        fieldsContainer.addView(input, params);

        return input;
    }

    private TextView createLink(String text) {

        TextView view = new TextView(this);

        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(ACCENT);
        view.setGravity(Gravity.CENTER);

        return view;
    }

    // ==========================================
    // LOGIN / REGISTRATION MODE
    // ==========================================

    private void switchAuthenticationMode() {

        if (busy) {
            return;
        }

        registrationMode = !registrationMode;

        updateAuthenticationMode();
    }

    private void updateAuthenticationMode() {

        title.setText(
                registrationMode
                        ? "Create Your Account"
                        : "Welcome Back"
        );

        subtitle.setText(
                registrationMode
                        ? "Create an account to access P AI."
                        : "Sign in to continue to P AI."
        );

        nameInput.setVisibility(
                registrationMode
                        ? View.VISIBLE
                        : View.GONE
        );

        confirmPasswordInput.setVisibility(
                registrationMode
                        ? View.VISIBLE
                        : View.GONE
        );

        forgotPassword.setVisibility(
                registrationMode
                        ? View.GONE
                        : View.VISIBLE
        );

        actionButton.setText(
                registrationMode
                        ? "CREATE ACCOUNT"
                        : "SIGN IN"
        );

        switchMode.setText(
                registrationMode
                        ? "Already have an account? Sign in"
                        : "New to P AI? Create an account"
        );
    }

    // ==========================================
    // AUTHENTICATION
    // ==========================================

    private void performAuthentication() {

        if (busy) {
            return;
        }

        String email = emailInput.getText()
                .toString()
                .trim();

        String password = passwordInput.getText()
                .toString();

        if (email.isEmpty() || password.isEmpty()) {

            showMessage("Please enter your email and password.");

            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS
                .matcher(email).matches()) {

            showMessage("Please enter a valid email address.");

            return;
        }

        if (registrationMode) {

            String fullName = nameInput.getText()
                    .toString()
                    .trim();

            String confirmPassword =
                    confirmPasswordInput.getText()
                            .toString();

            if (fullName.isEmpty()) {

                showMessage("Please enter your full name.");

                return;
            }

            if (password.length() < 6) {

                showMessage(
                        "Password must contain at least 6 characters."
                );

                return;
            }

            if (!password.equals(confirmPassword)) {

                showMessage("Passwords do not match.");

                return;
            }

            setBusy(true);

            authManager.signUp(
                    fullName,
                    email,
                    password,
                    result -> {

                        setBusy(false);

                        if (!result.success) {

                            showMessage(result.message);

                            return;
                        }

                        if (result.requiresEmailConfirmation) {

                            showMessage(
                                    "Account created. Please confirm your email before signing in."
                            );

                            registrationMode = false;

                            passwordInput.setText("");

                            confirmPasswordInput.setText("");

                            updateAuthenticationMode();

                        } else {

                            showMessage("Account created successfully.");

                            openMainActivity();
                        }
                    }
            );

        } else {

            setBusy(true);

            authManager.signIn(
                    email,
                    password,
                    result -> {

                        setBusy(false);

                        if (result.success) {

                            showMessage("Welcome to P AI.");

                            openMainActivity();

                        } else {

                            showMessage(result.message);
                        }
                    }
            );
        }
    }

    // ==========================================
    // PASSWORD RESET
    // ==========================================

    private void performPasswordReset() {

        if (busy) {
            return;
        }

        String email = emailInput.getText()
                .toString()
                .trim();

        if (!android.util.Patterns.EMAIL_ADDRESS
                .matcher(email).matches()) {

            showMessage("Enter your valid email address first.");

            return;
        }

        setBusy(true);

        authManager.sendPasswordReset(
                email,
                result -> {

                    setBusy(false);

                    showMessage(result.message);
                }
        );
    }

    // ==========================================
    // RESTORE SAVED LOGIN
    // ==========================================

    private void restoreExistingSession() {

        if (!authManager.hasSavedSession()) {
            return;
        }

        setBusy(true);

        authManager.restoreSession(result -> {

            setBusy(false);

            if (result.success) {

                openMainActivity();

            } else {

                showMessage(
                        "Please sign in to continue."
                );
            }
        });
    }

    // ==========================================
    // OPEN MAIN APPLICATION
    // ==========================================

    private void openMainActivity() {

        android.content.Intent intent =
                new android.content.Intent(
                        AuthActivity.this,
                        MainActivity.class
                );

        intent.setFlags(
                android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);

        finish();
    }

    // ==========================================
    // LOADING STATE
    // ==========================================

    private void setBusy(boolean state) {

        busy = state;

        actionButton.setEnabled(!state);

        forgotPassword.setEnabled(!state);

        switchMode.setEnabled(!state);

        progressBar.setVisibility(
                state
                        ? View.VISIBLE
                        : View.GONE
        );

        actionButton.setAlpha(state ? 0.5f : 1.0f);
    }

    // ==========================================
    // UI HELPERS
    // ==========================================

    private GradientDrawable roundedBackground(
            int color,
            int radius
    ) {

        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(color);

        drawable.setCornerRadius(dp(radius));

        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {

        return new LinearLayout.LayoutParams(
                -1,
                -2
        );
    }

    private int dp(float value) {

        return (int) (
                value * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    private void showMessage(String message) {

        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }
}