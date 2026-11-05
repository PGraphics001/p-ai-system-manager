package com.pgraphics.paisystemmanager;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    // ==================================================
    // CONFIGURATION
    // ==================================================

    private static final String API_URL =
            "https://p-ai-system-manager-api.vercel.app/api/chat";

    private static final String ACTION_START_VOICE =
            "com.pgraphics.paisystemmanager.START_VOICE";

    private static final String ACTION_STOP_VOICE =
            "com.pgraphics.paisystemmanager.STOP_VOICE";

    private static final int AUDIO_PERMISSION_REQUEST = 1001;

    private static final int SPEECH_REQUEST = 1002;

    // ==================================================
    // USER INTERFACE
    // ==================================================

    private LinearLayout rootLayout;

    private LinearLayout chatContainer;

    private EditText messageInput;

    private TextView statusText;

    private Button sendButton;

    private Button voiceButton;

    private Button offlineVoiceButton;

    private Button backgroundVoiceButton;

    // ==================================================
    // SYSTEM COMPONENTS
    // ==================================================

    private TextToSpeech textToSpeech;

    private SpeechRecognizer offlineSpeechRecognizer;

    private final ExecutorService aiExecutor =
            Executors.newSingleThreadExecutor();

    // ==================================================
    // APPLICATION STATE
    // ==================================================

    private boolean isActivityDestroyed = false;

    private boolean requestInProgress = false;

    private boolean textToSpeechReady = false;

    private boolean backgroundVoiceActive = false;

    private boolean offlineRecognitionActive = false;

    // ==================================================
    // ACTIVITY INITIALIZATION
    // ==================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        buildInterface();

        initializeTextToSpeech();

        checkAudioPermission();
    }

    // ==================================================
    // INTERFACE CREATION
    // ==================================================

    private void buildInterface() {

        rootLayout = new LinearLayout(this);

        rootLayout.setOrientation(
                LinearLayout.VERTICAL
        );

        rootLayout.setPadding(
                dp(16),
                dp(16),
                dp(16),
                dp(12)
        );

        rootLayout.setBackgroundColor(
                Color.rgb(15, 15, 20)
        );

        // APPLICATION TITLE

        TextView title = new TextView(this);

        title.setText("P AI SYSTEM MANAGER");

        title.setTextSize(23);

        title.setTextColor(
                Color.rgb(230, 180, 70)
        );

        title.setGravity(Gravity.CENTER);

        title.setPadding(
                0,
                dp(10),
                0,
                dp(12)
        );

        rootLayout.addView(
                title,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                )
        );

        // STATUS DISPLAY

        statusText = new TextView(this);

        statusText.setText("P AI is starting...");

        statusText.setTextSize(14);

        statusText.setTextColor(Color.LTGRAY);

        statusText.setGravity(Gravity.CENTER);

        statusText.setPadding(
                0,
                dp(5),
                0,
                dp(12)
        );

        rootLayout.addView(
                statusText,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                )
        );

        // CHAT DISPLAY

        ScrollView chatScroll = new ScrollView(this);

        chatContainer = new LinearLayout(this);

        chatContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        chatContainer.setPadding(
                dp(5),
                dp(5),
                dp(5),
                dp(5)
        );

        chatScroll.addView(chatContainer);

        rootLayout.addView(
                chatScroll,
                new LinearLayout.LayoutParams(
                        -1,
                        0,
                        1
                )
        );

        // MESSAGE INPUT

        messageInput = new EditText(this);

        messageInput.setHint(
                "Ask P AI anything..."
        );

        messageInput.setTextColor(Color.WHITE);

        messageInput.setHintTextColor(Color.GRAY);

        messageInput.setTextSize(16);

        messageInput.setSingleLine(false);

        messageInput.setMinLines(2);

        messageInput.setMaxLines(4);

        rootLayout.addView(
                messageInput,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                )
        );

        // BUTTONS

        sendButton = createButton(
                "SEND MESSAGE"
        );

        voiceButton = createButton(
                "VOICE INPUT"
        );

        offlineVoiceButton = createButton(
                "TEST OFFLINE VOICE"
        );

        backgroundVoiceButton = createButton(
                "START BACKGROUND VOICE"
        );

        rootLayout.addView(sendButton);

        rootLayout.addView(voiceButton);

        rootLayout.addView(offlineVoiceButton);

        rootLayout.addView(backgroundVoiceButton);

        // BUTTON ACTIONS

        sendButton.setOnClickListener(
                v -> sendMessage()
        );

        voiceButton.setOnClickListener(
                v -> startManualVoiceRecognition()
        );

        offlineVoiceButton.setOnClickListener(
                v -> startOfflineVoiceTest()
        );

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

    // ==================================================
    // BUTTON FACTORY
    // ==================================================

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
                        -1,
                        -2
                );

        params.setMargins(
                0,
                dp(5),
                0,
                dp(5)
        );

        button.setLayoutParams(params);

        return button;
    }

    // ==================================================
    // CHAT MESSAGE DISPLAY
    // ==================================================

    private void addChatMessage(
            String sender,
            String message
    ) {

        if (isActivityDestroyed || chatContainer == null) {
            return;
        }

        TextView chatMessage = new TextView(this);

        chatMessage.setText(
                sender + ":\n" + message
        );

        chatMessage.setTextSize(15);

        chatMessage.setTextColor(Color.WHITE);

        chatMessage.setPadding(
                dp(10),
                dp(10),
                dp(10),
                dp(10)
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        params.setMargins(
                0,
                dp(5),
                0,
                dp(5)
        );

        chatContainer.addView(
                chatMessage,
                params
        );
    }

    // ==================================================
    // STATUS MANAGEMENT
    // ==================================================

    private void updateStatus(String message) {

        if (!isActivityDestroyed && statusText != null) {

            statusText.setText(message);
        }
    }

    // ==================================================
    // DISPLAY DENSITY CONVERSION
    // ==================================================

    private int dp(float value) {

        return (int) (
                value * getResources()
                        .getDisplayMetrics()
                        .density + 0.5f
        );
    }

    // ==================================================
    // MICROPHONE PERMISSIONS
    // ==================================================

    private boolean hasAudioPermission() {

        return checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void checkAudioPermission() {

        if (!hasAudioPermission()) {

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

                updateStatus(
                        "Microphone permission granted."
                );

            } else {

                updateStatus(
                        "Microphone permission denied."
                );

                Toast.makeText(
                        this,
                        "P AI needs microphone permission for voice recognition.",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    // ==================================================
    // TEXT-TO-SPEECH INITIALIZATION
    // ==================================================

    private void initializeTextToSpeech() {

        textToSpeech = new TextToSpeech(
                this,
                status -> {

                    if (isActivityDestroyed) {
                        return;
                    }

                    if (status == TextToSpeech.SUCCESS
                            && textToSpeech != null) {

                        int result =
                                textToSpeech.setLanguage(
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

    // ==================================================
    // TEXT-TO-SPEECH OUTPUT
    // ==================================================

    private void speak(String message) {

        if (textToSpeech != null
                && textToSpeechReady
                && !isActivityDestroyed) {

            textToSpeech.speak(
                    message,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "PAI_SPEECH"
            );
        }
    }

    // ==================================================
    // EXISTING ONLINE VOICE RECOGNITION
    // ==================================================

    private void startManualVoiceRecognition() {

        if (!hasAudioPermission()) {

            checkAudioPermission();

            return;
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {

            showMessage(
                    "Speech recognition is unavailable on this device."
            );

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

            showMessage(
                    "Unable to start voice recognition."
            );
        }
    }

    // ==================================================
    // ONLINE VOICE RECOGNITION RESULT
    // ==================================================

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

    // ==================================================
    // ANDROID ON-DEVICE OFFLINE VOICE RECOGNITION
    // ==================================================

    private void startOfflineVoiceTest() {

        // Android 12 is required for the dedicated
        // on-device recognition API.

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {

            showMessage(
                    "Dedicated Android on-device recognition requires Android 12 or newer."
            );

            return;
        }

        if (!hasAudioPermission()) {

            checkAudioPermission();

            return;
        }

        if (offlineRecognitionActive) {

            showMessage(
                    "Offline voice recognition is already running."
            );

            return;
        }

        if (offlineSpeechRecognizer != null) {

            destroyOfflineRecognizer();
        }

        try {

            // Check actual device support.

            if (!SpeechRecognizer
                    .isOnDeviceRecognitionAvailable(this)) {

                showMessage(
                        "No available on-device speech recognition service was found."
                );

                updateStatus(
                        "Offline recognition unavailable."
                );

                return;
            }

            // Create the dedicated on-device recognizer.

            offlineSpeechRecognizer =
                    SpeechRecognizer
                            .createOnDeviceSpeechRecognizer(this);

            if (offlineSpeechRecognizer == null) {

                showMessage(
                        "Unable to create the on-device recognition engine."
                );

                return;
            }

            // Register recognition callbacks.

            offlineSpeechRecognizer.setRecognitionListener(
                    createOfflineRecognitionListener()
            );

            // Recognition configuration.

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
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    true
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    3
            );

            // This preference is supplementary:
            // createOnDeviceSpeechRecognizer is what
            // explicitly selects the on-device service.

            intent.putExtra(
                    RecognizerIntent.EXTRA_PREFER_OFFLINE,
                    true
            );

            offlineRecognitionActive = true;

            offlineVoiceButton.setEnabled(false);

            updateStatus(
                    "Preparing offline voice recognition..."
            );

            offlineSpeechRecognizer.startListening(intent);

        } catch (Exception e) {

            destroyOfflineRecognizer();

            String error = e.getMessage();

            if (error == null || error.trim().isEmpty()) {

                error = "An unexpected recognition error occurred.";
            }

            showMessage(error);

            updateStatus(
                    "Unable to start offline recognition."
            );
        }
    }

    // ==================================================
    // OFFLINE RECOGNITION LISTENER
    // ==================================================

    private RecognitionListener createOfflineRecognitionListener() {

        return new RecognitionListener() {

            @Override
            public void onReadyForSpeech(Bundle params) {

                updateStatus(
                        "Offline voice test: Listening..."
                );
            }

            @Override
            public void onBeginningOfSpeech() {

                updateStatus(
                        "I can hear you. Keep speaking..."
                );
            }

            @Override
            public void onRmsChanged(float rmsdB) {
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {

                updateStatus(
                        "Processing your offline speech..."
                );
            }

            @Override
            public void onError(int error) {

                String message =
                        getSpeechErrorMessage(error);

                destroyOfflineRecognizer();

                updateStatus(
                        "Offline recognition failed."
                );

                addChatMessage(
                        "Offline Voice Test",
                        message
                );
            }

            @Override
            public void onResults(Bundle results) {

                ArrayList<String> matches =
                        results.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                        );

                destroyOfflineRecognizer();

                if (matches != null && !matches.isEmpty()) {

                    String recognizedText = matches.get(0);

                    messageInput.setText(recognizedText);

                    addChatMessage(
                            "Offline Voice Test",
                            recognizedText
                    );

                    updateStatus(
                            "Offline recognition completed."
                    );

                } else {

                    addChatMessage(
                            "Offline Voice Test",
                            "No speech was recognised. Please try again."
                    );

                    updateStatus(
                            "No speech recognised."
                    );
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {

                ArrayList<String> partialMatches =
                        partialResults.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                        );

                if (partialMatches != null
                        && !partialMatches.isEmpty()) {

                    updateStatus(
                            "Hearing: "
                                    + partialMatches.get(0)
                    );
                }
            }

            @Override
            public void onEvent(
                    int eventType,
                    Bundle params
            ) {
            }
        };
    }

    // ==================================================
    // OFFLINE RECOGNIZER CLEANUP
    // ==================================================

    private void destroyOfflineRecognizer() {

        SpeechRecognizer recognizer =
                offlineSpeechRecognizer;

        offlineSpeechRecognizer = null;

        offlineRecognitionActive = false;

        if (offlineVoiceButton != null
                && !isActivityDestroyed) {

            offlineVoiceButton.setEnabled(true);
        }

        if (recognizer != null) {

            try {

                recognizer.cancel();

            } catch (Exception ignored) {
            }

            try {

                recognizer.destroy();

            } catch (Exception ignored) {
            }
        }
    }

    // ==================================================
    // SPEECH RECOGNITION ERROR MESSAGES
    // ==================================================

    private String getSpeechErrorMessage(int error) {

        switch (error) {

            case SpeechRecognizer.ERROR_AUDIO:

                return "Audio recording error. Check your microphone.";

            case SpeechRecognizer.ERROR_CLIENT:

                return "Recognition was interrupted. Please try again.";

            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:

                return "Microphone permission is missing.";

            case SpeechRecognizer.ERROR_NETWORK:

                return "The recognition service reported a network error.";

            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:

                return "The recognition service timed out.";

            case SpeechRecognizer.ERROR_NO_MATCH:

                return "No matching speech was recognised.";

            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:

                return "The recognition service is busy. Try again.";

            case SpeechRecognizer.ERROR_SERVER:

                return "The recognition service returned a server error.";

            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:

                return "No speech was detected. Please try again.";

            default:

                return "Speech recognition failed. Error code: "
                        + error;
        }
    }

    // ==================================================
    // BACKGROUND VOICE ASSISTANT
    // ==================================================

    private void startBackgroundListening() {

        if (!hasAudioPermission()) {

            checkAudioPermission();

            updateStatus(
                    "Microphone permission is required."
            );

            return;
        }

        Intent serviceIntent = new Intent(
                this,
                VoiceRecognitionService.class
        );

        serviceIntent.setAction(
                ACTION_START_VOICE
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

            showMessage(
                    "Voice service could not start."
            );
        }
    }

    private void stopBackgroundListening() {

        Intent serviceIntent = new Intent(
                this,
                VoiceRecognitionService.class
        );

        serviceIntent.setAction(
                ACTION_STOP_VOICE
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

    private void toggleBackgroundListening() {

        if (backgroundVoiceActive) {

            stopBackgroundListening();

        } else {

            startBackgroundListening();
        }
    }

    //
==================================================
    // AI MESSAGE PROCESSING
    // ==================================================

    private void sendMessage() {

        if (requestInProgress) {
            return;
        }

        String question = messageInput.getText()
                .toString()
                .trim();

        if (question.isEmpty()) {

            messageInput.setError(
                    "Enter a message first."
            );

            return;
        }

        messageInput.setText("");

        hideKeyboard();

        // Process application commands locally.

        if (question.toLowerCase(Locale.ROOT)
                .startsWith("open ")) {

            String appName = question.substring(5).trim();

            addChatMessage(
                    "You",
                    question
            );

            openInstalledApplication(appName);

            return;
        }

        addChatMessage(
                "You",
                question
        );

        updateStatus(
                "P AI is thinking..."
        );

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

                    requestBody.put(
                            "message",
                            question
                    );

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

                        inputStream =
                                connection.getInputStream();

                    } else {

                        inputStream =
                                connection.getErrorStream();
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

                    JSONObject responseJson =
                            new JSONObject(
                                    responseBuilder.toString()
                            );

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

                        if (!success
                                || reply.trim().isEmpty()) {

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

                            addChatMessage(
                                    "P AI",
                                    reply
                            );

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

                    if (errorMessage == null
                            || errorMessage.trim().isEmpty()) {

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

                        updateStatus(
                                "Connection failed"
                        );

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

            updateStatus(
                    "Unable to start AI request."
            );

            addChatMessage(
                    "System",
                    "Error: " + e.getMessage()
            );
        }
    }

    // ==================================================
    // INSTALLED APPLICATION MANAGEMENT
    // ==================================================

    private void openInstalledApplication(String appName) {

        if (appName == null
                || appName.trim().isEmpty()) {

            addChatMessage(
                    "P AI",
                    "Please specify the application you want to open."
            );

            return;
        }

        try {

            Intent launchIntent = null;

            String matchedAppName = null;

            Intent queryIntent =
                    new Intent(Intent.ACTION_MAIN);

            queryIntent.addCategory(
                    Intent.CATEGORY_LAUNCHER
            );

            List<android.content.pm.ResolveInfo> apps =
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

    // ==================================================
    // KEYBOARD MANAGEMENT
    // ==================================================

    private void hideKeyboard() {

        try {

            InputMethodManager manager =
                    (InputMethodManager)
                            getSystemService(
                                    Context.INPUT_METHOD_SERVICE
                            );

            if (manager != null && messageInput != null) {

                manager.hideSoftInputFromWindow(
                        messageInput.getWindowToken(),
                        0
                );
            }

        } catch (Exception ignored) {
        }
    }

    // ==================================================
    // GENERAL MESSAGE HELPER
    // ==================================================

    private void showMessage(String message) {

        if (!isActivityDestroyed) {

            Toast.makeText(
                    this,
                    message,
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    // ==================================================
    // ACTIVITY CLEANUP
    // ==================================================

    @Override
    protected void onDestroy() {

        isActivityDestroyed = true;

        // Release the offline recognition engine.

        if (offlineSpeechRecognizer != null) {

            SpeechRecognizer recognizer =
                    offlineSpeechRecognizer;

            offlineSpeechRecognizer = null;

            try {

                recognizer.cancel();

            } catch (Exception ignored) {
            }

            try {

                recognizer.destroy();

            } catch (Exception ignored) {
            }
        }

        offlineRecognitionActive = false;

        // Release text-to-speech.

        if (textToSpeech != null) {

            try {

                textToSpeech.stop();

                textToSpeech.shutdown();

            } catch (Exception ignored) {
            }

            textToSpeech = null;
        }

        // Stop pending AI network tasks.

        aiExecutor.shutdownNow();

        super.onDestroy();
    }
}