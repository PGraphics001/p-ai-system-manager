package com.pgraphics.paisystemmanager;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
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
    private TextToSpeech textToSpeech;
    private boolean speechReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initializeSpeech();
        createInterface();
    }

    private void initializeSpeech() {
        textToSpeech = new TextToSpeech(
                this,
                status -> {
                    if (status == TextToSpeech.SUCCESS) {
                        int result = textToSpeech.setLanguage(
                                Locale.getDefault()
                        );

                        speechReady =
                                result != TextToSpeech.LANG_MISSING_DATA
                                && result != TextToSpeech.LANG_NOT_SUPPORTED;

                        if (speechReady) {
                            textToSpeech.setSpeechRate(1.0f);
                        }
                    }
                }
        );
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

        Button stopButton = new Button(this);
        stopButton.setText("STOP SPEAKING");
        layout.addView(stopButton);

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

        stopButton.setOnClickListener(view -> {
            if (textToSpeech != null) {
                textToSpeech.stop();
            }
            responseText.setText("Speech stopped.");
        });

        setContentView(layout);
    }

    private void speakResponse(String message) {
        responseText.setText(message);

        if (speechReady && textToSpeech != null) {
            textToSpeech.speak(
                    message,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "PAI_RESPONSE"
            );
        }
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
            speakResponse(
                    "Speech recognition is unavailable."
            );
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
            speakResponse("Please type or speak a command.");
            return;
        }

        if (command.contains("whatsapp")) {
            openApp("com.whatsapp", "WhatsApp");

        } else if (command.contains("youtube")) {
            openApp("com.google.android.youtube", "YouTube");

        } else if (command.contains("settings")) {
            openSettings();

        } else if (command.contains("camera")) {
            openCamera();

        } else if (command.contains("stop speaking")
                || command.equals("stop talking")) {

            if (textToSpeech != null) {
                textToSpeech.stop();
            }

            responseText.setText("Speech stopped.");

        } else if (command.contains("hello")
                || command.contains("hi")) {

            speakResponse(
                    "Hello! I am P AI System Manager. "
                    + "How can I help you?"
            );

        } else if (command.contains("your name")) {
            speakResponse(
                    "My name is P AI System Manager."
            );

        } else if (command.contains("lock")) {
            speakResponse(
                    "Screen locking is not implemented yet."
            );

        } else {
            speakResponse(
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
                speakResponse("Opening " + appName + ".");
                startActivity(intent);
            } else {
                speakResponse(
                        appName + " is unavailable or not installed."
                );
            }

        } catch (Exception exception) {
            speakResponse("Unable to open " + appName + ".");
        }
    }

    private void openSettings() {
        try {
            speakResponse("Opening Settings.");

            startActivity(new Intent(
                    Settings.ACTION_SETTINGS
            ));

        } catch (Exception exception) {
            speakResponse("Unable to open Settings.");
        }
    }

    private void openCamera() {
        try {
            speakResponse("Opening Camera.");

            startActivity(new Intent(
                    "android.media.action.IMAGE_CAPTURE"
            ));

        } catch (Exception exception) {
            speakResponse("Unable to open Camera.");
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

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }

        super.onDestroy();
    }
}