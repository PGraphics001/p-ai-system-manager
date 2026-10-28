package com.pgraphics.paisystemmanager;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int VOICE_REQUEST_CODE = 100;

    private EditText commandInput;
    private TextView responseText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createInterface();
    }

    private void createInterface() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setPadding(24, 40, 24, 24);
        layout.setBackgroundColor(Color.rgb(18, 18, 18));

        TextView title = new TextView(this);
        title.setText("P AI SYSTEM MANAGER");
        title.setTextColor(Color.rgb(255, 215, 0));
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Your Intelligent Phone Assistant");
        subtitle.setTextColor(Color.WHITE);
        subtitle.setTextSize(15);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 8, 0, 24);
        layout.addView(subtitle);

        commandInput = new EditText(this);
        commandInput.setHint("Type a command...");
        commandInput.setSingleLine(true);
        commandInput.setTextColor(Color.WHITE);
        commandInput.setHintTextColor(Color.LTGRAY);
        layout.addView(commandInput);

        Button executeButton = new Button(this);
        executeButton.setText("EXECUTE COMMAND");
        layout.addView(executeButton);

        Button voiceButton = new Button(this);
        voiceButton.setText("SPEAK COMMAND");
        layout.addView(voiceButton);

        responseText = new TextView(this);
        responseText.setText(
                "System ready. Type or speak a command."
        );
        responseText.setTextColor(Color.WHITE);
        responseText.setTextSize(16);
        responseText.setGravity(Gravity.CENTER);
        responseText.setPadding(0, 30, 0, 0);
        layout.addView(responseText);

        executeButton.setOnClickListener(
                view -> executeCommand()
        );

        voiceButton.setOnClickListener(
                view -> startVoiceRecognition()
        );

        setContentView(layout);
    }

    private void startVoiceRecognition() {
        Intent intent = new Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale.getDefault().toLanguageTag()
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "Speak your command"
        );

        try {
            startActivityForResult(
                    intent,
                    VOICE_REQUEST_CODE
            );
        } catch (Exception exception) {
            Toast.makeText(
                    this,
                    "Speech recognition is unavailable.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode == VOICE_REQUEST_CODE
                && resultCode == RESULT_OK
                && data != null) {

            ArrayList<String> results =
                    data.getStringArrayListExtra(
                            RecognizerIntent.EXTRA_RESULTS
                    );

            if (results != null && !results.isEmpty()) {
                commandInput.setText(results.get(0));
                executeCommand();
            }
        }
    }

    private void executeCommand() {
        String command = commandInput.getText()
                .toString()
                .trim()
                .toLowerCase(Locale.ROOT);

        hideKeyboard();

        if (command.isEmpty()) {
            responseText.setText(
                    "Please type or speak a command."
            );
            return;
        }

        if (command.contains("whatsapp")) {
            openApp(
                    "com.whatsapp",
                    "WhatsApp"
            );

        } else if (command.contains("youtube")) {
            openApp(
                    "com.google.android.youtube",
                    "YouTube"
            );

        } else if (command.contains("settings")) {
            openSettings();

        } else if (command.contains("camera")) {
            openCamera();

        } else if (command.contains("lock")) {
            responseText.setText(
                    "Screen locking is not implemented yet."
            );

        } else {
            responseText.setText(
                    "Command not recognized. Try WhatsApp, "
                            + "YouTube, Settings, or Camera."
            );
        }
    }

    private void openApp(
            String packageName,
            String appName) {

        try {
            Intent intent = getPackageManager()
                    .getLaunchIntentForPackage(packageName);

            if (intent != null) {
                startActivity(intent);
                responseText.setText(
                        "Opening " + appName + "..."
                );
            } else {
                responseText.setText(
                        appName + " is unavailable or not installed."
                );
            }

        } catch (Exception exception) {
            responseText.setText(
                    "Unable to open " + appName + "."
            );
        }
    }

    private void openSettings() {
        try {
            Intent intent = new Intent(
                    Settings.ACTION_SETTINGS
            );

            startActivity(intent);
            responseText.setText("Opening Settings...");

        } catch (Exception exception) {
            responseText.setText(
                    "Unable to open Settings."
            );
        }
    }

    private void openCamera() {
        try {
            Intent intent = new Intent(
                    "android.media.action.IMAGE_CAPTURE"
            );

            startActivity(intent);
            responseText.setText("Opening Camera...");

        } catch (Exception exception) {
            responseText.setText(
                    "Unable to open Camera."
            );
        }
    }

    private void hideKeyboard() {
        if (commandInput == null) {
            return;
        }

        InputMethodManager manager =
                (InputMethodManager) getSystemService(
                        Context.INPUT_METHOD_SERVICE
                );

        if (manager != null) {
            manager.hideSoftInputFromWindow(
                    commandInput.getWindowToken(),
                    0
            );
        }
    }
}