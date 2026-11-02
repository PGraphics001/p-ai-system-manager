package com.pgraphics.paisystemmanager;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
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

    private static final String API_URL =
            "https://p-ai-system-manager-api.vercel.app/api/chat";

    private static final int AUDIO_PERMISSION_REQUEST = 1001;
    private static final int SPEECH_REQUEST = 1002;

    private LinearLayout rootLayout;
    private LinearLayout chatContainer;
    private EditText messageInput;

    private Button sendButton;
    private Button voiceButton;
    private Button backgroundVoiceButton;

    private TextView statusText;

    private TextToSpeech textToSpeech;
    private SpeechRecognizer speechRecognizer;

    private final ExecutorService aiExecutor =
            Executors.newSingleThreadExecutor();

    private boolean isListening = false;
    private boolean isActivityDestroyed = false;
    private boolean requestInProgress = false;
    private boolean textToSpeechReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        buildInterface();
        initializeTextToSpeech();
        checkAudioPermission();
    }

    private void buildInterface() {

        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(
                dp(16), dp(16), dp(16), dp(12)
        );

        rootLayout.setBackgroundColor(
                Color.rgb(15, 15, 20)
        );

        TextView title = new TextView(this);

        title.setText("P AI SYSTEM MANAGER");
        title.setTextSize(23);
        title.setTextColor(Color.rgb(230, 180, 70));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(10), 0, dp(12));

        rootLayout.addView(
                title,
                new LinearLayout.LayoutParams(
                        -1, -2
                )
        );

        statusText = new TextView(this);

        statusText.setText("P AI is starting...");
        statusText.setTextSize(14);
        statusText.setTextColor(Color.LTGRAY);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, dp(5), 0, dp(12));

        rootLayout.addView(
                statusText,
                new LinearLayout.LayoutParams(
                        -1, -2
                )
        );

        ScrollView chatScroll = new ScrollView(this);

        chatContainer = new LinearLayout(this);
        chatContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        chatContainer.setPadding(
                dp(5), dp(5), dp(5), dp(5)
        );

        chatScroll.addView(chatContainer);

        rootLayout.addView(
                chatScroll,
                new LinearLayout.LayoutParams(
                        -1, 0, 1
                )
        );

        messageInput = new EditText(this);

        messageInput.setHint("Ask P AI anything...");
        messageInput.setTextColor(Color.WHITE);
        messageInput.setHintTextColor(Color.GRAY);
        messageInput.setTextSize(16);
        messageInput.setSingleLine(false);
        messageInput.setMinLines(2);
        messageInput.setMaxLines(4);

        rootLayout.addView(
                messageInput,
                new LinearLayout.LayoutParams(
                        -1, -2
                )
        );

        sendButton = createButton("SEND MESSAGE");
        voiceButton = createButton("VOICE INPUT");
        backgroundVoiceButton =
                createButton("START BACKGROUND VOICE");

        rootLayout.addView(sendButton);
        rootLayout.addView(voiceButton);
        rootLayout.addView(backgroundVoiceButton);
sendButton.setOnClickListener(v -> sendMessage());

voiceButton.setOnClickListener(v -> startManualVoiceRecognition());

backgroundVoiceButton.setOnClickListener(
        v -> toggleBackgroundListening()
);
        setContentView(rootLayout);

        addChatMessage(
                "P AI",
                "Hello! I am P AI, your personal assistant. How can I help you?"
        );

        updateStatus("Ready");
    }

    private Button createButton(String label) {

        Button button = new Button(this);

        button.setText(label);
        button.setTextColor(Color.BLACK);
        button.setTextSize(13);
        button.setAllCaps(false);

        button.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        Color.rgb(230, 180, 70)
                )
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        params.setMargins(0, dp(5), 0, dp(5));

        button.setLayoutParams(params);

        return button;
    }

    private void addChatMessage(
            String sender,
            String message
    ) {

        TextView chatMessage = new TextView(this);

        chatMessage.setText(
                sender + ":\n" + message
        );

        chatMessage.setTextSize(15);
        chatMessage.setTextColor(Color.WHITE);
        chatMessage.setPadding(
                dp(10), dp(10), dp(10), dp(10)
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        params.setMargins(
                0, dp(5), 0, dp(5)
        );

        chatContainer.addView(
                chatMessage, params
        );
    }

    private void updateStatus(String message) {

        if (statusText != null) {
            statusText.setText(message);
        }
    }

    private int dp(float value) {

        return (int) (
                value * getResources()
                        .getDisplayMetrics()
                        .density + 0.5f
        );
    }

    // PART 2 CONTINUES BELOW
       private void checkAudioPermission() {

        if (checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{
                            Manifest.permission.RECORD_AUDIO
                    },
                    AUDIO_PERMISSION_REQUEST
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == AUDIO_PERMISSION_REQUEST) {

            if (grantResults.length > 0
                    && grantResults[0]
                    == PackageManager.PERMISSION_GRANTED) {

                updateStatus("Microphone permission granted.");

            } else {

                updateStatus("Microphone permission denied.");
            }
        }
    }

    private void initializeTextToSpeech() {

        textToSpeech = new TextToSpeech(
                this,
                status -> {

                    if (status == TextToSpeech.SUCCESS) {

                        int result = textToSpeech.setLanguage(
                                Locale.US
                        );

                        textToSpeechReady =
                                result != TextToSpeech.LANG_MISSING_DATA
                                && result != TextToSpeech.LANG_NOT_SUPPORTED;

                    } else {

                        textToSpeechReady = false;
                    }
                }
        );
    }

    private void speak(String message) {

        if (textToSpeech != null && textToSpeechReady) {

            textToSpeech.speak(
                    message,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "PAI_SPEECH"
            );
        }
    }

    private void startManualVoiceRecognition() {

        if (checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {

            checkAudioPermission();

            Toast.makeText(
                    this,
                    "Please grant microphone permission first.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {

            Toast.makeText(
                    this,
                    "Speech recognition is unavailable on this device.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        Intent intent = new Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale.US
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "Speak to P AI"
        );

        try {

            startActivityForResult(
                    intent,
                    SPEECH_REQUEST
            );

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Unable to start voice recognition.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode == SPEECH_REQUEST
                && resultCode == RESULT_OK
                && data != null) {

            ArrayList<String> results =
                    data.getStringArrayListExtra(
                            RecognizerIntent.EXTRA_RESULTS
                    );

            if (results != null && !results.isEmpty()) {

                String spokenText = results.get(0);

                messageInput.setText(spokenText);

                sendMessage();
            }
        }
    }

    private void startBackgroundListening() {

        if (checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {

            checkAudioPermission();

            updateStatus("Microphone permission is required.");

            return;
        }

        Intent serviceIntent = new Intent(
                this,
                VoiceRecognitionService.class
        );

        serviceIntent.setAction(
                "com.pgraphics.paisystemmanager.START_VOICE"
        );

        try {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                startForegroundService(serviceIntent);

            } else {

                startService(serviceIntent);
            }

            backgroundVoiceActive = true;

            backgroundVoiceButton.setText(
                    "STOP BACKGROUND VOICE"
            );

            updateStatus(
                    "Starting background voice assistant..."
            );

        } catch (Exception e) {

            backgroundVoiceActive = false;

            backgroundVoiceButton.setText(
                    "START BACKGROUND VOICE"
            );

            updateStatus(
                    "Unable to start background voice assistant."
            );

            Toast.makeText(
                    this,
                    "Voice service could not start.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void stopBackgroundListening() {

        Intent serviceIntent = new Intent(
                this,
                VoiceRecognitionService.class
        );

        serviceIntent.setAction(
                "com.pgraphics.paisystemmanager.STOP_VOICE"
        );

        try {

            startService(serviceIntent);

            backgroundVoiceActive = false;

            backgroundVoiceButton.setText(
                    "START BACKGROUND VOICE"
            );

            updateStatus(
                    "Background voice assistant stopped."
            );

        } catch (Exception e) {

            updateStatus(
                    "Unable to stop background voice assistant."
            );
        }
    }   
    // PART 3 CONTINUES BELOW
    private void sendMessage() {

        if (requestInProgress) {
            return;
        }

        String question = messageInput.getText()
                .toString()
                .trim();

        if (question.isEmpty()) {
            messageInput.setError("Enter a message first.");
            return;
        }

        messageInput.setText("");

        hideKeyboard();

        // Handle phone application commands locally.
        if (question.toLowerCase(Locale.ROOT)
                .startsWith("open ")) {

            String appName = question.substring(5).trim();

            addChatMessage("You", question);

            openInstalledApplication(appName);

            return;
        }

        addChatMessage("You", question);

        updateStatus("P AI is thinking...");

        sendButton.setEnabled(false);

        requestInProgress = true;

        try {

            aiExecutor.execute(() -> {

                HttpURLConnection connection = null;

                try {

                    URL url = new URL(API_URL);

                    connection = (HttpURLConnection)
                            url.openConnection();

                    connection.setRequestMethod("POST");

                    connection.setConnectTimeout(20000);

                    connection.setReadTimeout(30000);

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

                    byte[] requestBytes =
                            requestBody.toString().getBytes(
                                    StandardCharsets.UTF_8
                            );

                    connection.setFixedLengthStreamingMode(
                            requestBytes.length
                    );

                    try (OutputStream outputStream =
                                 connection.getOutputStream()) {

                        outputStream.write(requestBytes);

                        outputStream.flush();
                    }

                    int responseCode =
                            connection.getResponseCode();

                    InputStream inputStream;

                    if (responseCode >= 200
                            && responseCode < 300) {

                        inputStream = connection.getInputStream();

                    } else {

                        inputStream = connection.getErrorStream();
                    }

                    if (inputStream == null) {

                        throw new Exception(
                                "The server returned no response."
                        );
                    }

                    StringBuilder responseBuilder =
                            new StringBuilder();

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

                    String responseText =
                            responseBuilder.toString();

                    JSONObject responseJson =
                            new JSONObject(responseText);

                    if (responseCode >= 200
                            && responseCode < 300) {

                        boolean success =
                                responseJson.optBoolean(
                                        "success",
                                        false
                                );

                        String reply =
                                responseJson.optString(
                                        "reply",
                                        ""
                                );

                        if (!success || reply.trim().isEmpty()) {

                            throw new Exception(
                                    responseJson.optString(
                                            "error",
                                            "The AI returned an invalid response."
                                    )
                            );
                        }

                        runOnUiThread(() -> {

                            if (isActivityDestroyed) {
                                return;
                            }

                            addChatMessage("P AI", reply);

                            updateStatus("Ready");

                            sendButton.setEnabled(true);

                            requestInProgress = false;

                            speak(reply);
                        });

                    } else {

                        String errorMessage =
                                responseJson.optString(
                                        "error",
                                        "The server could not process your request."
                                );

                        throw new Exception(
                                "Server error "
                                        + responseCode
                                        + ": "
                                        + errorMessage
                        );
                    }

                } catch (Exception e) {

                    String errorMessage = e.getMessage();

                    if (errorMessage == null) {

                        errorMessage =
                                "An unexpected error occurred.";
                    }

                    final String displayError = errorMessage;

                    runOnUiThread(() -> {

                        if (isActivityDestroyed) {
                            return;
                        }

                        addChatMessage(
                                "System",
                                "Error: " + displayError
                        );

                        updateStatus("Connection failed");

                        sendButton.setEnabled(true);

                        requestInProgress = false;
                    });

                } finally {

                    if (connection != null) {

                        connection.disconnect();
                    }
                }
            });

        } catch (Exception e) {

            requestInProgress = false;

            sendButton.setEnabled(true);

            updateStatus("Unable to start AI request.");

            addChatMessage(
                    "System",
                    "Error: " + e.getMessage()
            );
        }
    }

    private void openInstalledApplication(String appName) {

        if (appName == null || appName.trim().isEmpty()) {

            addChatMessage(
                    "P AI",
                    "Please specify the application you want to open."
            );

            return;
        }

        try {

            android.content.Intent launchIntent = null;

            String matchedAppName = null;

            android.content.Intent queryIntent =
                    new android.content.Intent(
                            android.content.Intent.ACTION_MAIN
                    );

            queryIntent.addCategory(
                    android.content.Intent.CATEGORY_LAUNCHER
            );

            java.util.List<android.content.pm.ResolveInfo> apps =
                    getPackageManager().queryIntentActivities(
                            queryIntent,
                            0
                    );

            for (android.content.pm.ResolveInfo app : apps) {

                String label = app.loadLabel(
                        getPackageManager()
                ).toString();

                if (label.equalsIgnoreCase(appName.trim())) {

                    launchIntent =
                            getPackageManager()
                                    .getLaunchIntentForPackage(
                                            app.activityInfo.packageName
                                    );

                    matchedAppName = label;

                    break;
                }
            }

            if (launchIntent != null) {

                addChatMessage(
                        "P AI",
                        "Opening " + matchedAppName + "..."
                );

                startActivity(launchIntent);

            } else {

                addChatMessage(
                        "P AI",
                        "I couldn't find an installed application named "
                                + appName + "."
                );
            }

        } catch (Exception e) {

            addChatMessage(
                    "P AI",
                    "I couldn't open that application."
            );
        }
    }

    private void hideKeyboard() {

        try {

            InputMethodManager manager =
                    (InputMethodManager)
                            getSystemService(
                                    Context.INPUT_METHOD_SERVICE
                            );

            if (manager != null) {

                manager.hideSoftInputFromWindow(
                        messageInput.getWindowToken(),
                        0
                );
            }

        } catch (Exception ignored) {
        }
    }

    // PART 4 CONTINUES BELOW
    private boolean backgroundVoiceActive = false;

    private void toggleBackgroundListening() {

        if (backgroundVoiceActive) {

            stopBackgroundListening();

        } else {

            startBackgroundListening();
        }
    }

    @Override
    protected void onDestroy() {

        isActivityDestroyed = true;

        if (speechRecognizer != null) {

            speechRecognizer.destroy();

            speechRecognizer = null;
        }

        if (textToSpeech != null) {

            textToSpeech.stop();

            textToSpeech.shutdown();

            textToSpeech = null;
        }

        aiExecutor.shutdownNow();

        super.onDestroy();
    }

}