package com.pgraphics.paisystemmanager;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
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
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * P AI SYSTEM MANAGER
 *
 * Main chat screen.
 *
 * Responsibilities:
 * - Online AI communication.
 * - Manual online speech recognition.
 * - User-controlled text-to-speech.
 * - Installed application launching.
 * - Network and error handling.
 */
public class MainActivity extends Activity {

    // ============================================================
    // CONFIGURATION
    // ============================================================

    private static final String API_URL =
            "https://p-ai-system-manager-api.vercel.app/api/chat";

    private static final int AUDIO_PERMISSION_REQUEST = 1001;
    private static final int SPEECH_REQUEST = 1002;

    private static final int CONNECT_TIMEOUT_MS = 20000;
    private static final int READ_TIMEOUT_MS = 45000;
    private static final int MAX_TTS_CHUNK_LENGTH = 3000;

    private static final int BACKGROUND_COLOR =
            Color.rgb(15, 15, 20);

    private static final int ACCENT_COLOR =
            Color.rgb(230, 180, 70);

    // ============================================================
    // USER INTERFACE
    // ============================================================

    private LinearLayout rootLayout;
    private LinearLayout chatContainer;
    private ScrollView chatScroll;

    private EditText messageInput;

    private TextView statusText;

    private Button sendButton;
    private Button voiceButton;

    // ============================================================
    // SYSTEM COMPONENTS
    // ============================================================

    private TextToSpeech textToSpeech;

    private final ExecutorService aiExecutor =
            Executors.newSingleThreadExecutor();

    // ============================================================
    // APPLICATION STATE
    // ============================================================

    private volatile boolean isActivityDestroyed = false;

    private boolean requestInProgress = false;
    private boolean textToSpeechReady = false;

    private Button activeSpeechButton;

    private String activeSpeechRequestId;
    private String activeSpeechFinalUtteranceId;

    // ============================================================
    // ACTIVITY LIFECYCLE
    // ============================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        buildInterface();

        initializeTextToSpeech();

        updateStatus("Ready");
    }

    @Override
    protected void onDestroy() {
        isActivityDestroyed = true;

        stopCurrentSpeech();

        if (textToSpeech != null) {
            try {
                textToSpeech.stop();
                textToSpeech.shutdown();
            } catch (Exception ignored) {
                // Speech engine may already be released.
            }

            textToSpeech = null;
        }

        aiExecutor.shutdownNow();

        super.onDestroy();
    }

    // ============================================================
    // INTERFACE CONSTRUCTION
    // ============================================================

    private void buildInterface() {

        rootLayout = new LinearLayout(this);

        rootLayout.setOrientation(LinearLayout.VERTICAL);

        rootLayout.setPadding(
                dp(16),
                dp(12),
                dp(16),
                dp(10)
        );

        rootLayout.setBackgroundColor(BACKGROUND_COLOR);

        // Application title

        TextView title = new TextView(this);

        title.setText("P AI SYSTEM MANAGER");

        title.setTextSize(22);

        title.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        title.setTextColor(ACCENT_COLOR);

        title.setGravity(Gravity.CENTER);

        title.setPadding(
                0,
                dp(8),
                0,
                dp(8)
        );

        rootLayout.addView(
                title,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        // Status display

        statusText = new TextView(this);

        statusText.setText("Starting P AI...");

        statusText.setTextSize(13);

        statusText.setTextColor(Color.LTGRAY);

        statusText.setGravity(Gravity.CENTER);

        statusText.setPadding(
                0,
                dp(3),
                0,
                dp(10)
        );

        rootLayout.addView(
                statusText,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        // Chat scrolling area

        chatScroll = new ScrollView(this);

        chatScroll.setFillViewport(true);

        chatScroll.setSmoothScrollingEnabled(true);

        chatContainer = new LinearLayout(this);

        chatContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        chatContainer.setPadding(
                dp(3),
                dp(4),
                dp(3),
                dp(8)
        );

        chatScroll.addView(
                chatContainer,
                new ScrollView.LayoutParams(
                        ScrollView.LayoutParams.MATCH_PARENT,
                        ScrollView.LayoutParams.WRAP_CONTENT
                )
        );

        rootLayout.addView(
                chatScroll,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1
                )
        );

        // Message input

        messageInput = new EditText(this);

        messageInput.setHint("Ask P AI anything...");

        messageInput.setTextColor(Color.WHITE);

        messageInput.setHintTextColor(Color.GRAY);

        messageInput.setTextSize(16);

        messageInput.setSingleLine(false);

        messageInput.setMinLines(2);

        messageInput.setMaxLines(5);

        messageInput.setGravity(
                Gravity.TOP | Gravity.START
        );

        messageInput.setPadding(
                dp(12),
                dp(10),
                dp(12),
                dp(10)
        );

        rootLayout.addView(
                messageInput,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        // Action buttons

        sendButton = createButton("SEND MESSAGE");

        voiceButton = createButton("VOICE INPUT");

        rootLayout.addView(sendButton);

        rootLayout.addView(voiceButton);

        sendButton.setOnClickListener(
                v -> sendMessage()
        );

        voiceButton.setOnClickListener(
                v -> startManualVoiceRecognition()
        );

        setContentView(rootLayout);

        addChatMessage(
                "P AI",
                "Hello! I am P AI, your personal assistant. How can I help you?"
        );
    }

    private Button createButton(String label) {

        Button button = new Button(this);

        button.setText(label);

        button.setTextColor(Color.BLACK);

        button.setTextSize(13);

        button.setAllCaps(false);

        button.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        ACCENT_COLOR
                )
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.setMargins(
                0,
                dp(4),
                0,
                dp(4)
        );

        button.setLayoutParams(params);

        return button;
    }

    // ============================================================
    // CHAT MESSAGE DISPLAY
    // ============================================================

    private void addChatMessage(
            String sender,
            String message
    ) {

        if (isActivityDestroyed || chatContainer == null) {
            return;
        }

        LinearLayout messageCard = new LinearLayout(this);

        messageCard.setOrientation(
                LinearLayout.VERTICAL
        );

        messageCard.setPadding(
                dp(12),
                dp(9),
                dp(12),
                dp(8)
        );

        if ("You".equalsIgnoreCase(sender)) {

            messageCard.setBackgroundColor(
                    Color.rgb(42, 42, 52)
            );

        } else if ("P AI".equalsIgnoreCase(sender)) {

            messageCard.setBackgroundColor(
                    Color.rgb(31, 35, 43)
            );

        } else {

            messageCard.setBackgroundColor(
                    Color.rgb(55, 39, 39)
            );
        }

        TextView senderView = new TextView(this);

        senderView.setText(sender);

        senderView.setTextSize(13);

        senderView.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        senderView.setTextColor(
                "P AI".equalsIgnoreCase(sender)
                        ? ACCENT_COLOR
                        : Color.LTGRAY
        );

        messageCard.addView(
                senderView,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        TextView messageView = new TextView(this);

        messageView.setText(
                message == null ? "" : message
        );

        messageView.setTextSize(15);

        messageView.setTextColor(Color.WHITE);

        messageView.setTextIsSelectable(true);

        messageView.setLineSpacing(
                dp(2),
                1.0f
        );

        messageView.setPadding(
                0,
                dp(5),
                0,
                dp(3)
        );

        messageCard.addView(
                messageView,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        // Add speaker control only to P AI messages.

if ("P AI".equalsIgnoreCase(sender)
                && message != null
                && !message.trim().isEmpty()) {

            Button readButton = new Button(this);

            readButton.setText("🔊  Read aloud");

            readButton.setTextSize(12);

            readButton.setAllCaps(false);

            readButton.setTextColor(ACCENT_COLOR);

            readButton.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            Color.rgb(45, 45, 53)
                    )
            );

            LinearLayout.LayoutParams readParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );

            readParams.topMargin = dp(5);

            messageCard.addView(
                    readButton,
                    readParams
            );

            readButton.setOnClickListener(
                    v -> handleReadAloudClick(
                            message,
                            readButton
                    )
            );
        }

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(
                0,
                dp(5),
                0,
                dp(5)
        );

        chatContainer.addView(
                messageCard,
                cardParams
        );

        chatScroll.post(
                () -> chatScroll.fullScroll(
                        ScrollView.FOCUS_DOWN
                )
        );
    }

    // ============================================================
    // STATUS AND UTILITY METHODS
    // ============================================================

    private void updateStatus(String message) {

        if (!isActivityDestroyed && statusText != null) {
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

    private void showMessage(String message) {

        if (!isActivityDestroyed) {

            Toast.makeText(
                    this,
                    message,
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void hideKeyboard() {

        try {

            InputMethodManager manager =
                    (InputMethodManager) getSystemService(
                            Context.INPUT_METHOD_SERVICE
                    );

            if (manager != null && messageInput != null) {

                manager.hideSoftInputFromWindow(
                        messageInput.getWindowToken(),
                        0
                );
            }

        } catch (Exception ignored) {
            // Keyboard visibility is not critical.
        }
    }

    // ============================================================
    // TEXT TO SPEECH
    // ============================================================

    private void initializeTextToSpeech() {

        textToSpeech = new TextToSpeech(
                this,
                status -> {

                    if (isActivityDestroyed || textToSpeech == null) {
                        return;
                    }

                    if (status != TextToSpeech.SUCCESS) {

                        textToSpeechReady = false;

                        updateStatus(
                                "Text-to-speech is unavailable."
                        );

                        return;
                    }

                    int languageResult =
                            textToSpeech.setLanguage(Locale.US);

                    textToSpeechReady =
                            languageResult
                                    != TextToSpeech.LANG_MISSING_DATA
                                    && languageResult
                                    != TextToSpeech.LANG_NOT_SUPPORTED;

                    if (textToSpeechReady) {

                        updateStatus("Ready");

                    } else {

                        updateStatus(
                                "English speech data is unavailable."
                        );
                    }

                    textToSpeech.setOnUtteranceProgressListener(
                            new UtteranceProgressListener() {

                                @Override
                                public void onStart(String utteranceId) {
                                    // Playback started.
                                }

                                @Override
                                public void onDone(String utteranceId) {

                                    finishSpeechIfCurrent(
                                            utteranceId
                                    );
                                }

                                @Override
                                public void onError(String utteranceId) {

                                    finishSpeechIfCurrent(
                                            utteranceId
                                    );
                                }

                                @Override
                                public void onError(
                                        String utteranceId,
                                        int errorCode
                                ) {

                                    finishSpeechIfCurrent(
                                            utteranceId
                                    );
                                }
                            }
                    );
                }
        );
    }

    private void handleReadAloudClick(
            String message,
            Button button
    ) {

        if (!textToSpeechReady || textToSpeech == null) {

            showMessage(
                    "Speech is not ready yet. Please try again shortly."
            );

            return;
        }

        if (button == activeSpeechButton) {

            stopCurrentSpeech();

            return;
        }

        startReadingMessage(
                message,
                button
        );
    }

    private void startReadingMessage(
            String message,
            Button button
    ) {

        if (textToSpeech == null || !textToSpeechReady) {

            showMessage(
                    "Text-to-speech is not available."
            );

            return;
        }

        stopCurrentSpeech();

        String cleanText =
                message == null ? "" : message.trim();

        if (cleanText.isEmpty()) {
            return;
        }

        activeSpeechRequestId =
                UUID.randomUUID().toString();

        activeSpeechButton = button;

        button.setText("■  Stop reading");

        List<String> chunks = splitTextForSpeech(
                cleanText,
                MAX_TTS_CHUNK_LENGTH
        );

        if (chunks.isEmpty()) {

            resetSpeechButton();

            return;
        }

        activeSpeechFinalUtteranceId =
                activeSpeechRequestId
                        + "_part_"
                        + (chunks.size() - 1);

        for (int i = 0; i < chunks.size(); i++) {

            String utteranceId =
                    activeSpeechRequestId
                            + "_part_"
                            + i;

            int queueMode =
                    i == 0
                            ? TextToSpeech.QUEUE_FLUSH
                            : TextToSpeech.QUEUE_ADD;

            int result = textToSpeech.speak(
                    chunks.get(i),
                    queueMode,
                    null,
                    utteranceId
            );

            if (result == TextToSpeech.ERROR) {

                stopCurrentSpeech();

                showMessage(
                        "P AI could not start speaking this response."
                );

                return;
            }
        }
    }

    private List<String> splitTextForSpeech(
            String text,
            int maxLength
    ) {

        List<String> chunks = new ArrayList<>();

        if (text == null || text.trim().isEmpty()) {
            return chunks;
        }

        String remaining = text.trim();

        while (remaining.length() > maxLength) {

            int splitAt =
                    remaining.lastIndexOf(
                            ' ',
                            maxLength
                    );

            if (splitAt <= 0) {
                splitAt = maxLength;
            }

            // Avoid splitting a UTF-16 surrogate pair.

            if (splitAt > 0
                    && splitAt < remaining.length()
                    && Character.isHighSurrogate(
                            remaining.charAt(splitAt - 1)
                    )
                    && Character.isLowSurrogate(
                            remaining.charAt(splitAt)
                    )) {

                splitAt--;
            }

            chunks.add(
                    remaining.substring(0, splitAt).trim()
            );

            remaining =
                    remaining.substring(splitAt).trim();
        }

        if (!remaining.isEmpty()) {
            chunks.add(remaining);
        }

        return chunks;
    }

    private void finishSpeechIfCurrent(
            String utteranceId
    ) {

        runOnUiThread(() -> {

            if (isActivityDestroyed) {
                return;
            }

            if (activeSpeechFinalUtteranceId != null
                    && activeSpeechFinalUtteranceId.equals(
                            utteranceId
                    )) {

                resetSpeechButton();
            }
        });
    }

    private void stopCurrentSpeech() {

        if (textToSpeech != null) {

            try {

                textToSpeech.stop();

            } catch (Exception ignored) {
                // Continue resetting the interface.
            }
        }

        resetSpeechButton();
    }

    private void resetSpeechButton() {

        if (activeSpeechButton != null) {

            activeSpeechButton.setText(
                    "🔊  Read aloud"
            );
        }

        activeSpeechButton = null;

        activeSpeechRequestId = null;

        activeSpeechFinalUtteranceId = null;
    }

    // ============================================================
    // MANUAL ONLINE VOICE INPUT
    // ============================================================

private boolean hasAudioPermission() {

        return checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void startManualVoiceRecognition() {

        if (!hasAudioPermission()) {

            requestPermissions(
                    new String[]{
                            Manifest.permission.RECORD_AUDIO
                    },
                    AUDIO_PERMISSION_REQUEST
            );

            updateStatus(
                    "Microphone permission is required for voice input."
            );

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

        intent.putExtra(
                RecognizerIntent.EXTRA_MAX_RESULTS,
                1
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

                showMessage(
                        "You can now use voice input."
                );

            } else {

                updateStatus(
                        "Microphone permission denied."
                );

                showMessage(
                        "Microphone permission is needed for voice input."
                );
            }
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

                if (messageInput != null) {

                    messageInput.setText(spokenText);

                    messageInput.setSelection(
                            messageInput.getText().length()
                    );
                }

                sendMessage();
            }
        }
    }

    // ============================================================
    // AI CHAT REQUEST HANDLING
    // ============================================================

    private void sendMessage() {

        if (isActivityDestroyed) {
            return;
        }

        if (requestInProgress) {

            showMessage(
                    "P AI is still preparing the previous response."
            );

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

        hideKeyboard();

        // Handle local app-launch commands.

        if (question.toLowerCase(Locale.ROOT)
                .startsWith("open ")) {

            messageInput.setText("");

            addChatMessage(
                    "You",
                    question
            );

            openInstalledApplication(
                    question.substring(5).trim()
            );

            return;
        }

        messageInput.setText("");

        addChatMessage(
                "You",
                question
        );

        requestInProgress = true;

        sendButton.setEnabled(false);

        sendButton.setText("PLEASE WAIT...");

        updateStatus(
                "P AI is thinking..."
        );

        try {

            aiExecutor.execute(
                    () -> performAIRequest(question)
            );

        } catch (Exception e) {

            requestInProgress = false;

            restoreSendButton();

            addChatMessage(
                    "System",
                    "P AI could not start the request. Please try again."
            );

            updateStatus(
                    "Unable to start AI request."
            );
        }
    }

    private void performAIRequest(String question) {

        HttpURLConnection connection = null;

        try {

            URL url = new URL(API_URL);

            connection =
                    (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");

            connection.setConnectTimeout(
                    CONNECT_TIMEOUT_MS
            );

            connection.setReadTimeout(
                    READ_TIMEOUT_MS
            );

            connection.setDoOutput(true);

            connection.setUseCaches(false);

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

            InputStream responseStream =
                    responseCode >= 200 && responseCode < 300
                            ? connection.getInputStream()
                            : connection.getErrorStream();

            if (responseStream == null) {

                throw new Exception(
                        "The server did not return a response. Please try again."
                );
            }

            String responseText =
                    readResponse(responseStream);

            JSONObject responseJson =
                    new JSONObject(responseText);

            if (responseCode < 200 || responseCode >= 300) {

                String serverError =
                        responseJson.optString(
                                "error",
                                "The AI service could not process your request."
                        );

                throw new Exception(
                        "Server error ("
                                + responseCode
                                + "): "
                                + serverError
                );
            }

            boolean success =
                    responseJson.optBoolean(
                            "success",
                            false
                    );

            String reply =
                    responseJson.optString(
                            "reply",
                            ""
                    ).trim();

            if (!success || reply.isEmpty()) {

                String serverError =
                        responseJson.optString(
                                "error",
                                "The AI returned an empty or invalid response."
                        );

                throw new Exception(serverError);
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

                requestInProgress = false;

                restoreSendButton();

                // Automatic speech is intentionally disabled.
                // The user must tap the speaker button.
            });

        } catch (Exception e) {

            String errorMessage = e.getMessage();

            if (errorMessage == null
                    || errorMessage.trim().isEmpty()) {

                errorMessage =
                        "An unexpected connection error occurred.";
            }

            final String displayError = errorMessage;

            runOnUiThread(() -> {

                if (isActivityDestroyed) {
                    return;
                }

                addChatMessage(
                        "System",
                        "I couldn't get a response: "
                                + displayError
                );

                updateStatus(
                        "Connection failed"
                );

                requestInProgress = false;

                restoreSendButton();
            });

        } finally {

            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readResponse(
            InputStream inputStream
    ) throws Exception {

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

                responseBuilder
                        .append(line)
                        .append('\n');
            }
        }

        return responseBuilder.toString();
    }

    private void restoreSendButton() {

        if (sendButton != null && !isActivityDestroyed) {

            sendButton.setEnabled(true);

            sendButton.setText(
                    "SEND MESSAGE"
            );
        }
    }

    // ============================================================
    // INSTALLED APPLICATION LAUNCHING
    // ============================================================

private void openInstalledApplication(
            String appName
    ) {

        if (appName == null || appName.trim().isEmpty()) {

            addChatMessage(
                    "P AI",
                    "Please specify the application you want to open."
            );

            return;
        }

        String requestedName = appName.trim();

        try {

            Intent launcherQuery =
                    new Intent(Intent.ACTION_MAIN);

            launcherQuery.addCategory(
                    Intent.CATEGORY_LAUNCHER
            );

            List<ResolveInfo> installedApps =
                    getPackageManager().queryIntentActivities(
                            launcherQuery,
                            0
                    );

            ResolveInfo exactMatch = null;

            ResolveInfo partialMatch = null;

            for (ResolveInfo app : installedApps) {

                if (app.activityInfo == null) {
                    continue;
                }

                String label =
                        app.loadLabel(
                                getPackageManager()
                        ).toString();

                if (label.equalsIgnoreCase(requestedName)) {

                    exactMatch = app;

                    break;
                }

                if (partialMatch == null
                        && label.toLowerCase(Locale.ROOT)
                        .contains(
                                requestedName.toLowerCase(Locale.ROOT)
                        )) {

                    partialMatch = app;
                }
            }

            ResolveInfo selectedApp =
                    exactMatch != null
                            ? exactMatch
                            : partialMatch;

            if (selectedApp == null) {

                addChatMessage(
                        "P AI",
                        "I couldn't find an installed application named "
                                + requestedName + "."
                );

                return;
            }

            String packageName =
                    selectedApp.activityInfo.packageName;

            Intent launchIntent =
                    getPackageManager()
                            .getLaunchIntentForPackage(
                                    packageName
                            );

            if (launchIntent == null) {

                addChatMessage(
                        "P AI",
                        "I found "
                                + selectedApp.loadLabel(
                                        getPackageManager()
                                )
                                + ", but Android did not provide a launch action."
                );

                return;
            }

            addChatMessage(
                    "P AI",
                    "Opening "
                            + selectedApp.loadLabel(
                                    getPackageManager()
                            )
                            + "..."
            );

            startActivity(launchIntent);

        } catch (Exception e) {

            addChatMessage(
                    "P AI",
                    "I couldn't open that application. Please try again."
            );
        }
    }
}