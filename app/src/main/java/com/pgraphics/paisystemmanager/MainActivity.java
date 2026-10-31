package com.pgraphics.paisystemmanager;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final String TAG = "PAI_AI";

    private static final int VOICE_REQUEST_CODE = 100;

    private static final String AI_API_URL =
            "https://p-ai-system-manager-api.vercel.app/api/chat";

    private EditText commandInput;
    private TextView responseText;
    private Button executeButton;

    private TextToSpeech textToSpeech;

    private boolean speechReady = false;
    private boolean requestRunning = false;

    private final ExecutorService aiExecutor =
            Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initializeSpeech();
        createInterface();
    }

    // ==============================
    // SPEECH INITIALIZATION
    // ==============================

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

    // ==============================
    // USER INTERFACE
    // ==============================

    private void createInterface() {

        LinearLayout layout = new LinearLayout(this);

        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setPadding(24, 32, 24, 24);
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
        subtitle.setPadding(0, 8, 0, 20);

        layout.addView(subtitle);

        commandInput = new EditText(this);

        commandInput.setHint("Ask P AI anything...");
        commandInput.setTextColor(Color.WHITE);
        commandInput.setHintTextColor(Color.LTGRAY);
        commandInput.setMinLines(1);
        commandInput.setMaxLines(4);

        layout.addView(commandInput);

        executeButton = new Button(this);

        executeButton.setText("ASK AI / EXECUTE");

        layout.addView(executeButton);

        Button voiceButton = new Button(this);

        voiceButton.setText("SPEAK COMMAND");

        layout.addView(voiceButton);

        Button stopButton = new Button(this);

        stopButton.setText("STOP SPEAKING");

        layout.addView(stopButton);

        ScrollView scrollView = new ScrollView(this);

        responseText = new TextView(this);

        responseText.setText(
                "Welcome to P AI System Manager!\n\n"
                + "Ask a question or try:\n"
                + "• Open WhatsApp\n"
                + "• Open YouTube\n"
                + "• Open Settings\n"
                + "• Open Camera"
        );

        responseText.setTextColor(Color.WHITE);
        responseText.setTextSize(16);
        responseText.setPadding(0, 24, 0, 16);

        scrollView.addView(responseText);

        layout.addView(
                scrollView,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1
                )
        );

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

    // ==============================
    // TEXT TO SPEECH
    // ==============================

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

    // ==============================
    // VOICE RECOGNITION
    // ==============================

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
                "Speak your command or question"
        );

        try {

            startActivityForResult(
                    intent,
                    VOICE_REQUEST_CODE
            );

        } catch (Exception e) {

            Log.e(TAG, "Speech recognition failed.", e);

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

    // ==============================
    // COMMAND PROCESSING
    // ==============================

    private void executeCommand() {

        String originalCommand = commandInput.getText()
                .toString()
                .trim();

        String command = originalCommand.toLowerCase(
                Locale.ROOT
        );

        hideKeyboard();

        if (command.isEmpty()) {

            speakResponse("Please type or speak a command.");

            return;
        }

        if (command.equals("stop speaking")
                || command.equals("stop talking")) {

            if (textToSpeech != null) {
                textToSpeech.stop();
            }

            responseText.setText("Speech stopped.");

            return;
        }

        if (command.equals("open whatsapp")
                || command.equals("whatsapp")) {

            openApp("com.whatsapp", "WhatsApp");

            return;
        }

        if (command.equals("open youtube")
                || command.equals("youtube")) {

            openApp(
                    "com.google.android.youtube",
                    "YouTube"
            );

            return;
        }

        if (command.equals("open settings")
                || command.equals("settings")) {

            openSettings();

            return;
        }

        if (command.equals("open camera")
                || command.equals("camera")) {

            openCamera();

            return;
        }

        if (command.equals("hello")
                || command.equals("hi")
                || command.equals("hello pai")) {

            speakResponse(
                    "Hello! I am P AI System Manager. "
                    + "How can I help you today?"
            );

            return;
        }

        if (command.contains("your name")) {

            speakResponse(
                    "My name is P AI System Manager."
            );

            return;
        }

        askAI(originalCommand);
    }

    // ==============================
    // OPENROUTER BACKEND CONNECTION
    // ==============================

    private void askAI(String question) {

        if (requestRunning) {

            Toast.makeText(
                    this,
                    "Please wait for the current answer.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        requestRunning = true;

        executeButton.setEnabled(false);

        responseText.setText("P AI is thinking...");

        aiExecutor.execute(() -> {

            HttpURLConnection connection = null;

            try {

                URL url = new URL(AI_API_URL);

                connection = (HttpURLConnection)
                        url.openConnection();

                connection.setRequestMethod("POST");

                connection.setConnectTimeout(20000);

                connection.setReadTimeout(60000);

                connection.setDoOutput(true);

                connection.setRequestProperty(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                );

                connection.setRequestProperty(
                        "Accept",
                        "application/json"
                );

                JSONObject requestBody = new JSONObject();

                requestBody.put("message", question);

                byte[] requestBytes = requestBody.toString()
                        .getBytes(StandardCharsets.UTF_8);

                try (OutputStream output =
                             connection.getOutputStream()) {

                    output.write(requestBytes);
                    output.flush();
                }

                int statusCode = connection.getResponseCode();

                InputStream inputStream;

                if (statusCode >= 200 && statusCode < 300) {

                    inputStream = connection.getInputStream();

                } else {

                    inputStream = connection.getErrorStream();
                }

                StringBuilder responseBuilder =
                        new StringBuilder();

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

                            responseBuilder.append(line);
                        }
                    }
                }

                if (statusCode < 200 || statusCode >= 300) {

                    throw new Exception(
                            "Server returned HTTP " + statusCode
                    );
                }

                JSONObject responseJson =
                        new JSONObject(
                                responseBuilder.toString()
                        );

                String answer = responseJson.optString(
                        "reply",
                        ""
                ).trim();

                if (answer.isEmpty()) {

                    throw new Exception(
                            "The AI returned an empty response."
                    );
                }

                runOnUiThread(() -> {

                    requestRunning = false;

                    executeButton.setEnabled(true);

                    if (!isFinishing() && !isDestroyed()) {

                        speakResponse(answer);
                    }
                });

            } catch (Exception e) {

                Log.e(TAG, "AI request failed.", e);

                String errorMessage =
                        "AI request failed: " + e.getMessage();

                runOnUiThread(() -> {

                    requestRunning = false;

                    executeButton.setEnabled(true);

                    if (!isFinishing() && !isDestroyed()) {

                        speakResponse(errorMessage);
                    }
                });

            } finally {

                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    // ==============================
    // OPEN INSTALLED APPLICATIONS
    // ==============================

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
                        appName + " is not installed or unavailable."
                );
            }

        } catch (Exception e) {

            Log.e(TAG, "Could not open " + appName, e);

            speakResponse("Unable to open " + appName + ".");
        }
    }

    // ==============================
    // OPEN SETTINGS
    // ==============================

    private void openSettings() {

        try {

            speakResponse("Opening Settings.");

            startActivity(
                    new Intent(Settings.ACTION_SETTINGS)
            );

        } catch (Exception e) {

            Log.e(TAG, "Could not open Settings.", e);

            speakResponse("Unable to open Settings.");
        }
    }

    // ==============================
    // OPEN CAMERA
    // ==============================

    private void openCamera() {

        try {

            Intent intent = new Intent(
                    "android.media.action.IMAGE_CAPTURE"
            );

            if (intent.resolveActivity(
                    getPackageManager()) != null) {

                speakResponse("Opening Camera.");

                startActivity(intent);

            } else {

                speakResponse(
                        "No camera application was found."
                );
            }

        } catch (Exception e) {

            Log.e(TAG, "Could not open Camera.", e);

            speakResponse("Unable to open Camera.");
        }
    }

    // ==============================
    // KEYBOARD MANAGEMENT
    // ==============================

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

    // ==============================
    // ACTIVITY CLEANUP
    // ==============================

    @Override
    protected void onDestroy() {

        if (textToSpeech != null) {

            textToSpeech.stop();

            textToSpeech.shutdown();
        }

        aiExecutor.shutdownNow();

        super.onDestroy();
    }
}