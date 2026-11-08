package com.pgraphics.paisystemmanager;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class AuthActivity extends Activity {

    private static final int BLUE = Color.rgb(35, 115, 255);
    private static final int BACKGROUND = Color.rgb(10, 14, 25);
    private static final int WHITE = Color.WHITE;
    private static final int MUTED = Color.rgb(155, 170, 195);

    private LinearLayout root;
    private LinearLayout form;

    private EditText nameInput;
    private EditText emailInput;
    private EditText passwordInput;
    private EditText confirmPasswordInput;

    private TextView title;
    private TextView subtitle;
    private TextView switchMode;

    private Button actionButton;
    private TextView forgotPassword;

    private boolean registrationMode = false;
    private boolean busy = false;

    private AuthSessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);

        sessionManager = AuthSessionManager.getInstance(this);

        buildInterface();

        restoreSavedSession();
    }

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

        TextView logo = new TextView(this);
        logo.setText("P");
        logo.setTextSize(42);
        logo.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        logo.setTextColor(WHITE);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(background(BLUE, 22));

        LinearLayout.LayoutParams logoParams =
                new LinearLayout.LayoutParams(dp(82), dp(82));

        logoParams.gravity = Gravity.CENTER;
        logoParams.bottomMargin = dp(20);

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
        forgotPassword.setTextColor(BLUE);
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

        form.addView(actionButton,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(52)));

        switchMode = new TextView(this);
        switchMode.setTextColor(MUTED);
        switchMode.setTextSize(14);
        switchMode.setGravity(Gravity.CENTER);
        switchMode.setPadding(0, dp(22), 0, dp(5));

        switchMode.setOnClickListener(v -> {
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

        root.addView(scrollView,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1));

        updateAuthenticationMode();

        // CRITICAL FIX: Display the authentication interface.
        setContentView(root);
    }

    private EditText createInput(String hint) {

        EditText input = new EditText(this);

        input.setHint(hint);
        input.setHintTextColor(MUTED);
        input.setTextColor(WHITE);
        input.setTextSize(15);
        input.setSingleLine(true);
        input.setPadding(dp(15), 0, dp(15), 0);

        input.setBackground(background(
                Color.rgb(29, 40, 61), 10));

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(53));

        params.bottomMargin = dp(13);

        input.setLayoutParams(params);

        if (hint.toLowerCase().contains("password")) {
            input.setInputType(
                    android.text.InputType.TYPE_CLASS_TEXT |
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        } else if (hint.toLowerCase().contains("email")) {
            input.setInputType(
                    android.text.InputType.TYPE_CLASS_TEXT |
                    android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        } else {
            input.setInputType(
                    android.text.InputType.TYPE_CLASS_TEXT |
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        }

        return input;
    }

    private GradientDrawable background(int color, int radius) {

        GradientDrawable drawable = new GradientDrawable();

        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));

        return drawable;
    }

    private void updateAuthenticationMode() {

        if (registrationMode) {

            title.setText("Create Account");
            actionButton.setText("Create Account");

            nameInput.setVisibility(View.VISIBLE);
            confirmPasswordInput.setVisibility(View.VISIBLE);

            forgotPassword.setVisibility(View.GONE);

            switchMode.setText(
                    "Already have an account? Sign in");

        } else {

            title.setText("Welcome Back");
            actionButton.setText("Sign In");

            nameInput.setVisibility(View.GONE);
            confirmPasswordInput.setVisibility(View.GONE);

            forgotPassword.setVisibility(View.VISIBLE);

            switchMode.setText(
                    "New to P AI? Create an account");
        }

        actionButton.setOnClickListener(v -> authenticate());
    }

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

            performAuthentication(
                    "signUp", name, email, password);

        } else {

            performAuthentication(
                    "signIn", null, email, password);
        }
    }

    private void performAuthentication(
            String operation,
            String name,
            String email,
            String password) {

        setBusy(true);

        try {

            int argumentCount = operation.equals("signUp") ? 4 : 3;

            Method selected = null;

            for (Method method : sessionManager.getClass().getMethods()) {

                if (method.getName().equals(operation)
                        && method.getParameterTypes().length == argumentCount) {

                    selected = method;
                    break;
                }
            }

            if (selected == null) {
                setBusy(false);
                showMessage("Authentication method is unavailable.");
                return;
            }

            Class<?> callbackType =
                    selected.getParameterTypes()[argumentCount - 1];

            if (!callbackType.isInterface()) {
                setBusy(false);
                showMessage("Authentication callback configuration error.");
                return;
            }

            Method authMethod = selected;

            Object callback = Proxy.newProxyInstance(
                    callbackType.getClassLoader(),
                    new Class<?>[]{callbackType},
                    (proxy, method, args) -> {

                        if (args != null && args.length > 0) {

                            Object result = args[0];

                            boolean success =
                                    readBoolean(result, "success");

                            String message =
                                    readString(result, "message");

                            boolean confirmationRequired =
                                    readBoolean(result,
                                            "requiresEmailConfirmation");

                            runOnUiThread(() -> {

                                setBusy(false);

                                if (success) {

                                    if (confirmationRequired) {

                                        showMessage(
                                                "Account created. Please confirm your email.");

                                    } else {

                                        openMainActivity();
                                    }

                                } else {

                                    showMessage(message.isEmpty()
                                            ? "Authentication failed."
                                            : message);
                                }
                            });
                        }

                        return null;
                    });

            if (operation.equals("signUp")) {

                authMethod.invoke(
                        sessionManager,
                        name,
                        email,
                        password,
                        callback);

            } else {

                authMethod.invoke(
                        sessionManager,
                        email,
                        password,
                        callback);
            }

        } catch (Exception e) {

            setBusy(false);

            showMessage("Authentication error: "
                    + e.getMessage());
        }
    }

    private void resetPassword() {

        String email = emailInput.getText().toString().trim();

        if (email.isEmpty()) {
            showMessage("Enter your email address first.");
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showMessage("Enter a valid email address.");
            return;
        }

        try {

            Method selected = null;

            for (Method method : sessionManager.getClass().getMethods()) {

                if (method.getName().equals("sendPasswordReset")
                        && method.getParameterTypes().length == 2) {

                    selected = method;
                    break;
                }
            }

            if (selected == null) {
                showMessage("Password reset is unavailable.");
                return;
            }

            Class<?> callbackType =
                    selected.getParameterTypes()[1];

            if (!callbackType.isInterface()) {
                showMessage("Password reset callback configuration error.");
                return;
            }

            Object callback = Proxy.newProxyInstance(
                    callbackType.getClassLoader(),
                    new Class<?>[]{callbackType},
                    (proxy, method, args) -> {

                        if (args != null && args.length > 0) {

                            Object result = args[0];

                            boolean success =
                                    readBoolean(result, "success");

                            String message =
                                    readString(result, "message");

                            runOnUiThread(() ->
                                    showMessage(message.isEmpty()
                                            ? (success
                                                ? "Password reset request sent."
                                                : "Password reset failed.")
                                            : message));
                        }

                        return null;
                    });

            selected.invoke(sessionManager, email, callback);

        } catch (Exception e) {

            showMessage("Unable to process password reset.");
        }
    }

    private void restoreSavedSession() {

        try {

            Method hasSession =
                    sessionManager.getClass().getMethod("hasSavedSession");

            boolean saved = (Boolean) hasSession.invoke(sessionManager);

            if (!saved) return;

            Method restore = null;

            for (Method method : sessionManager.getClass().getMethods()) {

                if (method.getName().equals("restoreSession")
                        && method.getParameterTypes().length == 1) {

                    restore = method;
                    break;
                }
            }

            if (restore == null) return;

            Class<?> callbackType =
                    restore.getParameterTypes()[0];

            if (!callbackType.isInterface()) return;

            Object callback = Proxy.newProxyInstance(
                    callbackType.getClassLoader(),
                    new Class<?>[]{callbackType},
                    (proxy, method, args) -> {

                        if (args != null && args.length > 0) {

                            Object result = args[0];

                            boolean success =
                                    readBoolean(result, "success");

                            if (success) {
                                runOnUiThread(this::openMainActivity);
                            }
                        }

                        return null;
                    });

            restore.invoke(sessionManager, callback);

        } catch (Exception e) {

            android.util.Log.e(
                    "PAI_AUTH",
                    "Session restoration failed",
                    e);
        }
    }

    private boolean readBoolean(Object object, String name) {

        if (object == null) return false;

        try {

            Method method = object.getClass().getMethod(
                    "is" + Character.toUpperCase(name.charAt(0))
                            + name.substring(1));

            Object value = method.invoke(object);

            return Boolean.TRUE.equals(value);

        } catch (Exception ignored) {
        }

        try {

            Field field = object.getClass().getField(name);

            return Boolean.TRUE.equals(field.get(object));

        } catch (Exception ignored) {
        }

        return false;
    }

    private String readString(Object object, String name) {

        if (object == null) return "";

        try {

            Method method = object.getClass().getMethod(
                    "get" + Character.toUpperCase(name.charAt(0))
                            + name.substring(1));

            Object value = method.invoke(object);

            return value == null ? "" : value.toString();

        } catch (Exception ignored) {
        }

        try {

            Field field = object.getClass().getField(name);

            Object value = field.get(object);

            return value == null ? "" : value.toString();

        } catch (Exception ignored) {
        }

        return "";
    }

    private void openMainActivity() {

        if (isFinishing() || isDestroyed()) return;

        android.content.Intent intent =
                new android.content.Intent(
                    AuthActivity.this,
                        MainActivity.class);

        intent.setFlags(
                android.content.Intent.FLAG_ACTIVITY_NEW_TASK |
                android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);

        startActivity(intent);
        finish();
    }

    private void setBusy(boolean value) {

        busy = value;

        actionButton.setEnabled(!value);

        actionButton.setAlpha(value ? 0.6f : 1f);

        actionButton.setText(value
                ? "Please wait..."
                : registrationMode
                    ? "Create Account"
                    : "Sign In");
    }

    private void showMessage(String message) {

        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {

        return (int) (value *
                getResources().getDisplayMetrics().density + 0.5f);
    }
}
      
